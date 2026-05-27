"""
arcshield.twin.advisory
=======================
LLM-based advisory generation for the Twin layer.

TwinAdvisory calls an LLM (Anthropic Claude by default) with a structured
prompt assembled from RAG context and current sensor state. It parses the
response to extract a structured TwinGuidance recommendation.

Phase 2: TwinCoordinator short-circuits before reaching TwinAdvisory
(config.active=False). This class is instantiated but generate() is not called.

ANTI-REFLEXIVITY: TwinAdvisory has no access to the compliance scalar
[a_t = â_t]. It generates guidance solely from physical sensor state and
historical CIAER+ patterns. The compliance gate lives in the OGC layer.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field


# ---------------------------------------------------------------------------
# TwinGuidance — the output of the advisory pipeline
# ---------------------------------------------------------------------------

@dataclass
class TwinGuidance:
    """
    Structured advisory recommendation from the Twin.

    Fields
    ------
    advised_action_type:
        ActionType enum value string (e.g. "PARAMETER_ADJUST"), or None
        when the LLM cannot determine an appropriate action with confidence.

    confidence:
        Advisory confidence in [0.0, 1.0]. Derived from LLM certainty
        language ("definitely" → ~0.9, "likely" → ~0.7, "possibly" → ~0.5,
        "unclear" → ~0.3). Not calibrated to physical outcomes — that is
        the OGC layer's job.

    rationale:
        Plain-text explanation of the recommendation. Suitable for display
        in the debrief-ui operator dashboard.

    retrieved_event_ids:
        UUIDs (as strings) of the CIAER+ events used as RAG context.
        Stored in event.result.advised_action_type's companion record so
        the OGC layer can trace which historical patterns informed the advice.

    failure_mode_guess:
        Most likely failure_mode_tag inferred from the retrieved events.
        None when retrieval returned no events.
    """

    advised_action_type: str | None
    confidence: float
    rationale: str
    retrieved_event_ids: list[str] = field(default_factory=list)
    failure_mode_guess: str | None = None


# ---------------------------------------------------------------------------
# Action type vocabulary (local copy to avoid importing arcshield.schema)
# ---------------------------------------------------------------------------

_ACTION_TYPES = {
    "PARAMETER_ADJUST",
    "MECHANICAL_INSPECT",
    "MATERIAL_INTERVENE",
    "PROCESS_HALT",
    "MONITOR_HOLD",
    "ESCALATE",
}

# Confidence signal words extracted from LLM prose.
# Checked as whole words (space or word-boundary delimited) to avoid
# substring false-positives (e.g. "certain" inside "uncertain").
_HIGH_CONFIDENCE   = {"definitely", "clearly", "strongly", "certainly", "recommend"}
_MEDIUM_CONFIDENCE = {"likely", "probably", "suggest", "should", "appears"}
_LOW_CONFIDENCE    = {"possibly", "might", "unclear", "uncertain", "unsure"}


# ---------------------------------------------------------------------------
# TwinAdvisory
# ---------------------------------------------------------------------------

class TwinAdvisory:
    """
    Calls an LLM to generate a TwinGuidance from RAG context + sensor summary.

    The only currently implemented provider is "claude" (Anthropic Messages API).
    Other providers fall back to a stub that raises NotImplementedError.

    api_key:
        Anthropic API key. If None, falls back to the ANTHROPIC_API_KEY
        environment variable at call time. The key is never stored in the
        config dataclass — it is injected here or read from the environment.
    """

    def __init__(self, config, api_key: str | None = None) -> None:
        self._config  = config
        self._api_key = api_key  # None → read from env at call time

    # ------------------------------------------------------------------
    # Public interface
    # ------------------------------------------------------------------

    async def generate(
        self,
        context: str,
        current_sensor_summary: str,
        failure_mode_hint: str | None = None,
    ) -> TwinGuidance:
        """
        Call the LLM and return a structured TwinGuidance.

        Parameters
        ----------
        context:
            Formatted RAG context string from TwinRAG.format_context().
        current_sensor_summary:
            Human-readable summary of the current sensor state.
        failure_mode_hint:
            Optional failure_mode_tag from the retrieval step. Used as a
            tiebreaker when the LLM is ambiguous.

        Returns
        -------
        TwinGuidance with action_type, confidence, rationale, and event IDs.

        Raises
        ------
        RuntimeError:
            When the provider is unsupported or the API call fails fatally.
        """
        user_message = (
            f"Current equipment state:\n{current_sensor_summary}\n\n"
            f"Historical expert decisions:\n{context}\n\n"
            "What action should the operator take?"
        )

        if self._config.provider == "claude":
            raw_response = self._call_anthropic(
                system=self._config.system_prompt,
                user=user_message,
            )
        else:
            raise NotImplementedError(
                f"Provider '{self._config.provider}' is not implemented. "
                "Supported: 'claude'"
            )

        return self._parse_response(raw_response, failure_mode_hint=failure_mode_hint)

    # ------------------------------------------------------------------
    # HTTP transport
    # ------------------------------------------------------------------

    def _call_anthropic(self, system: str, user: str) -> str:
        """
        Synchronous HTTP POST to the Anthropic Messages API.

        Uses httpx when available; falls back to urllib.request from stdlib.
        Both paths send identical JSON payloads.

        Returns the text content of the first response block.
        Raises RuntimeError on non-2xx status or missing API key.
        """
        api_key = self._api_key or os.environ.get("ANTHROPIC_API_KEY", "")
        if not api_key:
            raise RuntimeError(
                "Anthropic API key not found. "
                "Set ANTHROPIC_API_KEY env var or pass api_key= to TwinAdvisory."
            )

        payload = {
            "model": self._config.model,
            "max_tokens": 512,
            "temperature": self._config.temperature,
            "system": system,
            "messages": [{"role": "user", "content": user}],
        }

        headers = {
            "x-api-key":         api_key,
            "anthropic-version": "2023-06-01",
            "content-type":      "application/json",
        }

        try:
            import httpx  # noqa: PLC0415
            with httpx.Client(timeout=30.0) as client:
                resp = client.post(
                    "https://api.anthropic.com/v1/messages",
                    json=payload,
                    headers=headers,
                )
            if resp.status_code != 200:
                raise RuntimeError(
                    f"Anthropic API error {resp.status_code}: {resp.text[:200]}"
                )
            data = resp.json()
        except ImportError:
            # stdlib fallback
            import urllib.request  # noqa: PLC0415
            import urllib.error    # noqa: PLC0415

            body = json.dumps(payload).encode()
            req  = urllib.request.Request(
                "https://api.anthropic.com/v1/messages",
                data=body,
                headers=headers,
                method="POST",
            )
            try:
                with urllib.request.urlopen(req, timeout=30) as resp:
                    data = json.loads(resp.read().decode())
            except urllib.error.HTTPError as exc:
                raise RuntimeError(
                    f"Anthropic API error {exc.code}: {exc.read().decode()[:200]}"
                ) from exc

        # Extract text from first content block
        try:
            return data["content"][0]["text"]
        except (KeyError, IndexError) as exc:
            raise RuntimeError(
                f"Unexpected Anthropic response shape: {str(data)[:200]}"
            ) from exc

    # ------------------------------------------------------------------
    # Response parsing
    # ------------------------------------------------------------------

    def _parse_response(
        self,
        text: str,
        failure_mode_hint: str | None = None,
    ) -> TwinGuidance:
        """
        Extract structured fields from LLM free-text response.

        Heuristic rules (in priority order):
        1. Scan for explicit ActionType keywords (case-insensitive).
        2. Estimate confidence from certainty vocabulary.
        3. Use failure_mode_hint as the failure_mode_guess when set.

        This is an intentionally simple parser. Phase 4+ can replace it
        with structured output / function calling once the corpus is large
        enough to validate the improvement.
        """
        upper = text.upper()

        # --- Action type detection ---
        action_type: str | None = None
        for candidate in _ACTION_TYPES:
            if candidate in upper or candidate.replace("_", " ") in upper:
                action_type = candidate
                break

        # --- Confidence estimation ---
        # Split into words to avoid substring false-positives
        # (e.g. "certain" inside "uncertain").
        import re as _re  # noqa: PLC0415
        lower = text.lower()
        words = set(_re.split(r'\W+', lower))
        confidence: float
        if words & _HIGH_CONFIDENCE:
            confidence = 0.85
        elif words & _MEDIUM_CONFIDENCE:
            confidence = 0.65
        elif words & _LOW_CONFIDENCE:
            confidence = 0.40
        else:
            confidence = 0.55  # default when no signal words found

        return TwinGuidance(
            advised_action_type=action_type,
            confidence=confidence,
            rationale=text.strip(),
            failure_mode_guess=failure_mode_hint,
        )
