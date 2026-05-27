"""
tests/test_twin.py
==================
Unit tests for the TwinCoordinator, TwinRAG, TwinAdvisory, and TwinConfig.

All tests run without external dependencies:
  - No LLM API calls (TwinAdvisory is not called when active=False)
  - No arcshield package (schema types are mocked via dataclasses)
  - pytest + pytest-asyncio only

Run:
    cd /home/user/Mp4Real/backend
    python -m pytest twin/tests/ -v
"""

from __future__ import annotations

import asyncio
from dataclasses import dataclass, field
from typing import Any
from unittest.mock import AsyncMock, MagicMock, patch
from uuid import UUID, uuid4

import pytest

from twin.config import TwinConfig
from twin.coordinator import TwinCoordinator
from twin.advisory import TwinAdvisory, TwinGuidance, _ACTION_TYPES
from twin.rag import TwinRAG


# ---------------------------------------------------------------------------
# Minimal schema stubs (no arcshield package required)
# ---------------------------------------------------------------------------

@dataclass
class _SensorReading:
    instrument_id: str
    value: float
    unit: str
    confidence: float


@dataclass
class _Intuition:
    failure_mode_tag: str
    srk_level: str
    causal_hypothesis: str
    confidence_level: float = 0.8
    projection: str = ""


@dataclass
class _ActionStep:
    step_id: int
    description: str
    rationale: str


@dataclass
class _Action:
    action_type: str
    action_timestamp: Any
    action_rationale: str
    action_sequence: list = field(default_factory=list)


@dataclass
class _Effect:
    capture_timestamp: Any
    sensor_readings: list = field(default_factory=list)
    deltas: list = field(default_factory=list)
    prediction_match: str = "CONFIRMED"


@dataclass
class _Result:
    graph_weight: float
    outcome_tag: str = "PROBLEM_PREVENTED"
    operator_id: str = "op_hash_xxxx"


@dataclass
class _MockEvent:
    event_id: UUID
    intuition: _Intuition
    action: _Action
    effect: _Effect
    result: _Result
    operator_id: str = "op_hash_xxxx"  # anonymized hash — never PII


def make_event(
    failure_mode_tag: str = "material_segregation",
    weight: float = 0.8,
    action_type: str = "PARAMETER_ADJUST",
    hypothesis: str = "Funnel flow causing pressure spike",
    outcome: str = "PROBLEM_PREVENTED",
    prediction_match: str = "CONFIRMED",
) -> _MockEvent:
    eid = uuid4()
    return _MockEvent(
        event_id=eid,
        intuition=_Intuition(
            failure_mode_tag=failure_mode_tag,
            srk_level="RULE",
            causal_hypothesis=hypothesis,
        ),
        action=_Action(
            action_type=action_type,
            action_timestamp=None,
            action_rationale="Reduce feed rate to stabilise flow.",
        ),
        effect=_Effect(
            capture_timestamp=None,
            prediction_match=prediction_match,
        ),
        result=_Result(graph_weight=weight, outcome_tag=outcome),
    )


# ---------------------------------------------------------------------------
# Mock CorpusBackend
# ---------------------------------------------------------------------------

class _MockCorpusBackend:
    def __init__(self, events: list | None = None):
        self._events = events or []

    async def query_by_cause_signature(self, query=None, **kw) -> list:
        return list(self._events)

    async def query_by_failure_mode(self, query=None, **kw) -> list:
        return list(self._events)

    @property
    def corpus_depth(self) -> int:
        return len(self._events)


# ===========================================================================
# TwinConfig tests
# ===========================================================================

class TestTwinConfig:
    def test_default_active_is_false(self):
        """Phase 2 contract: Twin is wired but inactive by default."""
        cfg = TwinConfig()
        assert cfg.active is False

    def test_default_provider_is_claude(self):
        cfg = TwinConfig()
        assert cfg.provider == "claude"

    def test_default_max_retrieved_events(self):
        cfg = TwinConfig()
        assert cfg.max_retrieved_events == 5

    def test_default_min_retrieved_weight(self):
        cfg = TwinConfig()
        assert cfg.min_retrieved_weight == 0.3

    def test_default_temperature_low(self):
        """Low temperature for deterministic advisory."""
        cfg = TwinConfig()
        assert cfg.temperature <= 0.3

    def test_override_active(self):
        cfg = TwinConfig(active=True)
        assert cfg.active is True

    def test_system_prompt_mentions_pvc(self):
        """System prompt should reference the domain to constrain LLM."""
        cfg = TwinConfig()
        assert "pvc" in cfg.system_prompt.lower() or "extrusion" in cfg.system_prompt.lower()


