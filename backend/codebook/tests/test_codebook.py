"""
Unit tests for the Phase 1 ArcShield behavioral primitive codebook.

Run from the backend/codebook directory:
    python -m pytest tests/ -v

No external dependencies beyond pydantic and stdlib.
"""

from __future__ import annotations

import json
import math
import sys
from datetime import datetime, timezone
from pathlib import Path

import pytest

# Ensure the package root (backend/codebook/) is importable as "codebook"
sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from codebook.codebook import PrimitiveCodebook
from codebook.matcher import (
    CosineCodebookMatcher,
    compute_delta_vector,
    cosine_similarity,
    extract_cause_features,
    primitive_to_features,
)
from codebook.schema import (
    CanonicalSensorReading,
    CodebookExpansionRequest,
    Primitive,
)

# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

SEED_READINGS = [
    {"instrument_id": "crammer_amps",   "value": 67.3, "unit": "A",   "confidence": 0.98},
    {"instrument_id": "zone1_temp_f",   "value": 348.0, "unit": "°F", "confidence": 0.97},
    {"instrument_id": "die_pressure_psi","value": 2840.0,"unit": "psi","confidence": 0.96},
    {"instrument_id": "motor_amps",     "value": 88.5, "unit": "A",   "confidence": 0.98},
    {"instrument_id": "melt_temp_f",    "value": 371.0, "unit": "°F", "confidence": 0.95},
]

SEED_ACOUSTIC = {"spectral_delta_db": 4.2, "dominant_freq_hz": 210}

SEED_BIO = {"hr_bpm": 84.0, "hrv_rmssd_ms": 31.2, "accelerometer_mag": 1.1}


def _make_primitive(
    primitive_id: str = "PRIM-001",
    failure_mode_tag: str = "material_segregation_funnel_flow",
    theta_tc: float = 0.70,
    escalation_state: int = 2,
    readings: list | None = None,
) -> Primitive:
    if readings is None:
        readings = SEED_READINGS
    return Primitive(
        primitive_id=primitive_id,
        failure_mode_tag=failure_mode_tag,
        description="Test primitive",
        srk_level="KNOWLEDGE",
        canonical_sensor_readings=[
            CanonicalSensorReading(**r) for r in readings
        ],
        canonical_acoustic_profile=SEED_ACOUSTIC,
        canonical_escalation_state=escalation_state,
        theta_tc=theta_tc,
        canonical_action_type="MECHANICAL_INSPECT",
        canonical_action_summary="Rake feed throat.",
        created_at=datetime(2026, 4, 8, 7, 48, tzinfo=timezone.utc),
        created_by="op_hash_kc_7a3f2b",
        source_event_ids=["6ab2942f-3a22-4824-9b9a-fb829e414e82"],
    )


# ---------------------------------------------------------------------------
# extract_cause_features
# ---------------------------------------------------------------------------

class TestExtractCauseFeatures:

    def test_sensor_readings_keys(self):
        feats = extract_cause_features(SEED_READINGS)
        assert "sensor.crammer_amps" in feats
        assert "sensor.zone1_temp_f" in feats

    def test_confidence_weights_value(self):
        feats = extract_cause_features(SEED_READINGS)
        # crammer_amps: 67.3 * 0.98
        assert math.isclose(feats["sensor.crammer_amps"], 67.3 * 0.98, rel_tol=1e-6)

    def test_missing_confidence_defaults_to_one(self):
        readings = [{"instrument_id": "x", "value": 10.0, "unit": "A"}]
        feats = extract_cause_features(readings)
        assert math.isclose(feats["sensor.x"], 10.0)

    def test_acoustic_profile_extracted(self):
        feats = extract_cause_features([], acoustic_profile=SEED_ACOUSTIC)
        assert math.isclose(feats["acoustic.spectral_delta_db"], 4.2)
        assert math.isclose(feats["acoustic.dominant_freq_hz"], 210.0)

    def test_acoustic_profile_none_skipped(self):
        feats = extract_cause_features([])
        assert "acoustic.spectral_delta_db" not in feats

    def test_biometric_extracted(self):
        feats = extract_cause_features([], biometric_snapshot=SEED_BIO)
        assert math.isclose(feats["bio.hr_bpm"], 84.0)
        assert math.isclose(feats["bio.hrv_rmssd_ms"], 31.2)
        assert math.isclose(feats["bio.accelerometer_mag"], 1.1)

    def test_biometric_none_fields_skipped(self):
        feats = extract_cause_features([], biometric_snapshot={"hr_bpm": None, "hrv_rmssd_ms": 30.0})
        assert "bio.hr_bpm" not in feats
        assert "bio.hrv_rmssd_ms" in feats

    def test_escalation_state_included(self):
        feats = extract_cause_features([], escalation_state=2)
        assert math.isclose(feats["escalation_state"], 2.0)


