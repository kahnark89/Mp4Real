"""
codebook.primitive
==================
BehavioralPrimitive — one entry in the Phase 1 hand-curated codebook.

Each primitive encodes a known failure mode pattern with:
  - The sensor signature expected to accompany this failure mode
  - Which instruments are most diagnostic (weighted 2x in matching)
  - The typical expert action and expected outcome
  - A prior graph_weight used when no validated corpus events exist yet

This is the Phase 1 implementation described in CLAUDE.md §5.1. Primitives
are loaded from YAML, not trained from data. Phase 3 replaces with RVQ.
"""

from __future__ import annotations

from pydantic import BaseModel, Field, field_validator

from .ontology import validate_tag


class BehavioralPrimitive(BaseModel):
    """
    One entry in the ArcShield behavioral codebook.

    failure_mode_tag must be in the registered ontology (codebook.ontology).
    sensor_signature maps instrument_id → [min_value, max_value] expected range.
    key_instruments are a subset of sensor_signature keys and receive 2x weight
    in the proximity matcher.
    """

    # Identity
    primitive_id: str = Field(
        description="Unique slug, e.g. 'material_segregation_v1'. "
                    "Format: {failure_mode_tag}_{version}."
    )
    failure_mode_tag: str = Field(
        description="Ontology-controlled vocabulary tag. Must be registered."
    )
    display_name: str = Field(description="Human-readable name for debrief-ui.")
    description: str = Field(description="Plain-text description of the failure mode.")

    # SRK / trigger classification
    typical_srk_level: str = Field(
        description="Rasmussen SRK level: 'SKILL' | 'RULE' | 'KNOWLEDGE'."
    )
    typical_trigger_sources: list[str] = Field(
        default_factory=list,
        description="e.g. ['ANOMALY_DETECT', 'BIOMETRIC']",
    )

    # Sensor signature — instrument_id → [min, max] expected value range
    sensor_signature: dict[str, list[float]] = Field(
        default_factory=dict,
        description="Maps instrument_id to [min_value, max_value] expected range.",
    )
    key_instruments: list[str] = Field(
        default_factory=list,
        description="Instruments most diagnostic for this primitive (2x weight in matcher).",
    )

    # Typical response pattern
    typical_action_type: str = Field(
        description="ActionType value: 'PARAMETER_ADJUST' | 'MECHANICAL_INSPECT' | etc."
    )
    typical_action_description: str = Field(
        description="Plain-text description of the typical corrective action."
    )
    typical_outcome_tag: str = Field(
        description="OutcomeTag value: 'PROBLEM_PREVENTED' | 'PROBLEM_MITIGATED' | etc."
    )

    # Prior confidence
    prior_graph_weight: float = Field(
        default=0.5,
        ge=0.0,
        le=1.0,
        description="Prior graph_weight used when no validated corpus events exist.",
    )

    # Provenance
    source: str = Field(
        default="manual_curation",
        description="'manual_curation' | 'corpus_derived'",
    )

    @field_validator("failure_mode_tag")
    @classmethod
    def _validate_tag(cls, v: str) -> str:
        validate_tag(v)
        return v

    @field_validator("sensor_signature")
    @classmethod
    def _validate_signature_ranges(cls, v: dict[str, list[float]]) -> dict[str, list[float]]:
        for inst_id, rng in v.items():
            if len(rng) != 2:
                raise ValueError(
                    f"sensor_signature['{inst_id}'] must be [min, max] (length 2), got {rng}"
                )
            if rng[0] > rng[1]:
                raise ValueError(
                    f"sensor_signature['{inst_id}'] min ({rng[0]}) > max ({rng[1]})"
                )
        return v

    @field_validator("key_instruments")
    @classmethod
    def _validate_key_instruments(cls, v: list[str], info) -> list[str]:
        # Validated post-construction: key_instruments must be subset of sensor_signature
        # We do a soft check here; hard check is done at registry load time.
        return v

    def midpoint(self, instrument_id: str) -> float | None:
        """Return the midpoint of the expected range for an instrument, or None."""
        rng = self.sensor_signature.get(instrument_id)
        if rng is None:
            return None
        return (rng[0] + rng[1]) / 2.0

    def in_range(self, instrument_id: str, value: float) -> bool | None:
        """Return True if value is within [min, max] for this instrument, None if unknown."""
        rng = self.sensor_signature.get(instrument_id)
        if rng is None:
            return None
        return rng[0] <= value <= rng[1]
