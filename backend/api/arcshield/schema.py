"""
arcshield.schema
================
Pydantic models for the CIAER+ event record.

Every field maps 1:1 to the ArcShield Technical Data Schema Reference v1.0.
Types are strict — no coercion. Downstream ML pipelines depend on schema
stability; field additions are additive, never destructive.
"""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Any
from uuid import UUID, uuid4

from pydantic import BaseModel, Field


# ---------------------------------------------------------------------------
# Enumerations
# ---------------------------------------------------------------------------

class TriggerSource(str, Enum):
    OPERATOR_MANUAL  = "OPERATOR_MANUAL"
    GAZE_DWELL       = "GAZE_DWELL"
    BIOMETRIC        = "BIOMETRIC"
    ANOMALY_DETECT   = "ANOMALY_DETECT"
    SCHEDULED        = "SCHEDULED"


class SRKLevel(str, Enum):
    SKILL     = "SKILL"
    RULE      = "RULE"
    KNOWLEDGE = "KNOWLEDGE"


class ActionType(str, Enum):
    PARAMETER_ADJUST   = "PARAMETER_ADJUST"
    MECHANICAL_INSPECT = "MECHANICAL_INSPECT"
    MATERIAL_INTERVENE = "MATERIAL_INTERVENE"
    PROCESS_HALT       = "PROCESS_HALT"
    MONITOR_HOLD       = "MONITOR_HOLD"
    ESCALATE           = "ESCALATE"


class PredictionMatch(str, Enum):
    CONFIRMED      = "CONFIRMED"
    PARTIAL        = "PARTIAL"
    DISCONFIRMED   = "DISCONFIRMED"
    INDETERMINATE  = "INDETERMINATE"


class OutcomeTag(str, Enum):
    PROBLEM_PREVENTED  = "PROBLEM_PREVENTED"
    PROBLEM_MITIGATED  = "PROBLEM_MITIGATED"
    NO_CHANGE          = "NO_CHANGE"
    ESCALATED          = "ESCALATED"
    FAILED             = "FAILED"


class DeltaDirection(str, Enum):
    IMPROVED   = "IMPROVED"
    DEGRADED   = "DEGRADED"
    UNCHANGED  = "UNCHANGED"


class ProductQualityImpact(str, Enum):
    NO_IMPACT        = "NO_IMPACT"
    MINOR_DEVIATION  = "MINOR_DEVIATION"
    MAJOR_DEVIATION  = "MAJOR_DEVIATION"
    SCRAP            = "SCRAP"


class RPhysStatus(str, Enum):
    PENDING       = "PENDING"        # deadline set, R_phys not yet received
    ARRIVED       = "ARRIVED"        # R_phys recorded; OGC update has fired
    INDETERMINATE = "INDETERMINATE"  # deadline passed without R_phys; weight unchanged
    NOT_REQUIRED  = "NOT_REQUIRED"   # event closed on own telemetry; no deferred R_phys


# ---------------------------------------------------------------------------
# Sub-objects
# ---------------------------------------------------------------------------

class SensorReading(BaseModel):
    instrument_id : str
    value         : float
    unit          : str
    confidence    : float = Field(ge=0.0, le=1.0)


class BiometricSnapshot(BaseModel):
    hr_bpm            : float | None = None
    hrv_rmssd_ms      : float | None = None
    accelerometer_mag : float | None = None   # resultant magnitude, m/s²
    raw_available     : bool = False           # True when R-R interval log present


class ActionStep(BaseModel):
    step_id          : int
    description      : str
    parameter_changed: str | None = None
    from_value       : Any        = None
    to_value         : Any        = None
    rationale        : str


class ShadowAction(BaseModel):
    """A rejected alternative — first-class sibling to the executed Action."""
    action_type          : ActionType
    rejection_rationale  : str
    confidence_in_rejection: float = Field(ge=0.0, le=1.0)


class SensorDelta(BaseModel):
    instrument_id : str
    delta         : float
    direction     : DeltaDirection


class RPhysRecord(BaseModel):
    """
    Outcome-Grounded Confidence (OGC) deferred reward record.
    Tracks the physical reward R_phys that gates the confidence update rule.

    Lifecycle: PENDING → ARRIVED (OGC update fires) or INDETERMINATE (deadline lapsed).
    NOT_REQUIRED is set at ingest for events that close on their own telemetry.

    This record is the FK that the OGC update rule requires (CLAUDE.md §6.2).
    No code path may update graph_weight via the OGC rule without referencing a
    non-None RPhysRecord with status=ARRIVED.
    """
    status     : RPhysStatus
    deadline   : datetime | None = None  # UTC; required when status=PENDING
    arrived_at : datetime | None = None  # UTC; set by record_r_phys
    value      : float | None   = None   # [0.0, 1.0]; present only when ARRIVED
    source     : str | None     = None   # "manual_qc" | "plc_motor_amps" | etc.


