"""
arcshield.withhold.distributions
==================================
Distribution tracking for the withhold-sampling subsystem.

Two classes:

DistributionPartition
    Maintains Q (live corpus event IDs) and Q' (counterfactual event IDs)
    as strictly separate frozensets. They are NEVER merged (CLAUDE.md §12).

KLDivergenceMonitor
    Computes KL(P‖Q), KL(P‖Q'), and KL(Q‖Q') over failure_mode_tag
    distributions extracted from CIAER+ event records.

    Distributions are estimated by failure_mode_tag frequency with Laplace
    smoothing (epsilon=0.01) to avoid divide-by-zero on unseen categories.
"""

from __future__ import annotations

import json
import math
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


# ---------------------------------------------------------------------------
# DistributionSummary
# ---------------------------------------------------------------------------

@dataclass
class DistributionSummary:
    """
    Aggregate statistics over a set of CIAER+ events.

    Used as input to KLDivergenceMonitor. Computed by
    KLDivergenceMonitor.summarize_events().

    failure_mode_counts:
        Histogram of failure_mode_tag values across the event set.
    outcome_counts:
        Histogram of outcome_tag values.
    mean_graph_weight:
        Mean graph_weight across the event set.
    event_count:
        Total number of events summarized.
    """

    failure_mode_counts: dict[str, int]
    outcome_counts: dict[str, int]
    mean_graph_weight: float
    event_count: int


# ---------------------------------------------------------------------------
# DistributionPartition
# ---------------------------------------------------------------------------

class DistributionPartition:
    """
    Tracks Q (live corpus) and Q' (counterfactual) as separate event ID sets.

    CRITICAL INVARIANT (CLAUDE.md §7.2 / §12 item 5):
        Q and Q' are NEVER merged. This class maintains them as separate
        sets from creation. There is no merge() method by design.

    Thread safety: not thread-safe. Single-process use only.
    """

    def __init__(self) -> None:
        self._q: set[str]       = set()
        self._q_prime: set[str] = set()

    # ------------------------------------------------------------------
    # Write operations
    # ------------------------------------------------------------------

    def add_to_q(self, event_id: str) -> None:
        """
        Add event_id to Q (live corpus — guidance was shown to operator).

        Raises ValueError if event_id is already in Q' (architectural
        invariant: an event cannot be in both partitions simultaneously).
        """
        if event_id in self._q_prime:
            raise ValueError(
                f"Event {event_id!r} is already in Q'. "
                "Events must belong to exactly one partition."
            )
        self._q.add(event_id)

    def add_to_q_prime(self, event_id: str) -> None:
        """
        Add event_id to Q' (counterfactual — guidance was withheld).

        Raises ValueError if event_id is already in Q.
        """
        if event_id in self._q:
            raise ValueError(
                f"Event {event_id!r} is already in Q. "
                "Events must belong to exactly one partition."
            )
        self._q_prime.add(event_id)

    # ------------------------------------------------------------------
    # Read operations
    # ------------------------------------------------------------------

    def get_q_ids(self) -> frozenset[str]:
        """Return an immutable snapshot of Q event IDs."""
        return frozenset(self._q)

    def get_q_prime_ids(self) -> frozenset[str]:
        """Return an immutable snapshot of Q' event IDs."""
        return frozenset(self._q_prime)

    def q_depth(self) -> int:
        """Number of events in Q."""
        return len(self._q)

    def q_prime_depth(self) -> int:
        """Number of events in Q'."""
        return len(self._q_prime)

    def __contains__(self, event_id: str) -> bool:
        """True if event_id is in either partition."""
        return event_id in self._q or event_id in self._q_prime

    # ------------------------------------------------------------------
    # Persistence
    # ------------------------------------------------------------------

    def save(self, path: Path) -> None:
        """
        Save partition state to a JSON file.

        JSON format: {"q_ids": [...], "q_prime_ids": [...]}

        Safe to call on an empty partition. Creates parent directories
        if needed.
        """
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        data = {
            "q_ids":       sorted(self._q),
            "q_prime_ids": sorted(self._q_prime),
        }
        path.write_text(json.dumps(data, indent=2), encoding="utf-8")

    @classmethod
    def load(cls, path: Path) -> "DistributionPartition":
        """
        Load partition state from a JSON file saved by save().

        Returns an empty DistributionPartition if the file does not exist.
        Raises ValueError on malformed JSON.
        """
        path = Path(path)
        instance = cls()
        if not path.exists():
            return instance

        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            raise ValueError(f"Malformed partition file {path}: {exc}") from exc

        for eid in data.get("q_ids", []):
            instance._q.add(str(eid))
        for eid in data.get("q_prime_ids", []):
            instance._q_prime.add(str(eid))

        return instance


# ---------------------------------------------------------------------------
# KLDivergenceMonitor
# ---------------------------------------------------------------------------