# ---------------------------------------------------------------------------
# cosine_similarity
# ---------------------------------------------------------------------------

class TestCosineSimilarity:

    def test_identical_dicts_score_one(self):
        a = {"x": 1.0, "y": 2.0, "z": 3.0}
        sim, n = cosine_similarity(a, a)
        assert math.isclose(sim, 1.0, rel_tol=1e-6)
        assert n == 3

    def test_proportional_event_score_one(self):
        # ratio vector [2, 2, 2] is proportional to [1, 1, 1] → cos=1.0
        event = {"x": 2.0, "y": 4.0, "z": 6.0}
        prim  = {"x": 1.0, "y": 2.0, "z": 3.0}
        sim, _ = cosine_similarity(event, prim)
        assert math.isclose(sim, 1.0, rel_tol=1e-6)

    def test_non_uniform_ratios_score_below_one(self):
        # event vec = [2.0, 0.1] → prim vec = [1, 1]
        # dot = 2.1, |event|=sqrt(4.01), |prim|=sqrt(2)
        event = {"x": 2.0, "y": 0.1}
        prim  = {"x": 1.0, "y": 1.0}
        sim, n = cosine_similarity(event, prim)
        expected = 2.1 / (math.sqrt(4.01) * math.sqrt(2))
        assert n == 2
        assert math.isclose(sim, expected, rel_tol=1e-5)

    def test_fewer_than_two_shared_returns_zero(self):
        event = {"a": 1.0}
        prim  = {"a": 1.0, "b": 2.0}
        sim, n = cosine_similarity(event, prim)
        assert sim == 0.0
        assert n == 1

    def test_no_shared_instruments_returns_zero(self):
        event = {"a": 1.0, "b": 2.0}
        prim  = {"c": 3.0, "d": 4.0}
        sim, n = cosine_similarity(event, prim)
        assert sim == 0.0
        assert n == 0

    def test_zero_primitive_value_skipped(self):
        event = {"x": 1.0, "y": 0.0, "z": 1.0}
        prim  = {"x": 1.0, "y": 0.0, "z": 1.0}
        # "y" is skipped (prim_val==0); only x and z count
        sim, n = cosine_similarity(event, prim)
        assert math.isclose(sim, 1.0, rel_tol=1e-6)
        assert n == 2


# ---------------------------------------------------------------------------
# compute_delta_vector
# ---------------------------------------------------------------------------

class TestComputeDeltaVector:

    def test_perfect_match_all_zeros(self):
        a = {"x": 1.0, "y": 2.0}
        delta = compute_delta_vector(a, a)
        assert math.isclose(delta["x"], 0.0)
        assert math.isclose(delta["y"], 0.0)

    def test_positive_deviation(self):
        event = {"x": 1.5}
        prim  = {"x": 1.0}
        delta = compute_delta_vector(event, prim)
        assert math.isclose(delta["x"], 0.5, rel_tol=1e-6)  # 50% above canonical

    def test_negative_deviation(self):
        event = {"x": 0.5}
        prim  = {"x": 1.0}
        delta = compute_delta_vector(event, prim)
        assert math.isclose(delta["x"], -0.5, rel_tol=1e-6)

    def test_only_shared_keys_in_delta(self):
        event = {"x": 1.0, "only_event": 5.0}
        prim  = {"x": 1.0, "only_prim": 5.0}
        delta = compute_delta_vector(event, prim)
        assert "x" in delta
        assert "only_event" not in delta
        assert "only_prim" not in delta

    def test_zero_primitive_value_skipped_in_delta(self):
        event = {"x": 1.0, "zero": 0.0}
        prim  = {"x": 1.0, "zero": 0.0}
        delta = compute_delta_vector(event, prim)
        assert "x" in delta
        assert "zero" not in delta


# ---------------------------------------------------------------------------
# CosineCodebookMatcher
# ---------------------------------------------------------------------------

