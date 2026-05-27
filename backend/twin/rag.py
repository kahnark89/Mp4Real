"""
arcshield.twin.rag
==================
Retrieval-Augmented Generation (RAG) layer for the Twin advisory system.

TwinRAG queries the CorpusBackend for historically similar CIAER+ events
and formats them as LLM context. It is deliberately stateless — all state
lives in the CorpusBackend.

Privacy: format_context() explicitly excludes raw operator_id values and
raw biometric readings. The LLM sees failure patterns and outcomes, not
personally identifiable operational data.
"""

from __future__ import annotations

import sys
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    pass


# ---------------------------------------------------------------------------
# Inline import guard: CorpusBackend lives in backend/api, not installed as
# a package when running twin/ tests in isolation. We import lazily so that
# tests can inject mock backends without triggering the import chain.
# ---------------------------------------------------------------------------


def _import_schema():
    """Late import to avoid hard dependency on backend/api package path."""
    try:
        from arcshield.schema import (  # type: ignore[import]
            CIAEREvent,
            CauseSignatureQuery,
            FailureModeQuery,
            SensorReading,
        )
        return CIAEREvent, CauseSignatureQuery, FailureModeQuery, SensorReading
    except ImportError:
        return None, None, None, None


class TwinRAG:
    """
    Retrieves similar CIAER+ events from the CorpusBackend for RAG context.

    The CorpusBackend interface is consumed via duck-typing so that tests can
    inject simple mock objects without importing the full arcshield package.
    """

    def __init__(self, backend: object) -> None:
        """
        Parameters
        ----------
        backend:
            Any object implementing the CorpusBackend interface (duck-typed).
            Must expose async query_by_cause_signature, query_by_failure_mode,
            and corpus_depth.
        """
        self._backend = backend

    async def retrieve_by_cause(
        self,
        sensor_readings: list,
        escalation_state: int,
        top_n: int = 5,
        min_weight: float = 0.3,
    ) -> list:
        """
        Query corpus for events matching current sensor state.

        Parameters
        ----------
        sensor_readings:
            List of SensorReading objects describing the current process state.
        escalation_state:
            Current escalation level (0–3).
        top_n:
            Maximum number of events to return.
        min_weight:
            Minimum graph_weight threshold for retrieved events.

        Returns
        -------
        List of CIAEREvent objects ranked by cause-signature similarity.
        Returns [] on empty corpus or no matches above min_weight.
        """
        _, CauseSignatureQuery, _, _ = _import_schema()
        if CauseSignatureQuery is None:
            # Fallback for test environments where arcshield package unavailable.
            # Build query dict and call backend with keyword args.
            try:
                return await self._backend.query_by_cause_signature(
                    sensor_readings=sensor_readings,
                    escalation_state=escalation_state,
                    top_n=top_n,
                    min_graph_weight=min_weight,
                )
            except Exception:
                return []

        query = CauseSignatureQuery(
            sensor_readings=sensor_readings,
            escalation_state=escalation_state,
            top_n=top_n,
            min_graph_weight=min_weight,
        )
        return await self._backend.query_by_cause_signature(query)

    async def retrieve_by_failure_mode(
        self,
        failure_mode_tag: str,
        top_n: int = 5,
        min_weight: float = 0.3,
    ) -> list:
        """
        Query corpus for events with a specific failure mode tag.

        Parameters
        ----------
        failure_mode_tag:
            Controlled-vocabulary tag from the domain ontology.
        top_n:
            Maximum number of events to return.
        min_weight:
            Minimum graph_weight threshold.

        Returns
        -------
        List of CIAEREvent objects ranked by graph_weight descending.
        Returns [] when no events match the tag.
        """
        _, _, FailureModeQuery, _ = _import_schema()
        if FailureModeQuery is None:
            try:
                return await self._backend.query_by_failure_mode(
                    failure_mode_tag=failure_mode_tag,
                    top_n=top_n,
                    min_graph_weight=min_weight,
                )
            except Exception:
                return []

        query = FailureModeQuery(
            failure_mode_tag=failure_mode_tag,
            top_n=top_n,
            min_graph_weight=min_weight,
        )
        return await self._backend.query_by_failure_mode(query)

    def format_context(self, events: list) -> str:
        """
        Format retrieved CIAER+ chains as RAG context for the LLM.

        Included fields (informative for advisory):
          - failure_mode_tag
          - srk_level
          - causal_hypothesis
          - action_type
          - action_rationale (first 200 chars)
          - outcome_tag
          - prediction_match
          - graph_weight

        Excluded fields (PII / noise for advisory):
          - operator_id (privacy)
          - raw sensor values (too noisy; LLM should reason on patterns)
          - voice_transcript (raw operator speech)
          - biometric readings
          - event_id (not meaningful to LLM)

        Returns
        -------
        Multi-line string ready to embed in an LLM prompt.
        Returns "(No historical events retrieved.)" when events list is empty.
        """
        if not events:
            return "(No historical events retrieved.)"

        lines: list[str] = []
        for i, event in enumerate(events, start=1):
            # Access fields via attribute or dict — handles both Pydantic models
            # and simple mock objects used in tests.
            def get(obj, *attrs, default=""):
                for attr in attrs:
                    try:
                        obj = getattr(obj, attr)
                    except AttributeError:
                        return default
                return obj if obj is not None else default

            failure_mode = get(event, "intuition", "failure_mode_tag")
            srk_level    = get(event, "intuition", "srk_level")
            hypothesis   = get(event, "intuition", "causal_hypothesis")
            action_type  = get(event, "action", "action_type")
            rationale    = get(event, "action", "action_rationale")
            if rationale and len(rationale) > 200:
                rationale = rationale[:200] + "..."
            outcome      = get(event, "result", "outcome_tag")
            pred_match   = get(event, "effect", "prediction_match")
            weight       = get(event, "result", "graph_weight", default=0.0)

            lines.append(f"--- Historical Event {i} (weight={weight:.2f}) ---")
            lines.append(f"Failure mode:        {failure_mode}")
            lines.append(f"SRK level:           {srk_level}")
            lines.append(f"Causal hypothesis:   {hypothesis}")
            lines.append(f"Action taken:        {action_type}")
            lines.append(f"Action rationale:    {rationale}")
            lines.append(f"Prediction match:    {pred_match}")
            lines.append(f"Outcome:             {outcome}")
            lines.append("")

        return "\n".join(lines).rstrip()
