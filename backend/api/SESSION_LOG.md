# ArcShield MCP Server — Session Log

Append new entries at the bottom. Each entry documents what was built, what was
deferred, and the exact location of every known modification point. This file is
the handoff surface between development sessions.

---

## Session 001 — 2026-05-24

**Scope:** CorpusBackend abstraction + Phase 1 JsonCorpusBackend + server.py MCP stdio layer

**Trigger:** Integration surface analysis of Google Antigravity SDK
(`google-antigravity/antigravity-sdk-python`). Decision: build CIAER corpus as a
framework-agnostic MCP stdio server rather than Antigravity-specific integration.
Antigravity, Claude Code, and any other MCP-compliant agent can consume the same server.

---

### What was built

| File | Purpose |
|------|---------|
| `arcshield/schema.py` | Pydantic v2 models for the full CIAER+ event record and all query types |
| `arcshield/corpus/backend.py` | Abstract base class `CorpusBackend` — the single interface all storage phases implement |
| `arcshield/corpus/backends/json_backend.py` | Phase 1 flat-file implementation |
| `arcshield/corpus/backends/__init__.py` | `get_backend()` factory — one-line config swap between phases |
| `server.py` | FastMCP stdio server — 7 tools, lifespan-managed backend, config.toml loading |
| `config.toml` | Server configuration: facility_id, backend type, allow_writes |
| `tests/test_corpus_backend_contract.py` | 28-test parameterized contract suite (28/28 passing) |

**Test run:** `python -m pytest tests/ -v --asyncio-mode=auto` → 28 passed in 0.22s

**Server validation:** `build_server(config)` → 7 tools registered:
`list_failure_modes`, `query_by_failure_mode`, `query_by_cause_signature`,
`get_event`, `get_shadow_actions`, `ingest_event`, `update_graph_weight`

---

### Known modification points

Each entry below is a deferred decision with its exact location, the trigger
condition for implementation, and what needs to change.

---

#### MOD-001 — Phase 2: SqliteCorpusBackend

**File:** `arcshield/corpus/backends/sqlite_backend.py` (does not exist yet)
**Factory entry:** `arcshield/corpus/backends/__init__.py` → `get_backend("sqlite", ...)`
**Config switch:** `config.toml` → `[backend] type = "sqlite"`, uncomment `[backend.sqlite]`

**Trigger:** `corpus_depth > ~500 events` or when `list_failure_modes` scan latency
becomes perceptible (> ~200ms).

**What to implement:**
- `aiosqlite` for native async I/O (remove `asyncio.to_thread` wrappers)
- Indexed columns: `failure_mode_tag`, `escalation_state`, `graph_weight`, `operator_id`
- `query_by_cause_signature`: escalation_state index pre-filter, then cosine
  similarity on the reduced candidate set in Python
- `list_failure_modes`: single `GROUP BY failure_mode_tag` query — no in-memory scan
- `corpus_depth`: `SELECT COUNT(*)` cached, invalidated on `ingest_event`
- `update_graph_weight` audit: separate `weight_audit` table (not sidecar file)
- `get_child_events`: `WHERE parent_event_id = ?` with index

**Contract guarantee:** Identical `CorpusBackend` interface. Add `"sqlite"` to the
`params` list in `tests/test_corpus_backend_contract.py::backend` fixture — all 28
tests run automatically.

---

#### MOD-002 — Phase 3: GraphCorpusBackend (Neo4j)

**File:** `arcshield/corpus/backends/graph_backend.py` (does not exist yet)
**Factory entry:** `arcshield/corpus/backends/__init__.py` → `get_backend("neo4j", ...)`
**Config switch:** `config.toml` → `[backend] type = "neo4j"`, uncomment `[backend.neo4j]`

**Trigger:** Multi-facility pattern matching required, OR corpus_depth > ~5000 events,
OR CIAER-QL probabilistic graph traversal queries are needed.

**What to implement:**
- `neo4j` async Python driver
- CIAER events as nodes; causal chain edges; divergence-point flagged nodes
- `query_by_cause_signature`: embedding-based ANN search (HNSW index in Neo4j 5+)
- `get_divergent_chains()`: override the optional method currently raising
  `NotImplementedError` in `CorpusBackend`. Native Cypher traversal.
