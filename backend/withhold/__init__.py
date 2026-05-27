"""
arcshield.withhold
==================
Withhold-sampling coordinator for the ArcShield reflexivity detector.

Implements CLAUDE.md §7: maintains Q (live corpus) and Q' (counterfactual)
as strictly separate partitions, monitors D_KL divergences, and gates
withhold-sampling based on config.

Phase 2 contract: p_withhold=0.0. The infrastructure exists and tracks
partitions, but withhold-sampling never fires. Activate at Phase 3+ with
a single config change.

CRITICAL INVARIANT (CLAUDE.md §12 item 5):
    Q and Q' are NEVER merged. They are separate collections from the
    moment of creation. The DistributionPartition class enforces this.

mp4Real™, ArcShield™, CIAER™, and CIAER+™ are trademarks of Capps Consulting
Company LLC. All rights reserved.
"""

from __future__ import annotations

from .config import WithholdConfig
from .coordinator import WithholdCoordinator
from .distributions import DistributionPartition, KLDivergenceMonitor

__all__ = [
    "WithholdCoordinator",
    "WithholdConfig",
    "DistributionPartition",
    "KLDivergenceMonitor",
]
