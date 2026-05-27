"""
tests/test_ingest.py
====================
Tests for the mp4Real ingest pipeline — sidecar parsing, candidate window
parsing, event skeleton construction, and HITL completion logic.

Run from backend/:
    python -m pytest ingest/tests/ -v

Or from backend/ingest/:
    pytest tests/test_ingest.py -v
"""

from __future__ import annotations

import json
import sys
from pathlib import Path
from uuid import UUID

import pytest

# Make ingest importable
_BACKEND_DIR = Path(__file__).parent.parent.parent
sys.path.insert(0, str(_BACKEND_DIR))

from ingest.sidecar import SessionSidecar, parse_sidecar, find_sidecar
from ingest.candidate_window import CandidateWindowLog, parse_shadow_log, find_shadow_log
from ingest.event_builder import EventSkeleton, build_event_skeleton, skeleton_to_partial_event


# ---------------------------------------------------------------------------
# Fixtures / helpers
# ---------------------------------------------------------------------------

SIDECAR_DATA = {
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "operatorId": "op_hash_abc123",
    "facilityId": "HOLLOWELL_PPVC1",
    "lineId": "PPVC_LINE_1",
    "captureSourceId": "phone_cameraX_v1",
    "biometricSourceId": "polar_h10_v1",
    "sessionStartNanos": 1_748_304_000_000_000_000,
    "epsSyncNanos": 85_000_000,
    "lowSyncConfidence": False,
    "trackMap": {"video": "0", "audio": "1", "accel": "2", "biometric": "3", "thermal": "4"},
}

CANDIDATE_WINDOW_DATA = {
    "detectedAtNanos": 1_748_304_060_000_000_000,
    "lambda": 2.34,
    "lambdaEnv": 1.20,
    "lambdaAcoustic": 0.80,
    "lambdaAccel": 0.40,
    "lambdaMotion": 0.00,
    "lambdaGaze": 0.00,
    "lambdaBio": 1.14,
    "activityGate": 0.80,
    "thresholdReached": True,
    "shadowMode": True,
}


def _make_sidecar(**overrides) -> SessionSidecar:
    data = {**SIDECAR_DATA, **overrides}
    return SessionSidecar.model_validate(data)


def _make_window(**overrides) -> CandidateWindowLog:
    data = {**CANDIDATE_WINDOW_DATA, **overrides}
    return CandidateWindowLog.model_validate(data)


def _write_sidecar(tmp_path: Path, filename: str = "session.mp4real.json") -> Path:
    p = tmp_path / filename
    p.write_text(json.dumps(SIDECAR_DATA), encoding="utf-8")
    return p


def _write_shadow_log(tmp_path: Path, session_id: str, windows: list[dict] | None = None) -> Path:
    if windows is None:
        windows = [CANDIDATE_WINDOW_DATA]
    p = tmp_path / f"{session_id}.ndjson"
    p.write_text("\n".join(json.dumps(w) for w in windows), encoding="utf-8")
    return p


# ---------------------------------------------------------------------------
# Part 1: SessionSidecar / parse_sidecar
# ---------------------------------------------------------------------------

class TestSessionSidecar:

    def test_round_trip_from_dict(self):
        sc = SessionSidecar.model_validate(SIDECAR_DATA)
        assert sc.sessionId == "550e8400-e29b-41d4-a716-446655440000"
        assert sc.facilityId == "HOLLOWELL_PPVC1"
        assert sc.lineId == "PPVC_LINE_1"
        assert sc.captureSourceId == "phone_cameraX_v1"
        assert sc.biometricSourceId == "polar_h10_v1"
        assert sc.epsSyncNanos == 85_000_000
        assert sc.lowSyncConfidence is False
        assert sc.trackMap["video"] == "0"

    def test_eps_sync_ms_property(self):
        sc = _make_sidecar(epsSyncNanos=85_000_000)
        assert sc.eps_sync_ms == pytest.approx(85.0, rel=0.01)

    def test_is_sync_valid_within_target(self):
        sc = _make_sidecar(epsSyncNanos=80_000_000, lowSyncConfidence=False)
        assert sc.is_sync_valid is True

    def test_is_sync_valid_exceeds_target(self):
        sc = _make_sidecar(epsSyncNanos=120_000_000, lowSyncConfidence=False)
        assert sc.is_sync_valid is False  # 120ms > 100ms target

    def test_is_sync_valid_false_when_flag_set(self):
        sc = _make_sidecar(epsSyncNanos=50_000_000, lowSyncConfidence=True)
        assert sc.is_sync_valid is False

    def test_parse_sidecar_from_file(self, tmp_path):
        p = _write_sidecar(tmp_path, "session.mp4real.json")
        sc = parse_sidecar(p)
        assert sc.sessionId == SIDECAR_DATA["sessionId"]
        assert sc.facilityId == SIDECAR_DATA["facilityId"]

    def test_parse_sidecar_raises_on_missing_file(self, tmp_path):
        with pytest.raises(FileNotFoundError):
            parse_sidecar(tmp_path / "nonexistent.mp4real.json")

    def test_find_sidecar_finds_matching_file(self, tmp_path):
        _write_sidecar(tmp_path, "session.mp4real.json")
        container = tmp_path / "session.mp4"
        container.touch()
        found = find_sidecar(container)
        assert found is not None
        assert found.name == "session.mp4real.json"

    def test_find_sidecar_returns_none_when_not_found(self, tmp_path):
        container = tmp_path / "missing_session.mp4"
        container.touch()
        found = find_sidecar(container)
        assert found is None