class TestCosineCodebookMatcher:

    def test_empty_codebook_is_novel(self):
        matcher = CosineCodebookMatcher([])
        result = matcher.match_from_features({"x": 1.0, "y": 2.0})
        assert result.is_novel is True
        assert result.tc_score == 0.0
        assert result.matched_primitive_id is None

    def test_exact_match_is_not_novel(self):
        prim = _make_primitive()
        matcher = CosineCodebookMatcher([prim])
        prim_feats = primitive_to_features(prim)
        result = matcher.match_from_features(prim_feats)
        assert result.is_novel is False
        assert result.matched_primitive_id == "PRIM-001"
        assert result.failure_mode_tag == "material_segregation_funnel_flow"
        assert math.isclose(result.tc_score, 1.0, rel_tol=1e-6)

    def test_fewer_than_two_shared_is_novel(self):
        prim = _make_primitive()
        matcher = CosineCodebookMatcher([prim])
        # Only one shared instrument with the primitive
        result = matcher.match_from_features({"sensor.crammer_amps": 67.3})
        assert result.is_novel is True
        assert result.tc_score == 0.0

    def test_below_theta_tc_is_novel(self):
        # Primitive with high threshold; construct event that scores ~0.742
        prim = _make_primitive(
            primitive_id="PRIM-TIGHT",
            theta_tc=0.75,
            readings=[
                {"instrument_id": "x", "value": 1.0, "unit": "A", "confidence": 1.0},
                {"instrument_id": "y", "value": 1.0, "unit": "A", "confidence": 1.0},
            ],
            escalation_state=0,
        )
        matcher = CosineCodebookMatcher([prim])
        # event_vec ratios: [2.0, 0.1] → score ≈ 0.742 < 0.75
        result = matcher.match_from_features({"sensor.x": 2.0, "sensor.y": 0.1})
        assert result.is_novel is True
        assert result.tc_score < 0.75

    def test_above_theta_tc_is_match(self):
        prim = _make_primitive(
            primitive_id="PRIM-LOOSE",
            theta_tc=0.70,
            readings=[
                {"instrument_id": "x", "value": 1.0, "unit": "A", "confidence": 1.0},
                {"instrument_id": "y", "value": 1.0, "unit": "A", "confidence": 1.0},
            ],
            escalation_state=0,
        )
        matcher = CosineCodebookMatcher([prim])
        # event_vec ratios: [2.0, 0.1] → score ≈ 0.742 >= 0.70
        result = matcher.match_from_features({"sensor.x": 2.0, "sensor.y": 0.1})
        assert result.is_novel is False
        assert result.matched_primitive_id == "PRIM-LOOSE"

    def test_all_scores_populated_for_every_primitive(self):
        p1 = _make_primitive("PRIM-001")
        p2 = _make_primitive(
            "PRIM-002",
            failure_mode_tag="die_drool",
            readings=[
                {"instrument_id": "a", "value": 1.0, "unit": "A", "confidence": 1.0},
                {"instrument_id": "b", "value": 2.0, "unit": "A", "confidence": 1.0},
            ],
        )
        matcher = CosineCodebookMatcher([p1, p2])
        prim_feats = primitive_to_features(p1)
        result = matcher.match_from_features(prim_feats)
        assert "PRIM-001" in result.all_scores
        assert "PRIM-002" in result.all_scores

    def test_delta_vector_empty_when_novel(self):
        matcher = CosineCodebookMatcher([])
        result = matcher.match_from_features({"x": 1.0, "y": 2.0})
        assert result.delta_vector == {}

    def test_delta_vector_populated_when_match(self):
        prim = _make_primitive()
        matcher = CosineCodebookMatcher([prim])
        prim_feats = primitive_to_features(prim)
        result = matcher.match_from_features(prim_feats)
        assert result.is_novel is False
        assert len(result.delta_vector) > 0

    def test_match_from_cause_convenience_wrapper(self):
        prim = _make_primitive()
        matcher = CosineCodebookMatcher([prim])
        result = matcher.match_from_cause(
            sensor_readings=SEED_READINGS,
            acoustic_profile=SEED_ACOUSTIC,
            escalation_state=2,
        )
        assert result.is_novel is False
        assert result.matched_primitive_id == "PRIM-001"


# ---------------------------------------------------------------------------
# PrimitiveCodebook
# ---------------------------------------------------------------------------

