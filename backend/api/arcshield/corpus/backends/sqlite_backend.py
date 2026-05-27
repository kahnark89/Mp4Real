"""
arcshield.corpus.backends.sqlite_backend
=========================================
Phase 2 implementation of CorpusBackend.

Storage: single SQLite database file via aiosqlite (native async).
Index:   dedicated columns on failure_mode_tag, escalation_state,
         graph_weight, operator_id — indexed for sub-millisecond pre-filtering.
Query:   value-proximity scoring (MOD-003) replaces coverage-only cosine;
         per-instrument score = 1 / (1 + |query_val − stored_val|),
         averaged across shared instruments, graph_weight as tiebreaker.

Suitable for: single-facility corpus from ~500 events up to ~5 000 events.
No external dependencies beyond aiosqlite and Python stdlib.

Upgrade path: replace SqliteCorpusBackend with GraphCorpusBackend in
config.toml — zero changes to any MCP tool handler.
"""

from __future__ import annotations

import json
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from uuid import UUID

import aiosqlite

from arcshield.corpus.backend import (
    BackendUnavailableError,
    CorpusBackend,
    EventNotFoundError,
    SchemaValidationError,
    WeightUpdate,
    RPhysUpdate,
)
from arcshield.schema import (
    CIAEREvent,
    CauseSignatureQuery,
    FailureModeQuery,
    FailureModeSummary,
    PredictionMatch,
    RPhysRecord,
    RPhysStatus,
)

# ---------------------------------------------------------------------------
# DDL
# ---------------------------------------------------------------------------

_DDL = """
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS events (
    event_id          TEXT PRIMARY KEY,
    facility_id       TEXT NOT NULL,
    operator_id       TEXT NOT NULL,
    failure_mode_tag  TEXT,
    srk_level         TEXT,
    escalation_state  INTEGER,
    graph_weight      REAL    NOT NULL DEFAULT 0.0,
    outcome_tag       TEXT,
    timestamp_start   TEXT    NOT NULL,
    -- JSON array of instrument_ids for fast overlap check
    instruments       TEXT    NOT NULL DEFAULT '[]',
    -- JSON dict {instrument_id: value} for value-proximity scoring (MOD-003)
    instrument_values TEXT    NOT NULL DEFAULT '{}',
    -- Full serialized CIAEREvent; source of truth for all other fields
    event_json        TEXT    NOT NULL,
    -- OGC deferred R_phys columns (CLAUDE.md §6) — NULL on pre-Phase-2 events
    r_phys_status     TEXT,
    r_phys_deadline   TEXT,
    r_phys_value      REAL
);

CREATE INDEX IF NOT EXISTS idx_failure_mode  ON events (failure_mode_tag);
CREATE INDEX IF NOT EXISTS idx_escalation    ON events (escalation_state);
CREATE INDEX IF NOT EXISTS idx_graph_weight  ON events (graph_weight DESC);
CREATE INDEX IF NOT EXISTS idx_operator      ON events (operator_id);
CREATE INDEX IF NOT EXISTS idx_r_phys        ON events (r_phys_status, r_phys_deadline);

CREATE TABLE IF NOT EXISTS weight_audit (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id    TEXT    NOT NULL,
    ts          TEXT    NOT NULL,
    new_weight  REAL    NOT NULL,
    rationale   TEXT    NOT NULL,
    updated_by  TEXT    NOT NULL,
    FOREIGN KEY (event_id) REFERENCES events (event_id)
);

CREATE INDEX IF NOT EXISTS idx_audit_event ON weight_audit (event_id);
"""