class KLDivergenceMonitor:
    """
    Computes KL divergences among P, Q, and Q' distributions.

    The primary distribution signal is failure_mode_tag frequency —
    the most informative indicator of corpus drift (CLAUDE.md §7).

    KL(P‖Q) = Σ_x P(x) · log(P(x) / Q(x))

    Laplace smoothing with epsilon ensures no zero-division on unseen
    categories. The smoothed probability for category x is:

        p(x) = (count(x) + ε) / (total + ε * num_categories)

    where num_categories is the union of categories across both distributions.

    epsilon:
        Smoothing parameter. Default 0.01. Smaller values give sharper
        divergences when one distribution has categories the other lacks.
    """

    def __init__(self, epsilon: float = 0.01) -> None:
        self._epsilon = epsilon

    def compute_kl(
        self,
        p_summary: DistributionSummary,
        q_summary: DistributionSummary,
    ) -> float:
        """
        Compute KL(P‖Q) over the failure_mode_tag distribution.

        Returns 0.0 for identical distributions (within floating-point).
        Always returns a non-negative value.

        Parameters
        ----------
        p_summary:
            Reference distribution (typically pre-Twin baseline P).
        q_summary:
            Comparison distribution (Q or Q').
        """
        p_counts = p_summary.failure_mode_counts
        q_counts = q_summary.failure_mode_counts

        # Union of all categories across both distributions
        all_categories = set(p_counts) | set(q_counts)
        if not all_categories:
            return 0.0

        n_cats = len(all_categories)
        eps    = self._epsilon

        # Total counts for normalization (before smoothing)
        p_total = sum(p_counts.values())
        q_total = sum(q_counts.values())

        # Smoothed totals
        p_total_smooth = p_total + eps * n_cats
        q_total_smooth = q_total + eps * n_cats

        kl = 0.0
        for cat in all_categories:
            p_x = (p_counts.get(cat, 0) + eps) / p_total_smooth
            q_x = (q_counts.get(cat, 0) + eps) / q_total_smooth
            kl += p_x * math.log(p_x / q_x)

        return max(0.0, kl)  # numerical precision guard

    def summarize_events(self, events: list) -> DistributionSummary:
        """
        Compute a DistributionSummary from a list of CIAER+ event objects.

        Accesses fields via getattr for compatibility with both Pydantic models
        and simple mock objects used in tests.

        Returns a DistributionSummary with zero counts when events is empty.
        """
        failure_mode_counts: dict[str, int] = {}
        outcome_counts: dict[str, int]      = {}
        total_weight: float                 = 0.0

        for event in events:
            # failure_mode_tag
            intuition = getattr(event, "intuition", None)
            fm_tag = getattr(intuition, "failure_mode_tag", None) if intuition else None
            if fm_tag:
                failure_mode_counts[fm_tag] = failure_mode_counts.get(fm_tag, 0) + 1

            # outcome_tag
            result = getattr(event, "result", None)
            outcome = getattr(result, "outcome_tag", None) if result else None
            # Handle Enum values
            if outcome is not None:
                outcome_str = outcome.value if hasattr(outcome, "value") else str(outcome)
                outcome_counts[outcome_str] = outcome_counts.get(outcome_str, 0) + 1

            # graph_weight
            gw = getattr(result, "graph_weight", 0.0) if result else 0.0
            total_weight += float(gw)

        count = len(events)
        mean_weight = total_weight / count if count > 0 else 0.0

        return DistributionSummary(
            failure_mode_counts=failure_mode_counts,
            outcome_counts=outcome_counts,
            mean_graph_weight=mean_weight,
            event_count=count,
        )

    def compute_all_divergences(
        self,
        p_events: list,
        q_events: list,
        q_prime_events: list,
    ) -> dict[str, float]:
        """
        Compute D_KL for all three pairings: P‖Q, P‖Q', Q‖Q'.

        Returns
        -------
        Dict with keys: "kl_p_q", "kl_p_q_prime", "kl_q_q_prime".
        All values are 0.0 when the corresponding event lists are empty.
        """
        p_summary       = self.summarize_events(p_events)
        q_summary       = self.summarize_events(q_events)
        q_prime_summary = self.summarize_events(q_prime_events)

        # When Q or Q' is empty, divergence is 0.0 (no signal)
        kl_p_q = (
            self.compute_kl(p_summary, q_summary)
            if q_summary.event_count > 0
            else 0.0
        )
        kl_p_q_prime = (
            self.compute_kl(p_summary, q_prime_summary)
            if q_prime_summary.event_count > 0
            else 0.0
        )
        kl_q_q_prime = (
            self.compute_kl(q_summary, q_prime_summary)
            if q_prime_summary.event_count > 0 and q_summary.event_count > 0
            else 0.0
        )

        return {
            "kl_p_q":       kl_p_q,
            "kl_p_q_prime": kl_p_q_prime,
            "kl_q_q_prime": kl_q_q_prime,
        }
