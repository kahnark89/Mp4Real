"""
tests/test_corpus_backend_contract.py
======================================
Parameterized contract tests for CorpusBackend implementations.

Every backend (json, sqlite, neo4j) must pass ALL tests in this file.
Add a new backend to the `backends` fixture and it gets the full suite for free.

Run:
    pytest tests/test_corpus_backend_contract.py -v
"""

from __future__ import annotations

import json
import tempfile
from datetime import datetime, timezone
from uuid import uuid4

import pytest
import pytest_asyncio

from arcshield.corpus.backend import (
    EventNotFoundError,
    SchemaValidationError,
    WeightUpdate,
)
from arcshield.corpus.backends import get_backend
from arcshield.schema import (
    ActionStep,
    ActionType,
    BiometricSnapshot,
    Cause,
    CIAEREvent,
    CauseSignatureQuery,
    Effect,
    FailureModeQuery,
    Intuition,
    Action,
    OutcomeTag,
    PreEnv,
    PredictionMatch,
    ProductQualityImpact,
    Result,
    SensorDelta,
    SensorReading,
    SRKLevel,
    ShadowAction,
    TriggerSource,
    DeltaDirection,
)

FACILITY = "TEST_FACILITY_01"
LINE     = "LINE_1"
OPERATOR = "op_hash_abc123"

NOW = datetime.now(timezone.utc)


def make_event(
    failure_mode_tag: str = "material_segregation",
    graph_weight: float = 0.85,
    escalation_state: int = 2,
    escalation_state_at_result: int | None = None,
    srk_level: SRKLevel = SRKLevel.RULE,
    outcome_tag: OutcomeTag = OutcomeTag.PROBLEM_PREVENTED,
    instruments: list[str] | None = None,
) -> CIAEREvent:
    """Construct a minimal but valid CIAER+ event for testing."""
    instruments = instruments or ["crammer_amps", "zone1_temp_f"]
    if escalation_state_at_result is None:
        escalation_state_at_result = max(0, escalation_state - 1)

    sensor_readings = [
        SensorReading(instrument_id=iid, value=50.0 + i, unit="A", confidence=0.9)
        for i, iid in enumerate(instruments)
    ]

    return CIAEREvent(
        event_id         = uuid4(),
        timestamp_start  = NOW,
        facility_id      = FACILITY,
        line_id          = LINE,
        operator_id      = OPERATOR,
        trigger_source   = TriggerSource.OPERATOR_MANUAL,
        domain_context   = {"process": "pvc_extrusion"},
        pre_env = PreEnv(
            operator_id    = OPERATOR,
            shift_phase    = "steady_state",
            crew_state_tag = "full_crew",
        ),
        cause = Cause(
            capture_timestamp = NOW,
            trigger_source    = TriggerSource.OPERATOR_MANUAL,
            sensor_readings   = sensor_readings,
            escalation_state  = escalation_state,
        ),
        intuition = Intuition(
            srk_level         = srk_level,
            causal_hypothesis = "Material bridging in crammer feed throat.",
            failure_mode_tag  = failure_mode_tag,
            confidence_level  = 0.8,
            projection        = "Crammer amps will normalize within 5 min.",
        ),
        action = Action(
            action_type      = ActionType.MECHANICAL_INSPECT,
            action_timestamp = NOW,
            action_rationale = "Rake feed throat to restore flow.",
            action_sequence  = [
                ActionStep(
                    step_id           = 1,
                    description       = "Reduce crammer speed to idle.",
                    parameter_changed = "crammer_rpm",
                    from_value        = 45,
                    to_value          = 10,
                    rationale         = "Prevent compaction of bridged material.",
                )
            ],
        ),
        shadow_actions = [
            ShadowAction(
                action_type            = ActionType.PROCESS_HALT,
                rejection_rationale    = "Halt unnecessary given escalation_state 2.",
                confidence_in_rejection= 0.9,
            )
        ],
        effect = Effect(
            capture_timestamp = NOW,
            sensor_readings   = sensor_readings,
            deltas            = [
                SensorDelta(instrument_id="crammer_amps", delta=-2.1, direction=DeltaDirection.IMPROVED)
            ],
            prediction_match  = PredictionMatch.CONFIRMED,
        ),
        result = Result(
            completed_at               = NOW,
            outcome_tag                = outcome_tag,
            escalation_state_at_result = escalation_state_at_result,
            escalation_delta           = escalation_state - escalation_state_at_result,
            hypothesis_confirmed       = True,
            product_quality_impact     = ProductQualityImpact.NO_IMPACT,
            graph_weight               = graph_weight,
        ),
    )


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def tmpdir():
    with tempfile.TemporaryDirectory() as d:
        yield d


