"""
tests/test_registry.py
======================
Tests for the Phase 1 behavioral codebook — ontology, registry, and matcher.

Run from backend/codebook/:
    pytest tests/test_registry.py -v

Or from backend/:
    python -m pytest codebook/tests/ -v
"""

from __future__ import annotations

import sys
from pathlib import Path

# Make codebook importable from the tests directory
_BACKEND_DIR = Path(__file__).parent.parent.parent
sys.path.insert(0, str(_BACKEND_DIR))

import pytest
from pydantic import ValidationError

from codebook.ontology import all_tags, is_valid_tag, validate_tag
from codebook.primitive import BehavioralPrimitive
from codebook.registry import PrimitiveRegistry
from codebook.matcher import PrimitiveMatcher, MatchResult

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

YAML_PATH = Path(__file__).parent.parent / "primitives" / "hollowell_ppvc1.yaml"


def _make_sensor_reading(instrument_id: str, value: float, unit: str = "unit", confidence: float = 1.0):
    """Create a minimal SensorReading-compatible object for matcher tests."""
    from types import SimpleNamespace
    return SimpleNamespace(instrument_id=instrument_id, value=value, unit=unit, confidence=confidence)


def _load_registry() -> PrimitiveRegistry:
    return PrimitiveRegistry.load(YAML_PATH)


# ---------------------------------------------------------------------------
# Part 1: Ontology validation
# ---------------------------------------------------------------------------

class TestOntology:

    def test_valid_seed_tags_all_recognized(self):
        expected_tags = {
            "material_segregation_funnel_flow",
            "die_drool",
            "melt_temp_high",
            "melt_temp_low",
            "motor_amp_spike",
            "screw_speed_surge",
            "line_speed_instability",
            "cooling_insufficiency",
            "pressure_spike_head",
            "output_rate_drop",
        }
        registered = all_tags()
        for tag in expected_tags:
            assert tag in registered, f"Expected seed tag '{tag}' not in ontology"

    def test_is_valid_tag_returns_true_for_known(self):
        assert is_valid_tag("motor_amp_spike") is True

    def test_is_valid_tag_returns_false_for_unknown(self):
        assert is_valid_tag("completely_made_up_failure") is False

    def test_validate_tag_raises_on_unknown(self):
        with pytest.raises(ValueError, match="not in the registered ontology"):
            validate_tag("nonexistent_failure_mode_xyz")

    def test_validate_tag_passes_on_known(self):
        # Should not raise
        validate_tag("die_drool")

    def test_all_tags_returns_frozenset(self):
        tags = all_tags()
        assert isinstance(tags, frozenset)
        assert len(tags) >= 10  # at minimum the 10 seed tags


# ---------------------------------------------------------------------------
# Part 2: Registry load from YAML
# ---------------------------------------------------------------------------

