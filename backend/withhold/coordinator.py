"""
arcshield.withhold.coordinator
================================
WithholdCoordinator — top-level orchestrator for withhold-sampling.

CLAUDE.md §7 architectural contract:
  1. The coordinator maintains Q and Q' as strictly separate partitions.
  2. Phase 2: p_withhold=0.0 → should_withhold() always returns False.
  3. Phase 3+: p_withhold=0.05 → Bernoulli sampling fires for eligible events.
  4. High-weight events (graph_weight > 0.7) are sampled at 1.5× rate because
     they are the most informative counterfactual samples.
  5. Sampling is suppressed until corpus_depth >= min_corpus_depth_to_activate.
  6. D_KL computation fetches events from the backend — P is proxied by the
     earliest 20% of the corpus (pre-Twin baseline).

Thread safety: not thread-safe. Single-process MCP server use only.
"""

from __future__ import annotations

import random
from typing import Any

from .config import WithholdConfig
from .distributions import DistributionPartition, KLDivergenceMonitor


class WithholdCoordinator:
    """
    Withhold-sampling coordinator.

    Phase 2: p_withhold=0.0. Partition tracking is active (Q/Q' are
    maintained), but should_withhold() always returns False, so all
    events enter Q. The infrastructure is ready for Phase 3 activation.

    Phase 3+: Set p_withhold=0.05 in WithholdConfig. No code changes.
    """

    def __init__(
        self,
        backend: object,
        partition: DistributionPartition,
        config: WithholdConfig | None = None,
        rng_seed: int | None = None,
    ) -> None:
        """
        Parameters
        ----------
        backend:
            CorpusBackend-compatible object (duck-typed). Used by
            compute_divergences() to fetch events for P, Q, Q' summaries.
        partition:
            DistributionPartition tracking Q and Q' event ID sets.
            Caller owns the partition; the coordinator mutates it via
            classify_event().
        config:
            WithholdConfig instance. Defaults to WithholdConfig()
            (p_withhold=0.0).
        rng_seed:
            Seed for the internal RNG. Set for reproducibility in tests.
            None → non-deterministic.
        """
        self._backend   = backend
        self._partition = partition
        self.config     = config or WithholdConfig()
        self._rng       = random.Random(rng_seed)
        self._monitor   = KLDivergenceMonitor()

    # ------------------------------------------------------------------
    # Core withhold decision
    # ------------------------------------------------------------------

    def should_withhold(self, event_id: str, graph_weight: float) -> bool:
        """
        Decide whether to withhold advisory guidance for this event.

        Returns False unconditionally when:
          - config.p_withhold == 0.0 (Phase 2 default)
          - backend corpus_depth < config.min_corpus_depth_to_activate

        When active (Phase 3+, p_withhold > 0):
          - Sample Bernoulli(effective_p) where:
              effective_p = p_withhold * 1.5 if graph_weight > 0.7
              effective_p = p_withhold        otherwise
          - Clamp effective_p to [0.0, 1.0].

        High-weight events (graph_weight > 0.7) are sampled at 1.5× rate
        because they represent the most informative counterfactual samples
        — these are events where the Twin has high confidence and the
        operator's independent decision is most revealing.

        Parameters
        ----------
        event_id:
            UUID string of the event being considered.
        graph_weight:
            Current graph_weight of the event (0.0–1.0).
        """
        if self.config.p_withhold == 0.0:
            return False

        corpus_depth = getattr(self._backend, "corpus_depth", 0)
        if corpus_depth < self.config.min_corpus_depth_to_activate:
            return False

        # Effective probability with high-weight boost
        effective_p = self.config.p_withhold
        if graph_weight > 0.7:
            effective_p = min(1.0, effective_p * 1.5)

        return self._rng.random() < effective_p

    # ------------------------------------------------------------------
    # Partition management
    # ------------------------------------------------------------------

    def classify_event(self, event_id: str, is_counterfactual: bool) -> None:
        """
        Route event_id to the correct partition.

        is_counterfactual=True  → add to Q' (guidance was withheld)
        is_counterfactual=False → add to Q  (guidance was shown)

        Raises ValueError (from DistributionPartition) if event_id is
        already in the other partition — this is an architectural invariant
        violation and must not be silently swallowed.
        """
        if is_counterfactual:
            self._partition.add_to_q_prime(event_id)
        else:
            self._partition.add_to_q(event_id)

    # ------------------------------------------------------------------
    # KL divergence monitoring
    # ------------------------------------------------------------------

    async def compute_divergences(self) -> dict[str, float]:
        """
        Compute D_KL(P‖Q), D_KL(P‖Q'), D_KL(Q‖Q') from the corpus.

        P is proxied by the earliest 20% of corpus events (pre-Twin
        baseline). This is a Phase 2/3 approximation; Phase 4+ will use
        a crisp activation timestamp to split P vs. Q.

        Returns
        -------
        Dict: {"kl_p_q": float, "kl_p_q_prime": float, "kl_q_q_prime": float}
        All values are 0.0 when Q is empty.
        """
        q_ids       = self._partition.get_q_ids()
        q_prime_ids = self._partition.get_q_prime_ids()

        if not q_ids:
            return {"kl_p_q": 0.0, "kl_p_q_prime": 0.0, "kl_q_q_prime": 0.0}

        # Fetch Q events
        q_events = await self._fetch_events(q_ids)

        # Fetch Q' events
        q_prime_events = await self._fetch_events(q_prime_ids)

        # Proxy for P: earliest 20% of corpus as pre-Twin baseline
        p_events = await self._fetch_p_proxy()

        return self._monitor.compute_all_divergences(
            p_events=p_events,
            q_events=q_events,
            q_prime_events=q_prime_events,
        )

    async def _fetch_events(self, event_ids: frozenset[str]) -> list:
        """Fetch a set of events from the backend by ID. Silently skips missing IDs."""
        if not event_ids:
            return []
        events = []
        for eid in event_ids:
            try:
                from uuid import UUID  # noqa: PLC0415
                event = await self._backend.get_event(UUID(eid))
                events.append(event)
            except Exception:
                # Missing event or UUID parse failure — skip silently.
                # Corpus events may be deleted or backend may be unavailable.
                pass
        return events

    async def _fetch_p_proxy(self) -> list:
        """
        Fetch events that proxy the pre-Twin P distribution.

        Phase 2/3 approximation: query the earliest failure modes from
        the corpus and take a 20%-depth slice. This is intentionally
        approximate — Phase 4 will use a crisp timestamp split.

        Returns [] when the backend has no events or does not support
        the required query methods.
        """
        try:
            # Use list_failure_modes to get the corpus overview, then
            # query the top failure modes to approximate P.
            failure_modes = await self._backend.list_failure_modes()
        except Exception:
            return []

        if not failure_modes:
            return []

        corpus_depth = getattr(self._backend, "corpus_depth", 0)
        p_target = max(1, int(corpus_depth * 0.2))
        p_events: list = []

        for fm_summary in failure_modes:
            if len(p_events) >= p_target:
                break
            tag = getattr(fm_summary, "failure_mode_tag", None)
            if not tag:
                continue
            try:
                from uuid import UUID  # noqa: PLC0415
                # Try schema import — graceful fallback for test environments
                try:
                    from arcshield.schema import FailureModeQuery  # type: ignore[import]
                    query = FailureModeQuery(
                        failure_mode_tag=tag,
                        top_n=p_target,
                        min_graph_weight=0.0,
                    )
                    events = await self._backend.query_by_failure_mode(query)
                except ImportError:
                    events = await self._backend.query_by_failure_mode(
                        failure_mode_tag=tag, top_n=p_target, min_graph_weight=0.0
                    )
                p_events.extend(events)
            except Exception:
                continue

        return p_events[:p_target]

    # ------------------------------------------------------------------
    # Spike detection
    # ------------------------------------------------------------------

    def kl_spike_detected(self, divergences: dict[str, float]) -> bool:
        """
        Returns True when D_KL(P‖Q) exceeds config.kl_spike_threshold.

        A spike indicates that the live corpus Q has drifted significantly
        from the pre-Twin baseline P. This is the signal to:
          1. Review recent events for reflexivity artifacts.
          2. Increase p_withhold temporarily.
          3. Trigger re-weighting of high-confidence primitives.

        CLAUDE.md §7.1: persistent divergence between Q and Q' is the
        most informative re-weighting signal; kl_spike_detected() only
        flags the first-order drift.
        """
        return divergences.get("kl_p_q", 0.0) > self.config.kl_spike_threshold
