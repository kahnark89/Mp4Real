"""
arcshield.corpus.backends.graph_backend
=========================================
Phase 3 implementation of CorpusBackend backed by Kuzu embedded graph DB.

Kuzu is a high-performance embedded property graph database that supports
Cypher-style queries. It is appropriate for ArcShield Phase 3 because:
  - Embedded (no separate server process) — matches the single-VPS deployment model
  - Native graph traversal for get_divergent_chains()
  - Property graph model maps 1:1 to the CIAER+ DAG structure

If kuzu is not installed, all methods raise BackendUnavailableError with a
clear installation message. The backend object itself can be constructed and
the error surfaces only at open() (and subsequently on any method call).

Graph schema
------------
Node types:
    Event     (event_id:STRING, failure_mode_tag:STRING, graph_weight:DOUBLE,
               operator_id:STRING, srk_level:STRING, escalation_state:INT64,
               outcome_tag:STRING, timestamp_start:STRING, event_json:STRING)
    Operator  (operator_id:STRING)
    FailureMode (tag:STRING)

Relationship types:
    OPERATOR_DECIDED  (Operator → Event)
    TAGGED_WITH       (Event → FailureMode)
    FOLLOWED_BY       (Event → Event)  — parent_event_id
    PARALLEL_WITH     (Event → Event)  — parallel_event_ids

The full serialized CIAEREvent JSON is stored in event_json for fast
full-record retrieval without re-joining across multiple tables.

Upgrade path
------------
Replace SqliteCorpusBackend with GraphCorpusBackend in config.toml:
    [backend]
    type = "neo4j"
    ...
Zero changes to any MCP tool handler.
"""

from __future__ import annotations

import json
import math
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from uuid import UUID

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
# Kuzu import guard
# ---------------------------------------------------------------------------

def _require_kuzu() -> Any:
    """
    Import kuzu or raise BackendUnavailableError with install instructions.
    Called at the start of any method that needs the DB driver.
    """
    try:
        import kuzu  # type: ignore[import]
        return kuzu
    except ImportError as exc:
        raise BackendUnavailableError(
            "GraphCorpusBackend requires kuzu. Install with: pip install kuzu"
        ) from exc


# ---------------------------------------------------------------------------
# Cypher schema (applied at open())
# ---------------------------------------------------------------------------

_SCHEMA_STATEMENTS = [
    # Node tables
    """CREATE NODE TABLE IF NOT EXISTS Event (
        event_id         STRING,
        facility_id      STRING,
        operator_id      STRING,
        failure_mode_tag STRING,
        srk_level        STRING,
        escalation_state INT64,
        graph_weight     DOUBLE,
        outcome_tag      STRING,
        timestamp_start  STRING,
        r_phys_status    STRING,
        r_phys_deadline  STRING,
        r_phys_value     DOUBLE,
        event_json       STRING,
        PRIMARY KEY (event_id)
    )""",
    """CREATE NODE TABLE IF NOT EXISTS Operator (
        operator_id STRING,
        PRIMARY KEY (operator_id)
    )""",
    """CREATE NODE TABLE IF NOT EXISTS FailureMode (
        tag STRING,
        PRIMARY KEY (tag)
    )""",
    # Relationship tables
    """CREATE REL TABLE IF NOT EXISTS OPERATOR_DECIDED (
        FROM Operator TO Event
    )""",
    """CREATE REL TABLE IF NOT EXISTS TAGGED_WITH (
        FROM Event TO FailureMode
    )""",
    """CREATE REL TABLE IF NOT EXISTS FOLLOWED_BY (
        FROM Event TO Event
    )""",
    """CREATE REL TABLE IF NOT EXISTS PARALLEL_WITH (
        FROM Event TO Event
    )""",
]