# ===========================================================================
# TwinCoordinator tests
# ===========================================================================

class TestTwinCoordinator:
    def test_construction_does_not_require_api_key(self):
        """TwinCoordinator can be constructed without an API key (deferred)."""
        backend = _MockCorpusBackend()
        coord = TwinCoordinator(backend)
        assert coord is not None

    def test_default_config_is_inactive(self):
        backend = _MockCorpusBackend()
        coord = TwinCoordinator(backend)
        assert coord.config.active is False

    @pytest.mark.asyncio
    async def test_advise_returns_none_when_inactive(self):
        """Phase 2 contract: advise() returns None when active=False."""
        backend = _MockCorpusBackend([make_event()])
        coord = TwinCoordinator(backend)

        readings = [_SensorReading("crammer_amps", 130.0, "A", 0.9)]
        result = await coord.advise(sensor_readings=readings, escalation_state=2)
        assert result is None

    @pytest.mark.asyncio
    async def test_advise_returns_none_empty_backend_inactive(self):
        """Returns None when inactive regardless of corpus state."""
        backend = _MockCorpusBackend([])
        coord = TwinCoordinator(backend, config=TwinConfig(active=False))
        result = await coord.advise([], escalation_state=0)
        assert result is None

    @pytest.mark.asyncio
    async def test_advise_active_calls_rag_and_llm(self):
        """When active=True, coordinator calls RAG retrieval and LLM."""
        events = [make_event(weight=0.9)]
        backend = _MockCorpusBackend(events)
        config = TwinConfig(active=True)
        coord = TwinCoordinator(backend, config=config)

        # Patch the advisory to avoid real LLM call
        mock_guidance = TwinGuidance(
            advised_action_type="PARAMETER_ADJUST",
            confidence=0.8,
            rationale="Reduce feed rate.",
        )
        coord._advisory.generate = AsyncMock(return_value=mock_guidance)

        readings = [_SensorReading("crammer_amps", 130.0, "A", 0.9)]
        result = await coord.advise(sensor_readings=readings, escalation_state=2)

        assert result is not None
        assert result.advised_action_type == "PARAMETER_ADJUST"
        coord._advisory.generate.assert_awaited_once()

    @pytest.mark.asyncio
    async def test_advise_attaches_retrieved_event_ids(self):
        """Returned guidance includes event_ids from retrieved events."""
        e = make_event()
        backend = _MockCorpusBackend([e])
        config = TwinConfig(active=True)
        coord = TwinCoordinator(backend, config=config)

        mock_guidance = TwinGuidance(
            advised_action_type="MONITOR_HOLD",
            confidence=0.6,
            rationale="Monitor for now.",
        )
        coord._advisory.generate = AsyncMock(return_value=mock_guidance)

        result = await coord.advise(
            sensor_readings=[_SensorReading("zone1_temp_f", 385.0, "F", 0.95)],
            escalation_state=1,
        )

        assert str(e.event_id) in result.retrieved_event_ids

    def test_format_sensor_summary_empty(self):
        backend = _MockCorpusBackend()
        coord = TwinCoordinator(backend)
        summary = coord.format_sensor_summary([])
        assert "No sensor readings" in summary

    def test_format_sensor_summary_includes_instrument_ids(self):
        backend = _MockCorpusBackend()
        coord = TwinCoordinator(backend)
        readings = [
            _SensorReading("crammer_amps", 127.3, "A", 0.95),
            _SensorReading("zone1_temp_f", 385.0, "F", 0.98),
        ]
        summary = coord.format_sensor_summary(readings)
        assert "crammer_amps" in summary
        assert "zone1_temp_f" in summary


# ===========================================================================
# TwinRAG tests
# ===========================================================================

