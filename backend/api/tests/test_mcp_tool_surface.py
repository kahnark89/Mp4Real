"""
tests/test_mcp_tool_surface.py
==============================
Coverage for the MCP tool layer in server.py — the FastMCP `@mcp.tool`
wrappers, which the backend contract suite does NOT exercise (those test
`CorpusBackend` directly and never import server.py).

What this locks down:
  - The full tool surface: exactly the 8 expected tools are registered.
  - get_divergent_chains is declared with the right input schema
    (failure_mode_tag required, min_operators optional).
  - get_divergent_chains returns the NOT_AVAILABLE error envelope on the
    Phase 1/2 JSON backend (which raises NotImplementedError), rather than
    crashing — the contract that lets clients discover it and degrade
    gracefully until GraphCorpusBackend (Phase 3) lands.

Requires `mcp` and `pydantic` installed (server.py imports FastMCP).

Run:
    pytest tests/test_mcp_tool_surface.py -v
"""

from __future__ import annotations

import json
from types import SimpleNamespace

import pytest

from arcshield.corpus.backends import get_backend
from server import build_server
from tests.test_corpus_backend_contract import make_event

FACILITY    = "TEST_FACILITY_01"
ALLOWED_OP  = "op_hash_abc123"
DENIED_OP   = "op_hash_unknown"

CONFIG = {
    "server":  {"name": "arcshield-test", "facility_id": FACILITY, "allow_writes": False},
    "backend": {"type": "json", "json": {"corpus_dir": "/tmp/arcshield_mcp_surface_unused"}},
}

CONFIG_WRITES_NO_ALLOWLIST = {
    "server":  {"name": "arcshield-test", "facility_id": FACILITY, "allow_writes": True},
    "backend": {"type": "json", "json": {"corpus_dir": "/tmp/arcshield_mcp_surface_unused"}},
    "auth":    {"write_operators": []},
}

CONFIG_WRITES_WITH_ALLOWLIST = {
    "server":  {"name": "arcshield-test", "facility_id": FACILITY, "allow_writes": True},
    "backend": {"type": "json", "json": {"corpus_dir": "/tmp/arcshield_mcp_surface_unused"}},
    "auth":    {"write_operators": [ALLOWED_OP]},
}

EXPECTED_TOOLS = {
    "list_failure_modes",
    "query_by_failure_mode",
    "query_by_cause_signature",
    "get_event",
    "get_shadow_actions",
    "ingest_event",
    "update_graph_weight",
    "get_divergent_chains",
    # OGC tools (MCP-MOD-005)
    "record_r_phys",
    "expire_r_phys_deadlines",
    "list_pending_r_phys",
    # Phase 1 codebook tools
    "match_primitive",
    "list_primitives",
}


def _build():
    # build_server registers tools via decorators; it does not open the
    # backend (that happens in the lifespan), so no I/O occurs here.
    return build_server(CONFIG)


def _tool_callable(mcp, name):
    """
    Best-effort retrieval of the raw tool function from FastMCP's tool
    manager. Internal layout differs across mcp SDK versions, so callers
    skip when it can't be resolved rather than failing spuriously.
    """
    tm = getattr(mcp, "_tool_manager", None)
    if tm is None:
        return None
    tools = getattr(tm, "_tools", None)
    tool = None
    if isinstance(tools, dict):
        tool = tools.get(name)
    if tool is None:
        getter = getattr(tm, "get_tool", None)
        if getter is not None:
            try:
                tool = getter(name)
            except Exception:
                tool = None
    if tool is None:
        return None
    return getattr(tool, "fn", None) or getattr(tool, "func", None)


@pytest.mark.asyncio
async def test_exactly_thirteen_tools_registered():
    tools = await _build().list_tools()
    names = {t.name for t in tools}
    assert names == EXPECTED_TOOLS, f"unexpected tool surface: {names ^ EXPECTED_TOOLS}"
    assert len(tools) == 13


@pytest.mark.asyncio
async def test_get_divergent_chains_input_schema():
    tools = await _build().list_tools()
    tool = next(t for t in tools if t.name == "get_divergent_chains")
    props = tool.inputSchema.get("properties", {})
    required = tool.inputSchema.get("required", [])
    assert "failure_mode_tag" in props
    assert "min_operators" in props
    assert "failure_mode_tag" in required
    assert "min_operators" not in required  # has a default


@pytest.mark.asyncio
async def test_json_backend_get_divergent_chains_raises_not_implemented(tmp_path):
    # The NOT_AVAILABLE wrapper depends on this backend contract.
    backend = get_backend("json", corpus_dir=str(tmp_path), facility_id=FACILITY)
    async with backend:
        with pytest.raises(NotImplementedError):
            await backend.get_divergent_chains("material_segregation", 2)


@pytest.mark.asyncio
async def test_get_divergent_chains_tool_returns_not_available(tmp_path):
    mcp = _build()
    fn = _tool_callable(mcp, "get_divergent_chains")
    if fn is None:
        pytest.skip("FastMCP tool callable not resolvable on this mcp SDK version")
    backend = get_backend("json", corpus_dir=str(tmp_path), facility_id=FACILITY)
    async with backend:
        ctx = SimpleNamespace(
            request_context=SimpleNamespace(lifespan_context={"backend": backend})
        )
        raw = await fn(ctx, failure_mode_tag="material_segregation")
        payload = json.loads(raw)
        assert payload["error"] == "NOT_AVAILABLE"
        assert payload["tool"] == "get_divergent_chains"


