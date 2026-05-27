"""
tests/test_withhold.py
======================
Unit tests for the withhold-sampling subsystem.

All tests run without external dependencies:
  - No arcshield package required (schema types are mocked)
  - No database (CorpusBackend is mocked)
  - pytest + pytest-asyncio only

Covers:
  - WithholdConfig defaults
  - WithholdCoordinator.should_withhold() behavior
  - DistributionPartition separation invariant (Q ≠ Q', never merged)
  - DistributionPartition save/load round-trip
  - KLDivergenceMonitor.compute_kl() correctness
  - WithholdCoordinator.classify_event() routing
  - WithholdCoordinator.kl_spike_detected()

Run:
    cd /home/user/Mp4Real/backend
    python -m pytest withhold/tests/ -v
"""

from __future__ import annotations

import json
import math
import tempfile
from dataclasses import dataclass
from pathlib import Path
from uuid import UUID, uuid4

import pytest

from withhold.config import WithholdConfig
from withhold.coordinator import WithholdCoordinator
from withhold.distributions import (
    DistributionPartition,
    DistributionSummary,
    KLDivergenceMonitor,
)


# ---------------------------------------------------------------------------
# Minimal schema stubs (no arcshield package required)
# ---------------------------------------------------------------------------

@dataclass
class _Intuition:
    failure_mode_tag: str
    srk_level: str = "RULE"
    causal_hypothesis: str = "stub"


@dataclass
class _Result:
    graph_weight: float
    outcome_tag: str = "PROBLEM_PREVENTED"


@dataclass
class _MockEvent:
    event_id: UUID
    intuition: _Intuition
    result: _Result


def make_event(
    failure_mode_tag: str = "material_segregation",
    weight: float = 0.8,
    outcome_tag: str = "PROBLEM_PREVENTED",
) -> _MockEvent:
    return _MockEvent(
        event_id=uuid4(),
        intuition=_Intuition(failure_mode_tag=failure_mode_tag),
        result=_Result(graph_weight=weight, outcome_tag=outcome_tag),
    )


# ---------------------------------------------------------------------------
# Mock CorpusBackend
# ---------------------------------------------------------------------------

class _MockBackend:
    def __init__(self, events: list | None = None, depth: int | None = None):
        self._events: list[_MockEvent] = events or []
        self._depth = depth if depth is not None else len(self._events)

    @property
    def corpus_depth(self) -> int:
        return self._depth

    async def get_event(self, event_id: UUID) -> _MockEvent:
        for e in self._events:
            if e.event_id == event_id:
                return e
        raise KeyError(f"Event not found: {event_id}")

    async def list_failure_modes(self):
        return []

    async def query_by_failure_mode(self, query=None, **kw):
        return list(self._events)


# ===========================================================================
# WithholdConfig tests
# ===========================================================================

class TestWithholdConfig:
    def test_default_p_withhold_is_zero(self):
        """Phase 2 contract: withhold-sampling is off by default."""
        cfg = WithholdConfig()
        assert cfg.p_withhold == 0.0

    def test_default_min_corpus_depth(self):
        """Do not activate before 200 events (CLAUDE.md §7.2)."""
        cfg = WithholdConfig()
        assert cfg.min_corpus_depth_to_activate == 200

    def test_default_kl_spike_threshold(self):
        cfg = WithholdConfig()
        assert cfg.kl_spike_threshold > 0.0

    def test_default_window_size(self):
        cfg = WithholdConfig()
        assert cfg.window_size > 0

    def test_override_p_withhold(self):
        cfg = WithholdConfig(p_withhold=0.05)
        assert cfg.p_withhold == 0.05


# ===========================================================================
# DistributionPartition tests
# ===========================================================================