# ---------------------------------------------------------------------------
# Phase objects
# ---------------------------------------------------------------------------

class PreEnv(BaseModel):
    shift_phase            : str | None = None   # e.g. "startup", "steady_state", "shutdown"
    material_batch_id      : str | None = None
    ambient_temp_f         : float | None = None
    recent_events_summary  : str | None = None
    crew_state_tag         : str | None = None   # e.g. "full_crew", "short_staffed"
    operator_id            : str                 # anonymized hash, never PII


class Cause(BaseModel):
    capture_timestamp         : datetime
    trigger_source            : TriggerSource
    sensor_readings           : list[SensorReading]
    biometric_snapshot        : BiometricSnapshot | None = None
    acoustic_profile          : dict[str, Any] | None = None   # FFT summary or raw features
    gaze_dwell_duration_sec   : float | None = None
    visual_anchor_description : str | None = None
    escalation_state          : int = Field(ge=0, le=3)


class Intuition(BaseModel):
    srk_level         : SRKLevel
    causal_hypothesis : str
    failure_mode_tag  : str                          # ontology-controlled vocabulary
    confidence_level  : float = Field(ge=0.0, le=1.0)
    projection        : str                          # predicted effect if hypothesis is correct
    voice_transcript  : str | None = None
    biometric_signature: BiometricSnapshot | None = None


class Action(BaseModel):
    action_type       : ActionType
    action_timestamp  : datetime
    action_rationale  : str
    action_sequence   : list[ActionStep]


class Effect(BaseModel):
    capture_timestamp : datetime
    sensor_readings   : list[SensorReading]
    deltas            : list[SensorDelta]            # auto-computed vs Cause readings
    prediction_match  : PredictionMatch


class Result(BaseModel):
    completed_at              : datetime
    outcome_tag               : OutcomeTag
    escalation_state_at_result: int = Field(ge=0, le=3)
    escalation_delta          : int                  # positive = improved, negative = worsened
    hypothesis_confirmed      : bool
    model_revision            : str | None = None    # expert's own words when disconfirmed
    product_quality_impact    : ProductQualityImpact
    graph_weight              : float = Field(ge=0.0, le=1.0)
    operator_notes            : str | None = None
    # OGC fields (CLAUDE.md §6) — None on pre-Phase-2 events; additive, never destructive
    r_phys              : RPhysRecord | None = None
    # Gating scalar precursor: None = no Twin guidance (compliance = 1 always).
    # Populated by Twin at Phase 3+. Architecturally separate from r_phys — the
    # compliance column and the reward column must never share a code path.
    advised_action_type : ActionType | None  = None


# ---------------------------------------------------------------------------
# Top-level event envelope
# ---------------------------------------------------------------------------

class CIAEREvent(BaseModel):
    """
    The atomic unit of the ArcShield knowledge graph.
    One complete expert decision cycle, from perceptual trigger to model update.
    """
    schema_version       : str = "1.0"
    event_id             : UUID
    timestamp_start      : datetime
    timestamp_end        : datetime | None = None    # derived from result.completed_at
    duration_sec         : int | None = None         # derived
    facility_id          : str
    line_id              : str
    operator_id          : str                       # anonymized hash
    operator_tenure_years: float | None = None
    shift_id             : str | None = None
    trigger_source       : TriggerSource
    domain_context       : dict[str, Any]            # process, material, product_spec, etc.
    parent_event_id      : UUID | None = None
    child_event_ids      : list[UUID] = Field(default_factory=list)
    pattern_match_ids    : list[UUID] = Field(default_factory=list)

    pre_env     : PreEnv
    cause       : Cause
    intuition   : Intuition
    action      : Action
    shadow_actions: list[ShadowAction] = Field(default_factory=list)
    effect      : Effect
    result      : Result


# ---------------------------------------------------------------------------
# Query parameter types (used by CorpusBackend callers, not stored)
# ---------------------------------------------------------------------------

class CauseSignatureQuery(BaseModel):
    """
    Input to query_by_cause_signature.
    Caller provides the current sensor state; backend computes similarity.
    """
    sensor_readings  : list[SensorReading]
    escalation_state : int = Field(ge=0, le=3)
    top_n            : int = Field(default=5, ge=1, le=50)
    min_graph_weight : float = Field(default=0.0, ge=0.0, le=1.0)


class FailureModeQuery(BaseModel):
    failure_mode_tag : str
    top_n            : int = Field(default=5, ge=1, le=50)
    min_graph_weight : float = Field(default=0.0, ge=0.0, le=1.0)
    srk_filter       : SRKLevel | None = None


class FailureModeSummary(BaseModel):
    failure_mode_tag : str
    event_count      : int
    mean_graph_weight: float
    outcome_distribution: dict[str, int]   # OutcomeTag → count
