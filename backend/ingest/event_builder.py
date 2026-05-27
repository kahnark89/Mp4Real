"""
ingest.event_builder
====================
EventSkeleton and the skeleton builder pipeline.

EventSkeleton is a partial CIAER+ event record that needs HITL completion
before it can be ingested into the corpus. Auto-populated fields come from
the sidecar and the candidate window log. Fields requiring operator input
(failure_mode_tag, srk_level, causal_hypothesis, action_type, outcome_tag,
hypothesis_confirmed) are initialized to None.

Per CLAUDE.md §11 Phase 2: the manual validation UI (debrief-ui) presents
the skeleton; the operator fills in the HITL fields; submit_completed_skeleton
merges them and validates as a full CIAEREvent.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any
from uuid import UUID, uuid4

from .candidate_window import CandidateWindowLog
from .sidecar import SessionSidecar

# ε_sync threshold for flagging low-confidence windows (CLAUDE.md §3.2)
_EPS_SYNC_LOW_CONFIDENCE_NS = 250_000_000  # 250 ms


@dataclass
class EventSkeleton:
    """
    Partial CIAER+ event record awaiting HITL completion.

    Auto-populated fields are filled from the sidecar and candidate window log.
    HITL fields are None until the operator fills them in via debrief-ui.
    """

    # System-assigned identity
    event_id: UUID

    # Sidecar reference (kept for downstream context)
    sidecar: SessionSidecar

    # Trigger window from shadow-mode log (None if no matching window found)
    trigger_window: CandidateWindowLog | None

    # Auto-populated from sidecar
    facility_id: str
    line_id: str
    operator_id: str
    timestamp_start: datetime

    # HITL fields — operator must fill in via debrief-ui
    failure_mode_tag: str | None = None
    srk_level: str | None = None
    causal_hypothesis: str | None = None
    action_type: str | None = None
    outcome_tag: str | None = None
    hypothesis_confirmed: bool | None = None

    # LLR context for display in debrief-ui
    lambda_env: float = 0.0
    lambda_bio: float = 0.0
    low_sync_confidence: bool = False

    def is_complete(self) -> bool:
        """
        Returns True when all required HITL fields are populated.

        Required fields: failure_mode_tag, srk_level, causal_hypothesis,
        action_type, outcome_tag, hypothesis_confirmed.
        """
        required = [
            self.failure_mode_tag,
            self.srk_level,
            self.causal_hypothesis,
            self.action_type,
            self.outcome_tag,
        ]
        return all(f is not None for f in required) and self.hypothesis_confirmed is not None

    def missing_fields(self) -> list[str]:
        """Return the list of required HITL fields that are still None."""
        missing = []
        if self.failure_mode_tag is None:
            missing.append("failure_mode_tag")
        if self.srk_level is None:
            missing.append("srk_level")
        if self.causal_hypothesis is None:
            missing.append("causal_hypothesis")
        if self.action_type is None:
            missing.append("action_type")
        if self.outcome_tag is None:
            missing.append("outcome_tag")
        if self.hypothesis_confirmed is None:
            missing.append("hypothesis_confirmed")
        return missing


def build_event_skeleton(
    sidecar: SessionSidecar,
    trigger_window: CandidateWindowLog | None = None,
) -> EventSkeleton:
    """
    Construct an EventSkeleton from a parsed sidecar and optional trigger window.

    Auto-populates all fields derivable from the sidecar and window log.
    HITL fields are left as None.

    Parameters
    ----------
    sidecar : SessionSidecar
        Parsed .mp4real.json sidecar.
    trigger_window : CandidateWindowLog | None
        The candidate window that fired the LLR gate, if available.
        If None, lambda_env/lambda_bio will be 0.0.
    """
    # Convert sessionStartNanos (elapsedRealtimeNanos, monotonic) to a UTC
    # datetime. Since elapsedRealtimeNanos is device-local monotonic time,
    # we use it as an offset from the Unix epoch for corpus storage.
    # In a full implementation this would be anchored to a wall-clock
    # reference via the NTP handshake (CLAUDE.md §3.2).
    timestamp_start = datetime.fromtimestamp(
        sidecar.sessionStartNanos / 1_000_000_000.0,
        tz=timezone.utc,
    )

    # Determine sync confidence
    low_sync = (
        sidecar.lowSyncConfidence
        or sidecar.epsSyncNanos > _EPS_SYNC_LOW_CONFIDENCE_NS
    )

    # Extract LLR components from trigger window
    lambda_env = 0.0
    lambda_bio = 0.0
    if trigger_window is not None:
        lambda_env = trigger_window.lambdaEnv
        lambda_bio = trigger_window.lambdaBio

    return EventSkeleton(
        event_id=uuid4(),
        sidecar=sidecar,
        trigger_window=trigger_window,
        facility_id=sidecar.facilityId,
        line_id=sidecar.lineId,
        operator_id=sidecar.operatorId,
        timestamp_start=timestamp_start,
        lambda_env=lambda_env,
        lambda_bio=lambda_bio,
        low_sync_confidence=low_sync,
    )


def skeleton_to_partial_event(skeleton: EventSkeleton) -> dict[str, Any]:
    """
    Serialize an EventSkeleton to a JSON-serializable dict for debrief-ui display.

    All populated fields are included. None fields are included with value None
    so the UI knows which fields need operator input.

    Returns a flat dict rather than a full CIAEREvent — the partial event
    only becomes a full CIAEREvent after HITL completion and validation.
    """
    result: dict[str, Any] = {
        # Identity
        "event_id": str(skeleton.event_id),
        "facility_id": skeleton.facility_id,
        "line_id": skeleton.line_id,
        "operator_id": skeleton.operator_id,
        "timestamp_start": skeleton.timestamp_start.isoformat(),
        # Capture provenance
        "session_id": skeleton.sidecar.sessionId,
        "capture_source_id": skeleton.sidecar.captureSourceId,
        "biometric_source_id": skeleton.sidecar.biometricSourceId,
        "eps_sync_ms": skeleton.sidecar.eps_sync_ms,
        "low_sync_confidence": skeleton.low_sync_confidence,
        # LLR context
        "lambda_env": skeleton.lambda_env,
        "lambda_bio": skeleton.lambda_bio,
    }

    # Include trigger window summary if available
    if skeleton.trigger_window is not None:
        tw = skeleton.trigger_window
        result["trigger_window"] = {
            "detected_at_nanos": tw.detectedAtNanos,
            "lambda": tw.lambda_,
            "lambda_env": tw.lambdaEnv,
            "lambda_bio": tw.lambdaBio,
            "threshold_reached": tw.thresholdReached,
            "activity_gate": tw.activityGate,
        }
    else:
        result["trigger_window"] = None

    # HITL fields (populated or None)
    result["failure_mode_tag"] = skeleton.failure_mode_tag
    result["srk_level"] = skeleton.srk_level
    result["causal_hypothesis"] = skeleton.causal_hypothesis
    result["action_type"] = skeleton.action_type
    result["outcome_tag"] = skeleton.outcome_tag
    result["hypothesis_confirmed"] = skeleton.hypothesis_confirmed

    # Completion status
    result["is_complete"] = skeleton.is_complete()
    result["missing_fields"] = skeleton.missing_fields()

    return result