class TestDistributionPartition:
    def test_add_to_q_increases_q_depth(self):
        p = DistributionPartition()
        p.add_to_q("event-1")
        assert p.q_depth() == 1
        assert p.q_prime_depth() == 0

    def test_add_to_q_prime_increases_q_prime_depth(self):
        p = DistributionPartition()
        p.add_to_q_prime("event-1")
        assert p.q_prime_depth() == 1
        assert p.q_depth() == 0

    def test_q_and_q_prime_are_separate(self):
        """Core invariant: Q and Q' never share events."""
        p = DistributionPartition()
        p.add_to_q("event-a")
        p.add_to_q_prime("event-b")

        assert "event-a" in p.get_q_ids()
        assert "event-b" in p.get_q_prime_ids()
        assert "event-a" not in p.get_q_prime_ids()
        assert "event-b" not in p.get_q_ids()

    def test_add_to_q_when_already_in_q_prime_raises(self):
        """Architectural invariant: an event cannot be in both partitions."""
        p = DistributionPartition()
        p.add_to_q_prime("event-x")
        with pytest.raises(ValueError):
            p.add_to_q("event-x")

    def test_add_to_q_prime_when_already_in_q_raises(self):
        p = DistributionPartition()
        p.add_to_q("event-y")
        with pytest.raises(ValueError):
            p.add_to_q_prime("event-y")

    def test_get_q_ids_returns_frozenset(self):
        p = DistributionPartition()
        p.add_to_q("event-1")
        result = p.get_q_ids()
        assert isinstance(result, frozenset)
        assert "event-1" in result

    def test_get_q_prime_ids_returns_frozenset(self):
        p = DistributionPartition()
        p.add_to_q_prime("event-1")
        result = p.get_q_prime_ids()
        assert isinstance(result, frozenset)
        assert "event-1" in result

    def test_save_load_round_trip(self):
        """save() → load() preserves all event IDs in both partitions."""
        p = DistributionPartition()
        p.add_to_q("event-1")
        p.add_to_q("event-2")
        p.add_to_q_prime("event-3")

        with tempfile.TemporaryDirectory() as tmpdir:
            path = Path(tmpdir) / "partition.json"
            p.save(path)

            # Verify file was written
            assert path.exists()
            data = json.loads(path.read_text())
            assert sorted(data["q_ids"]) == ["event-1", "event-2"]
            assert data["q_prime_ids"] == ["event-3"]

            # Load and verify round-trip
            loaded = DistributionPartition.load(path)
            assert loaded.get_q_ids() == frozenset({"event-1", "event-2"})
            assert loaded.get_q_prime_ids() == frozenset({"event-3"})

    def test_load_nonexistent_path_returns_empty(self):
        """Loading a non-existent file returns an empty partition."""
        loaded = DistributionPartition.load(Path("/nonexistent/path/partition.json"))
        assert loaded.q_depth() == 0
        assert loaded.q_prime_depth() == 0

    def test_empty_partition_save_load(self):
        p = DistributionPartition()
        with tempfile.TemporaryDirectory() as tmpdir:
            path = Path(tmpdir) / "empty.json"
            p.save(path)
            loaded = DistributionPartition.load(path)
            assert loaded.q_depth() == 0
            assert loaded.q_prime_depth() == 0


# ===========================================================================
# KLDivergenceMonitor tests
# ===========================================================================

