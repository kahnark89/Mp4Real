"""
arcshield.twin.config
=====================
Configuration dataclass for the TwinCoordinator.

Phase 2 default: active=False. The Twin is wired but does not call the LLM.
Phase 3+: set active=True in config. No code changes required.
"""

from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class TwinConfig:
    """
    Configuration for the Twin advisory layer.

    active: False in Phase 2. Flip to True at Phase 3+. This is the only
    change required to activate the Twin — no structural code changes.

    provider: LLM provider identifier. Maps to the LlmClient abstraction
    described in CLAUDE.md §9. Currently only "claude" is implemented.

    model: Cost-optimized Haiku for low-latency advisory. Upgrade to Sonnet
    at Phase 4 if advisory quality requires it.

    temperature: Low temperature (0.2) for deterministic, reproducible advisory
    recommendations. The Twin is not creative — it retrieves and synthesizes.
    """

    active: bool = False
    provider: str = "claude"
    model: str = "claude-haiku-4-5-20251001"
    max_retrieved_events: int = 5
    min_retrieved_weight: float = 0.3
    temperature: float = 0.2
    system_prompt: str = field(
        default=(
            "You are a PVC extrusion expert advisor for the ArcShield system. "
            "Based on the historical expert decisions below, recommend the most "
            "appropriate action for the current equipment state. Be specific about "
            "parameter changes. If confidence is low, say so."
        )
    )
