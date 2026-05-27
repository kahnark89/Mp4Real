"""
codebook.matcher
================
Phase 1 codebook matching: cosine similarity on per-instrument ratio feature vectors.

Architecture (CLAUDE.md §5.3 / FIG. 8):
  1. Extract per-track summary features from event Cause fields.
  2. For each primitive π_i, compute cosine similarity TC(event | π_i).
  3. Select π* = argmax_i TC.
  4. If TC* >= theta_tc: return compressed token (primitive_id + delta_vector).
  5. If TC* < theta_tc: is_novel=True → HITL path (FIG. 8 step 806→807).

Phase 1 substitution: cosine similarity on shared-instrument ratio vectors stands
in for the formal total correlation TC. Phase 3 replaces this with per-track
causal Transformer embeddings + VICReg contrastive training.

Normalization: each instrument's value is divided by the primitive's canonical
confidence-weighted value for that instrument, producing a ratio vector.
Cosine similarity on ratio vectors is scale-invariant across units (A, °F, psi,
etc.) — die_pressure_psi (≈2800) does not dominate crammer_amps (≈67).

Score semantics:
  - 1.0: event's sensor profile is exactly proportional to the primitive's
         canonical profile across all shared instruments.
  - <1.0: non-uniform ratio distribution — some instruments elevated while
          others are depressed, indicating a different anomaly pattern.
  - 0.0: fewer than 2 shared instruments (structurally incomparable).

Limitation (Phase 1 known): if all sensors deviate from canonical by the SAME
scalar factor (e.g., line running hotter overall), the ratio vector is still
[1, 1, ..., 1] scaled uniformly, giving score=1.0. Phase 3 embeddings handle
this via absolute-value context in the Transformer's positional encoding.
"""

from __future__ import annotations

import math
from typing import Any

from codebook.schema import CodebookMatchResult, Primitive


# ---------------------------------------------------------------------------
# Feature extraction
# ---------------------------------------------------------------------------

def extract_cause_features(
    sensor_readings: list[dict],
    acoustic_profile: dict[str, Any] | None = None,
    biometric_snapshot: dict[str, Any] | None = None,
    escalation_state: int = 0,
) -> dict[str, float]:
    """
    Extract a flat feature dict from raw cause-phase components.

    Each key is prefixed by track type (sensor.*, acoustic.*, bio.*, escalation)
    to avoid instrument_id collisions across tracks.  Values are raw (unscaled).

    Args:
        sensor_readings: list of dicts with instrument_id, value, [confidence].
        acoustic_profile: optional; extracts spectral_delta_db and dominant_freq_hz.
        biometric_snapshot: optional; extracts hr_bpm, hrv_rmssd_ms, accelerometer_mag.
        escalation_state: integer 0–3.

    Returns:
        dict mapping feature names → float values.
    """
    features: dict[str, float] = {}

    for r in sensor_readings:
        iid = r.get("instrument_id", "")
        val = r.get("value")
        conf = r.get("confidence", 1.0)
        if iid and val is not None:
            features[f"sensor.{iid}"] = float(val) * float(conf)

    if acoustic_profile:
        if "spectral_delta_db" in acoustic_profile:
            features["acoustic.spectral_delta_db"] = float(acoustic_profile["spectral_delta_db"])
        if "dominant_freq_hz" in acoustic_profile:
            features["acoustic.dominant_freq_hz"] = float(acoustic_profile["dominant_freq_hz"])

    if biometric_snapshot:
        bio = biometric_snapshot
        if bio.get("hr_bpm") is not None:
            features["bio.hr_bpm"] = float(bio["hr_bpm"])
        if bio.get("hrv_rmssd_ms") is not None:
            features["bio.hrv_rmssd_ms"] = float(bio["hrv_rmssd_ms"])
        if bio.get("accelerometer_mag") is not None:
            features["bio.accelerometer_mag"] = float(bio["accelerometer_mag"])

    features["escalation_state"] = float(escalation_state)

    return features


def primitive_to_features(primitive: Primitive) -> dict[str, float]:
    """Extract the canonical feature dict from a Primitive's stored readings."""
    readings_as_dicts = [
        {
            "instrument_id": r.instrument_id,
            "value": r.value,
            "confidence": r.confidence,
        }
        for r in primitive.canonical_sensor_readings
    ]
    return extract_cause_features(
        sensor_readings=readings_as_dicts,
        acoustic_profile=primitive.canonical_acoustic_profile,
        biometric_snapshot=None,
        escalation_state=primitive.canonical_escalation_state,
    )


# ---------------------------------------------------------------------------
# Cosine similarity on ratio vectors
# ---------------------------------------------------------------------------

