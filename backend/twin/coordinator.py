"""
arcshield.twin.coordinator
===========================
TwinCoordinator — the main entry point for Twin advisory.

Orchestrates:
  1. RAG retrieval from CorpusBackend
  2. Context formatting
  3. LLM advisory generation via TwinAdvisory

Phase 2 contract: when config.active=False, advise() returns None immediately
without touching the LLM. The coordinator and all sub-components are still
constructed so the Phase 3 activation is a config flip, not a code change.

ANTI-REFLEXIVITY INVARIANT (CLAUDE.md §6):
    TwinCoordinator.advise() NEVER reads or accepts the compliance scalar
    [a_t = â_t]. TwinGuidance flows out to the app layer. The app layer
    tracks operator compliance separately. These two code paths must never
    share a variable.
"""

from __future__ import annotations

from .advisory import TwinAdvisory, TwinGuidance
from .config import TwinConfig
from .rag import TwinRAG


class TwinCoordinator:
    """
    Main entry point for the ArcShield Twin advisory layer.

    Phase 2: active=False → advise() always returns None.
    Phase 3+: active=True → full RAG → LLM pipeline runs.

    Thread safety: the coordinator is not thread-safe. Use one instance
    per concurrent request in async contexts, or serialize access.
    """

    def __init__(
        self,
        backend: object,
        config: TwinConfig | None = None,
        api_key: str | None = None,
    ) -> None:
        """
        Parameters
        ----------
        backend:
            Any CorpusBackend-compatible object (duck-typed). Must expose
            async query_by_cause_signature and query_by_failure_mode.
        config:
            TwinConfig instance. Defaults to TwinConfig() (active=False).
        api_key:
            LLM API key. None → read from environment variable at call time.
        """
        self.config   = config or TwinConfig()
        self._rag     = TwinRAG(backend)
        self._advisory = TwinAdvisory(self.config, api_key=api_key)

    # ------------------------------------------------------------------
    # Primary interface
    # ------------------------------------------------------------------

    async def advise(
        self,
        sensor_readings: list,
        escalation_state: int,
        failure_mode_hint: str | None = None,
    ) -> TwinGuidance | None:
        """
        Generate an advisory recommendation for the current equipment state.

        Parameters
        ----------
        sensor_readings:
            Current sensor readings (list of SensorReading objects).
        escalation_state:
            Current escalation level (0–3).
        failure_mode_hint:
            Optional failure_mode_tag hint. When provided, the retrieval
            step includes a failure-mode query in addition to the cause
            signature query. The higher-weight results from either query
            are used.

        Returns
        -------
        TwinGuidance when active=True and retrieval + LLM succeed.
        None when active=False (Phase 2 default) — caller must handle None.

        Notes
        -----
        The compliance scalar [a_t = â_t] is NEVER passed to this method.
        TwinGuidance is consumed by the app layer which tracks compliance
        separately in event.result.advised_action_type. These two paths
        must never share a variable (CLAUDE.md §6.3).
        """
        if not self.config.active:
            return None

        # Step 1: Retrieve by cause signature
        events = await self._rag.retrieve_by_cause(
            sensor_readings=sensor_readings,
            escalation_state=escalation_state,
            top_n=self.config.max_retrieved_events,
            min_weight=self.config.min_retrieved_weight,
        )

        # Step 2: Optionally supplement with failure-mode retrieval
        if failure_mode_hint:
            fm_events = await self._rag.retrieve_by_failure_mode(
                failure_mode_tag=failure_mode_hint,
                top_n=self.config.max_retrieved_events,
                min_weight=self.config.min_retrieved_weight,
            )
            # Merge, dedup by event_id, keep higher-weight events
            seen_ids: set[str] = set()
            merged: list = []
            for e in list(events) + list(fm_events):
                eid = str(getattr(e, "event_id", id(e)))
                if eid not in seen_ids:
                    seen_ids.add(eid)
                    merged.append(e)
            # Sort by graph_weight descending, take top N
            merged.sort(
                key=lambda e: getattr(getattr(e, "result", None), "graph_weight", 0.0),
                reverse=True,
            )
            events = merged[: self.config.max_retrieved_events]

        # Step 3: Format context
        context = self._rag.format_context(events)

        # Step 4: Build current sensor summary
        sensor_summary = self.format_sensor_summary(sensor_readings)

        # Step 5: Generate advisory
        guidance = await self._advisory.generate(
            context=context,
            current_sensor_summary=sensor_summary,
            failure_mode_hint=failure_mode_hint,
        )

        # Attach retrieved event IDs
        guidance.retrieved_event_ids = [
            str(getattr(e, "event_id", ""))
            for e in events
            if getattr(e, "event_id", None) is not None
        ]

        return guidance

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    def format_sensor_summary(self, sensor_readings: list) -> str:
        """
        Format current sensor state as a compact human-readable string
        suitable for LLM prompt embedding.

        Example output:
            crammer_amps=127.3 A (conf=0.95)
            zone1_temp_f=385.0 °F (conf=0.98)

        Returns "(No sensor readings)" when list is empty.
        """
        if not sensor_readings:
            return "(No sensor readings)"

        lines: list[str] = []
        for reading in sensor_readings:
            iid   = getattr(reading, "instrument_id", str(reading))
            value = getattr(reading, "value", "?")
            unit  = getattr(reading, "unit", "")
            conf  = getattr(reading, "confidence", None)
            conf_str = f" (conf={conf:.2f})" if conf is not None else ""
            lines.append(f"{iid}={value} {unit}{conf_str}".strip())

        return "\n".join(lines)
