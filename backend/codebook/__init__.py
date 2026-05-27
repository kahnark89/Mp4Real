"""
codebook
========
Phase 1 hand-curated behavioral primitive map for ArcShield/mp4Real.

Per CLAUDE.md §5.1: For corpus depth < ~100 events, the codebook is a Python
dict keyed by failure_mode_tag, matched by value-proximity scoring on per-track
summary embeddings. Debuggable, inspectable, every entry readable.

Default primitives are loaded from primitives/hollowell_ppvc1.yaml — the
Hollowell PPVC Line 1 seed set covering common PVC extrusion failure modes.
"""

from __future__ import annotations

from .primitive import BehavioralPrimitive
from .registry import PrimitiveRegistry
from .matcher import PrimitiveMatcher, MatchResult

__all__ = ["BehavioralPrimitive", "PrimitiveRegistry", "PrimitiveMatcher", "MatchResult"]