# ---------------------------------------------------------------------------
# Part 2: CandidateWindowLog / parse_shadow_log
# ---------------------------------------------------------------------------

class TestCandidateWindowLog:

    def test_round_trip_from_dict(self):
        w = CandidateWindowLog.model_validate(CANDIDATE_WINDOW_DATA)
        assert w.lambda_ == pytest.approx(2.34)
        assert w.lambdaEnv == pytest.approx(1.20)
        assert w.lambdaBio == pytest.approx(1.14)
        assert w.thresholdReached is True
        assert w.shadowMode is True

    def test_parse_shadow_log_from_file(self, tmp_path):
        session_id = "550e8400-e29b-41d4-a716-446655440000"
        _write_shadow_log(tmp_path, session_id)
        log_path = tmp_path / f"{session_id}.ndjson"
        windows = parse_shadow_log(log_path)
        assert len(windows) == 1
        assert windows[0].lambda_ == pytest.approx(2.34)

    def test_parse_shadow_log_multiple_entries(self, tmp_path):
        w1 = {**CANDIDATE_WINDOW_DATA, "lambda": 1.5, "thresholdReached": False}
        w2 = {**CANDIDATE_WINDOW_DATA, "lambda": 2.8, "thresholdReached": True}
        session_id = "test-session-multi"
        _write_shadow_log(tmp_path, session_id, [w1, w2])
        windows = parse_shadow_log(tmp_path / f"{session_id}.ndjson")
        assert len(windows) == 2
        assert windows[1].lambda_ == pytest.approx(2.8)

    def test_find_shadow_log_finds_file(self, tmp_path):
        session_id = "550e8400-e29b-41d4-a716-446655440000"
        _write_shadow_log(tmp_path, session_id)
        found = find_shadow_log(session_id, tmp_path)
        assert found is not None
        assert found.name == f"{session_id}.ndjson"

    def test_find_shadow_log_returns_none_when_missing(self, tmp_path):
        found = find_shadow_log("no-such-session", tmp_path)
        assert found is None


# ---------------------------------------------------------------------------
# Part 3: EventSkeleton / build_event_skeleton
# ---------------------------------------------------------------------------

class TestEventSkeleton:

    def test_build_skeleton_populates_auto_fields(self):
        sc = _make_sidecar()
        skeleton = build_event_skeleton(sc)
        assert isinstance(skeleton.event_id, UUID)
        assert skeleton.facility_id == "HOLLOWELL_PPVC1"
        assert skeleton.line_id == "PPVC_LINE_1"
        assert skeleton.operator_id == "op_hash_abc123"
        assert skeleton.timestamp_start is not None

    def test_build_skeleton_without_trigger_window(self):
        sc = _make_sidecar()
        skeleton = build_event_skeleton(sc, trigger_window=None)
        assert skeleton.trigger_window is None
        assert skeleton.lambda_env == pytest.approx(0.0)
        assert skeleton.lambda_bio == pytest.approx(0.0)

    def test_build_skeleton_with_trigger_window(self):
        sc = _make_sidecar()
        tw = _make_window()
        skeleton = build_event_skeleton(sc, trigger_window=tw)
        assert skeleton.trigger_window is tw
        assert skeleton.lambda_env == pytest.approx(1.20)
        assert skeleton.lambda_bio == pytest.approx(1.14)

    def test_skeleton_is_complete_returns_false_when_missing_fields(self):
        sc = _make_sidecar()
        skeleton = build_event_skeleton(sc)
        # Nothing filled in yet
        assert skeleton.is_complete() is False

    def test_skeleton_is_complete_returns_true_when_all_fields_present(self):
        sc = _make_sidecar()
        skeleton = build_event_skeleton(sc)
        skeleton.failure_mode_tag = "motor_amp_spike"
        skeleton.srk_level = "KNOWLEDGE"
        skeleton.causal_hypothesis = "Screw obstruction causing motor overload."
        skeleton.action_type = "PROCESS_HALT"
        skeleton.outcome_tag = "PROBLEM_MITIGATED"
        skeleton.hypothesis_confirmed = True
        assert skeleton.is_complete() is True

    def test_skeleton_missing_fields_lists_all_when_empty(self):
        sc = _make_sidecar()
        skeleton = build_event_skeleton(sc)
        missing = skeleton.missing_fields()
        assert "failure_mode_tag" in missing
        assert "srk_level" in missing
        assert "causal_hypothesis" in missing
        assert "action_type" in missing
        assert "outcome_tag" in missing
        assert "hypothesis_confirmed" in missing

    def test_skeleton_missing_fields_reduces_as_fields_filled(self):
        sc = _make_sidecar()
        skeleton = build_event_skeleton(sc)
        skeleton.failure_mode_tag = "die_drool"
        skeleton.srk_level = "SKILL"
        missing = skeleton.missing_fields()
        assert "failure_mode_tag" not in missing
        assert "srk_level" not in missing
        assert "causal_hypothesis" in missing

    def test_low_sync_confidence_set_for_high_eps_sync(self):
        # epsSyncNanos > 250ms threshold
        sc = _make_sidecar(epsSyncNanos=300_000_000, lowSyncConfidence=False)
        skeleton = build_event_skeleton(sc)
        assert skeleton.low_sync_confidence is True

    def test_low_sync_confidence_propagated_from_sidecar_flag(self):
        sc = _make_sidecar(epsSyncNanos=50_000_000, lowSyncConfidence=True)
        skeleton = build_event_skeleton(sc)
        assert skeleton.low_sync_confidence is True