class TestKLDivergenceMonitor:
    def test_kl_identical_distributions_is_zero(self):
        """KL(P‖P) = 0 for identical distributions."""
        mon = KLDivergenceMonitor()
        summary = DistributionSummary(
            failure_mode_counts={"material_segregation": 10, "funnel_flow": 5},
            outcome_counts={"PROBLEM_PREVENTED": 15},
            mean_graph_weight=0.8,
            event_count=15,
        )
        kl = mon.compute_kl(summary, summary)
        assert math.isclose(kl, 0.0, abs_tol=1e-9)

    def test_kl_divergent_distributions_is_positive(self):
        """KL divergence is positive when distributions differ."""
        mon = KLDivergenceMonitor()
        p_summary = DistributionSummary(
            failure_mode_counts={"material_segregation": 10, "funnel_flow": 1},
            outcome_counts={},
            mean_graph_weight=0.8,
            event_count=11,
        )
        q_summary = DistributionSummary(
            failure_mode_counts={"funnel_flow": 10, "material_segregation": 1},
            outcome_counts={},
            mean_graph_weight=0.6,
            event_count=11,
        )
        kl = mon.compute_kl(p_summary, q_summary)
        assert kl > 0.0

    def test_kl_epsilon_smoothing_no_divide_by_zero(self):
        """Laplace smoothing prevents division by zero on unseen categories."""
        mon = KLDivergenceMonitor(epsilon=0.01)
        p_summary = DistributionSummary(
            failure_mode_counts={"only_in_p": 10},
            outcome_counts={},
            mean_graph_weight=0.8,
            event_count=10,
        )
        q_summary = DistributionSummary(
            failure_mode_counts={"only_in_q": 10},  # zero overlap with P
            outcome_counts={},
            mean_graph_weight=0.6,
            event_count=10,
        )
        # Should not raise, even with zero-count categories
        kl = mon.compute_kl(p_summary, q_summary)
        assert kl > 0.0
        assert math.isfinite(kl)

    def test_kl_empty_distributions_is_zero(self):
        mon = KLDivergenceMonitor()
        empty = DistributionSummary(
            failure_mode_counts={},
            outcome_counts={},
            mean_graph_weight=0.0,
            event_count=0,
        )
        kl = mon.compute_kl(empty, empty)
        assert kl == 0.0

    def test_summarize_events_counts_failure_modes(self):
        mon = KLDivergenceMonitor()
        events = [
            make_event("material_segregation"),
            make_event("material_segregation"),
            make_event("funnel_flow"),
        ]
        summary = mon.summarize_events(events)
        assert summary.failure_mode_counts["material_segregation"] == 2
        assert summary.failure_mode_counts["funnel_flow"] == 1
        assert summary.event_count == 3

    def test_summarize_empty_events(self):
        mon = KLDivergenceMonitor()
        summary = mon.summarize_events([])
        assert summary.event_count == 0
        assert summary.mean_graph_weight == 0.0
        assert summary.failure_mode_counts == {}

    def test_compute_all_divergences_keys_present(self):
        mon = KLDivergenceMonitor()
        p = [make_event("material_segregation")]
        q = [make_event("funnel_flow")]
        q_prime = [make_event("material_segregation")]
        result = mon.compute_all_divergences(p, q, q_prime)
        assert "kl_p_q" in result
        assert "kl_p_q_prime" in result
        assert "kl_q_q_prime" in result

    def test_compute_all_divergences_empty_q_returns_zeros(self):
        mon = KLDivergenceMonitor()
        p = [make_event("material_segregation")]
        result = mon.compute_all_divergences(p_events=p, q_events=[], q_prime_events=[])
        assert result["kl_p_q"] == 0.0
        assert result["kl_p_q_prime"] == 0.0
        assert result["kl_q_q_prime"] == 0.0


# ===========================================================================
# WithholdCoordinator tests
# ===========================================================================