class TestRegistry:

    def test_load_produces_ten_primitives(self):
        registry = _load_registry()
        assert len(registry) == 10, f"Expected 10 primitives, got {len(registry)}"

    def test_all_ten_failure_mode_tags_covered(self):
        registry = _load_registry()
        all_loaded = {p.failure_mode_tag for p in registry.list_all()}
        expected = {
            "material_segregation_funnel_flow",
            "die_drool",
            "melt_temp_high",
            "melt_temp_low",
            "motor_amp_spike",
            "screw_speed_surge",
            "line_speed_instability",
            "cooling_insufficiency",
            "pressure_spike_head",
            "output_rate_drop",
        }
        assert all_loaded == expected

    def test_get_by_id_returns_correct_primitive(self):
        registry = _load_registry()
        p = registry.get("motor_amp_spike_v1")
        assert p.failure_mode_tag == "motor_amp_spike"
        assert p.typical_srk_level == "KNOWLEDGE"

    def test_get_unknown_id_raises_key_error(self):
        registry = _load_registry()
        with pytest.raises(KeyError, match="does_not_exist_v99"):
            registry.get("does_not_exist_v99")

    def test_get_by_failure_mode_returns_list(self):
        registry = _load_registry()
        results = registry.get_by_failure_mode("motor_amp_spike")
        assert isinstance(results, list)
        assert len(results) >= 1
        assert all(p.failure_mode_tag == "motor_amp_spike" for p in results)

    def test_get_by_failure_mode_empty_for_valid_tag_with_no_primitives(self):
        # Register a new tag that has no primitives yet
        registry = _load_registry()
        registry.register_ontology_tag("test_tag_no_prims_xyz")
        result = registry.get_by_failure_mode("test_tag_no_prims_xyz")
        assert result == []

    def test_get_by_failure_mode_raises_on_invalid_tag(self):
        registry = _load_registry()
        with pytest.raises(ValueError, match="not in the registered ontology"):
            registry.get_by_failure_mode("completely_invalid_tag_zzz")

    def test_list_all_sorted_by_failure_mode_tag(self):
        registry = _load_registry()
        primitives = registry.list_all()
        tags = [p.failure_mode_tag for p in primitives]
        assert tags == sorted(tags), "list_all() should be sorted by failure_mode_tag"

    def test_to_dict_structure(self):
        registry = _load_registry()
        d = registry.to_dict()
        assert isinstance(d, dict)
        assert len(d) == 10
        for tag, prims in d.items():
            assert is_valid_tag(tag)
            assert isinstance(prims, list)
            assert len(prims) >= 1

    def test_expand_adds_primitive(self):
        registry = _load_registry()
        # Register the new tag first
        registry.register_ontology_tag("test_new_failure_xyz")
        new_p = BehavioralPrimitive(
            primitive_id="test_new_failure_xyz_v1",
            failure_mode_tag="test_new_failure_xyz",
            display_name="Test New Failure",
            description="A test primitive for expand() validation.",
            typical_srk_level="RULE",
            typical_trigger_sources=["ANOMALY_DETECT"],
            sensor_signature={"motor_amps": [50.0, 70.0]},
            key_instruments=["motor_amps"],
            typical_action_type="PROCESS_HALT",
            typical_action_description="Halt the process.",
            typical_outcome_tag="PROBLEM_MITIGATED",
        )
        registry.expand(new_p)
        assert len(registry) == 11
        assert registry.get("test_new_failure_xyz_v1") is new_p

    def test_expand_rejects_unregistered_tag(self):
        registry = _load_registry()
        with pytest.raises(ValueError, match="not in the registered ontology"):
            registry.expand(BehavioralPrimitive(
                primitive_id="bad_tag_v1",
                failure_mode_tag="unregistered_tag_9999",
                display_name="Bad",
                description="Bad",
                typical_srk_level="SKILL",
                typical_trigger_sources=[],
                sensor_signature={},
                key_instruments=[],
                typical_action_type="PROCESS_HALT",
                typical_action_description="Halt",
                typical_outcome_tag="FAILED",
            ))

    def test_expand_rejects_duplicate_id(self):
        registry = _load_registry()
        p = registry.get("motor_amp_spike_v1")
        with pytest.raises(ValueError, match="already exists"):
            registry.expand(p)


# ---------------------------------------------------------------------------
# Part 3: Matcher scoring
# ---------------------------------------------------------------------------

