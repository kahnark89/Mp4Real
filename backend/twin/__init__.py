"""
arcshield.twin
==============
Phase 2 RAG-based Twin advisory layer.

Wired but inactive in Phase 2 (config.active = False).
Set active = True at Phase 3+ to enable LLM advisory generation.

CLAUDE.md §1: The Twin NEVER reads the compliance scalar [a_t = â_t] as a
reward source. TwinGuidance is an output; compliance tracking is a separate
concern handled upstream by the OGC layer.

mp4Real™, ArcShield™, CIAER™, and CIAER+™ are trademarks of Capps Consulting
Company LLC. All rights reserved.
"""

from __future__ import annotations

from .config import TwinConfig
from .coordinator import TwinCoordinator

__all__ = ["TwinCoordinator", "TwinConfig"]
