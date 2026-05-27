"""
arcshield.corpus.backends.json_backend
=======================================
Phase 1 implementation of CorpusBackend.

Storage: one JSON file per CIAER event, in {corpus_dir}/events/{event_id}.json
Index:   in-memory dict built at open(), updated incrementally on ingest.
Query:   full in-memory scan with cosine similarity for cause signature matching.

Suitable for: single-facility, single-operator corpus up to ~500 events.
No external dependencies beyond pydantic and Python stdlib.

Upgrade path: replace JsonCorpusBackend with SqliteCorpusBackend in
arcshield_mcp/config.toml — zero changes to any MCP tool handler.
"""

from __future__ import annotations

import asyncio
import json
import math
import os
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from uuid import UUID

from arcshield.corpus.backend import (
    CorpusBackend,
    EventNotFoundError,
    SchemaValidationError,
    BackendUnavailableError,
    WeightUpdate,
    RPhysUpdate,
)
from arcshield.schema import (
    CIAEREvent,
    CauseSignatureQuery,
    FailureModeQuery,
    FailureModeSummary,
    OutcomeTag,
    PredictionMatch,
    RPhysRecord,
    RPhysStatus,
)


class JsonCorpusBackend(CorpusBackend):
    """
    Flat-file JSON implementation of CorpusBackend.

    Thread safety: not thread-safe. Designed for single-process MCP server use.
    All async methods delegate sync I/O to asyncio.to_thread() to avoid
    blocking the event loop.
    """

    def __init__(self, corpus_dir: str | Path, facility_id: str, ogc_alpha: float = 0.1) -> None:
        self._corpus_dir   = Path(corpus_dir)
        self._facility_id  = facility_id
        self._ogc_alpha    = ogc_alpha
        self._events_dir   = self._corpus_dir / "events"
        self._audit_dir    = self._corpus_dir / "audit"

        # In-memory index: event_id (str) → lightweight summary dict.
        # Full records are loaded from disk on demand.
        self._index: dict[str, dict[str, Any]] = {}
        self._failure_mode_cache: list[FailureModeSummary] | None = None
        self._is_open = False

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------

    async def open(self) -> None:
        if self._is_open:
            return
        await asyncio.to_thread(self._init_directories)
        await asyncio.to_thread(self._build_index)
        self._is_open = True

    async def close(self) -> None:
        # No persistent connections to close in Phase 1.
        self._is_open = False

    def _init_directories(self) -> None:
        self._events_dir.mkdir(parents=True, exist_ok=True)
        self._audit_dir.mkdir(parents=True, exist_ok=True)

    def _build_index(self) -> None:
        """Scan corpus directory and build the in-memory index."""
        self._index = {}
        for path in self._events_dir.glob("*.json"):
            try:
                raw = json.loads(path.read_text(encoding="utf-8"))
                self._index_from_raw(raw)
            except Exception as exc:
                # Log and skip corrupt files — don't crash the server.
                print(f"[arcshield] WARNING: skipping corrupt event file {path}: {exc}")
        self._failure_mode_cache = None  # invalidate

    def _index_from_raw(self, raw: dict) -> None:
        """Extract index fields from a raw event dict without full validation."""
        eid = raw.get("event_id")
        if not eid:
            return
        self._index[str(eid)] = {
            "event_id"        : str(eid),
            "failure_mode_tag": raw.get("intuition", {}).get("failure_mode_tag"),
            "srk_level"       : raw.get("intuition", {}).get("srk_level"),
            "escalation_state": raw.get("cause", {}).get("escalation_state"),
            "graph_weight"    : raw.get("result", {}).get("graph_weight", 0.0),
            "outcome_tag"     : raw.get("result", {}).get("outcome_tag"),
            "timestamp_start" : raw.get("timestamp_start"),
            "instruments"     : [
                r["instrument_id"]
                for r in raw.get("cause", {}).get("sensor_readings", [])
            ],
        }

    def _event_path(self, event_id: UUID) -> Path:
        return self._events_dir / f"{event_id}.json"

    def _audit_path(self, event_id: UUID) -> Path:
        return self._audit_dir / f"{event_id}_weight_log.json"

    # ------------------------------------------------------------------
    # Identity
    # ------------------------------------------------------------------

    @property
    def facility_id(self) -> str:
        return self._facility_id

    @property
    def corpus_depth(self) -> int:
        return len(self._index)

    # ------------------------------------------------------------------
    # Internal I/O helpers
    # ------------------------------------------------------------------

    def _load_event(self, event_id: UUID) -> CIAEREvent:
        path = self._event_path(event_id)
        if not path.exists():
            raise EventNotFoundError(event_id)
        raw = json.loads(path.read_text(encoding="utf-8"))
        return CIAEREvent.model_validate(raw)

    def _save_event(self, event: CIAEREvent) -> None:
        path = self._event_path(event.event_id)
        path.write_text(
            event.model_dump_json(indent=2),
            encoding="utf-8"
        )

    def _append_audit_entry(self, update: WeightUpdate) -> None:
        path = self._audit_path(update.event_id)
        entries: list[dict] = []
        if path.exists():
            entries = json.loads(path.read_text(encoding="utf-8"))
        entries.append({
            "timestamp"  : datetime.now(timezone.utc).isoformat(),
            "new_weight" : update.new_weight,
            "rationale"  : update.rationale,
            "updated_by" : update.updated_by,
        })
        path.write_text(json.dumps(entries, indent=2), encoding="utf-8")

    # ------------------------------------------------------------------
    # Write operations
    # ------------------------------------------------------------------

    async def ingest_event(self, event: CIAEREvent) -> UUID:
        self._assert_open()

        # Privacy boundary check
        if event.facility_id != self._facility_id:
            raise SchemaValidationError(
                f"Event facility_id '{event.facility_id}' does not match "
                f"backend facility_id '{self._facility_id}'."
            )

        # Uniqueness check
        if str(event.event_id) in self._index:
            raise SchemaValidationError(
                f"Event {event.event_id} already exists in corpus."
            )

        # Temporal coherence
        if event.timestamp_end and event.timestamp_start >= event.timestamp_end:
            raise SchemaValidationError(
                "timestamp_start must be before timestamp_end."
            )

        # escalation_delta coherence (CLAUDE.md §2.4 schema invariant)
        expected_delta = (
            event.cause.escalation_state - event.result.escalation_state_at_result
        )
        if event.result.escalation_delta != expected_delta:
            raise SchemaValidationError(
                f"escalation_delta {event.result.escalation_delta} does not match "
                f"cause.escalation_state ({event.cause.escalation_state}) - "
                f"result.escalation_state_at_result "
                f"({event.result.escalation_state_at_result}) = {expected_delta}"
            )

        # INDETERMINATE events must have a pending R_phys deadline (CLAUDE.md §2.4)
        if event.effect.prediction_match == PredictionMatch.INDETERMINATE:
            rp = event.result.r_phys
            if rp is None or rp.status != RPhysStatus.PENDING:
                raise SchemaValidationError(
                    "Events with prediction_match=INDETERMINATE must have "
                    "result.r_phys.status=PENDING (deadline set). "
                    "Either confirm/disconfirm on own telemetry or queue a deferred R_phys."
                )

        try:
            await asyncio.to_thread(self._save_event, event)
        except OSError as exc:
            raise BackendUnavailableError(f"Write failed: {exc}") from exc

        # Update index
        raw = json.loads(event.model_dump_json())
        self._index_from_raw(raw)
        self._failure_mode_cache = None  # invalidate summary cache

        return event.event_id

    async def update_graph_weight(self, update: WeightUpdate) -> CIAEREvent:
        self._assert_open()

        if not (0.0 <= update.new_weight <= 1.0):
            raise ValueError(f"new_weight must be in [0.0, 1.0], got {update.new_weight}")

        def _do_update() -> CIAEREvent:
            event = self._load_event(update.event_id)
            # Pydantic v2: create updated copy
            updated = event.model_copy(
                update={"result": event.result.model_copy(
                    update={"graph_weight": update.new_weight}
                )}
            )
            self._save_event(updated)
            self._append_audit_entry(update)
            return updated

        updated_event = await asyncio.to_thread(_do_update)

        # Update index entry
        eid = str(update.event_id)
        if eid in self._index:
            self._index[eid]["graph_weight"] = update.new_weight
        self._failure_mode_cache = None

        return updated_event

    # ------------------------------------------------------------------
    # Read — single event
    # ------------------------------------------------------------------

    async def get_event(self, event_id: UUID) -> CIAEREvent:
        self._assert_open()
        if str(event_id) not in self._index:
            raise EventNotFoundError(event_id)
        return await asyncio.to_thread(self._load_event, event_id)

    async def get_shadow_actions(self, event_id: UUID) -> list[dict]:
        self._assert_open()
        event = await self.get_event(event_id)
        return [sa.model_dump() for sa in event.shadow_actions]

    # ------------------------------------------------------------------
    # Read — queries
    # ------------------------------------------------------------------

    async def query_by_failure_mode(
        self, query: FailureModeQuery
    ) -> list[CIAEREvent]:
        self._assert_open()

        candidates = [
            entry for entry in self._index.values()
            if (
                entry["failure_mode_tag"] == query.failure_mode_tag
                and entry["graph_weight"] >= query.min_graph_weight
                and (query.srk_filter is None or entry["srk_level"] == query.srk_filter.value)
            )
        ]
        candidates.sort(key=lambda e: e["graph_weight"], reverse=True)
        candidates = candidates[: query.top_n]

        events = []
        for entry in candidates:
            try:
                event = await asyncio.to_thread(
                    self._load_event, UUID(entry["event_id"])
                )
                events.append(event)
            except (EventNotFoundError, Exception):
                pass  # index/disk sync issue; skip

        return events

    async def query_by_cause_signature(
        self, query: CauseSignatureQuery
    ) -> list[CIAEREvent]:
        self._assert_open()

        query_instruments = {
            r.instrument_id: r.value for r in query.sensor_readings
        }

        scored: list[tuple[float, str]] = []
        for entry in self._index.values():
            # Hard filter: escalation_state within ±1
            stored_esc = entry.get("escalation_state")
            if stored_esc is None:
                continue
            if abs(stored_esc - query.escalation_state) > 1:
                continue
            if entry["graph_weight"] < query.min_graph_weight:
                continue

            # Cosine similarity on shared instruments
            stored_instruments: list[str] = entry.get("instruments", [])
            shared = set(query_instruments.keys()) & set(stored_instruments)
            if not shared:
                continue

            # Similarity = fraction of query instruments covered, weighted by
            # graph_weight as tiebreaker. Full records needed for value comparison
            # in Phase 1 — we use instrument coverage as the proxy.
            coverage = len(shared) / max(len(query_instruments), 1)
            score = coverage * 0.9 + entry["graph_weight"] * 0.1
            scored.append((score, entry["event_id"]))

        scored.sort(reverse=True)
        top = scored[: query.top_n]

        events = []
        for _, eid in top:
            try:
                events.append(await asyncio.to_thread(self._load_event, UUID(eid)))
            except Exception:
                pass

        return events

    async def list_failure_modes(self) -> list[FailureModeSummary]:
        self._assert_open()

        if self._failure_mode_cache is not None:
            return self._failure_mode_cache

        aggregator: dict[str, dict] = defaultdict(lambda: {
            "count": 0,
            "weight_sum": 0.0,
            "outcomes": defaultdict(int),
        })

        for entry in self._index.values():
            tag = entry.get("failure_mode_tag") or "UNKNOWN"
            agg = aggregator[tag]
            agg["count"] += 1
            agg["weight_sum"] += entry.get("graph_weight", 0.0)
            outcome = entry.get("outcome_tag") or "UNKNOWN"
            agg["outcomes"][outcome] += 1

        summaries = [
            FailureModeSummary(
                failure_mode_tag     = tag,
                event_count          = agg["count"],
                mean_graph_weight    = agg["weight_sum"] / agg["count"],
                outcome_distribution = dict(agg["outcomes"]),
            )
            for tag, agg in aggregator.items()
        ]
        summaries.sort(key=lambda s: s.event_count, reverse=True)

        self._failure_mode_cache = summaries
        return summaries

    # ------------------------------------------------------------------
    # OGC — Outcome-Grounded Confidence (CLAUDE.md §6)
    # ------------------------------------------------------------------

    async def record_r_phys(self, update: RPhysUpdate) -> CIAEREvent:
        self._assert_open()

        if not (0.0 <= update.value <= 1.0):
            raise ValueError(f"R_phys value must be in [0.0, 1.0], got {update.value}")

        def _do_record() -> CIAEREvent:
            event = self._load_event(update.event_id)
            rp = event.result.r_phys
            if rp is None:
                raise SchemaValidationError(
                    f"Event {update.event_id} has no r_phys record. "
                    "Set r_phys at ingest time (r_phys_deadline_hours) before recording arrival."
                )
            if rp.status != RPhysStatus.PENDING:
                raise SchemaValidationError(
                    f"Event {update.event_id} r_phys.status is '{rp.status.value}', "
                    "expected PENDING. Cannot record R_phys twice or after expiry."
                )

            # OGC update rule (CLAUDE.md §6.1) — reward and compliance are separate paths
            α = self._ogc_alpha
            advised = event.result.advised_action_type
            compliance = 1 if advised is None else (1 if event.action.action_type == advised else 0)
            δ = update.value - event.result.graph_weight
            new_weight = max(0.0, min(1.0, event.result.graph_weight + α * δ * compliance))

            now = datetime.now(timezone.utc)
            updated_r_phys = rp.model_copy(update={
                "status"    : RPhysStatus.ARRIVED,
                "arrived_at": now,
                "value"     : update.value,
                "source"    : update.source,
            })
            updated = event.model_copy(update={
                "result": event.result.model_copy(update={
                    "graph_weight": new_weight,
                    "r_phys"      : updated_r_phys,
                })
            })
            self._save_event(updated)
            self._append_audit_entry(WeightUpdate(
                event_id   = update.event_id,
                new_weight = new_weight,
                rationale  = f"OGC_R_PHYS: {update.source} (value={update.value:.4f}, "
                             f"delta={δ:.4f}, compliance={compliance}, alpha={α})",
                updated_by = update.updated_by,
            ))
            return updated

        updated_event = await asyncio.to_thread(_do_record)
        eid = str(update.event_id)
        if eid in self._index:
            self._index[eid]["graph_weight"] = updated_event.result.graph_weight
        self._failure_mode_cache = None
        return updated_event

    async def expire_r_phys_deadlines(self) -> list[UUID]:
        self._assert_open()
        now = datetime.now(timezone.utc)

        def _do_expire() -> list[UUID]:
            expired: list[UUID] = []
            for eid_str in list(self._index.keys()):
                try:
                    event = self._load_event(UUID(eid_str))
                except EventNotFoundError:
                    continue
                rp = event.result.r_phys
                if rp is None or rp.status != RPhysStatus.PENDING:
                    continue
                if rp.deadline is None or rp.deadline > now:
                    continue
                updated = event.model_copy(update={
                    "result": event.result.model_copy(update={
                        "r_phys": rp.model_copy(update={"status": RPhysStatus.INDETERMINATE})
                    })
                })
                self._save_event(updated)
                expired.append(event.event_id)
            return expired

        return await asyncio.to_thread(_do_expire)

    async def list_pending_r_phys(self, max_results: int = 50) -> list[CIAEREvent]:
        self._assert_open()

        def _do_list() -> list[CIAEREvent]:
            pending: list[CIAEREvent] = []
            for eid_str in self._index.keys():
                try:
                    event = self._load_event(UUID(eid_str))
                except EventNotFoundError:
                    continue
                rp = event.result.r_phys
                if rp is not None and rp.status == RPhysStatus.PENDING:
                    pending.append(event)
            pending.sort(key=lambda e: (
                e.result.r_phys.deadline or datetime.max.replace(tzinfo=timezone.utc)
            ))
            return pending[:max_results]

        return await asyncio.to_thread(_do_list)

    # ------------------------------------------------------------------
    # Diagnostics
    # ------------------------------------------------------------------

    async def health_check(self) -> dict:
        status = "ok" if self._is_open else "unavailable"
        if self._is_open and not self._events_dir.exists():
            status = "degraded"
        return {
            "status"       : status,
            "backend_type" : "json",
            "facility_id"  : self._facility_id,
            "corpus_depth" : self.corpus_depth,
            "storage_path" : str(self._corpus_dir),
        }

    # ------------------------------------------------------------------
    # Guard
    # ------------------------------------------------------------------

    def _assert_open(self) -> None:
        if not self._is_open:
            raise BackendUnavailableError(
                "Backend is not open. Call await backend.open() first."
            )