class SqliteCorpusBackend(CorpusBackend):
    """
    SQLite-backed implementation of CorpusBackend (Phase 2).

    Thread safety: aiosqlite serializes access to the connection; this class
    is safe for single-process, concurrent-coroutine MCP server use.

    corpus_depth is maintained as an in-memory counter updated on every
    ingest — O(1), never scans the table.
    """

    def __init__(self, db_path: str | Path, facility_id: str, ogc_alpha: float = 0.1) -> None:
        self._db_path        = Path(db_path)
        self._facility_id    = facility_id
        self._ogc_alpha      = ogc_alpha
        self._db: aiosqlite.Connection | None = None
        self._corpus_depth   = 0
        self._failure_mode_cache: list[FailureModeSummary] | None = None

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------

    async def open(self) -> None:
        if self._db is not None:
            return
        self._db_path.parent.mkdir(parents=True, exist_ok=True)
        self._db = await aiosqlite.connect(self._db_path)
        self._db.row_factory = aiosqlite.Row
        await self._db.executescript(_DDL)
        await self._db.commit()
        # Additive column migration for databases created before OGC columns existed
        for col, defn in [
            ("r_phys_status",   "TEXT"),
            ("r_phys_deadline", "TEXT"),
            ("r_phys_value",    "REAL"),
        ]:
            try:
                await self._db.execute(f"ALTER TABLE events ADD COLUMN {col} {defn}")
                await self._db.commit()
            except aiosqlite.OperationalError:
                pass  # column already present
        # Seed in-memory depth counter from DB (handles restart after crash)
        async with self._db.execute("SELECT COUNT(*) FROM events") as cur:
            row = await cur.fetchone()
            self._corpus_depth = row[0] if row else 0

    async def close(self) -> None:
        if self._db is not None:
            await self._db.close()
            self._db = None

    # ------------------------------------------------------------------
    # Identity
    # ------------------------------------------------------------------

    @property
    def facility_id(self) -> str:
        return self._facility_id

    @property
    def corpus_depth(self) -> int:
        return self._corpus_depth

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    def _assert_open(self) -> None:
        if self._db is None:
            raise BackendUnavailableError(
                "Backend is not open. Call await backend.open() first."
            )

    @staticmethod
    def _extract_instruments(event: CIAEREvent) -> tuple[list[str], dict[str, float]]:
        """Return (instrument_id list, {instrument_id: value} dict) from event.cause."""
        ids: list[str] = []
        vals: dict[str, float] = {}
        for sr in event.cause.sensor_readings:
            ids.append(sr.instrument_id)
            if sr.value is not None:
                vals[sr.instrument_id] = float(sr.value)
        return ids, vals

    @staticmethod
    def _row_to_event(row: aiosqlite.Row) -> CIAEREvent:
        return CIAEREvent.model_validate_json(row["event_json"])

    # ------------------------------------------------------------------
    # Write operations
    # ------------------------------------------------------------------

    async def ingest_event(self, event: CIAEREvent) -> UUID:
        self._assert_open()

        if event.facility_id != self._facility_id:
            raise SchemaValidationError(
                f"Event facility_id '{event.facility_id}' does not match "
                f"backend facility_id '{self._facility_id}'."
            )

        eid = str(event.event_id)

        async with self._db.execute(
            "SELECT 1 FROM events WHERE event_id = ?", (eid,)
        ) as cur:
            if await cur.fetchone():
                raise SchemaValidationError(
                    f"Event {event.event_id} already exists in corpus."
                )

        if event.timestamp_end and event.timestamp_start >= event.timestamp_end:
            raise SchemaValidationError(
                "timestamp_start must be before timestamp_end."
            )

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

        ids, vals = self._extract_instruments(event)

        rp = event.result.r_phys
        try:
            await self._db.execute(
                """
                INSERT INTO events (
                    event_id, facility_id, operator_id, failure_mode_tag, srk_level,
                    escalation_state, graph_weight, outcome_tag, timestamp_start,
                    instruments, instrument_values, event_json,
                    r_phys_status, r_phys_deadline, r_phys_value
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    eid,
                    event.facility_id,
                    event.operator_id,
                    event.intuition.failure_mode_tag if event.intuition else None,
                    event.intuition.srk_level.value if event.intuition else None,
                    event.cause.escalation_state,
                    event.result.graph_weight,
                    event.result.outcome_tag.value if event.result.outcome_tag else None,
                    event.timestamp_start.isoformat(),
                    json.dumps(ids),
                    json.dumps(vals),
                    event.model_dump_json(),
                    rp.status.value if rp else None,
                    rp.deadline.isoformat() if rp and rp.deadline else None,
                    rp.value if rp else None,
                ),
            )
            await self._db.commit()
        except aiosqlite.Error as exc:
            raise BackendUnavailableError(f"Write failed: {exc}") from exc

        self._corpus_depth += 1
        self._failure_mode_cache = None
        return event.event_id

    async def update_graph_weight(self, update: WeightUpdate) -> CIAEREvent:
        self._assert_open()

        if not (0.0 <= update.new_weight <= 1.0):
            raise ValueError(
                f"new_weight must be in [0.0, 1.0], got {update.new_weight}"
            )

        eid = str(update.event_id)
        async with self._db.execute(
            "SELECT event_json FROM events WHERE event_id = ?", (eid,)
        ) as cur:
            row = await cur.fetchone()
        if row is None:
            raise EventNotFoundError(update.event_id)

        event = CIAEREvent.model_validate_json(row["event_json"])
        updated = event.model_copy(
            update={"result": event.result.model_copy(
                update={"graph_weight": update.new_weight}
            )}
        )

        ts = datetime.now(timezone.utc).isoformat()
        await self._db.execute(
            "UPDATE events SET graph_weight = ?, event_json = ? WHERE event_id = ?",
            (update.new_weight, updated.model_dump_json(), eid),
        )
        await self._db.execute(
            """
            INSERT INTO weight_audit (event_id, ts, new_weight, rationale, updated_by)
            VALUES (?, ?, ?, ?, ?)
            """,
            (eid, ts, update.new_weight, update.rationale, update.updated_by),
        )
        await self._db.commit()

        self._failure_mode_cache = None
        return updated

    # ------------------------------------------------------------------
    # Read — single event
    # ------------------------------------------------------------------

    async def get_event(self, event_id: UUID) -> CIAEREvent:
        self._assert_open()
        async with self._db.execute(
            "SELECT event_json FROM events WHERE event_id = ?", (str(event_id),)
        ) as cur:
            row = await cur.fetchone()
        if row is None:
            raise EventNotFoundError(event_id)
        return CIAEREvent.model_validate_json(row["event_json"])

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

        params: list[Any] = [query.failure_mode_tag, query.min_graph_weight]
        sql = """
            SELECT event_json FROM events
            WHERE failure_mode_tag = ? AND graph_weight >= ?
        """
        if query.srk_filter is not None:
            sql += " AND srk_level = ?"
            params.append(query.srk_filter.value)
        sql += " ORDER BY graph_weight DESC LIMIT ?"
        params.append(query.top_n)

        async with self._db.execute(sql, params) as cur:
            rows = await cur.fetchall()
        return [CIAEREvent.model_validate_json(r["event_json"]) for r in rows]

    async def query_by_cause_signature(
        self, query: CauseSignatureQuery
    ) -> list[CIAEREvent]:
        self._assert_open()

        query_vals = {r.instrument_id: float(r.value) for r in query.sensor_readings}
        query_ids  = set(query_vals.keys())

        # Indexed pre-filter: escalation_state within ±1, min graph_weight
        async with self._db.execute(
            """
            SELECT event_id, graph_weight, instruments, instrument_values
            FROM events
            WHERE escalation_state BETWEEN ? AND ?
              AND graph_weight >= ?
            """,
            (
                query.escalation_state - 1,
                query.escalation_state + 1,
                query.min_graph_weight,
            ),
        ) as cur:
            candidates = await cur.fetchall()

        if not candidates:
            return []

        scored: list[tuple[float, str]] = []
        for row in candidates:
            stored_ids: list[str] = json.loads(row["instruments"])
            stored_vals: dict[str, float] = json.loads(row["instrument_values"])
            shared = query_ids & set(stored_ids)
            if not shared:
                continue

            # Value-proximity scoring (MOD-003):
            # per-instrument proximity = 1 / (1 + |query_val - stored_val|)
            # averaged over shared instruments, graph_weight as 10% tiebreaker.
            proximity_sum = 0.0
            for iid in shared:
                q_val = query_vals[iid]
                s_val = stored_vals.get(iid, q_val)  # fall back to q_val if missing
                proximity_sum += 1.0 / (1.0 + abs(q_val - s_val))
            proximity = proximity_sum / len(shared)
            score = proximity * 0.9 + row["graph_weight"] * 0.1
            scored.append((score, row["event_id"]))

        scored.sort(reverse=True)
        top_ids = [eid for _, eid in scored[: query.top_n]]

        if not top_ids:
            return []

        placeholders = ",".join("?" * len(top_ids))
        async with self._db.execute(
            f"SELECT event_id, event_json FROM events WHERE event_id IN ({placeholders})",
            top_ids,
        ) as cur:
            rows = {r["event_id"]: r["event_json"] async for r in cur}

        # Return in scored order
        return [
            CIAEREvent.model_validate_json(rows[eid])
            for eid in top_ids
            if eid in rows
        ]

    async def list_failure_modes(self) -> list[FailureModeSummary]:
        self._assert_open()

        if self._failure_mode_cache is not None:
            return self._failure_mode_cache

        async with self._db.execute(
            """
            SELECT failure_mode_tag,
                   COUNT(*)           AS event_count,
                   AVG(graph_weight)  AS mean_weight
            FROM events
            GROUP BY failure_mode_tag
            ORDER BY event_count DESC
            """
        ) as cur:
            agg_rows = await cur.fetchall()

        # outcome_distribution requires a second pass
        async with self._db.execute(
            "SELECT failure_mode_tag, outcome_tag, COUNT(*) AS cnt FROM events GROUP BY failure_mode_tag, outcome_tag"
        ) as cur:
            outcome_rows = await cur.fetchall()

        outcome_map: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
        for r in outcome_rows:
            tag     = r["failure_mode_tag"] or "UNKNOWN"
            outcome = r["outcome_tag"]      or "UNKNOWN"
            outcome_map[tag][outcome] += r["cnt"]

        summaries = [
            FailureModeSummary(
                failure_mode_tag     = r["failure_mode_tag"] or "UNKNOWN",
                event_count          = r["event_count"],
                mean_graph_weight    = r["mean_weight"] or 0.0,
                outcome_distribution = dict(outcome_map.get(r["failure_mode_tag"] or "UNKNOWN", {})),
            )
            for r in agg_rows
        ]

        self._failure_mode_cache = summaries
        return summaries

    # ------------------------------------------------------------------
    # OGC — Outcome-Grounded Confidence (CLAUDE.md §6)
    # ------------------------------------------------------------------

    async def record_r_phys(self, update: RPhysUpdate) -> CIAEREvent:
        self._assert_open()

        if not (0.0 <= update.value <= 1.0):
            raise ValueError(f"R_phys value must be in [0.0, 1.0], got {update.value}")

        eid = str(update.event_id)
        async with self._db.execute(
            "SELECT event_json FROM events WHERE event_id = ?", (eid,)
        ) as cur:
            row = await cur.fetchone()
        if row is None:
            raise EventNotFoundError(update.event_id)

        event = CIAEREvent.model_validate_json(row["event_json"])
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

        # OGC update rule — reward and compliance are separate paths (CLAUDE.md §6.3)
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

        rationale = (
            f"OGC_R_PHYS: {update.source} (value={update.value:.4f}, "
            f"delta={δ:.4f}, compliance={compliance}, alpha={α})"
        )
        ts = now.isoformat()
        await self._db.execute(
            "UPDATE events SET graph_weight = ?, r_phys_status = ?, r_phys_value = ?, "
            "event_json = ? WHERE event_id = ?",
            (new_weight, RPhysStatus.ARRIVED.value, update.value, updated.model_dump_json(), eid),
        )
        await self._db.execute(
            "INSERT INTO weight_audit (event_id, ts, new_weight, rationale, updated_by) "
            "VALUES (?, ?, ?, ?, ?)",
            (eid, ts, new_weight, rationale, update.updated_by),
        )
        await self._db.commit()

        self._failure_mode_cache = None
        return updated

    async def expire_r_phys_deadlines(self) -> list[UUID]:
        self._assert_open()
        now = datetime.now(timezone.utc).isoformat()

        async with self._db.execute(
            "SELECT event_id, event_json FROM events "
            "WHERE r_phys_status = 'PENDING' AND r_phys_deadline IS NOT NULL "
            "AND r_phys_deadline < ?",
            (now,),
        ) as cur:
            rows = await cur.fetchall()

        if not rows:
            return []

        expired: list[UUID] = []
        for row in rows:
            event = CIAEREvent.model_validate_json(row["event_json"])
            rp = event.result.r_phys
            if rp is None:
                continue
            updated = event.model_copy(update={
                "result": event.result.model_copy(update={
                    "r_phys": rp.model_copy(update={"status": RPhysStatus.INDETERMINATE})
                })
            })
            await self._db.execute(
                "UPDATE events SET r_phys_status = ?, event_json = ? WHERE event_id = ?",
                (RPhysStatus.INDETERMINATE.value, updated.model_dump_json(), row["event_id"]),
            )
            expired.append(event.event_id)

        await self._db.commit()
        return expired

    async def list_pending_r_phys(self, max_results: int = 50) -> list[CIAEREvent]:
        self._assert_open()
        async with self._db.execute(
            "SELECT event_json FROM events WHERE r_phys_status = 'PENDING' "
            "ORDER BY r_phys_deadline ASC NULLS LAST LIMIT ?",
            (max_results,),
        ) as cur:
            rows = await cur.fetchall()
        return [CIAEREvent.model_validate_json(r["event_json"]) for r in rows]

    # ------------------------------------------------------------------
    # Diagnostics
    # ------------------------------------------------------------------

    async def health_check(self) -> dict:
        status = "unavailable" if self._db is None else "ok"
        if self._db is not None:
            try:
                await self._db.execute("SELECT 1")
            except aiosqlite.Error:
                status = "degraded"
        return {
            "status"       : status,
            "backend_type" : "sqlite",
            "facility_id"  : self._facility_id,
            "corpus_depth" : self._corpus_depth,
            "storage_path" : str(self._db_path),
        }