class GraphCorpusBackend(CorpusBackend):
    """
    Kuzu-backed implementation of CorpusBackend (Phase 3).

    Implements get_divergent_chains() with native graph traversal — the
    primary reason to upgrade from SqliteCorpusBackend.

    All other methods match the SqliteCorpusBackend interface contract.

    Parameters
    ----------
    db_path:
        Path to the Kuzu database directory. Created if it does not exist.
    facility_id:
        Facility privacy boundary. All reads are filtered to this facility.
    ogc_alpha:
        OGC learning rate α (CLAUDE.md §6.1). Default 0.1.
    """

    def __init__(
        self,
        db_path: str | Path,
        facility_id: str,
        ogc_alpha: float = 0.1,
    ) -> None:
        self._db_path     = Path(db_path)
        self._facility_id = facility_id
        self._ogc_alpha   = ogc_alpha
        self._db          = None   # kuzu.Database, set at open()
        self._conn        = None   # kuzu.Connection, set at open()
        self._corpus_depth = 0
        self._failure_mode_cache: list[FailureModeSummary] | None = None

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------

    async def open(self) -> None:
        """Initialize Kuzu database and apply schema."""
        if self._conn is not None:
            return
        kuzu = _require_kuzu()
        self._db_path.mkdir(parents=True, exist_ok=True)
        self._db   = kuzu.Database(str(self._db_path))
        self._conn = kuzu.Connection(self._db)
        self._apply_schema()
        self._corpus_depth = self._count_events()

    async def close(self) -> None:
        """Release Kuzu connection and database handle."""
        if self._conn is not None:
            # Kuzu Connection/Database have no explicit close() in all versions.
            # Setting to None releases the reference; GC handles cleanup.
            self._conn = None
            self._db   = None

    def _apply_schema(self) -> None:
        """Apply node/relationship table DDL. Idempotent (IF NOT EXISTS)."""
        for stmt in _SCHEMA_STATEMENTS:
            try:
                self._conn.execute(stmt)
            except Exception:
                pass  # Table already exists — Kuzu raises on re-create

    def _count_events(self) -> int:
        """Count Event nodes scoped to this facility."""
        _require_kuzu()
        try:
            result = self._conn.execute(
                "MATCH (e:Event) WHERE e.facility_id = $fid "
                "RETURN COUNT(e) AS cnt",
                {"fid": self._facility_id},
            )
            row = result.get_next()
            return int(row[0]) if row else 0
        except Exception:
            return 0

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    def _assert_open(self) -> None:
        if self._conn is None:
            raise BackendUnavailableError(
                "Backend is not open. Call await backend.open() first."
            )

    def _execute(self, query: str, params: dict | None = None) -> Any:
        """Execute a Kuzu query. Returns the QueryResult object."""
        _require_kuzu()
        return self._conn.execute(query, params or {})

    def _row_to_event(self, event_json: str) -> CIAEREvent:
        return CIAEREvent.model_validate_json(event_json)

    @staticmethod
    def _extract_instruments(event: CIAEREvent) -> tuple[list[str], dict[str, float]]:
        ids: list[str] = []
        vals: dict[str, float] = {}
        for sr in event.cause.sensor_readings:
            ids.append(sr.instrument_id)
            if sr.value is not None:
                vals[sr.instrument_id] = float(sr.value)
        return ids, vals

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
    # Write — ingest_event
    # ------------------------------------------------------------------

    async def ingest_event(self, event: CIAEREvent) -> UUID:
        self._assert_open()

        if event.facility_id != self._facility_id:
            raise SchemaValidationError(
                f"Event facility_id '{event.facility_id}' does not match "
                f"backend facility_id '{self._facility_id}'."
            )

        eid = str(event.event_id)

        # Uniqueness check
        result = self._execute(
            "MATCH (e:Event {event_id: $eid}) RETURN e.event_id",
            {"eid": eid},
        )
        if result.has_next():
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
                f"cause ({event.cause.escalation_state}) - "
                f"result ({event.result.escalation_state_at_result}) = {expected_delta}"
            )

        if event.effect.prediction_match == PredictionMatch.INDETERMINATE:
            rp = event.result.r_phys
            if rp is None or rp.status != RPhysStatus.PENDING:
                raise SchemaValidationError(
                    "Events with prediction_match=INDETERMINATE must have "
                    "result.r_phys.status=PENDING."
                )

        rp = event.result.r_phys
        fm_tag = event.intuition.failure_mode_tag if event.intuition else ""
        op_id  = event.operator_id

        # Upsert Operator node
        self._execute(
            "MERGE (o:Operator {operator_id: $op_id})",
            {"op_id": op_id},
        )

        # Upsert FailureMode node
        if fm_tag:
            self._execute(
                "MERGE (fm:FailureMode {tag: $tag})",
                {"tag": fm_tag},
            )

        # Create Event node
        self._execute(
            """
            CREATE (e:Event {
                event_id:         $event_id,
                facility_id:      $facility_id,
                operator_id:      $operator_id,
                failure_mode_tag: $failure_mode_tag,
                srk_level:        $srk_level,
                escalation_state: $escalation_state,
                graph_weight:     $graph_weight,
                outcome_tag:      $outcome_tag,
                timestamp_start:  $timestamp_start,
                r_phys_status:    $r_phys_status,
                r_phys_deadline:  $r_phys_deadline,
                r_phys_value:     $r_phys_value,
                event_json:       $event_json
            })
            """,
            {
                "event_id":         eid,
                "facility_id":      event.facility_id,
                "operator_id":      op_id,
                "failure_mode_tag": fm_tag,
                "srk_level":        event.intuition.srk_level.value if event.intuition else "",
                "escalation_state": event.cause.escalation_state,
                "graph_weight":     float(event.result.graph_weight),
                "outcome_tag":      event.result.outcome_tag.value if event.result.outcome_tag else "",
                "timestamp_start":  event.timestamp_start.isoformat(),
                "r_phys_status":    rp.status.value if rp else "",
                "r_phys_deadline":  rp.deadline.isoformat() if rp and rp.deadline else "",
                "r_phys_value":     float(rp.value) if rp and rp.value is not None else -1.0,
                "event_json":       event.model_dump_json(),
            },
        )

        # OPERATOR_DECIDED edge
        self._execute(
            """
            MATCH (o:Operator {operator_id: $op_id}), (e:Event {event_id: $eid})
            CREATE (o)-[:OPERATOR_DECIDED]->(e)
            """,
            {"op_id": op_id, "eid": eid},
        )

        # TAGGED_WITH edge
        if fm_tag:
            self._execute(
                """
                MATCH (e:Event {event_id: $eid}), (fm:FailureMode {tag: $tag})
                CREATE (e)-[:TAGGED_WITH]->(fm)
                """,
                {"eid": eid, "tag": fm_tag},
            )

        # FOLLOWED_BY edge (parent chain)
        if event.parent_event_id:
            parent_eid = str(event.parent_event_id)
            parent_check = self._execute(
                "MATCH (p:Event {event_id: $pid}) RETURN p.event_id",
                {"pid": parent_eid},
            )
            if parent_check.has_next():
                self._execute(
                    """
                    MATCH (p:Event {event_id: $pid}), (e:Event {event_id: $eid})
                    CREATE (p)-[:FOLLOWED_BY]->(e)
                    """,
                    {"pid": parent_eid, "eid": eid},
                )

        # PARALLEL_WITH edges (multi-expert divergence)
        for parallel_id in event.pattern_match_ids:
            peer_eid = str(parallel_id)
            peer_check = self._execute(
                "MATCH (p:Event {event_id: $pid}) RETURN p.event_id",
                {"pid": peer_eid},
            )
            if peer_check.has_next():
                self._execute(
                    """
                    MATCH (e:Event {event_id: $eid}), (p:Event {event_id: $pid})
                    CREATE (e)-[:PARALLEL_WITH]->(p)
                    """,
                    {"eid": eid, "pid": peer_eid},
                )

        self._corpus_depth += 1
        self._failure_mode_cache = None
        return event.event_id

    # ------------------------------------------------------------------
    # Write — update_graph_weight
    # ------------------------------------------------------------------

    async def update_graph_weight(self, update: WeightUpdate) -> CIAEREvent:
        self._assert_open()

        if not (0.0 <= update.new_weight <= 1.0):
            raise ValueError(f"new_weight {update.new_weight} outside [0.0, 1.0].")

        eid = str(update.event_id)
        result = self._execute(
            "MATCH (e:Event {event_id: $eid, facility_id: $fid}) RETURN e.event_json",
            {"eid": eid, "fid": self._facility_id},
        )
        if not result.has_next():
            raise EventNotFoundError(update.event_id)

        row = result.get_next()
        event = self._row_to_event(row[0])
        event.result.graph_weight = update.new_weight

        self._execute(
            "MATCH (e:Event {event_id: $eid}) SET e.graph_weight = $w, e.event_json = $json",
            {
                "eid":  eid,
                "w":    float(update.new_weight),
                "json": event.model_dump_json(),
            },
        )
        self._failure_mode_cache = None
        return event

    # ------------------------------------------------------------------
    # Write — record_r_phys (OGC update)
    # ------------------------------------------------------------------

    async def record_r_phys(self, update: RPhysUpdate) -> CIAEREvent:
        self._assert_open()

        if not (0.0 <= update.value <= 1.0):
            raise ValueError(f"R_phys value {update.value} outside [0.0, 1.0].")

        eid = str(update.event_id)
        result = self._execute(
            "MATCH (e:Event {event_id: $eid, facility_id: $fid}) RETURN e.event_json",
            {"eid": eid, "fid": self._facility_id},
        )
        if not result.has_next():
            raise EventNotFoundError(update.event_id)

        row = result.get_next()
        event = self._row_to_event(row[0])

        rp = event.result.r_phys
        if rp is None or rp.status != RPhysStatus.PENDING:
            raise SchemaValidationError(
                f"Event {update.event_id} has r_phys.status="
                f"{rp.status if rp else None}; expected PENDING."
            )

        # OGC update rule (CLAUDE.md §6.1)
        # compliance scalar [a_t = â_t] — gate only, never a reward source
        if event.result.advised_action_type is None:
            compliance = 1
        else:
            compliance = 1 if event.action.action_type == event.result.advised_action_type else 0

        delta      = update.value - event.result.graph_weight
        new_weight = max(0.0, min(1.0, event.result.graph_weight + self._ogc_alpha * delta * compliance))

        event.result.r_phys = RPhysRecord(
            status=RPhysStatus.ARRIVED,
            arrived_at=datetime.now(timezone.utc),
            value=update.value,
            source=update.source,
            deadline=rp.deadline,
        )
        event.result.graph_weight = new_weight

        self._execute(
            """
            MATCH (e:Event {event_id: $eid})
            SET e.graph_weight = $w, e.r_phys_status = $status,
                e.r_phys_value = $rval, e.event_json = $json
            """,
            {
                "eid":    eid,
                "w":      float(new_weight),
                "status": RPhysStatus.ARRIVED.value,
                "rval":   float(update.value),
                "json":   event.model_dump_json(),
            },
        )
        self._failure_mode_cache = None
        return event

    # ------------------------------------------------------------------
    # Write — expire_r_phys_deadlines
    # ------------------------------------------------------------------

    async def expire_r_phys_deadlines(self) -> list[UUID]:
        self._assert_open()
        now_iso = datetime.now(timezone.utc).isoformat()

        result = self._execute(
            """
            MATCH (e:Event)
            WHERE e.facility_id = $fid
              AND e.r_phys_status = 'PENDING'
              AND e.r_phys_deadline <= $now
            RETURN e.event_id, e.event_json
            """,
            {"fid": self._facility_id, "now": now_iso},
        )

        expired: list[UUID] = []
        rows = []
        while result.has_next():
            rows.append(result.get_next())

        for row in rows:
            eid   = row[0]
            event = self._row_to_event(row[1])
            if event.result.r_phys:
                event.result.r_phys = RPhysRecord(
                    status=RPhysStatus.INDETERMINATE,
                    deadline=event.result.r_phys.deadline,
                    arrived_at=None,
                    value=None,
                    source=event.result.r_phys.source,
                )
            self._execute(
                "MATCH (e:Event {event_id: $eid}) "
                "SET e.r_phys_status = 'INDETERMINATE', e.event_json = $json",
                {"eid": eid, "json": event.model_dump_json()},
            )
            expired.append(UUID(eid))

        return expired

    # ------------------------------------------------------------------
    # Write — list_pending_r_phys
    # ------------------------------------------------------------------

    async def list_pending_r_phys(self, max_results: int = 50) -> list[CIAEREvent]:
        self._assert_open()

        result = self._execute(
            """
            MATCH (e:Event)
            WHERE e.facility_id = $fid AND e.r_phys_status = 'PENDING'
            RETURN e.event_json
            ORDER BY e.r_phys_deadline ASC
            LIMIT $lim
            """,
            {"fid": self._facility_id, "lim": max_results},
        )

        events: list[CIAEREvent] = []
        while result.has_next():
            row = result.get_next()
            events.append(self._row_to_event(row[0]))
        return events

    # ------------------------------------------------------------------
    # Read — single event
    # ------------------------------------------------------------------

    async def get_event(self, event_id: UUID) -> CIAEREvent:
        self._assert_open()

        result = self._execute(
            "MATCH (e:Event {event_id: $eid, facility_id: $fid}) RETURN e.event_json",
            {"eid": str(event_id), "fid": self._facility_id},
        )
        if not result.has_next():
            raise EventNotFoundError(event_id)
        row = result.get_next()
        return self._row_to_event(row[0])

    async def get_shadow_actions(self, event_id: UUID) -> list[dict]:
        event = await self.get_event(event_id)
        return [sa.model_dump() for sa in event.shadow_actions]

    # ------------------------------------------------------------------
    # Read — query_by_failure_mode
    # ------------------------------------------------------------------

    async def query_by_failure_mode(self, query: FailureModeQuery) -> list[CIAEREvent]:
        self._assert_open()

        cypher = (
            "MATCH (e:Event) "
            "WHERE e.facility_id = $fid "
            "  AND e.failure_mode_tag = $tag "
            "  AND e.graph_weight >= $min_w "
        )
        params: dict[str, Any] = {
            "fid":   self._facility_id,
            "tag":   query.failure_mode_tag,
            "min_w": float(query.min_graph_weight),
        }

        if query.srk_filter is not None:
            cypher += "  AND e.srk_level = $srk "
            params["srk"] = query.srk_filter.value

        cypher += "RETURN e.event_json ORDER BY e.graph_weight DESC LIMIT $lim"
        params["lim"] = query.top_n

        result = self._execute(cypher, params)
        events: list[CIAEREvent] = []
        while result.has_next():
            row = result.get_next()
            events.append(self._row_to_event(row[0]))
        return events

    # ------------------------------------------------------------------
    # Read — query_by_cause_signature
    # ------------------------------------------------------------------

    async def query_by_cause_signature(
        self, query: CauseSignatureQuery
    ) -> list[CIAEREvent]:
        """
        Phase 3 implementation: embedding-based ANN search is deferred to
        Phase 4 (ml/embedding module). Phase 3 uses value-proximity scoring
        identical to SqliteCorpusBackend, executed in Python after a graph
        pre-filter on escalation_state.
        """
        self._assert_open()

        # Pre-filter: escalation state (exact ± 1)
        esc = query.escalation_state
        result = self._execute(
            """
            MATCH (e:Event)
            WHERE e.facility_id = $fid
              AND e.graph_weight >= $min_w
              AND e.escalation_state >= $esc_lo
              AND e.escalation_state <= $esc_hi
            RETURN e.event_json
            """,
            {
                "fid":    self._facility_id,
                "min_w":  float(query.min_graph_weight),
                "esc_lo": max(0, esc - 1),
                "esc_hi": min(3, esc + 1),
            },
        )

        candidates: list[CIAEREvent] = []
        while result.has_next():
            row = result.get_next()
            candidates.append(self._row_to_event(row[0]))

        if not candidates:
            return []

        # Value-proximity scoring (same algorithm as SqliteCorpusBackend)
        query_vals: dict[str, float] = {
            sr.instrument_id: float(sr.value)
            for sr in query.sensor_readings
        }
        query_ids = set(query_vals)

        def score(event: CIAEREvent) -> float:
            stored_vals: dict[str, float] = {
                sr.instrument_id: float(sr.value)
                for sr in event.cause.sensor_readings
            }
            shared = query_ids & set(stored_vals)
            if not shared:
                return 0.0
            proximity = sum(
                1.0 / (1.0 + abs(query_vals[iid] - stored_vals[iid]))
                for iid in shared
            ) / len(shared)
            return proximity * event.result.graph_weight  # graph_weight as tiebreaker

        scored = sorted(candidates, key=score, reverse=True)
        return scored[: query.top_n]

    # ------------------------------------------------------------------
    # Read — list_failure_modes
    # ------------------------------------------------------------------

    async def list_failure_modes(self) -> list[FailureModeSummary]:
        self._assert_open()

        if self._failure_mode_cache is not None:
            return self._failure_mode_cache

        result = self._execute(
            """
            MATCH (e:Event)
            WHERE e.facility_id = $fid AND e.failure_mode_tag <> ''
            RETURN e.failure_mode_tag, e.graph_weight, e.outcome_tag
            """,
            {"fid": self._facility_id},
        )

        counts:     dict[str, int]          = defaultdict(int)
        weights:    dict[str, list[float]]  = defaultdict(list)
        outcomes:   dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))

        while result.has_next():
            row = result.get_next()
            tag, gw, ot = row[0], row[1], row[2]
            counts[tag] += 1
            weights[tag].append(float(gw))
            if ot:
                outcomes[tag][ot] += 1

        summaries = [
            FailureModeSummary(
                failure_mode_tag=tag,
                event_count=cnt,
                mean_graph_weight=sum(weights[tag]) / cnt if cnt > 0 else 0.0,
                outcome_distribution=dict(outcomes[tag]),
            )
            for tag, cnt in counts.items()
        ]
        summaries.sort(key=lambda s: s.event_count, reverse=True)
        self._failure_mode_cache = summaries
        return summaries

    # ------------------------------------------------------------------
    # Graph traversal — get_divergent_chains (Phase 3 override)
    # ------------------------------------------------------------------

    async def get_divergent_chains(
        self,
        failure_mode_tag: str,
        min_operators: int = 2,
    ) -> list[list[CIAEREvent]]:
        """
        Return groups of events where multiple operators produced different
        Intuition-to-Action paths from the same failure mode.

        Implemented via native Kuzu graph traversal: MATCH events with the
        given failure_mode_tag, group by operator, return groups where
        >= min_operators distinct operators are represented.

        Each returned inner list contains all events from a single operator
        for this failure mode, sorted by timestamp descending (most recent
        first). The caller can compare Intuition.causal_hypothesis and
        Action.action_type across the outer list to find divergence points.
        """
        self._assert_open()

        result = self._execute(
            """
            MATCH (e:Event)
            WHERE e.facility_id = $fid AND e.failure_mode_tag = $tag
            RETURN e.operator_id, e.event_json
            ORDER BY e.operator_id, e.timestamp_start DESC
            """,
            {"fid": self._facility_id, "tag": failure_mode_tag},
        )

        operator_events: dict[str, list[CIAEREvent]] = defaultdict(list)
        while result.has_next():
            row = result.get_next()
            op_id, event_json = row[0], row[1]
            operator_events[op_id].append(self._row_to_event(event_json))

        # Filter to groups with at least min_operators distinct operators
        chains = [
            events_list
            for events_list in operator_events.values()
        ]

        if len(chains) < min_operators:
            return []

        return chains

    # ------------------------------------------------------------------
    # Graph traversal — get_child_events
    # ------------------------------------------------------------------

    async def get_child_events(self, parent_event_id: UUID) -> list[CIAEREvent]:
        """
        Return all CIAER+ events with the given parent_event_id (FOLLOWED_BY).
        Uses native FOLLOWED_BY edge traversal.
        """
        self._assert_open()

        result = self._execute(
            """
            MATCH (p:Event {event_id: $pid})-[:FOLLOWED_BY]->(c:Event)
            WHERE c.facility_id = $fid
            RETURN c.event_json
            """,
            {"pid": str(parent_event_id), "fid": self._facility_id},
        )

        events: list[CIAEREvent] = []
        while result.has_next():
            row = result.get_next()
            events.append(self._row_to_event(row[0]))
        return events

    # ------------------------------------------------------------------
    # Diagnostics
    # ------------------------------------------------------------------

    async def health_check(self) -> dict:
        status = "ok" if self._conn is not None else "unavailable"
        return {
            "status":       status,
            "backend_type": "neo4j",    # config.toml key — "neo4j" maps to GraphCorpusBackend
            "facility_id":  self._facility_id,
            "corpus_depth": self._corpus_depth,
            "storage_path": str(self._db_path),
        }
