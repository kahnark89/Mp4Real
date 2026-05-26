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
from pathlib import Path
from typing import Any
from uuid import UUID

import tomllib
from mcp.server.fastmcp import FastMCP

# ---------------------------------------------------------------------------
# Local imports — sys.path adjustment for running server.py directly
# ---------------------------------------------------------------------------
sys.path.insert(0, str(Path(__file__).parent))

from arcshield.corpus.backend import (
    EventNotFoundError,
    SchemaValidationError,
    WeightUpdate,
)
from arcshield.corpus.backends import get_backend
from arcshield.schema import (
    CIAEREvent,
    CauseSignatureQuery,
    FailureModeQuery,
    SensorReading,
    SRKLevel,
)

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

    # Resolve relative backend paths to be relative to the config file, not CWD.
    # This makes the server invocable from any working directory (MOD-002).
    config_dir = path.parent
    backend_type = config.get("backend", {}).get("type", "json")
    backend_section = config.get("backend", {}).get(backend_type, {})
    if "corpus_dir" in backend_section:
        raw = backend_section["corpus_dir"]
        if not Path(raw).is_absolute():
            backend_section["corpus_dir"] = str(config_dir / raw)

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

    facility_id  = config["server"]["facility_id"]
    allow_writes = config["server"].get("allow_writes", False)
    backend_type = config["backend"]["type"]
    backend_kwargs = {
        **config["backend"].get(backend_type, {}),
        "facility_id": facility_id,
    }

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
        try:
            yield {"backend": backend}
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
            "Phase 1 similarity uses instrument-id coverage + graph_weight. "
            "Phase 2 adds value-proximity weighting. "
            "Phase 3 replaces with embedding-based ANN search. "
            "Update this docstring when the similarity algorithm changes."
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
            "IMPLEMENTATION NOTE (server.py:ingest_event): "
            "Phase 2 add per-operator signed token auth before accepting writes. "
            "Phase 2 add escalation_delta coherence check "
            "(delta == cause.escalation_state - result.escalation_state_at_result). "
            "See config.toml [auth] section."
        )
    )
    async def ingest_event(ctx, event_json: str) -> str:
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
            "IMPLEMENTATION NOTE (server.py:update_graph_weight): "
            "Phase 2 restrict updated_by to the write_operators allowlist in config.toml. "
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
