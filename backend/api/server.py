"""
server.py
=========
ArcShield MCP Server — framework-agnostic stdio transport.

Exposes the ArcShield CIAER knowledge corpus as MCP tools consumable by any
MCP-compliant client: Claude Code, Google Antigravity, Cursor, LangChain, or
any agent that speaks JSON-RPC over stdio.

Usage
-----
    python server.py                        # uses ./config.toml
    python server.py --config /path/to/config.toml

Claude Code / claude_desktop_config.json
-----------------------------------------
    {
      "mcpServers": {
        "arcshield": {
          "command": "python",
          "args": ["/path/to/arcshield-mcp/server.py"]
        }
      }
    }

Google Antigravity
------------------
    config = LocalAgentConfig(
        mcp_servers=[McpStdioServer(
            command="python",
            args=["/path/to/arcshield-mcp/server.py"]
        )],
    )

Architecture
------------
Layer 1 (this file): MCP protocol — tool registration, JSON-RPC dispatch,
                      response serialization, error surface.
Layer 2:             CorpusBackend — storage-agnostic query/write interface.
Layer 3:             JsonCorpusBackend / SqliteCorpusBackend / GraphCorpusBackend

To swap storage backends: edit config.toml [backend] section only.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import logging
import sys
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from uuid import UUID

import tomllib
from mcp.server.fastmcp import FastMCP

# ---------------------------------------------------------------------------
# Local imports — sys.path adjustment for running server.py directly
# ---------------------------------------------------------------------------
sys.path.insert(0, str(Path(__file__).parent))
# backend/ on path so `import codebook` resolves backend/codebook/__init__.py
sys.path.insert(0, str(Path(__file__).parent.parent))

from arcshield.corpus.backend import (
    EventNotFoundError,
    SchemaValidationError,
    WeightUpdate,
    RPhysUpdate,
)
from arcshield.corpus.backends import get_backend
from arcshield.schema import (
    CIAEREvent,
    CauseSignatureQuery,
    FailureModeQuery,
    SensorReading,
    SRKLevel,
    RPhysRecord,
    RPhysStatus,
)
from codebook.codebook import PrimitiveCodebook
from codebook.matcher import CosineCodebookMatcher
from codebook.schema import CodebookExpansionRequest

# ---------------------------------------------------------------------------
# Logging — stderr only; stdout is the MCP JSON-RPC channel
# ---------------------------------------------------------------------------
logging.basicConfig(
    stream=sys.stderr,
    level=logging.INFO,
    format="[arcshield] %(levelname)s %(message)s",
)
log = logging.getLogger("arcshield")


# ---------------------------------------------------------------------------
# Config loading
# ---------------------------------------------------------------------------

def load_config(config_path: str | Path = "config.toml") -> dict:
    path = Path(config_path).resolve()
    if not path.exists():
        log.warning("config.toml not found at %s — using defaults.", path)
        return {
            "server":  {"name": "arcshield-mcp", "facility_id": "DEFAULT", "allow_writes": False},
            "backend": {"type": "json", "json": {"corpus_dir": str(path.parent / "corpus")}},
        }
    with open(path, "rb") as f:
        config = tomllib.load(f)

    # Resolve relative paths to be relative to the config file, not CWD (MOD-002).
    config_dir = path.parent
    backend_type = config.get("backend", {}).get("type", "json")
    backend_section = config.get("backend", {}).get(backend_type, {})
    if "corpus_dir" in backend_section:
        raw = backend_section["corpus_dir"]
        if not Path(raw).is_absolute():
            backend_section["corpus_dir"] = str(config_dir / raw)

    # Resolve codebook path relative to config file
    codebook_section = config.setdefault("codebook", {})
    if "path" in codebook_section:
        raw_cb = codebook_section["path"]
        if not Path(raw_cb).is_absolute():
            codebook_section["path"] = str(config_dir / raw_cb)

    return config


# ---------------------------------------------------------------------------
# Response helpers
# ---------------------------------------------------------------------------

def _ok(tool_name: str, backend_facility: str, corpus_depth: int, **payload) -> str:
    """Wrap a successful tool result in the standard ArcShield envelope."""
    return json.dumps({
        "tool"          : tool_name,
        "facility_id"   : backend_facility,
        "corpus_depth"  : corpus_depth,
        **payload,
    }, indent=2, default=str)


def _error(tool_name: str, error_type: str, message: str) -> str:
    return json.dumps({
        "tool"       : tool_name,
        "error"      : error_type,
        "message"    : message,
    }, indent=2)


def _serialize_event(event: CIAEREvent) -> dict:
    """Serialize a CIAEREvent to a plain dict suitable for JSON embedding."""
    return json.loads(event.model_dump_json())


# ---------------------------------------------------------------------------
# Server factory — backend is injected via lifespan
# ---------------------------------------------------------------------------

def build_server(config: dict) -> FastMCP:
    """
    Construct and return the FastMCP server with all tools registered.
    The backend is instantiated once in the lifespan context and shared
    across all tool calls via closure — no global state.
    """

    facility_id    = config["server"]["facility_id"]
    allow_writes   = config["server"].get("allow_writes", False)
    write_operators: list[str] = config.get("auth", {}).get("write_operators", [])
    ogc_alpha: float = config.get("ogc", {}).get("alpha", 0.1)
    backend_type   = config["backend"]["type"]
    backend_kwargs = {
        **config["backend"].get(backend_type, {}),
        "facility_id": facility_id,
        "ogc_alpha"  : ogc_alpha,
    }

    # Codebook path: from config or package default
    _codebook_path_str = config.get("codebook", {}).get("path")
    _codebook_path = Path(_codebook_path_str) if _codebook_path_str else None

    # ------------------------------------------------------------------
    # Auth helper — checked before any write reaches the backend
    # ------------------------------------------------------------------

    def _check_write_auth(operator: str, tool_name: str) -> str | None:
        """
        Return an AUTH_FAILED error string if operator is not in the
        write_operators allowlist, or None if auth passes.
        Empty allowlist = no restriction (allow_writes still gates first).
        """
        if write_operators and operator not in write_operators:
            return _error(
                tool_name,
                "AUTH_FAILED",
                f"operator_id '{operator}' is not in the write_operators allowlist. "
                "Contact the facility administrator to be added.",
            )
        return None

    # ------------------------------------------------------------------
    # Lifespan: open backend once, close on shutdown
    # ------------------------------------------------------------------

    @asynccontextmanager
    async def lifespan(app: FastMCP):
        backend = get_backend(backend_type, **backend_kwargs)
        await backend.open()
        log.info(
            "Backend '%s' open. facility=%s corpus_depth=%d",
            backend_type, facility_id, backend.corpus_depth,
        )
        codebook = PrimitiveCodebook.load(_codebook_path)
        matcher = CosineCodebookMatcher(codebook.list_all())
        log.info("Codebook loaded: %d primitive(s)", len(codebook))
        state: dict = {"backend": backend, "codebook": codebook, "matcher": matcher}
        try:
            yield state
        finally:
            await backend.close()
            log.info("Backend closed.")

    mcp = FastMCP(
        name      = config["server"]["name"],
        lifespan  = lifespan,
    )

    # ------------------------------------------------------------------
    # Tool: list_failure_modes
    # ------------------------------------------------------------------

    @mcp.tool(
        description=(
            "List every failure mode tag in the ArcShield corpus with event count, "
            "mean graph_weight confidence, and outcome distribution. "
            "Use this first to discover what failure modes are available before querying. "
            "Returns an empty list on an empty corpus."
        )
    )
    async def list_failure_modes(ctx) -> str:
        backend = ctx.request_context.lifespan_context["backend"]
        try:
            summaries = await backend.list_failure_modes()
            return _ok(
                "list_failure_modes",
                backend.facility_id,
                backend.corpus_depth,
                failure_modes=[s.model_dump() for s in summaries],
                count=len(summaries),
            )
        except Exception as exc:
            log.exception("list_failure_modes failed")
            return _error("list_failure_modes", "BACKEND_ERROR", str(exc))

    # ------------------------------------------------------------------
    # Tool: query_by_failure_mode
    # ------------------------------------------------------------------

    @mcp.tool(
        description=(
            "Retrieve the top-N CIAER expert decision records matching a failure mode tag, "
            "sorted by graph_weight confidence descending. "
            "Use list_failure_modes first to discover valid tag values. "
            "Each result is a complete CIAER+ record: cause → intuition → action → "
            "shadow_actions → effect → result, grounding every recommendation in real "
            "expert reasoning chains.\n\n"
            "Args:\n"
            "  failure_mode_tag: ontology tag, e.g. 'material_segregation', 'die_drool'\n"
            "  top_n: max results (1–50, default 5)\n"
            "  min_graph_weight: confidence floor 0.0–1.0 (default 0.0)\n"
            "  srk_filter: optional SRK level filter — 'SKILL', 'RULE', or 'KNOWLEDGE'"
        )
    )
    async def query_by_failure_mode(
        ctx,
        failure_mode_tag : str,
        top_n            : int   = 5,
        min_graph_weight : float = 0.0,
        srk_filter       : str | None = None,
    ) -> str:
        backend = ctx.request_context.lifespan_context["backend"]
        try:
            srk = SRKLevel(srk_filter) if srk_filter else None
            query = FailureModeQuery(
                failure_mode_tag = failure_mode_tag,
                top_n            = top_n,
                min_graph_weight = min_graph_weight,
                srk_filter       = srk,
            )
            events = await backend.query_by_failure_mode(query)
            return _ok(
                "query_by_failure_mode",
                backend.facility_id,
                backend.corpus_depth,
                failure_mode_tag = failure_mode_tag,
                result_count     = len(events),
                events           = [_serialize_event(e) for e in events],
            )
        except ValueError as exc:
            return _error("query_by_failure_mode", "INVALID_PARAMS", str(exc))
        except Exception as exc:
            log.exception("query_by_failure_mode failed")
            return _error("query_by_failure_mode", "BACKEND_ERROR", str(exc))

    # ------------------------------------------------------------------
    # Tool: query_by_cause_signature
    # ------------------------------------------------------------------

    @mcp.tool(
        description=(
            "Retrieve the top-N CIAER events whose Cause phase most closely matches "
            "the provided sensor signature and escalation_state. "
            "This is the RAG retrieval path for the apprentice interface: given what "
            "you are currently observing on the line, find what experts did in similar situations.\n\n"
            "Similarity considers: escalation_state proximity (±1), shared instrument "
            "coverage, and graph_weight as a tiebreaker.\n\n"
            "Args:\n"
            "  sensor_readings: JSON array of {instrument_id, value, unit, confidence}\n"
            "  escalation_state: current escalation level 0–3\n"
            "  top_n: max results (1–50, default 5)\n"
            "  min_graph_weight: confidence floor 0.0–1.0 (default 0.0)\n\n"
            "IMPLEMENTATION NOTE (server.py:query_by_cause_signature): "
            "Current similarity (MOD-003): value-proximity per shared instrument "
            "= 1/(1+|q_val−s_val|), averaged, graph_weight as 10%% tiebreaker. "
            "Phase 3 replaces with embedding-based ANN search."
        )
    )
    async def query_by_cause_signature(
        ctx,
        sensor_readings  : str,   # JSON string — MCP passes complex args as strings
        escalation_state : int,
        top_n            : int   = 5,
        min_graph_weight : float = 0.0,
    ) -> str:
        backend = ctx.request_context.lifespan_context["backend"]
        try:
            raw_readings = json.loads(sensor_readings)
            readings = [SensorReading.model_validate(r) for r in raw_readings]
            query = CauseSignatureQuery(
                sensor_readings  = readings,
                escalation_state = escalation_state,
                top_n            = top_n,
                min_graph_weight = min_graph_weight,
            )
            events = await backend.query_by_cause_signature(query)
            return _ok(
                "query_by_cause_signature",
                backend.facility_id,
                backend.corpus_depth,
                escalation_state = escalation_state,
                instruments_queried = [r.instrument_id for r in readings],
                result_count     = len(events),
                events           = [_serialize_event(e) for e in events],
            )
        except (json.JSONDecodeError, ValueError) as exc:
            return _error("query_by_cause_signature", "INVALID_PARAMS", str(exc))
        except Exception as exc:
            log.exception("query_by_cause_signature failed")
            return _error("query_by_cause_signature", "BACKEND_ERROR", str(exc))

    # ------------------------------------------------------------------
    # Tool: get_event
    # ------------------------------------------------------------------

    @mcp.tool(
        description=(
            "Retrieve the complete CIAER+ record for a single event by event_id UUID. "
            "Use when you have a specific event_id from a prior query result and need "
            "the full record including all nested micro-cycles and linked pattern matches."
        )
    )
    async def get_event(ctx, event_id: str) -> str:
        backend = ctx.request_context.lifespan_context["backend"]
        try:
            uuid = UUID(event_id)
            event = await backend.get_event(uuid)
            return _ok(
                "get_event",
                backend.facility_id,
                backend.corpus_depth,
                event = _serialize_event(event),
            )
        except ValueError:
            return _error("get_event", "INVALID_PARAMS", f"'{event_id}' is not a valid UUID.")
        except EventNotFoundError as exc:
            return _error("get_event", "NOT_FOUND", str(exc))
        except Exception as exc:
            log.exception("get_event failed")
            return _error("get_event", "BACKEND_ERROR", str(exc))

    # ------------------------------------------------------------------
    # Tool: get_shadow_actions
    # ------------------------------------------------------------------

    @mcp.tool(
        description=(
            "Return only the shadow_actions array for a given event_id. "
            "Shadow actions are the alternatives the expert considered and rejected — "
            "first-class structured data, not free-text notes. Each contains: "
            "action_type, rejection_rationale, and confidence_in_rejection.\n\n"
            "This is the counterfactual surface: what the expert decided NOT to do, "
            "and why. Useful for policy enforcement (A13 gap) and for understanding "
            "the decision boundary at this failure mode and escalation_state."
        )
    )
    async def get_shadow_actions(ctx, event_id: str) -> str:
        backend = ctx.request_context.lifespan_context["backend"]
        try:
            uuid = UUID(event_id)
            shadows = await backend.get_shadow_actions(uuid)
            return _ok(
                "get_shadow_actions",
                backend.facility_id,
                backend.corpus_depth,
                event_id      = event_id,
                shadow_actions = shadows,
                count          = len(shadows),
            )
        except ValueError:
            return _error("get_shadow_actions", "INVALID_PARAMS", f"'{event_id}' is not a valid UUID.")
        except EventNotFoundError as exc:
            return _error("get_shadow_actions", "NOT_FOUND", str(exc))
        except Exception as exc:
            log.exception("get_shadow_actions failed")
            return _error("get_shadow_actions", "BACKEND_ERROR", str(exc))

    # ------------------------------------------------------------------
    # Tool: ingest_event (write — guarded by allow_writes)
    # ------------------------------------------------------------------

    @mcp.tool(
        description=(
            "Ingest a new validated CIAER+ event record into the corpus. "
            "Requires allow_writes=true in config.toml.\n\n"
            "Args:\n"
            "  event_json: complete CIAER+ event record as a JSON string.\n\n"
            "Validation enforced:\n"
            "  - facility_id must match server facility_id\n"
            "  - event_id must be globally unique\n"
            "  - timestamp_start must precede timestamp_end\n"
            "  - All required schema fields must be present\n\n"
            "Per-operator auth: when config.toml [auth] write_operators is non-empty, "
            "event.operator_id must appear in that list or AUTH_FAILED is returned.\n\n"
            "IMPLEMENTATION NOTE (server.py:ingest_event): "
            "Phase 3 upgrade: replace allowlist lookup with signed-token verification "
            "once MCP header support lands upstream."
        )
    )
    async def ingest_event(
        ctx,
        event_json: str,
        r_phys_deadline_hours: float | None = None,
    ) -> str:
        if not allow_writes:
            return _error(
                "ingest_event",
                "WRITE_DISABLED",
                "This server is configured read-only (allow_writes=false in config.toml).",
            )
        backend = ctx.request_context.lifespan_context["backend"]
        try:
            raw = json.loads(event_json)
            event = CIAEREvent.model_validate(raw)
            if auth_err := _check_write_auth(event.operator_id, "ingest_event"):
                return auth_err
            # Inject pending R_phys deadline if caller provided one and event doesn't have one
            if r_phys_deadline_hours is not None and event.result.r_phys is None:
                deadline = datetime.now(timezone.utc) + timedelta(hours=r_phys_deadline_hours)
                event = event.model_copy(update={
                    "result": event.result.model_copy(update={
                        "r_phys": RPhysRecord(
                            status   = RPhysStatus.PENDING,
                            deadline = deadline,
                        )
                    })
                })
            event_id = await backend.ingest_event(event)
            log.info("Ingested event %s (failure_mode=%s)", event_id, event.intuition.failure_mode_tag)
            return _ok(
                "ingest_event",
                backend.facility_id,
                backend.corpus_depth,
                event_id       = str(event_id),
                failure_mode   = event.intuition.failure_mode_tag,
                graph_weight   = event.result.graph_weight,
            )
        except (json.JSONDecodeError, ValueError) as exc:
            return _error("ingest_event", "INVALID_PARAMS", str(exc))
        except SchemaValidationError as exc:
            return _error("ingest_event", "SCHEMA_VALIDATION_ERROR", str(exc))
        except Exception as exc:
            log.exception("ingest_event failed")
            return _error("ingest_event", "BACKEND_ERROR", str(exc))

    # ------------------------------------------------------------------
    # Tool: update_graph_weight (write — guarded by allow_writes)
    # ------------------------------------------------------------------

    @mcp.tool(
        description=(
            "Update the graph_weight confidence field of an existing event. "
            "Requires allow_writes=true in config.toml.\n\n"
            "Every weight update is appended to an immutable audit log alongside "
            "the rationale — the log is never overwritten or deleted. This audit "
            "trail is the mechanism that prevents graph_weight from becoming "
            "reflexively self-confirming at scale (ArcShield open gap A13).\n\n"
            "Args:\n"
            "  event_id: UUID of the event to update\n"
            "  new_weight: float in [0.0, 1.0]\n"
            "  rationale: plain-text explanation of why the weight changed (required)\n"
            "  updated_by: operator_id hash or 'SYSTEM'\n\n"
            "Per-operator auth: when config.toml [auth] write_operators is non-empty, "
            "updated_by must appear in that list or AUTH_FAILED is returned.\n\n"
            "IMPLEMENTATION NOTE (server.py:update_graph_weight): "
            "Phase 3 trigger Leiden community re-detection when a high-centrality "
            "event's weight changes by > 0.2."
        )
    )
    async def update_graph_weight(
        ctx,
        event_id   : str,
        new_weight : float,
        rationale  : str,
        updated_by : str = "SYSTEM",
    ) -> str:
        if not allow_writes:
            return _error(
                "update_graph_weight",
                "WRITE_DISABLED",
                "This server is configured read-only (allow_writes=false in config.toml).",
            )
        if auth_err := _check_write_auth(updated_by, "update_graph_weight"):
            return auth_err
        backend = ctx.request_context.lifespan_context["backend"]
        try:
            update = WeightUpdate(
                event_id   = UUID(event_id),
                new_weight = new_weight,
                rationale  = rationale,
                updated_by = updated_by,
            )
            updated = await backend.update_graph_weight(update)
            log.info("Weight updated: event=%s new_weight=%.3f", event_id, new_weight)
            return _ok(
                "update_graph_weight",
                backend.facility_id,
                backend.corpus_depth,
                event_id   = event_id,
                new_weight = updated.result.graph_weight,
                rationale  = rationale,
            )
        except ValueError as exc:
            return _error("update_graph_weight", "INVALID_PARAMS", str(exc))
        except EventNotFoundError as exc:
            return _error("update_graph_weight", "NOT_FOUND", str(exc))
        except Exception as exc:
            log.exception("update_graph_weight failed")
            return _error("update_graph_weight", "BACKEND_ERROR", str(exc))

    # ------------------------------------------------------------------
    # Tool: record_r_phys (OGC write — guarded by allow_writes)
    # ------------------------------------------------------------------

    @mcp.tool(
        description=(
            "Record physical reward R_phys for an event and fire the OGC confidence "
            "update rule (CLAUDE.md §6). This is the ONLY legitimate path that mutates "
            "graph_weight based on physical outcome.\n\n"
            "OGC rule: δ = R_phys − graph_weight; "
            "new_weight = graph_weight + α·δ·[a_t = â_t]\n"
            "where [a_t = â_t] = 1 when advised_action_type is None (Phase 1/2) or "
            "operator followed Twin advice.\n\n"
            "R_phys is architecturally isolated from the compliance scalar — they are "
            "read from separate fields and never share a code path.\n\n"
            "Args:\n"
            "  event_id: UUID of the event (must have r_phys.status=PENDING)\n"
            "  value: physical reward in [0.0, 1.0] — 1.0=fully resolved, 0.0=worsened\n"
            "  source: where R_phys came from (e.g. 'manual_qc', 'plc_motor_amps')\n"
            "  updated_by: operator_id hash or 'SYSTEM'\n\n"
            "Per-operator auth: updated_by must be in write_operators allowlist if set."
        )
    )
    async def record_r_phys(
        ctx,
        event_id   : str,
        value      : float,
        source     : str,
        updated_by : str = "SYSTEM",
    ) -> str:
        if not allow_writes:
            return _error(
                "record_r_phys", "WRITE_DISABLED",
                "This server is configured read-only (allow_writes=false in config.toml).",
            )
        if auth_err := _check_write_auth(updated_by, "record_r_phys"):
            return auth_err
        backend = ctx.request_context.lifespan_context["backend"]
        try:
            upd = RPhysUpdate(
                event_id   = UUID(event_id),
                value      = value,
                source     = source,
                updated_by = updated_by,
            )
            updated = await backend.record_r_phys(upd)
            log.info("R_phys recorded: event=%s value=%.3f source=%s", event_id, value, source)
            return _ok(
                "record_r_phys",
                backend.facility_id,
                backend.corpus_depth,
                event_id         = event_id,
                r_phys_value     = value,
                source           = source,
                new_graph_weight = updated.result.graph_weight,
                r_phys_status    = updated.result.r_phys.status.value if updated.result.r_phys else None,
            )
        except ValueError as exc:
            return _error("record_r_phys", "INVALID_PARAMS", str(exc))
        except EventNotFoundError as exc:
            return _error("record_r_phys", "NOT_FOUND", str(exc))
        except SchemaValidationError as exc:
            return _error("record_r_phys", "SCHEMA_VALIDATION_ERROR", str(exc))
        except Exception as exc:
            log.exception("record_r_phys failed")
            return _error("record_r_phys", "BACKEND_ERROR", str(exc))

    # ------------------------------------------------------------------
    # Tool: expire_r_phys_deadlines (OGC admin — guarded by allow_writes)
    # ------------------------------------------------------------------

    @mcp.tool(
        description=(
            "Scan for events with r_phys.status=PENDING whose deadline has passed "
            "and mark them INDETERMINATE. These events will NOT update graph_weight — "
            "they are frozen at their current confidence and flagged for human review.\n\n"
            "Call this periodically (e.g. at shift start) to close out stale pending "
            "R_phys entries. Returns the list of expired event IDs.\n\n"
            "Requires allow_writes=true. Per-operator auth applies to updated_by."
        )
    )
    async def expire_r_phys_deadlines(
        ctx,
        updated_by: str = "SYSTEM",
    ) -> str:
        if not allow_writes:
            return _error(
                "expire_r_phys_deadlines", "WRITE_DISABLED",
                "This server is configured read-only (allow_writes=false in config.toml).",
            )
        if auth_err := _check_write_auth(updated_by, "expire_r_phys_deadlines"):
            return auth_err
        backend = ctx.request_context.lifespan_context["backend"]
        try:
            expired_ids = await backend.expire_r_phys_deadlines()
            log.info("R_phys expiry: %d events marked INDETERMINATE", len(expired_ids))
            return _ok(
                "expire_r_phys_deadlines",
                backend.facility_id,
                backend.corpus_depth,
                expired_count = len(expired_ids),
                expired_ids   = [str(eid) for eid in expired_ids],
            )
        except Exception as exc:
            log.exception("expire_r_phys_deadlines failed")
            return _error("expire_r_phys_deadlines", "BACKEND_ERROR", str(exc))

    # ------------------------------------------------------------------
    # Tool: list_pending_r_phys (read)
    # ------------------------------------------------------------------

    @mcp.tool(
        description=(
            "List events waiting for physical reward R_phys to arrive, ordered by "
            "deadline ascending (soonest first). Use to drive a monitoring dashboard "
            "or to know which events need manual QC entry next.\n\n"
            "Args:\n"
            "  max_results: upper bound on results returned (default 50)"
        )
    )
    async def list_pending_r_phys(
        ctx,
        max_results: int = 50,
    ) -> str:
        backend = ctx.request_context.lifespan_context["backend"]
        try:
            events = await backend.list_pending_r_phys(max_results=max_results)
            return _ok(
                "list_pending_r_phys",
                backend.facility_id,
                backend.corpus_depth,
                pending_count = len(events),
                events        = [_serialize_event(e) for e in events],
            )
        except Exception as exc:
            log.exception("list_pending_r_phys failed")
            return _error("list_pending_r_phys", "BACKEND_ERROR", str(exc))

    # ------------------------------------------------------------------
    # Tool: get_divergent_chains (graph traversal — Phase 3+)
    # ------------------------------------------------------------------

    @mcp.tool(
        description=(
            "Return groups of events where multiple operators produced different "
            "Intuition-to-Action paths from the same Cause signature — the "
            "multi-expert divergence surface (parallel_event_ids). The divergence "
            "point is among the highest information-density nodes in the knowledge graph.\n\n"
            "Args:\n"
            "  failure_mode_tag: ontology tag to scope the divergence search\n"
            "  min_operators: minimum distinct operators per group (default 2)\n\n"
            "AVAILABILITY: requires graph traversal (GraphCorpusBackend, Phase 3). "
            "On the Phase 1/2 JSON backend this returns NOT_AVAILABLE. The tool is "
            "declared now so clients can discover it and handle NOT_AVAILABLE gracefully."
        )
    )
    async def get_divergent_chains(
        ctx,
        failure_mode_tag : str,
        min_operators    : int = 2,
    ) -> str:
        backend = ctx.request_context.lifespan_context["backend"]
        try:
            groups = await backend.get_divergent_chains(failure_mode_tag, min_operators)
            return _ok(
                "get_divergent_chains",
                backend.facility_id,
                backend.corpus_depth,
                failure_mode_tag = failure_mode_tag,
                group_count      = len(groups),
                divergent_groups = [[_serialize_event(e) for e in group] for group in groups],
            )
        except NotImplementedError as exc:
            return _error("get_divergent_chains", "NOT_AVAILABLE", str(exc))
        except ValueError as exc:
            return _error("get_divergent_chains", "INVALID_PARAMS", str(exc))
        except Exception as exc:
            log.exception("get_divergent_chains failed")
            return _error("get_divergent_chains", "BACKEND_ERROR", str(exc))

    # ------------------------------------------------------------------
    # Tool: codebook_match — score cause signature against all primitives
    # ------------------------------------------------------------------

    @mcp.tool(
        description=(
            "Match a cause sensor signature against the Phase 1 behavioral codebook "
            "and return the best-matching primitive (FIG. 8 path 803→804).\n\n"
            "If tc_score >= theta_tc (the primitive's match threshold), the event "
            "maps to an existing primitive — is_novel=false. The delta_vector gives "
            "the fractional deviation of each sensor from the primitive's canonical.\n\n"
            "If tc_score < theta_tc, is_novel=true — the event should be stored "
            "uncompressed and queued for HITL validation (FIG. 8 path 806→807). "
            "After validation, use codebook_expand to add the new primitive.\n\n"
            "Args:\n"
            "  sensor_readings: JSON array of {instrument_id, value, unit, confidence}\n"
            "  escalation_state: current escalation level 0–3\n"
            "  acoustic_profile: optional JSON object {spectral_delta_db, dominant_freq_hz}\n"
            "  biometric_snapshot: optional JSON object {hr_bpm, hrv_rmssd_ms, accelerometer_mag}"
        )
    )
    async def codebook_match(
        ctx,
        sensor_readings  : str,
        escalation_state : int,
        acoustic_profile : str | None = None,
        biometric_snapshot: str | None = None,
    ) -> str:
        backend = ctx.request_context.lifespan_context["backend"]
        matcher: CosineCodebookMatcher = ctx.request_context.lifespan_context["matcher"]
        try:
            raw_readings  = json.loads(sensor_readings)
            raw_acoustic  = json.loads(acoustic_profile)  if acoustic_profile  else None
            raw_bio       = json.loads(biometric_snapshot) if biometric_snapshot else None
            result = matcher.match_from_cause(
                sensor_readings=raw_readings,
                acoustic_profile=raw_acoustic,
                biometric_snapshot=raw_bio,
                escalation_state=escalation_state,
            )
            return _ok(
                "codebook_match",
                backend.facility_id,
                backend.corpus_depth,
                matched_primitive_id = result.matched_primitive_id,
                failure_mode_tag     = result.failure_mode_tag,
                tc_score             = result.tc_score,
                theta_tc             = result.theta_tc,
                is_novel             = result.is_novel,
                shared_instruments   = result.shared_instruments,
                delta_vector         = result.delta_vector,
                all_scores           = result.all_scores,
            )
        except (json.JSONDecodeError, ValueError) as exc:
            return _error("codebook_match", "INVALID_PARAMS", str(exc))
        except Exception as exc:
            log.exception("codebook_match failed")
            return _error("codebook_match", "BACKEND_ERROR", str(exc))

    # ------------------------------------------------------------------
    # Tool: codebook_list_primitives — list all primitives (summary)
    # ------------------------------------------------------------------

    @mcp.tool(
        description=(
            "List all behavioral primitives in the Phase 1 codebook with summary "
            "metadata. Does not return full canonical sensor readings — use "
            "codebook_get_primitive for the full record.\n\n"
            "Returns an empty list on a fresh deployment before HITL expansion."
        )
    )
    async def codebook_list_primitives(ctx) -> str:
        backend = ctx.request_context.lifespan_context["backend"]
        codebook: PrimitiveCodebook = ctx.request_context.lifespan_context["codebook"]
        primitives = codebook.list_all()
        summaries = [
            {
                "primitive_id"         : p.primitive_id,
                "failure_mode_tag"     : p.failure_mode_tag,
                "srk_level"            : p.srk_level,
                "theta_tc"             : p.theta_tc,
                "canonical_escalation_state": p.canonical_escalation_state,
                "canonical_action_type": p.canonical_action_type,
                "source_event_count"   : len(p.source_event_ids),
                "created_at"           : str(p.created_at),
                "created_by"           : p.created_by,
            }
            for p in primitives
        ]
        return _ok(
            "codebook_list_primitives",
            backend.facility_id,
            backend.corpus_depth,
            primitive_count  = len(primitives),
            failure_modes    = codebook.list_failure_modes(),
            primitives       = summaries,
        )

    # ------------------------------------------------------------------
    # Tool: codebook_get_primitive — full primitive record by ID
    # ------------------------------------------------------------------

    @mcp.tool(
        description=(
            "Retrieve the full primitive record for a specific primitive_id, "
            "including canonical sensor readings, acoustic profile, shadow actions, "
            "and provenance metadata.\n\n"
            "Args:\n"
            "  primitive_id: e.g. 'PRIM-001'"
        )
    )
    async def codebook_get_primitive(ctx, primitive_id: str) -> str:
        backend = ctx.request_context.lifespan_context["backend"]
        codebook: PrimitiveCodebook = ctx.request_context.lifespan_context["codebook"]
        prim = codebook.get_primitive(primitive_id)
        if prim is None:
            return _error(
                "codebook_get_primitive",
                "NOT_FOUND",
                f"Primitive '{primitive_id}' not found in codebook.",
            )
        return _ok(
            "codebook_get_primitive",
            backend.facility_id,
            backend.corpus_depth,
            primitive = json.loads(prim.model_dump_json()),
        )

    # ------------------------------------------------------------------
    # Tool: codebook_expand — HITL path: add a new primitive (write-auth)
    # ------------------------------------------------------------------

    @mcp.tool(
        description=(
            "HITL expansion path (FIG. 8 step 808): add a new behavioral primitive to "
            "the codebook from a validated corpus event.\n\n"
            "Intended workflow:\n"
            "  1. codebook_match returns is_novel=true for an event.\n"
            "  2. Human reviews the event in debrief-ui and confirms the failure mode.\n"
            "  3. Call codebook_expand with the source event's ID and the confirmed tag.\n"
            "  4. The new primitive is persisted to the codebook JSON file.\n\n"
            "Requires allow_writes=true in config.toml.\n"
            "Per-operator auth: requested_by must be in write_operators if set.\n\n"
            "Args:\n"
            "  source_event_id: UUID of the validated corpus event\n"
            "  confirmed_failure_mode_tag: ontology tag confirmed by the human reviewer\n"
            "  description: free-text description of the primitive\n"
            "  requested_by: operator_id hash of the reviewer\n"
            "  theta_tc_override: optional match threshold override (default: codebook default)"
        )
    )
    async def codebook_expand(
        ctx,
        source_event_id            : str,
        confirmed_failure_mode_tag : str,
        description                : str,
        requested_by               : str,
        theta_tc_override          : float | None = None,
    ) -> str:
        if not allow_writes:
            return _error(
                "codebook_expand", "WRITE_DISABLED",
                "This server is configured read-only (allow_writes=false in config.toml).",
            )
        if auth_err := _check_write_auth(requested_by, "codebook_expand"):
            return auth_err

        backend = ctx.request_context.lifespan_context["backend"]
        codebook: PrimitiveCodebook = ctx.request_context.lifespan_context["codebook"]
        state = ctx.request_context.lifespan_context

        try:
            uuid = UUID(source_event_id)
            event = await backend.get_event(uuid)
            event_dict = json.loads(event.model_dump_json())

            req = CodebookExpansionRequest(
                source_event_id            = source_event_id,
                confirmed_failure_mode_tag = confirmed_failure_mode_tag,
                description                = description,
                theta_tc_override          = theta_tc_override,
                requested_by               = requested_by,
            )
            new_id = codebook.add_from_corpus_event(event_dict, req)
            codebook.save(_codebook_path)
            # Rebuild matcher so subsequent codebook_match calls see the new primitive
            state["matcher"] = CosineCodebookMatcher(codebook.list_all())

            log.info(
                "Codebook expanded: %s → %s (failure_mode=%s)",
                source_event_id, new_id, confirmed_failure_mode_tag,
            )
            return _ok(
                "codebook_expand",
                backend.facility_id,
                backend.corpus_depth,
                new_primitive_id           = new_id,
                confirmed_failure_mode_tag = confirmed_failure_mode_tag,
                codebook_size              = len(codebook),
            )
        except (json.JSONDecodeError, ValueError) as exc:
            return _error("codebook_expand", "INVALID_PARAMS", str(exc))
        except EventNotFoundError as exc:
            return _error("codebook_expand", "NOT_FOUND", str(exc))
        except Exception as exc:
            log.exception("codebook_expand failed")
            return _error("codebook_expand", "BACKEND_ERROR", str(exc))

    return mcp


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(description="ArcShield MCP Server")
    parser.add_argument(
        "--config",
        default="config.toml",
        help="Path to config.toml (default: ./config.toml)",
    )
    args = parser.parse_args()

    config = load_config(args.config)
    server = build_server(config)

    log.info(
        "Starting ArcShield MCP server — facility=%s backend=%s writes=%s",
        config["server"]["facility_id"],
        config["backend"]["type"],
        config["server"].get("allow_writes", False),
    )

    asyncio.run(server.run_stdio_async())


if __name__ == "__main__":
    main()
