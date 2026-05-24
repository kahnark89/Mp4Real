"""
bootstrap_seed_event.py
=======================
One-time script. Ingests the April 8, 2026 PIE demonstration event —
the prior art priority date for the ArcShield / mp4Real patent filing.

Run from backend/api/:
    python bootstrap_seed_event.py

Safe to run multiple times — duplicate event_id raises SchemaValidationError
and exits cleanly without corrupting the corpus.
"""

import asyncio
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from arcshield.corpus.backends import get_backend
from arcshield.schema import (
    Action, ActionStep, ActionType,
    BiometricSnapshot,
    Cause,
    CIAEREvent,
    DeltaDirection,
    Effect,
    Intuition,
    OutcomeTag,
    PreEnv,
    PredictionMatch,
    ProductQualityImpact,
    Result,
    SensorDelta,
    SensorReading,
    ShadowAction,
    SRKLevel,
    TriggerSource,
)
from arcshield.corpus.backend import SchemaValidationError

from datetime import datetime, timezone
from uuid import UUID

# ---------------------------------------------------------------------------
# Seed event — April 8, 2026, PPVC Line 1, Hollowell Industries
# First live PIE (Perceptual Inference Elicitation) demonstration.
# Prior art priority date for ArcShield / mp4Real provisional patent.
# ---------------------------------------------------------------------------

# CDT = UTC-5 in April (before DST switch)
T_CAUSE  = datetime(2026, 4, 8, 7, 42, 11, tzinfo=timezone.utc)   # 02:42 CDT
T_EFFECT = datetime(2026, 4, 8, 7, 46, 33, tzinfo=timezone.utc)   # ~4 min later
T_RESULT = datetime(2026, 4, 8, 7, 48,  0, tzinfo=timezone.utc)