- `get_child_events()`: native graph traversal on `parent_event_id` edges
- Cross-facility federation: signed tokens, privacy boundary enforced at Cypher
  query layer (not application layer)
- CIAER-QL: probabilistic Cypher-compatible query surface targeting
  `graph_weight`-weighted path traversal (see §6.6 of whitepaper)

**Also update:** `server.py` — add `get_divergent_chains` as an 8th MCP tool when
`GraphCorpusBackend` is available. Guard with `isinstance(backend, GraphCorpusBackend)`
check or expose via a `capabilities` endpoint.

---

#### MOD-003 — query_by_cause_signature similarity algorithm

**File:** `arcshield/corpus/backends/json_backend.py`
**Method:** `JsonCorpusBackend.query_by_cause_signature()`
**Also:** `server.py` tool docstring for `query_by_cause_signature` — contains an
explicit `IMPLEMENTATION NOTE` marker to update.

**Current behavior (Phase 1):**
Score = `instrument_coverage * 0.9 + graph_weight * 0.1`
where `instrument_coverage` = fraction of query instruments present in the stored event.
Value proximity is NOT considered — only instrument-id overlap.

**Phase 2 target (SqliteCorpusBackend):**
After escalation_state index pre-filter, load full Cause records for candidates and
compute per-instrument normalized distance: `1 / (1 + |query_val - stored_val|)`.
Score = weighted sum across shared instruments × graph_weight tiebreaker.

**Phase 3 target (GraphCorpusBackend):**
Replace entirely with embedding-based ANN search. Embed `sensor_readings` +
`escalation_state` as a feature vector; index with HNSW. This is the correct
solution once the corpus is large enough to train meaningful embeddings (target:
~200+ events per failure mode tag).

**Update checklist when changing:**
- [ ] Update the similarity description in `CorpusBackend.query_by_cause_signature`
  docstring (abstract method)
- [ ] Update the `IMPLEMENTATION NOTE` in `server.py` tool docstring
- [ ] Add a Phase 2/3 specific test in `tests/test_corpus_backend_contract.py`
  class `TestQueryByCauseSignature` that validates value-proximity ranking

---

#### MOD-004 — Write tool authorization (per-operator tokens)

**File:** `server.py` — `ingest_event` and `update_graph_weight` tools
**Also:** `config.toml` → `[auth]` section (currently commented out)
**Also:** `arcshield/corpus/backend.py` → `WeightUpdate.updated_by` field

**Current behavior (Phase 1):**
`allow_writes = true/false` is a binary server-wide flag. No per-operator auth.
Any caller can ingest or update if writes are enabled.

**Phase 2 target:**
- `config.toml [auth] write_operators = ["op_hash_abc123", ...]` allowlist
- Signed token passed as a tool argument (or MCP header when header support lands)
- `ingest_event`: verify `event.operator_id` is in the allowlist
- `update_graph_weight`: verify `updated_by` matches a write_operator
- Reject with `_error("...", "AUTH_FAILED", ...)` on mismatch

**Note:** `IMPLEMENTATION NOTE` marker is already present in both tool docstrings
in `server.py`. Grep for `IMPLEMENTATION NOTE` to find all pending items.

---

#### MOD-005 — escalation_delta coherence check on ingest

**File:** `arcshield/corpus/backends/json_backend.py` → `ingest_event()`
**Also:** `arcshield/corpus/backend.py` → `ingest_event()` docstring (already documents
this as a required validation step that is currently not enforced).

**Current behavior:** The docstring specifies that backends MUST verify
`escalation_delta == cause.escalation_state - result.escalation_state_at_result`,
but `JsonCorpusBackend.ingest_event()` does not yet enforce this check.

**Phase 2 target:** Add before the `_save_event()` call:
```python
expected_delta = (
    event.cause.escalation_state - event.result.escalation_state_at_result
)
if event.result.escalation_delta != expected_delta:
    raise SchemaValidationError(
        f"escalation_delta {event.result.escalation_delta} does not match "
        f"cause ({event.cause.escalation_state}) - result "
        f"({event.result.escalation_state_at_result}) = {expected_delta}"
    )
```
Add corresponding test in `TestIngest`.