@pytest_asyncio.fixture(params=["json", "sqlite"])
async def backend(request, tmpdir):
    """
    Parameterized fixture. Each test runs against every backend in this list.
    Add "neo4j" here when GraphCorpusBackend exists.
    """
    btype = request.param
    if btype == "json":
        kwargs = {"corpus_dir": tmpdir, "facility_id": FACILITY}
    elif btype == "sqlite":
        import os
        kwargs = {"db_path": os.path.join(tmpdir, "corpus.db"), "facility_id": FACILITY}
    else:
        kwargs = {}
    b = get_backend(btype, **kwargs)
    async with b:
        yield b


# ---------------------------------------------------------------------------
# Contract tests
# ---------------------------------------------------------------------------

class TestLifecycle:
    async def test_open_is_idempotent(self, backend):
        await backend.open()  # second open — must not raise
        assert await backend.health_check() is not None

    async def test_health_check_returns_required_keys(self, backend):
        result = await backend.health_check()
        for key in ("status", "backend_type", "facility_id", "corpus_depth"):
            assert key in result, f"health_check missing key: {key}"

    async def test_facility_id_matches_config(self, backend):
        assert backend.facility_id == FACILITY

    async def test_empty_corpus_depth_is_zero(self, backend):
        assert backend.corpus_depth == 0


class TestIngest:
    async def test_ingest_returns_event_id(self, backend):
        event = make_event()
        result = await backend.ingest_event(event)
        assert result == event.event_id

    async def test_corpus_depth_increments(self, backend):
        for _ in range(3):
            await backend.ingest_event(make_event())
        assert backend.corpus_depth == 3

    async def test_duplicate_event_id_raises(self, backend):
        event = make_event()
        await backend.ingest_event(event)
        with pytest.raises(SchemaValidationError):
            await backend.ingest_event(event)

    async def test_wrong_facility_raises(self, backend):
        event = make_event()
        bad = event.model_copy(update={"facility_id": "WRONG_FACILITY"})
        with pytest.raises(SchemaValidationError):
            await backend.ingest_event(bad)

    async def test_mismatched_escalation_delta_raises(self, backend):
        event = make_event()  # escalation_state=2, escalation_state_at_result=1, escalation_delta=1
        bad = event.model_copy(
            update={"result": event.result.model_copy(update={"escalation_delta": 0})}
        )
        with pytest.raises(SchemaValidationError, match="escalation_delta"):
            await backend.ingest_event(bad)


class TestGetEvent:
    async def test_get_event_roundtrip(self, backend):
        event = make_event()
        await backend.ingest_event(event)
        retrieved = await backend.get_event(event.event_id)
        assert retrieved.event_id == event.event_id
        assert retrieved.intuition.failure_mode_tag == event.intuition.failure_mode_tag

    async def test_get_missing_event_raises(self, backend):
        with pytest.raises(EventNotFoundError):
            await backend.get_event(uuid4())


class TestShadowActions:
    async def test_get_shadow_actions_returns_list(self, backend):
        event = make_event()
        await backend.ingest_event(event)
        shadows = await backend.get_shadow_actions(event.event_id)
        assert len(shadows) == 1
        assert shadows[0]["action_type"] == ActionType.PROCESS_HALT.value

    async def test_get_shadow_actions_missing_event_raises(self, backend):
        with pytest.raises(EventNotFoundError):
            await backend.get_shadow_actions(uuid4())


class TestGraphWeight:
    async def test_update_graph_weight(self, backend):
        event = make_event(graph_weight=0.7)
        await backend.ingest_event(event)

        update = WeightUpdate(
            event_id   = event.event_id,
            new_weight = 0.95,
            rationale  = "Confirmed by second operator at same escalation_state.",
            updated_by = OPERATOR,
        )
        updated = await backend.update_graph_weight(update)
        assert updated.result.graph_weight == pytest.approx(0.95)

    async def test_weight_persists_after_update(self, backend):
        event = make_event(graph_weight=0.5)
        await backend.ingest_event(event)

        await backend.update_graph_weight(WeightUpdate(
            event_id=event.event_id, new_weight=0.99,
            rationale="test", updated_by="SYSTEM"
        ))

        retrieved = await backend.get_event(event.event_id)
        assert retrieved.result.graph_weight == pytest.approx(0.99)

    async def test_update_missing_event_raises(self, backend):
        with pytest.raises(EventNotFoundError):
            await backend.update_graph_weight(WeightUpdate(
                event_id=uuid4(), new_weight=0.5,
                rationale="test", updated_by="SYSTEM"
            ))

    async def test_invalid_weight_raises(self, backend):
        event = make_event()
        await backend.ingest_event(event)
        with pytest.raises(ValueError):
            await backend.update_graph_weight(WeightUpdate(
                event_id=event.event_id, new_weight=1.5,
                rationale="out of range", updated_by="SYSTEM"
            ))