def cosine_similarity(
    event_features: dict[str, float],
    primitive_features: dict[str, float],
) -> tuple[float, int]:
    """
    Cosine similarity between event and primitive feature dicts via ratio vectors.

    For each shared dimension i: ratio_i = event_val[i] / prim_val[i].
    event_vec = [ratio_1, ..., ratio_N]; prim_vec = [1, 1, ..., 1].
    sim = dot(event_vec, prim_vec) / (|event_vec| × |prim_vec|).

    Dimensions where prim_val == 0.0 are skipped (cannot normalize).
    Returns (0.0, 0) when fewer than 2 shared dimensions exist.

    Returns:
        (similarity ∈ [0.0, 1.0], shared_dimension_count)
    """
    shared_keys = [
        k for k in event_features
        if k in primitive_features and primitive_features[k] != 0.0
    ]
    if len(shared_keys) < 2:
        return 0.0, len(shared_keys)

    ratios = [event_features[k] / primitive_features[k] for k in shared_keys]

    n = len(ratios)
    dot = sum(ratios)
    mag_event = math.sqrt(sum(r * r for r in ratios))
    mag_prim = math.sqrt(n)

    if mag_event == 0.0:
        return 0.0, n

    sim = dot / (mag_event * mag_prim)
    return max(0.0, min(1.0, sim)), n


# ---------------------------------------------------------------------------
# Delta vector (the compressed token payload — FIG. 8 step 805)
# ---------------------------------------------------------------------------

def compute_delta_vector(
    event_features: dict[str, float],
    primitive_features: dict[str, float],
) -> dict[str, float]:
    """
    Fractional deviation of event features from primitive canonical values.

    delta[k] = (event[k] - prim[k]) / prim[k]

    Positive → event exceeds canonical; negative → event below canonical.
    Only shared keys with non-zero primitive values are included.
    """
    return {
        k: (event_features[k] - primitive_features[k]) / primitive_features[k]
        for k in event_features
        if k in primitive_features and primitive_features[k] != 0.0
    }


# ---------------------------------------------------------------------------
# Matcher
# ---------------------------------------------------------------------------

class CosineCodebookMatcher:
    """
    Phase 1 codebook matcher: cosine similarity on normalized feature vectors.

    Constructed once per server lifespan from the current PrimitiveCodebook.
    Stateless after construction — safe for concurrent read calls.

    Usage:
        matcher = CosineCodebookMatcher(codebook.list_all())
        result  = matcher.match_from_cause(sensor_readings, acoustic_profile,
                                           biometric_snapshot, escalation_state)
    """

    def __init__(self, primitives: list[Primitive]) -> None:
        self._primitives = primitives
        self._primitive_features: dict[str, dict[str, float]] = {
            p.primitive_id: primitive_to_features(p)
            for p in primitives
        }

    # ------------------------------------------------------------------
    # Match
    # ------------------------------------------------------------------

    def match_from_features(
        self,
        event_features: dict[str, float],
    ) -> CodebookMatchResult:
        """
        Score event_features against all primitives and return the best match.

        If the codebook is empty, returns is_novel=True with tc_score=0.0.
        """
        if not self._primitives:
            return CodebookMatchResult(
                matched_primitive_id=None,
                failure_mode_tag=None,
                tc_score=0.0,
                theta_tc=1.0,
                is_novel=True,
                shared_instruments=0,
                delta_vector={},
                all_scores={},
            )

        scores: dict[str, tuple[float, int]] = {}
        for p in self._primitives:
            sim, shared = cosine_similarity(event_features, self._primitive_features[p.primitive_id])
            scores[p.primitive_id] = (sim, shared)

        best_id = max(scores, key=lambda k: scores[k][0])
        best_score, best_shared = scores[best_id]
        best_prim = next(p for p in self._primitives if p.primitive_id == best_id)
        is_novel = best_score < best_prim.theta_tc

        delta = (
            {}
            if is_novel
            else compute_delta_vector(event_features, self._primitive_features[best_id])
        )

        return CodebookMatchResult(
            matched_primitive_id=None if is_novel else best_id,
            failure_mode_tag=None if is_novel else best_prim.failure_mode_tag,
            tc_score=best_score,
            theta_tc=best_prim.theta_tc,
            is_novel=is_novel,
            shared_instruments=best_shared,
            delta_vector=delta,
            all_scores={k: v[0] for k, v in scores.items()},
        )

    def match_from_cause(
        self,
        sensor_readings: list[dict],
        acoustic_profile: dict[str, Any] | None = None,
        biometric_snapshot: dict[str, Any] | None = None,
        escalation_state: int = 0,
    ) -> CodebookMatchResult:
        """Convenience wrapper: extract features then match."""
        features = extract_cause_features(
            sensor_readings=sensor_readings,
            acoustic_profile=acoustic_profile,
            biometric_snapshot=biometric_snapshot,
            escalation_state=escalation_state,
        )
        return self.match_from_features(features)