class TestTwinRAG:
    @pytest.mark.asyncio
    async def test_retrieve_by_cause_returns_backend_results(self):
        """RAG retrieval passes through backend results unchanged."""
        events = [make_event(weight=0.7), make_event(weight=0.5)]
        backend = _MockCorpusBackend(events)
        rag = TwinRAG(backend)

        readings = [_SensorReading("crammer_amps", 130.0, "A", 0.9)]
        result = await rag.retrieve_by_cause(
            sensor_readings=readings,
            escalation_state=2,
            top_n=5,
            min_weight=0.3,
        )
        assert len(result) == 2

    @pytest.mark.asyncio
    async def test_retrieve_by_failure_mode_returns_results(self):
        events = [make_event(failure_mode_tag="funnel_flow", weight=0.8)]
        backend = _MockCorpusBackend(events)
        rag = TwinRAG(backend)

        result = await rag.retrieve_by_failure_mode(
            failure_mode_tag="funnel_flow",
            top_n=5,
            min_weight=0.3,
        )
        assert len(result) == 1

    def test_format_context_empty_list_returns_no_events_message(self):
        backend = _MockCorpusBackend()
        rag = TwinRAG(backend)
        ctx = rag.format_context([])
        assert "No historical events retrieved" in ctx

    def test_format_context_includes_failure_mode_tag(self):
        event = make_event(failure_mode_tag="material_segregation")
        backend = _MockCorpusBackend()
        rag = TwinRAG(backend)
        ctx = rag.format_context([event])
        assert "material_segregation" in ctx

    def test_format_context_includes_causal_hypothesis(self):
        event = make_event(hypothesis="Funnel flow causing pressure spike")
        backend = _MockCorpusBackend()
        rag = TwinRAG(backend)
        ctx = rag.format_context([event])
        assert "Funnel flow causing pressure spike" in ctx

    def test_format_context_includes_outcome(self):
        event = make_event(outcome="PROBLEM_PREVENTED")
        backend = _MockCorpusBackend()
        rag = TwinRAG(backend)
        ctx = rag.format_context([event])
        assert "PROBLEM_PREVENTED" in ctx

    def test_format_context_excludes_operator_id_raw_value(self):
        """operator_id is anonymized and should not appear as a raw header in context."""
        event = make_event()
        # operator_id is an anonymized hash on the event — should not show up
        # as a labelled field in the RAG context
        backend = _MockCorpusBackend()
        rag = TwinRAG(backend)
        ctx = rag.format_context([event])
        assert "operator_id" not in ctx.lower()

    def test_format_context_multiple_events_numbered(self):
        events = [make_event(), make_event()]
        backend = _MockCorpusBackend()
        rag = TwinRAG(backend)
        ctx = rag.format_context(events)
        assert "Historical Event 1" in ctx
        assert "Historical Event 2" in ctx


# ===========================================================================
# TwinAdvisory tests
# ===========================================================================

class TestTwinAdvisory:
    def test_construction_without_api_key(self):
        """Advisory can be constructed without an API key (key is deferred)."""
        config = TwinConfig()
        advisory = TwinAdvisory(config, api_key=None)
        assert advisory is not None

    def test_construction_with_explicit_api_key(self):
        config = TwinConfig()
        advisory = TwinAdvisory(config, api_key="sk-test-key")
        assert advisory._api_key == "sk-test-key"

    def test_parse_response_detects_parameter_adjust(self):
        config = TwinConfig()
        advisory = TwinAdvisory(config)
        guidance = advisory._parse_response(
            "I strongly recommend a PARAMETER_ADJUST to reduce zone 3 temperature."
        )
        assert guidance.advised_action_type == "PARAMETER_ADJUST"

    def test_parse_response_high_confidence_signal(self):
        config = TwinConfig()
        advisory = TwinAdvisory(config)
        guidance = advisory._parse_response("I strongly recommend adjusting parameters.")
        assert guidance.confidence >= 0.8

    def test_parse_response_low_confidence_signal(self):
        config = TwinConfig()
        advisory = TwinAdvisory(config)
        guidance = advisory._parse_response("This is unclear and uncertain, possibly MONITOR_HOLD.")
        assert guidance.confidence < 0.6

    def test_parse_response_unknown_action_returns_none_type(self):
        config = TwinConfig()
        advisory = TwinAdvisory(config)
        guidance = advisory._parse_response("The situation requires careful observation.")
        assert guidance.advised_action_type is None

    def test_guidance_dataclass_has_required_fields(self):
        """TwinGuidance is a proper dataclass with all required fields."""
        guidance = TwinGuidance(
            advised_action_type="PARAMETER_ADJUST",
            confidence=0.8,
            rationale="Reduce feed rate.",
            retrieved_event_ids=["id-1", "id-2"],
            failure_mode_guess="material_segregation",
        )
        assert guidance.advised_action_type == "PARAMETER_ADJUST"
        assert guidance.confidence == 0.8
        assert guidance.rationale == "Reduce feed rate."
        assert guidance.retrieved_event_ids == ["id-1", "id-2"]
        assert guidance.failure_mode_guess == "material_segregation"

    def test_guidance_default_retrieved_event_ids_is_empty_list(self):
        guidance = TwinGuidance(
            advised_action_type=None,
            confidence=0.5,
            rationale="Monitor.",
        )
        assert guidance.retrieved_event_ids == []

    def test_guidance_default_failure_mode_guess_is_none(self):
        guidance = TwinGuidance(
            advised_action_type=None,
            confidence=0.5,
            rationale="Monitor.",
        )
        assert guidance.failure_mode_guess is None
