"""
codebook.schema
===============
Pydantic v2 models for the Phase 1 behavioral primitive codebook.

A Primitive is the codebook atom: a hand-curated prototypical cause signature
for a single failure_mode_tag. The dict is keyed by primitive_id; each
failure_mode_tag may have multiple primitives (e.g. early-stage vs. advanced).

Matching logic lives in matcher.py.  Dict management lives in codebook.py.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field


class CanonicalSensorReading(BaseModel):
    instrument_id: str
    value: float
    unit: str
    confidence: float = Field(ge=0.0, le=1.0, default=1.0)


class CanonicalShadowAction(BaseModel):
    """Typical rejected alternative for this failure mode — informational only."""
    action_type: str
    rejection_rationale: str


class Primitive(BaseModel):
    """
    A behavioral primitive — the prototypical cause signature for one failure mode.

    Each primitive is the canonical Pattern that new events are matched against.
    A match (tc_score >= theta_tc) emits a compressed reference token (FIG. 8
    path 804→805). A non-match flags the event as novel and queues it for HITL
    validation (FIG. 8 path 804→806→807).
    """
    primitive_id: str
    failure_mode_tag: str
    description: str
    srk_level: str  # "SKILL" | "RULE" | "KNOWLEDGE"

    # Canonical cause signature
    canonical_sensor_readings: list[CanonicalSensorReading]
    canonical_acoustic_profile: dict[str, Any] | None = None
    canonical_escalation_state: int = Field(ge=0, le=3)

    # θ_TC: similarity threshold for this primitive (calibrated from shadow-mode data)
    theta_tc: float = Field(default=0.70, ge=0.0, le=1.0)

    # Canonical response
    canonical_action_type: str
    canonical_action_summary: str
    canonical_shadow_actions: list[CanonicalShadowAction] = Field(default_factory=list)

    # Administrative
    created_at: datetime
    created_by: str
    source_event_ids: list[str] = Field(default_factory=list)
    domain_context: dict[str, Any] = Field(default_factory=dict)


class CodebookMatchResult(BaseModel):
    """
    Result of matching an event's cause signature against the codebook (FIG. 8).

    is_novel=False → compressed token = (matched_primitive_id, delta_vector)
    is_novel=True  → HITL required; store full uncompressed waveform (FIG. 8 path 806)
    """
    matched_primitive_id: str | None  # None when is_novel=True
    failure_mode_tag: str | None      # None when is_novel=True
    tc_score: float                   # cosine similarity ∈ [0.0, 1.0]
    theta_tc: float                   # threshold used for this match decision
    is_novel: bool                    # True → HITL required
    shared_instruments: int           # number of shared feature dimensions used in scoring
    delta_vector: dict[str, float]    # {feature_key: fractional_deviation}; {} when novel
    all_scores: dict[str, float]      # {primitive_id: tc_score} for every primitive scored


class CodebookExpansionRequest(BaseModel):
    """
    HITL request to add a new primitive to the codebook (FIG. 8 step 808).

    source_event_id must point to a validated event in the corpus backend.
    confirmed_failure_mode_tag is the human-verified tag — may differ from
    the event's intuition.failure_mode_tag if the operator revised it during
    debrief-ui validation.
    """
    source_event_id: str
    confirmed_failure_mode_tag: str
    description: str
    theta_tc_override: float | None = None  # None → use codebook theta_tc_default
    requested_by: str