SEED_EVENT = CIAEREvent(
    schema_version        = "1.0",
    event_id              = UUID("6ab2942f-3a22-4824-9b9a-fb829e414e82"),
    timestamp_start       = T_CAUSE,
    timestamp_end         = T_RESULT,
    duration_sec          = int((T_RESULT - T_CAUSE).total_seconds()),
    facility_id           = "HOLLOWELL_PPVC1",
    line_id               = "PPVC_LINE_1",
    operator_id           = "op_hash_kc_7a3f2b",   # Kahn Capps, anonymized
    operator_tenure_years = 10.0,
    shift_id              = "2026-04-08-DAY",
    trigger_source        = TriggerSource.OPERATOR_MANUAL,
    domain_context        = {
        "process"        : "pvc_extrusion",
        "product_spec"   : "PPVC_compound_schedule_A",
        "line_output_lbh": 380,
        "screw_rpm"      : 52,
        "note"           : "First live PIE demonstration — prior art priority date 2026-04-08",
    },

    pre_env = PreEnv(
        operator_id           = "op_hash_kc_7a3f2b",
        shift_phase           = "steady_state",
        material_batch_id     = "BATCH-2026-04-08-A",
        ambient_temp_f        = 78.0,
        recent_events_summary = "Shift start normal. Line running steady for 90 min. No prior anomalies.",
        crew_state_tag        = "full_crew",
    ),

    cause = Cause(
        capture_timestamp = T_CAUSE,
        trigger_source    = TriggerSource.OPERATOR_MANUAL,
        sensor_readings   = [
            SensorReading(instrument_id="crammer_amps",    value=67.3, unit="A",   confidence=0.98),
            SensorReading(instrument_id="zone1_temp_f",    value=348.0, unit="°F", confidence=0.97),
            SensorReading(instrument_id="die_pressure_psi",value=2840.0,unit="psi",confidence=0.96),
            SensorReading(instrument_id="motor_amps",      value=88.5, unit="A",   confidence=0.98),
            SensorReading(instrument_id="melt_temp_f",     value=371.0, unit="°F", confidence=0.95),
        ],
        biometric_snapshot = BiometricSnapshot(
            hr_bpm            = 84.0,
            hrv_rmssd_ms      = 31.2,
            accelerometer_mag = 1.1,
            raw_available     = False,   # Polar not yet integrated at demo date
        ),
        acoustic_profile          = {"spectral_delta_db": 4.2, "dominant_freq_hz": 210, "note": "elevated crammer rattle"},
        gaze_dwell_duration_sec   = 3.8,
        visual_anchor_description = "crammer amp display — reading 67A vs 42A baseline",
        escalation_state          = 2,
    ),

    intuition = Intuition(
        srk_level         = SRKLevel.KNOWLEDGE,
        causal_hypothesis = (
            "Material bridging in crammer feed throat / funnel. PVC regrind "
            "from this batch has higher fines content than spec — fines pack "
            "under crammer pressure and form an arch that restricts gravity "
            "feed into the screw. Crammer overcomes it intermittently, hence "
            "the amp spike pattern rather than a sustained high reading. "
            "Zone 1 temp drop is a secondary effect: reduced material throughput "
            "into Zone 1 means less frictional heat generated at the screw root."
        ),
        failure_mode_tag  = "material_segregation_funnel_flow",
        confidence_level  = 0.87,
        projection        = (
            "If uncorrected: crammer will cycle between overcurrent trips and "
            "recovery, output rate drops 15-20%, and Zone 1 temp continues to "
            "fall — risking poor melt homogeneity and surface defect on extrudate."
        ),
        voice_transcript  = (
            "Crammer's spiking again. Sixty-seven. That's the fines bridging — "
            "same pattern as last Tuesday. Zone one's dropping too. I'm going "
            "to rake the throat."
        ),
        biometric_signature = BiometricSnapshot(
            hr_bpm            = 84.0,
            hrv_rmssd_ms      = 28.5,
            accelerometer_mag = 1.3,
            raw_available     = False,
        ),
    ),

    action = Action(
        action_type      = ActionType.MECHANICAL_INSPECT,
        action_timestamp = T_CAUSE,
        action_rationale = (
            "Rake feed throat to break the fines arch and restore gravity flow. "
            "Crammer speed reduced to idle first to prevent compaction of the "
            "bridged material under active crammer pressure."
        ),
        action_sequence  = [
            ActionStep(
                step_id           = 1,
                description       = "Reduce crammer speed to idle.",
                parameter_changed = "crammer_rpm",
                from_value        = 45,
                to_value          = 10,
                rationale         = "Prevent compaction of bridged material under active crammer pressure.",
            ),
            ActionStep(
                step_id           = 2,
                description       = "Rake feed throat with brass rod — three passes.",
                parameter_changed = None,
                from_value        = None,
                to_value          = None,
                rationale         = "Break the fines arch. Brass rod to avoid contamination.",
            ),
            ActionStep(
                step_id           = 3,
                description       = "Restore crammer speed to 45 RPM.",
                parameter_changed = "crammer_rpm",
                from_value        = 10,
                to_value          = 45,
                rationale         = "Resume normal feed once flow confirmed by amp normalization.",
            ),
        ],
    ),

    shadow_actions = [
        ShadowAction(
            action_type             = ActionType.PROCESS_HALT,
            rejection_rationale     = (
                "Full halt unnecessary at escalation_state 2. Bridging is recoverable "
                "without stopping the screw — a halt would introduce a cold plug risk "
                "and waste ~20 min of restart time. Halt warranted only if crammer "
                "trips on overcurrent or if melt temp falls below 360°F."
            ),
            confidence_in_rejection = 0.92,
        ),
        ShadowAction(
            action_type             = ActionType.PARAMETER_ADJUST,
            rejection_rationale     = (
                "Increasing Zone 1 setpoint to compensate for the temp drop would "
                "mask the root cause rather than fix it. The temp drop is a throughput "
                "effect — fix the feed, the temp self-corrects. Chasing the temperature "
                "risks overshoot once the bridge clears."
            ),
            confidence_in_rejection = 0.88,
        ),
    ],

    effect = Effect(
        capture_timestamp = T_EFFECT,
        sensor_readings   = [
            SensorReading(instrument_id="crammer_amps",    value=44.1, unit="A",   confidence=0.98),
            SensorReading(instrument_id="zone1_temp_f",    value=352.5, unit="°F", confidence=0.97),
            SensorReading(instrument_id="die_pressure_psi",value=2795.0,unit="psi",confidence=0.96),
            SensorReading(instrument_id="motor_amps",      value=86.2, unit="A",   confidence=0.98),
            SensorReading(instrument_id="melt_temp_f",     value=373.0, unit="°F", confidence=0.95),
        ],
        deltas = [
            SensorDelta(instrument_id="crammer_amps",    delta=-23.2, direction=DeltaDirection.IMPROVED),
            SensorDelta(instrument_id="zone1_temp_f",    delta=4.5,   direction=DeltaDirection.IMPROVED),
            SensorDelta(instrument_id="die_pressure_psi",delta=-45.0, direction=DeltaDirection.IMPROVED),
            SensorDelta(instrument_id="motor_amps",      delta=-2.3,  direction=DeltaDirection.IMPROVED),
            SensorDelta(instrument_id="melt_temp_f",     delta=2.0,   direction=DeltaDirection.IMPROVED),
        ],
        prediction_match = PredictionMatch.CONFIRMED,
    ),

    result = Result(
        completed_at               = T_RESULT,
        outcome_tag                = OutcomeTag.PROBLEM_PREVENTED,
        escalation_state_at_result = 0,
        escalation_delta           = 2,    # cause=2, result=0 → delta=2
        hypothesis_confirmed       = True,
        model_revision             = None,
        product_quality_impact     = ProductQualityImpact.NO_IMPACT,
        graph_weight               = 0.88,
        operator_notes             = (
            "Textbook funnel-flow bridge on fines-heavy regrind. Crammer normalized "
            "in under 4 minutes post-rake. Zone 1 self-corrected without setpoint "
            "change — confirms the thermal effect was throughput-driven, not heater "
            "failure. Flag this batch for QC review on fines content."
        ),
    ),
)


async def main() -> None:
    config_path = Path(__file__).parent / "config.toml"
    corpus_dir  = Path(__file__).parent / "corpus"

    backend = get_backend("json", corpus_dir=str(corpus_dir), facility_id="HOLLOWELL_PPVC1")
    async with backend:
        try:
            event_id = await backend.ingest_event(SEED_EVENT)
            print(f"Ingested seed event: {event_id}")
            print(f"Corpus depth: {backend.corpus_depth}")
            print(f"Corpus location: {corpus_dir.resolve()}")
        except SchemaValidationError as exc:
            if "already exists" in str(exc):
                print(f"Seed event already present — skipping. (corpus_depth={backend.corpus_depth})")
            else:
                print(f"Schema error: {exc}", file=sys.stderr)
                sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())