class TestQueryByFailureMode:
    async def test_returns_matching_events(self, backend):
        for _ in range(3):
            await backend.ingest_event(make_event(failure_mode_tag="material_segregation"))
        for _ in range(2):
            await backend.ingest_event(make_event(failure_mode_tag="die_drool"))

        results = await backend.query_by_failure_mode(
            FailureModeQuery(failure_mode_tag="material_segregation", top_n=10)
        )
        assert len(results) == 3
        assert all(e.intuition.failure_mode_tag == "material_segregation" for e in results)

    async def test_sorted_by_graph_weight_descending(self, backend):
        weights = [0.5, 0.9, 0.7]
        for w in weights:
            await backend.ingest_event(make_event(graph_weight=w))

        results = await backend.query_by_failure_mode(
            FailureModeQuery(failure_mode_tag="material_segregation", top_n=10)
        )
        returned_weights = [e.result.graph_weight for e in results]
        assert returned_weights == sorted(returned_weights, reverse=True)

    async def test_top_n_respected(self, backend):
        for _ in range(5):
            await backend.ingest_event(make_event())

        results = await backend.query_by_failure_mode(
            FailureModeQuery(failure_mode_tag="material_segregation", top_n=2)
        )
        assert len(results) <= 2

    async def test_min_graph_weight_filter(self, backend):
        await backend.ingest_event(make_event(graph_weight=0.3))
        await backend.ingest_event(make_event(graph_weight=0.9))

        results = await backend.query_by_failure_mode(
            FailureModeQuery(
                failure_mode_tag="material_segregation",
                top_n=10,
                min_graph_weight=0.8,
            )
        )
        assert all(e.result.graph_weight >= 0.8 for e in results)

    async def test_empty_corpus_returns_empty_list(self, backend):
        results = await backend.query_by_failure_mode(
            FailureModeQuery(failure_mode_tag="nonexistent_tag", top_n=5)
        )
        assert results == []


class TestQueryByCauseSignature:
    async def test_returns_events_with_shared_instruments(self, backend):
        await backend.ingest_event(
            make_event(instruments=["crammer_amps", "zone1_temp_f"])
        )
        await backend.ingest_event(
            make_event(instruments=["die_pressure_psi"])
        )

        results = await backend.query_by_cause_signature(
            CauseSignatureQuery(
                sensor_readings=[
                    SensorReading(instrument_id="crammer_amps", value=52.0, unit="A", confidence=0.9)
                ],
                escalation_state=2,
                top_n=5,
            )
        )
        assert len(results) >= 1
        instrument_ids = {
            r.instrument_id
            for e in results
            for r in e.cause.sensor_readings
        }
        assert "crammer_amps" in instrument_ids

    async def test_escalation_state_filter_excludes_distant(self, backend):
        """Events at escalation_state 0 should not match a query for state 3."""
        await backend.ingest_event(make_event(escalation_state=0))

        results = await backend.query_by_cause_signature(
            CauseSignatureQuery(
                sensor_readings=[
                    SensorReading(instrument_id="crammer_amps", value=52.0, unit="A", confidence=0.9)
                ],
                escalation_state=3,
                top_n=5,
            )
        )
        # escalation_state distance > 1: must not appear
        matching_eids = {str(e.event_id) for e in results}
        assert len(matching_eids) == 0

    async def test_no_matching_instruments_returns_empty(self, backend):
        await backend.ingest_event(make_event(instruments=["zone1_temp_f"]))

        results = await backend.query_by_cause_signature(
            CauseSignatureQuery(
                sensor_readings=[
                    SensorReading(instrument_id="completely_different_sensor", value=1.0, unit="psi", confidence=0.9)
                ],
                escalation_state=2,
                top_n=5,
            )
        )
        assert results == []


class TestListFailureModes:
    async def test_returns_all_tags(self, backend):
        await backend.ingest_event(make_event(failure_mode_tag="material_segregation"))
        await backend.ingest_event(make_event(failure_mode_tag="die_drool"))
        await backend.ingest_event(make_event(failure_mode_tag="material_segregation"))

        summaries = await backend.list_failure_modes()
        tags = {s.failure_mode_tag for s in summaries}
        assert "material_segregation" in tags
        assert "die_drool" in tags

    async def test_event_count_is_accurate(self, backend):
        for _ in range(4):
            await backend.ingest_event(make_event(failure_mode_tag="thermal_drift"))

        summaries = await backend.list_failure_modes()
        drift = next(s for s in summaries if s.failure_mode_tag == "thermal_drift")
        assert drift.event_count == 4

    async def test_sorted_by_event_count_descending(self, backend):
        for _ in range(3):
            await backend.ingest_event(make_event(failure_mode_tag="material_segregation"))
        for _ in range(1):
            await backend.ingest_event(make_event(failure_mode_tag="die_drool"))

        summaries = await backend.list_failure_modes()
        counts = [s.event_count for s in summaries]
        assert counts == sorted(counts, reverse=True)

    async def test_empty_corpus_returns_empty_list(self, backend):
        assert await backend.list_failure_modes() == []