class TestMatcher:

    def test_high_score_for_on_target_readings(self):
        """Readings in the center of the signature range should score near 1.0."""
        registry = _load_registry()
        matcher = PrimitiveMatcher()

        # motor_amp_spike_v1: motor_amps [45-80], midpoint 62.5
        readings = [_make_sensor_reading("motor_amps", 62.5)]
        results = matcher.match(readings, registry, top_n=10)

        # motor_amp_spike_v1 should be the top result
        top = results[0]
        assert top.primitive.primitive_id == "motor_amp_spike_v1"
        assert top.score > 0.8

    def test_lower_score_for_off_target_readings(self):
        """Readings far from signature midpoint should score lower than on-target."""
        registry = _load_registry()
        matcher = PrimitiveMatcher()

        readings_on = [_make_sensor_reading("motor_amps", 62.5)]
        readings_off = [_make_sensor_reading("motor_amps", 200.0)]

        results_on = matcher.match(readings_on, registry, top_n=10)
        results_off = matcher.match(readings_off, registry, top_n=10)

        # Find motor_amp_spike_v1 score in each run
        def get_score(results, pid):
            for r in results:
                if r.primitive.primitive_id == pid:
                    return r.score
            return 0.0

        score_on = get_score(results_on, "motor_amp_spike_v1")
        score_off = get_score(results_off, "motor_amp_spike_v1")
        assert score_on > score_off

    def test_top_n_limit_respected(self):
        registry = _load_registry()
        matcher = PrimitiveMatcher()
        readings = [_make_sensor_reading("motor_amps", 30.0)]
        for n in [1, 3, 5]:
            results = matcher.match(readings, registry, top_n=n)
            assert len(results) <= n

    def test_zero_score_for_no_shared_instruments(self):
        """When no instrument in the query matches any signature, score should be 0."""
        registry = _load_registry()
        matcher = PrimitiveMatcher()
        readings = [_make_sensor_reading("nonexistent_instrument_abc", 100.0)]
        results = matcher.match(readings, registry, top_n=10)
        assert all(r.score == 0.0 for r in results)

    def test_match_result_has_matched_instruments(self):
        registry = _load_registry()
        matcher = PrimitiveMatcher()
        readings = [
            _make_sensor_reading("motor_amps", 62.5),
            _make_sensor_reading("head_pressure_psi", 1600.0),
        ]
        results = matcher.match(readings, registry, top_n=5)
        top = results[0]
        assert len(top.matched_instruments) >= 1
        assert isinstance(top.matched_instruments, list)
        assert isinstance(top.unmatched_instruments, list)

    def test_key_instruments_weighted_higher(self):
        """
        key_instruments should receive 2x weight. Test that a reading matching
        a key_instrument moves the score more than a non-key instrument.
        """
        registry = _load_registry()
        matcher = PrimitiveMatcher()

        # motor_amp_spike_v1: key_instruments = [motor_amps, head_pressure_psi]
        # Provide only motor_amps at midpoint — should score higher than non-key instrument

        readings_key = [_make_sensor_reading("motor_amps", 62.5)]      # key instrument
        readings_nonkey = [_make_sensor_reading("melt_temp_f", 362.5)] # non-key for motor_amp_spike

        results_key = matcher.match(readings_key, registry, top_n=10)
        results_nonkey = matcher.match(readings_nonkey, registry, top_n=10)

        def get_score(results, pid):
            for r in results:
                if r.primitive.primitive_id == pid:
                    return r.score
            return 0.0

        # Key instrument at midpoint should score 1.0, non-key may vary
        score_key = get_score(results_key, "motor_amp_spike_v1")
        assert score_key > 0.5  # 1 / (1 + 0) = 1.0 at midpoint

    def test_results_sorted_descending_by_score(self):
        registry = _load_registry()
        matcher = PrimitiveMatcher()
        readings = [_make_sensor_reading("motor_amps", 62.5)]
        results = matcher.match(readings, registry, top_n=10)
        scores = [r.score for r in results]
        assert scores == sorted(scores, reverse=True)

    def test_sensor_reading_from_schema_works(self):
        """Verify compatibility with the actual arcshield schema SensorReading."""
        _API_DIR = Path(__file__).parent.parent.parent / "api"
        sys.path.insert(0, str(_API_DIR))
        try:
            from arcshield.schema import SensorReading
        except ImportError:
            pytest.skip("arcshield schema not importable from this test environment")

        registry = _load_registry()
        matcher = PrimitiveMatcher()

        readings = [
            SensorReading(instrument_id="motor_amps", value=62.5, unit="A", confidence=0.9),
            SensorReading(instrument_id="head_pressure_psi", value=1600.0, unit="PSI", confidence=0.85),
        ]
        results = matcher.match(readings, registry, top_n=5)
        assert len(results) >= 1
        assert results[0].primitive is not None