# ---------------------------------------------------------------------------
# MCP-MOD-004: per-operator write auth
# ---------------------------------------------------------------------------

class TestWriteAuth:
    """
    Three cases for each write tool:
    (a) empty allowlist → any operator passes
    (b) operator in allowlist → passes
    (c) operator NOT in allowlist → AUTH_FAILED

    update_graph_weight auth is checked before any backend call, so no event
    needs to exist in the backend for the denied case.

    ingest_event auth is checked after event parsing (operator_id lives inside
    the CIAER+ JSON), so a valid event is required even for the denied case.
    """

    # ------------------------------------------------------------------ helpers

    def _get_fn(self, config, tool):
        mcp = build_server(config)
        fn = _tool_callable(mcp, tool)
        if fn is None:
            pytest.skip("FastMCP tool callable not resolvable on this mcp SDK version")
        return fn

    # ------------------------------------------------------------------ update_graph_weight

    async def test_update_graph_weight_no_allowlist_passes(self, tmp_path):
        fn = self._get_fn(CONFIG_WRITES_NO_ALLOWLIST, "update_graph_weight")
        # Auth should pass even for an operator not in any list (empty = no restriction).
        # The backend will raise EventNotFoundError (no event in corpus), but NOT AUTH_FAILED.
        backend = get_backend("json", corpus_dir=str(tmp_path), facility_id=FACILITY)
        async with backend:
            ctx = SimpleNamespace(
                request_context=SimpleNamespace(lifespan_context={"backend": backend})
            )
            raw = await fn(ctx, event_id="00000000-0000-0000-0000-000000000000",
                           new_weight=0.5, rationale="test", updated_by=DENIED_OP)
            payload = json.loads(raw)
            # Empty allowlist: must NOT be AUTH_FAILED — any other error is fine
            assert payload.get("error") != "AUTH_FAILED"

    async def test_update_graph_weight_allowed_op_passes(self, tmp_path):
        fn = self._get_fn(CONFIG_WRITES_WITH_ALLOWLIST, "update_graph_weight")
        backend = get_backend("json", corpus_dir=str(tmp_path), facility_id=FACILITY)
        async with backend:
            ctx = SimpleNamespace(
                request_context=SimpleNamespace(lifespan_context={"backend": backend})
            )
            raw = await fn(ctx, event_id="00000000-0000-0000-0000-000000000000",
                           new_weight=0.5, rationale="test", updated_by=ALLOWED_OP)
            payload = json.loads(raw)
            assert payload.get("error") != "AUTH_FAILED"

    async def test_update_graph_weight_denied_op_returns_auth_failed(self, tmp_path):
        fn = self._get_fn(CONFIG_WRITES_WITH_ALLOWLIST, "update_graph_weight")
        backend = get_backend("json", corpus_dir=str(tmp_path), facility_id=FACILITY)
        async with backend:
            ctx = SimpleNamespace(
                request_context=SimpleNamespace(lifespan_context={"backend": backend})
            )
            raw = await fn(ctx, event_id="00000000-0000-0000-0000-000000000000",
                           new_weight=0.5, rationale="test", updated_by=DENIED_OP)
            payload = json.loads(raw)
            assert payload["error"] == "AUTH_FAILED"
            assert payload["tool"] == "update_graph_weight"
            assert DENIED_OP in payload["message"]

    # ------------------------------------------------------------------ ingest_event

    async def test_ingest_event_no_allowlist_passes(self, tmp_path):
        fn = self._get_fn(CONFIG_WRITES_NO_ALLOWLIST, "ingest_event")
        event = make_event()
        # Use an operator that is NOT in any allowlist to confirm empty list = no restriction
        event2 = event.model_copy(update={"operator_id": DENIED_OP})
        backend = get_backend("json", corpus_dir=str(tmp_path), facility_id=FACILITY)
        async with backend:
            ctx = SimpleNamespace(
                request_context=SimpleNamespace(lifespan_context={"backend": backend})
            )
            raw = await fn(ctx, event_json=event2.model_dump_json())
            payload = json.loads(raw)
            assert payload.get("error") != "AUTH_FAILED"

    async def test_ingest_event_allowed_op_passes(self, tmp_path):
        fn = self._get_fn(CONFIG_WRITES_WITH_ALLOWLIST, "ingest_event")
        event = make_event()  # operator_id = "op_hash_abc123" = ALLOWED_OP
        backend = get_backend("json", corpus_dir=str(tmp_path), facility_id=FACILITY)
        async with backend:
            ctx = SimpleNamespace(
                request_context=SimpleNamespace(lifespan_context={"backend": backend})
            )
            raw = await fn(ctx, event_json=event.model_dump_json())
            payload = json.loads(raw)
            assert payload.get("error") != "AUTH_FAILED"
            assert "event_id" in payload  # successful ingest returns event_id

    async def test_ingest_event_denied_op_returns_auth_failed(self, tmp_path):
        fn = self._get_fn(CONFIG_WRITES_WITH_ALLOWLIST, "ingest_event")
        event = make_event()
        denied_event = event.model_copy(update={"operator_id": DENIED_OP})
        backend = get_backend("json", corpus_dir=str(tmp_path), facility_id=FACILITY)
        async with backend:
            ctx = SimpleNamespace(
                request_context=SimpleNamespace(lifespan_context={"backend": backend})
            )
            raw = await fn(ctx, event_json=denied_event.model_dump_json())
            payload = json.loads(raw)
            assert payload["error"] == "AUTH_FAILED"
            assert payload["tool"] == "ingest_event"
            assert DENIED_OP in payload["message"]
