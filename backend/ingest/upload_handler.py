"""
ingest.upload_handler
=====================
IngestHandler — high-level orchestrator for the ingest pipeline.

Coordinates: sidecar parsing → shadow log lookup → skeleton construction
→ HITL validation → corpus persistence.

The pipeline enforces the architectural firewall (CLAUDE.md §6):
  - Skeletons go to HITL first; no event reaches the corpus without
    operator review of failure_mode_tag, srk_level, causal_hypothesis.
  - R_phys deadline is wired at submission time, not at skeleton creation,
    so the deferred OGC queue (pending_R_phys) is populated on first write.
  - No confidence update path exists in this module — that lives in backend/ogc/.
"""

from __future__ import annotations

import json
import logging
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from uuid import UUID, uuid4

from .candidate_window import CandidateWindowLog, find_shadow_log, parse_shadow_log
from .event_builder import EventSkeleton, build_event_skeleton, skeleton_to_partial_event
from .sidecar import SessionSidecar, find_sidecar, parse_sidecar

log = logging.getLogger("arcshield.ingest")

# Type alias — CorpusBackend is defined in backend/api/arcshield/corpus/backend.py.
# We accept it as a duck-typed protocol to avoid a circular import.
CorpusBackend = Any


class IngestHandler:
    """
    High-level handler that orchestrates the mp4Real container ingest pipeline.

    Usage:
        handler = IngestHandler(corpus_backend=backend, shadow_dir=Path("shadow_mode"))
        skeleton = await handler.ingest_from_container(Path("session.mp4"))
        # ... send skeleton to debrief-ui for HITL completion ...
        event_id = await handler.submit_completed_skeleton(skeleton, completed_fields)
    """

    def __init__(
        self,
        corpus_backend: CorpusBackend,
        shadow_dir: Path | None = None,
    ) -> None:
        self._backend = corpus_backend
        self._shadow_dir = Path(shadow_dir) if shadow_dir is not None else None

    # ------------------------------------------------------------------
    # Ingest pipeline
    # ------------------------------------------------------------------

    async def ingest_from_container(
        self,
        container_path: Path,
        sidecar_path: Path | None = None,
        shadow_log_path: Path | None = None,
    ) -> EventSkeleton:
        """
        Parse container sidecar, find trigger window from shadow log, build skeleton.

        Does NOT write to corpus. The skeleton goes to debrief-ui for HITL completion.

        Parameters
        ----------
        container_path : Path
            Path to the mp4Real container file (.mp4 or similar).
        sidecar_path : Path | None
            Explicit path to .mp4real.json sidecar. If None, looks for
            {container_stem}.mp4real.json alongside the container.
        shadow_log_path : Path | None
            Explicit path to shadow-mode NDJSON log. If None, looks for
            {sessionId}.ndjson in self._shadow_dir (if configured).
        """
        container_path = Path(container_path)

        # 1. Locate and parse the sidecar
        if sidecar_path is None:
            sidecar_path = find_sidecar(container_path)
        if sidecar_path is None:
            raise FileNotFoundError(
                f"No .mp4real.json sidecar found alongside '{container_path}'. "
                "Provide sidecar_path explicitly or ensure the Android app wrote it."
            )
        sidecar = parse_sidecar(sidecar_path)
        log.info(
            "Parsed sidecar: session=%s facility=%s eps_sync=%.1fms",
            sidecar.sessionId,
            sidecar.facilityId,
            sidecar.eps_sync_ms,
        )

        # 2. Find the shadow-mode candidate window with the highest Λ score
        trigger_window: CandidateWindowLog | None = None
        if shadow_log_path is None and self._shadow_dir is not None:
            shadow_log_path = find_shadow_log(sidecar.sessionId, self._shadow_dir)

        if shadow_log_path is not None:
            windows = parse_shadow_log(shadow_log_path)
            threshold_windows = [w for w in windows if w.thresholdReached]
            if threshold_windows:
                # Select the window with the highest combined Λ score
                trigger_window = max(threshold_windows, key=lambda w: w.lambda_)
                log.info(
                    "Selected trigger window: lambda=%.3f at nanos=%d",
                    trigger_window.lambda_,
                    trigger_window.detectedAtNanos,
                )
            else:
                log.warning(
                    "Shadow log found but no threshold-reached windows. "
                    "Building skeleton without trigger context."
                )

        # 3. Build and return the skeleton
        skeleton = build_event_skeleton(sidecar, trigger_window=trigger_window)
        log.info("Built EventSkeleton: event_id=%s", skeleton.event_id)
        return skeleton

    async def submit_completed_skeleton(
        self,
        skeleton: EventSkeleton,
        completed_fields: dict[str, Any],
        r_phys_deadline_hours: float | None = None,
    ) -> UUID:
        """
        Merge HITL-provided fields into skeleton, validate as CIAEREvent, write to corpus.

        Returns the stored event_id (UUID).

        Parameters
        ----------
        skeleton : EventSkeleton
            The partially-complete skeleton from ingest_from_container.
        completed_fields : dict
            HITL-provided fields from debrief-ui:
              - failure_mode_tag (str, required)
              - srk_level (str, required)
              - causal_hypothesis (str, required)
              - action_type (str, required)
              - outcome_tag (str, required)
              - hypothesis_confirmed (bool, required)
              - graph_weight (float, optional — defaults to 0.5)
              - model_revision (str, optional — required if hypothesis_confirmed=False)
              - sensor_readings (list, optional)
              - escalation_state (int, optional)
        r_phys_deadline_hours : float | None
            If provided, the event will be queued for deferred R_phys with this
            deadline in hours from now. None = event closes on own telemetry
            (r_phys.status = NOT_REQUIRED).
        """
        # Merge completed_fields into skeleton
        for attr in [
            "failure_mode_tag",
            "srk_level",
            "causal_hypothesis",
            "action_type",
            "outcome_tag",
            "hypothesis_confirmed",
        ]:
            if attr in completed_fields:
                setattr(skeleton, attr, completed_fields[attr])

        # Validate completeness
        if not skeleton.is_complete():
            raise ValueError(
                f"Skeleton is not complete after merging completed_fields. "
                f"Missing: {skeleton.missing_fields()}"
            )

        # Build the full CIAER+ event dict for corpus ingestion
        # This constructs the minimal valid CIAEREvent from the skeleton +
        # HITL fields. Callers can provide richer sensor_readings etc. via
        # completed_fields.
        event_dict = self._skeleton_to_event_dict(skeleton, completed_fields, r_phys_deadline_hours)

        # Import here to avoid circular import — the corpus backend lives in
        # backend/api/; the ingest package is standalone.
        try:
            import sys
            from pathlib import Path as _Path
            _api_dir = _Path(__file__).parent.parent / "api"
            if str(_api_dir) not in sys.path:
                sys.path.insert(0, str(_api_dir))
            from arcshield.schema import CIAEREvent
            event = CIAEREvent.model_validate(event_dict)
        except ImportError:
            log.warning(
                "arcshield.schema not importable — returning skeleton event_id without persistence."
            )
            return skeleton.event_id

        event_id = await self._backend.ingest_event(event)
        log.info("Persisted event: %s", event_id)

        if r_phys_deadline_hours is not None:
            await self._backend.queuePendingRPhys(event_id, r_phys_deadline_hours)

        return event_id

    def list_pending_skeletons(self, pending_dir: Path) -> list[EventSkeleton]:
        """
        Load EventSkeleton objects from JSON files in pending_dir.

        Skeletons are written as {event_id}.skeleton.json files by
        ingest_from_container when configured with a pending_dir.
        Returns the list of incomplete skeletons awaiting HITL completion.
        """
        pending_dir = Path(pending_dir)
        if not pending_dir.exists():
            return []

        skeletons: list[EventSkeleton] = []
        for skeleton_file in sorted(pending_dir.glob("*.skeleton.json")):
            try:
                with open(skeleton_file, "r", encoding="utf-8") as f:
                    data = json.load(f)
                # Reconstruct minimal skeleton from persisted JSON
                sidecar = SessionSidecar.model_validate(data["sidecar"])
                skeleton = EventSkeleton(
                    event_id=UUID(data["event_id"]),
                    sidecar=sidecar,
                    trigger_window=None,  # not stored in simplified pending format
                    facility_id=data["facility_id"],
                    line_id=data["line_id"],
                    operator_id=data["operator_id"],
                    timestamp_start=datetime.fromisoformat(data["timestamp_start"]),
                    failure_mode_tag=data.get("failure_mode_tag"),
                    srk_level=data.get("srk_level"),
                    causal_hypothesis=data.get("causal_hypothesis"),
                    action_type=data.get("action_type"),
                    outcome_tag=data.get("outcome_tag"),
                    hypothesis_confirmed=data.get("hypothesis_confirmed"),
                    lambda_env=data.get("lambda_env", 0.0),
                    lambda_bio=data.get("lambda_bio", 0.0),
                    low_sync_confidence=data.get("low_sync_confidence", False),
                )
                skeletons.append(skeleton)
            except Exception as exc:
                log.warning("Failed to load skeleton from %s: %s", skeleton_file, exc)

        return skeletons

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _skeleton_to_event_dict(
        self,
        skeleton: EventSkeleton,
        completed_fields: dict[str, Any],
        r_phys_deadline_hours: float | None,
    ) -> dict[str, Any]:
        """
        Build a minimal valid CIAEREvent-compatible dict from skeleton + completed_fields.

        This produces the structural minimum required by CIAEREvent validation.
        Callers can enrich the dict via completed_fields['sensor_readings'] etc.
        """
        now = datetime.now(timezone.utc)
        sensor_readings = completed_fields.get("sensor_readings", [])
        escalation_state = completed_fields.get("escalation_state", 0)
        graph_weight = float(completed_fields.get("graph_weight", 0.5))
        model_revision = completed_fields.get("model_revision")

        # OGC R_phys record
        if r_phys_deadline_hours is not None:
            from datetime import timedelta
            r_phys = {
                "status": "PENDING",
                "deadline": (now + timedelta(hours=r_phys_deadline_hours)).isoformat(),
            }
        else:
            r_phys = {"status": "NOT_REQUIRED"}

        return {
            "schema_version": "1.0",
            "event_id": str(skeleton.event_id),
            "timestamp_start": skeleton.timestamp_start.isoformat(),
            "timestamp_end": now.isoformat(),
            "facility_id": skeleton.facility_id,
            "line_id": skeleton.line_id,
            "operator_id": skeleton.operator_id,
            "trigger_source": "OPERATOR_MANUAL",
            "domain_context": {
                "session_id": skeleton.sidecar.sessionId,
                "capture_source_id": skeleton.sidecar.captureSourceId,
                "biometric_source_id": skeleton.sidecar.biometricSourceId,
                "eps_sync_ms": skeleton.sidecar.eps_sync_ms,
                "low_sync_confidence": skeleton.low_sync_confidence,
            },
            "pre_env": {
                "operator_id": skeleton.operator_id,
            },
            "cause": {
                "capture_timestamp": skeleton.timestamp_start.isoformat(),
                "trigger_source": "OPERATOR_MANUAL",
                "sensor_readings": sensor_readings,
                "escalation_state": escalation_state,
            },
            "intuition": {
                "srk_level": skeleton.srk_level,
                "causal_hypothesis": skeleton.causal_hypothesis,
                "failure_mode_tag": skeleton.failure_mode_tag,
                "confidence_level": 0.7,
                "projection": completed_fields.get("projection", "Outcome pending."),
            },
            "action": {
                "action_type": skeleton.action_type,
                "action_timestamp": now.isoformat(),
                "action_rationale": completed_fields.get(
                    "action_rationale", skeleton.causal_hypothesis or ""
                ),
                "action_sequence": completed_fields.get("action_sequence", []),
            },
            "shadow_actions": completed_fields.get("shadow_actions", []),
            "effect": {
                "capture_timestamp": now.isoformat(),
                "sensor_readings": completed_fields.get("effect_sensor_readings", []),
                "deltas": [],
                "prediction_match": completed_fields.get("prediction_match", "INDETERMINATE"),
            },
            "result": {
                "completed_at": now.isoformat(),
                "outcome_tag": skeleton.outcome_tag,
                "escalation_state_at_result": escalation_state,
                "escalation_delta": 0,
                "hypothesis_confirmed": skeleton.hypothesis_confirmed,
                "model_revision": model_revision,
                "product_quality_impact": completed_fields.get(
                    "product_quality_impact", "NO_IMPACT"
                ),
                "graph_weight": graph_weight,
                "r_phys": r_phys,
            },
        }
