"""
arcshield.withhold.config
==========================
Configuration dataclass for the WithholdCoordinator.

Phase 2 defaults: p_withhold=0.0. The coordinator is instantiated and
partition tracking is active, but withhold-sampling never fires.

Phase 3+ deployment:
  - Set p_withhold=0.05 (5% of matched events withheld).
  - Empirical calibration required before Phase 4 launch (CLAUDE.md §17).
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass
class WithholdConfig:
    """
    Configuration for the withhold-sampling subsystem.

    p_withhold:
        Probability that advisory guidance is withheld for a given event,
        causing it to enter Q' instead of Q. Phase 2 default is 0.0 (off).
        CLAUDE.md §7.2: default to 0.05 when advisory guidance goes live.

    min_corpus_depth_to_activate:
        Minimum corpus depth before withhold-sampling is allowed to fire.
        Even if p_withhold > 0, sampling is suppressed until at least this
        many validated events exist. Prevents premature counterfactual
        sampling on a sparse corpus.

    kl_spike_threshold:
        D_KL(P‖Q) above this value triggers a re-weighting review flag.
        The coordinator does not automatically re-weight; it surfaces the
        signal for human review (dashboard / automated alert).

    window_size:
        Rolling window size for distribution estimation. The KL divergence
        is computed over the most recent window_size events in Q and Q'.
        Reduces sensitivity to historical distribution shift.
    """

    p_withhold: float = 0.0
    min_corpus_depth_to_activate: int = 200
    kl_spike_threshold: float = 0.5
    window_size: int = 50