---

#### MOD-006 — graph_weight A13 counterfactual policy (withhold-sampling)

**File:** `server.py` → `update_graph_weight` tool docstring (Phase 3 note)
**Also:** `arcshield/corpus/backends/graph_backend.py` (Phase 3, does not exist yet)

**Context:** Open gap A13 in the whitepaper — preventing `graph_weight` from becoming
reflexively self-confirming at scale. The audit log (implemented in `JsonCorpusBackend`
as `_append_audit_entry`) is the Phase 1 mechanism: every weight change is recorded
with rationale and timestamp, preventing silent drift.

**Phase 3 target:**
When `GraphCorpusBackend.update_graph_weight()` is called and the event has high
betweenness centrality in the causal chain graph (i.e., many other events' pattern
matches route through it), trigger Leiden community re-detection to verify that
the weight change does not create a self-reinforcing cluster.

**Threshold for trigger (proposed):** `|new_weight - old_weight| > 0.2` AND
the event appears in > 10% of `pattern_match_ids` lists in the corpus.

---

#### MOD-007 — get_divergent_chains MCP tool

**File:** `server.py` — add as an 8th tool after `GraphCorpusBackend` is implemented

**Current state:** `CorpusBackend.get_divergent_chains()` exists as an optional method
with a `NotImplementedError` default. `JsonCorpusBackend` does not override it.

**Phase 3 target:**
```python
@mcp.tool(description="...")
async def get_divergent_chains(ctx, failure_mode_tag: str, min_operators: int = 2) -> str:
    backend = ctx.request_context.lifespan_context["backend"]
    try:
        chains = await backend.get_divergent_chains(failure_mode_tag, min_operators)
        ...
    except NotImplementedError:
        return _error("get_divergent_chains", "NOT_AVAILABLE",
                      "Divergent chain traversal requires GraphCorpusBackend (Phase 3).")
```

**Whitepaper reference:** §3.4 — divergence-point nodes as highest information-density
nodes in the knowledge graph. This tool exposes that surface to agents.

---

#### MOD-008 — Android app → MCP ingest bridge

**Context:** The Android ArcShield app (Kotlin/Jetpack Compose) captures CIAER+ events
via the `CorpusSink` provider interface. Currently events are written to local storage on
the device.

**Target:** Add a `McpCorpusSink` implementation in the Android app that serializes
completed CIAER+ events as JSON and pipes them to `server.py` via the MCP `ingest_event`
tool. This closes the capture → corpus loop.

**Not in this repo.** Tracked here for cross-repo awareness.
Implementation location: Android app `arcshield-android/data/corpus/McpCorpusSink.kt`

---

#### MOD-009 — config.toml transport to Claude Code

**File:** Root `README.md` (does not exist yet in this repo)

When ready to register with Claude Code, add to `claude_desktop_config.json`:
```json
{
  "mcpServers": {
    "arcshield": {
      "command": "python",
      "args": ["/absolute/path/to/arcshield-mcp/server.py"],
      "env": {}
    }
  }
}
```

For Google Antigravity:
```python
config = LocalAgentConfig(
    mcp_servers=[McpStdioServer(
        command="python",
        args=["/absolute/path/to/arcshield-mcp/server.py"]
    )],
)
```

Both consume the same `server.py` with zero changes. This is the framework-agnostic
guarantee.

---

### Open questions for next session

1. **Sensor readings as JSON string in MCP:** `query_by_cause_signature` accepts
   `sensor_readings` as a JSON string because MCP tool parameters are typed scalars.
   Consider whether a structured prompt template wrapping this call would be cleaner
   for agent consumption than raw JSON string injection.

2. **corpus_dir path resolution:** Currently relative to the working directory when
   `server.py` is invoked. For Claude Code / Antigravity use, should be absolute.
   Consider resolving relative to `config.toml` location in `load_config()`.

3. **Corpus bootstrap:** Need at least one real CIAER+ event from PPVC Line 1 in
   `corpus/events/` before the server is useful to any consuming agent. The April 8
   live demonstration event is the target seed record.

---