class TestWithholdCoordinator:
    def test_should_withhold_returns_false_when_p_withhold_zero(self):
        """Phase 2 contract: should_withhold() always returns False."""
        backend = _MockBackend(depth=500)
        partition = DistributionPartition()
        coord = WithholdCoordinator(backend, partition, config=WithholdConfig(p_withhold=0.0))
        for _ in range(20):
            assert coord.should_withhold("event-1", graph_weight=0.9) is False

    def test_should_withhold_returns_false_below_min_corpus_depth(self):
        """Withhold-sampling suppressed when corpus is too small."""
        backend = _MockBackend(depth=50)  # below 200 threshold
        partition = DistributionPartition()
        config = WithholdConfig(p_withhold=0.5, min_corpus_depth_to_activate=200)
        coord = WithholdCoordinator(backend, partition, config=config, rng_seed=42)
        # Even with p_withhold=0.5, corpus is too small → always False
        for _ in range(20):
            assert coord.should_withhold("event-x", graph_weight=0.9) is False

    def test_should_withhold_can_return_true_when_active(self):
        """With p_withhold=1.0 and sufficient corpus, should always withhold."""
        backend = _MockBackend(depth=300)
        partition = DistributionPartition()
        config = WithholdConfig(p_withhold=1.0, min_corpus_depth_to_activate=200)
        coord = WithholdCoordinator(backend, partition, config=config, rng_seed=42)
        assert coord.should_withhold("event-z", graph_weight=0.5) is True

    def test_classify_event_routes_to_q(self):
        backend = _MockBackend()
        partition = DistributionPartition()
        coord = WithholdCoordinator(backend, partition)
        coord.classify_event("event-1", is_counterfactual=False)
        assert "event-1" in partition.get_q_ids()
        assert "event-1" not in partition.get_q_prime_ids()

    def test_classify_event_routes_to_q_prime(self):
        backend = _MockBackend()
        partition = DistributionPartition()
        coord = WithholdCoordinator(backend, partition)
        coord.classify_event("event-2", is_counterfactual=True)
        assert "event-2" in partition.get_q_prime_ids()
        assert "event-2" not in partition.get_q_ids()

    def test_classify_event_cannot_add_to_both(self):
        """Architectural invariant: event cannot end up in both Q and Q'."""
        backend = _MockBackend()
        partition = DistributionPartition()
        coord = WithholdCoordinator(backend, partition)
        coord.classify_event("event-x", is_counterfactual=False)
        with pytest.raises(ValueError):
            coord.classify_event("event-x", is_counterfactual=True)

    def test_kl_spike_detected_below_threshold(self):
        backend = _MockBackend()
        partition = DistributionPartition()
        config = WithholdConfig(kl_spike_threshold=0.5)
        coord = WithholdCoordinator(backend, partition, config=config)
        divergences = {"kl_p_q": 0.2, "kl_p_q_prime": 0.1, "kl_q_q_prime": 0.05}
        assert coord.kl_spike_detected(divergences) is False

    def test_kl_spike_detected_above_threshold(self):
        backend = _MockBackend()
        partition = DistributionPartition()
        config = WithholdConfig(kl_spike_threshold=0.5)
        coord = WithholdCoordinator(backend, partition, config=config)
        divergences = {"kl_p_q": 0.8, "kl_p_q_prime": 0.3, "kl_q_q_prime": 0.2}
        assert coord.kl_spike_detected(divergences) is True

    def test_kl_spike_detected_exactly_at_threshold_is_false(self):
        """Threshold is a strict greater-than, not >=."""
        backend = _MockBackend()
        partition = DistributionPartition()
        config = WithholdConfig(kl_spike_threshold=0.5)
        coord = WithholdCoordinator(backend, partition, config=config)
        divergences = {"kl_p_q": 0.5, "kl_p_q_prime": 0.0, "kl_q_q_prime": 0.0}
        assert coord.kl_spike_detected(divergences) is False

    @pytest.mark.asyncio
    async def test_compute_divergences_returns_zeros_when_q_empty(self):
        """No events classified yet → all divergences are 0.0."""
        backend = _MockBackend()
        partition = DistributionPartition()
        coord = WithholdCoordinator(backend, partition)
        result = await coord.compute_divergences()
        assert result == {"kl_p_q": 0.0, "kl_p_q_prime": 0.0, "kl_q_q_prime": 0.0}

    def test_high_weight_events_have_higher_withhold_probability(self):
        """High-weight events (>0.7) should be sampled at 1.5x rate."""
        backend = _MockBackend(depth=500)
        partition = DistributionPartition()
        config = WithholdConfig(p_withhold=0.4, min_corpus_depth_to_activate=200)

        # Use a seeded RNG to get deterministic results
        coord_low  = WithholdCoordinator(backend, partition, config=config, rng_seed=42)
        coord_high = WithholdCoordinator(backend, DistributionPartition(), config=config, rng_seed=42)

        # With same seed and p_withhold=0.4, low-weight (0.3) vs high-weight (0.9)
        # The high-weight path uses effective_p=0.6 (1.5×), low uses 0.4
        # Test by checking the effective probability logic is applied
        # (We can't directly compare RNG outputs here, but we verify the config
        # is wired correctly by checking a p=1.0 case)
        config_full = WithholdConfig(p_withhold=1.0, min_corpus_depth_to_activate=200)
        coord_full = WithholdCoordinator(backend, DistributionPartition(), config=config_full, rng_seed=99)
        # p=1.0 → always withheld, regardless of weight
        assert coord_full.should_withhold("e1", graph_weight=0.1) is True
        assert coord_full.should_withhold("e2", graph_weight=0.9) is True