class TestPrimitiveCodebook:

    def test_load_missing_file_returns_empty(self, tmp_path):
        book = PrimitiveCodebook.load(tmp_path / "nonexistent.json")
        assert len(book) == 0

    def test_load_from_default_primitives_json(self):
        book = PrimitiveCodebook.load()
        assert len(book) >= 1
        prim = book.get_primitive("PRIM-001")
        assert prim is not None
        assert prim.failure_mode_tag == "material_segregation_funnel_flow"

    def test_get_primitive_returns_correct_object(self):
        book = PrimitiveCodebook.load()
        prim = book.get_primitive("PRIM-001")
        assert prim is not None
        assert prim.primitive_id == "PRIM-001"

    def test_get_primitive_missing_returns_none(self):
        book = PrimitiveCodebook.load()
        assert book.get_primitive("PRIM-NOPE") is None

    def test_get_by_failure_mode_returns_matching(self):
        book = PrimitiveCodebook.load()
        results = book.get_by_failure_mode("material_segregation_funnel_flow")
        assert len(results) >= 1
        assert all(p.failure_mode_tag == "material_segregation_funnel_flow" for p in results)

    def test_get_by_failure_mode_unknown_returns_empty(self):
        book = PrimitiveCodebook.load()
        assert book.get_by_failure_mode("unknown_mode") == []

    def test_list_all_returns_list(self):
        book = PrimitiveCodebook.load()
        all_prims = book.list_all()
        assert isinstance(all_prims, list)
        assert len(all_prims) == len(book)

    def test_list_failure_modes_sorted_unique(self):
        book = PrimitiveCodebook(
            primitives=[
                _make_primitive("PRIM-001", "mode_b"),
                _make_primitive("PRIM-002", "mode_a"),
                _make_primitive("PRIM-003", "mode_b"),
            ]
        )
        modes = book.list_failure_modes()
        assert modes == sorted(set(modes))
        assert "mode_a" in modes
        assert "mode_b" in modes
        assert len(modes) == 2

    def test_add_from_corpus_event_creates_new_primitive(self):
        book = PrimitiveCodebook(primitives=[])
        seed_event = _seed_event_dict()
        req = CodebookExpansionRequest(
            source_event_id="6ab2942f-3a22-4824-9b9a-fb829e414e82",
            confirmed_failure_mode_tag="material_segregation_funnel_flow",
            description="Fines bridging in feed throat.",
            requested_by="op_hash_kc_7a3f2b",
        )
        pid = book.add_from_corpus_event(seed_event, req)
        assert pid == "PRIM-001"
        assert len(book) == 1
        prim = book.get_primitive("PRIM-001")
        assert prim is not None
        assert prim.failure_mode_tag == "material_segregation_funnel_flow"

    def test_add_duplicate_source_event_raises(self):
        book = PrimitiveCodebook(primitives=[_make_primitive("PRIM-001")])
        seed_event = _seed_event_dict()
        req = CodebookExpansionRequest(
            source_event_id="6ab2942f-3a22-4824-9b9a-fb829e414e82",
            confirmed_failure_mode_tag="material_segregation_funnel_flow",
            description="Duplicate.",
            requested_by="op_hash_kc_7a3f2b",
        )
        with pytest.raises(ValueError, match="already contributed"):
            book.add_from_corpus_event(seed_event, req)

    def test_next_primitive_id_sequential(self):
        book = PrimitiveCodebook(primitives=[
            _make_primitive("PRIM-001"),
            _make_primitive("PRIM-002"),
        ])
        assert book._next_primitive_id() == "PRIM-003"

    def test_next_primitive_id_empty_codebook(self):
        book = PrimitiveCodebook(primitives=[])
        assert book._next_primitive_id() == "PRIM-001"

    def test_save_and_reload(self, tmp_path):
        orig = PrimitiveCodebook.load()
        dest = tmp_path / "primitives.json"
        orig.save(dest)
        reloaded = PrimitiveCodebook.load(dest)
        assert len(reloaded) == len(orig)
        p_orig = orig.get_primitive("PRIM-001")
        p_rel  = reloaded.get_primitive("PRIM-001")
        assert p_orig is not None and p_rel is not None
        assert p_orig.failure_mode_tag == p_rel.failure_mode_tag
        assert p_orig.canonical_escalation_state == p_rel.canonical_escalation_state

    def test_theta_tc_override_applied(self):
        book = PrimitiveCodebook(primitives=[])
        seed_event = _seed_event_dict()
        req = CodebookExpansionRequest(
            source_event_id="6ab2942f-3a22-4824-9b9a-fb829e414e82",
            confirmed_failure_mode_tag="material_segregation_funnel_flow",
            description="High-confidence match threshold.",
            requested_by="op_hash_kc_7a3f2b",
            theta_tc_override=0.90,
        )
        pid = book.add_from_corpus_event(seed_event, req)
        prim = book.get_primitive(pid)
        assert prim is not None
        assert math.isclose(prim.theta_tc, 0.90)


# ---------------------------------------------------------------------------
# Helper — minimal CIAER event dict matching the seed event structure
# ---------------------------------------------------------------------------

def _seed_event_dict() -> dict:
    return {
        "schema_version": "1.0",
        "event_id": "6ab2942f-3a22-4824-9b9a-fb829e414e82",
        "domain_context": {"process": "pvc_extrusion"},
        "cause": {
            "escalation_state": 2,
            "sensor_readings": SEED_READINGS,
            "acoustic_profile": SEED_ACOUSTIC,
        },
        "intuition": {
            "srk_level": "KNOWLEDGE",
            "failure_mode_tag": "material_segregation_funnel_flow",
        },
        "action": {
            "action_type": "MECHANICAL_INSPECT",
            "action_rationale": "Rake feed throat.",
        },
        "shadow_actions": [
            {"action_type": "PROCESS_HALT", "rejection_rationale": "Not warranted."},
        ],
    }