# ---------------------------------------------------------------------------
# Part 4: skeleton_to_partial_event serialization
# ---------------------------------------------------------------------------

class TestSkeletonToPartialEvent:

    def test_includes_all_populated_fields(self):
        sc = _make_sidecar()
        tw = _make_window()
        skeleton = build_event_skeleton(sc, trigger_window=tw)
        skeleton.failure_mode_tag = "motor_amp_spike"
        skeleton.srk_level = "KNOWLEDGE"
        partial = skeleton_to_partial_event(skeleton)

        assert partial["facility_id"] == "HOLLOWELL_PPVC1"
        assert partial["line_id"] == "PPVC_LINE_1"
        assert partial["operator_id"] == "op_hash_abc123"
        assert partial["failure_mode_tag"] == "motor_amp_spike"
        assert partial["srk_level"] == "KNOWLEDGE"
        assert partial["session_id"] == sc.sessionId
        assert partial["eps_sync_ms"] == pytest.approx(85.0, rel=0.01)

    def test_includes_none_for_missing_hitl_fields(self):
        sc = _make_sidecar()
        skeleton = build_event_skeleton(sc)
        partial = skeleton_to_partial_event(skeleton)

        assert partial["failure_mode_tag"] is None
        assert partial["srk_level"] is None
        assert partial["causal_hypothesis"] is None
        assert partial["action_type"] is None
        assert partial["outcome_tag"] is None
        assert partial["hypothesis_confirmed"] is None

    def test_includes_trigger_window_summary(self):
        sc = _make_sidecar()
        tw = _make_window()
        skeleton = build_event_skeleton(sc, trigger_window=tw)
        partial = skeleton_to_partial_event(skeleton)

        assert partial["trigger_window"] is not None
        assert partial["trigger_window"]["lambda"] == pytest.approx(2.34)
        assert partial["trigger_window"]["threshold_reached"] is True

    def test_trigger_window_none_when_not_provided(self):
        sc = _make_sidecar()
        skeleton = build_event_skeleton(sc, trigger_window=None)
        partial = skeleton_to_partial_event(skeleton)
        assert partial["trigger_window"] is None

    def test_is_complete_in_partial_event(self):
        sc = _make_sidecar()
        skeleton = build_event_skeleton(sc)
        partial_incomplete = skeleton_to_partial_event(skeleton)
        assert partial_incomplete["is_complete"] is False

        skeleton.failure_mode_tag = "die_drool"
        skeleton.srk_level = "SKILL"
        skeleton.causal_hypothesis = "Melt buildup at die face."
        skeleton.action_type = "MECHANICAL_INSPECT"
        skeleton.outcome_tag = "PROBLEM_PREVENTED"
        skeleton.hypothesis_confirmed = True
        partial_complete = skeleton_to_partial_event(skeleton)
        assert partial_complete["is_complete"] is True
        assert partial_complete["missing_fields"] == []

    def test_partial_event_is_json_serializable(self):
        sc = _make_sidecar()
        tw = _make_window()
        skeleton = build_event_skeleton(sc, trigger_window=tw)
        partial = skeleton_to_partial_event(skeleton)
        # Should not raise
        json_str = json.dumps(partial)
        assert isinstance(json_str, str)
        # Round-trip
        recovered = json.loads(json_str)
        assert recovered["facility_id"] == "HOLLOWELL_PPVC1"
