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

FACILITY = "TEST_FACILITY_01"

CONFIG = {
    "server":  {"name": "arcshield-test", "facility_id": FACILITY, "allow_writes": False},
    "backend": {"type": "json", "json": {"corpus_dir": "/tmp/arcshield_mcp_surface_unused"}},
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
async def test_exactly_eight_tools_registered():
    tools = await _build().list_tools()
    names = {t.name for t in tools}
    assert names == EXPECTED_TOOLS, f"unexpected tool surface: {names ^ EXPECTED_TOOLS}"
    assert len(tools) == 8


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
