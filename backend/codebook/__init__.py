"""
ArcShield Phase 1 behavioral primitive codebook.

Public API:
    PrimitiveCodebook      — dict management (load / save / HITL expand)
    CosineCodebookMatcher  — cosine similarity scoring against primitives
    Primitive              — canonical cause signature for a failure mode
    CodebookMatchResult    — match output (primitive reference + delta vector)
    CodebookExpansionRequest — HITL input to add a new primitive

Architecture (CLAUDE.md §5.1, §5.3 / FIG. 8):
  Phase 1: hand-curated dict keyed by failure_mode_tag, matched by cosine
  similarity on per-instrument ratio feature vectors.
  Phase 3+: replace with learned per-track causal Transformer embeddings + VICReg
  contrastive training + residual VQ.

mp4Real™, ArcShield™, CIAER™, and CIAER+™ are trademarks of Capps Consulting
Company LLC. All rights reserved.
"""

from codebook.codebook import PrimitiveCodebook
from codebook.matcher import CosineCodebookMatcher
from codebook.schema import (
    CodebookExpansionRequest,
    CodebookMatchResult,
    Primitive,
)

__all__ = [
    "PrimitiveCodebook",
    "CosineCodebookMatcher",
    "Primitive",
    "CodebookMatchResult",
    "CodebookExpansionRequest",
]
