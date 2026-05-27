"""
codebook.matcher
================
PrimitiveMatcher — Phase 1 cause-signature matching.

Implements value-proximity scoring against the behavioral primitive codebook.
Same algorithm as MOD-003 in the sqlite_backend (query_by_cause_signature):
  - For each instrument shared between query and primitive signature:
      proximity = 1 / (1 + |query_val - midpoint(range)|)
  - Average across shared instruments
  - key_instruments are weighted 2x
  - Primitives with zero shared instruments receive score 0.0

Phase 3+ replaces with embedding-based ANN search (CLAUDE.md §5.2).
"""

from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class MatchResult:
    """
    Result of matching one query sensor signature against one primitive.

    score is in [0.0, 1.0] where higher = closer match.
    matched_instruments are the instrument IDs present in both query and primitive.
    unmatched_instruments are instruments in the primitive signature not in the query.
    """
    primitive: "BehavioralPrimitive"  # type: ignore[name-defined]  # noqa: F821
    score: float
    matched_instruments: list[str] = field(default_factory=list)
    unmatched_instruments: list[str] = field(default_factory=list)


class PrimitiveMatcher:
    """
    Phase 1 value-proximity codebook matcher.

    Usage:
        matcher = PrimitiveMatcher()
        results = matcher.match(sensor_readings, registry, top_n=5)
    """

    def match(
        self,
        sensor_readings: list,  # list[SensorReading] — typed loosely to avoid circular import
        registry: "PrimitiveRegistry",  # type: ignore[name-defined]  # noqa: F821
        top_n: int = 5,
    ) -> list[MatchResult]:
        """
        Match the provided sensor readings against all primitives in the registry.

        Returns up to *top_n* MatchResult objects sorted by score descending.

        Parameters
        ----------
        sensor_readings : list[SensorReading]
            Current sensor state from the cause window.
        registry : PrimitiveRegistry
            The loaded primitive registry.
        top_n : int
            Maximum results to return.
        """
        # Build query dict: instrument_id → value
        query: dict[str, float] = {
            r.instrument_id: r.value for r in sensor_readings
        }

        results: list[MatchResult] = []

        for primitive in registry.list_all():
            score, matched, unmatched = self._score(query, primitive)
            results.append(MatchResult(
                primitive=primitive,
                score=score,
                matched_instruments=matched,
                unmatched_instruments=unmatched,
            ))

        # Sort by score descending; stable sort preserves insertion order for ties
        results.sort(key=lambda r: r.score, reverse=True)
        return results[:top_n]

    # ------------------------------------------------------------------
    # Scoring
    # ------------------------------------------------------------------

    @staticmethod
    def _score(
        query: dict[str, float],
        primitive: "BehavioralPrimitive",  # type: ignore[name-defined]  # noqa: F821
    ) -> tuple[float, list[str], list[str]]:
        """
        Compute the value-proximity score for one primitive against the query.

        Returns (score, matched_instruments, unmatched_instruments).
        """
        key_set = set(primitive.key_instruments)
        matched: list[str] = []
        unmatched: list[str] = []

        weighted_sum = 0.0
        weight_total = 0.0

        for inst_id, rng in primitive.sensor_signature.items():
            if inst_id in query:
                matched.append(inst_id)
                query_val = query[inst_id]
                midpoint = (rng[0] + rng[1]) / 2.0
                proximity = 1.0 / (1.0 + abs(query_val - midpoint))
                weight = 2.0 if inst_id in key_set else 1.0
                weighted_sum += proximity * weight
                weight_total += weight
            else:
                unmatched.append(inst_id)

        if weight_total == 0.0:
            return 0.0, matched, unmatched

        score = weighted_sum / weight_total
        return score, matched, unmatched
