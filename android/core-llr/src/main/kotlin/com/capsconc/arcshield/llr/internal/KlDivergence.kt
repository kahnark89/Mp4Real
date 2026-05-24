package com.capsconc.arcshield.llr.internal

import kotlin.math.ln
import kotlin.math.max

/**
 * KL divergence (relative entropy) between two discrete probability distributions.
 *
 * Used as the log-likelihood ratio test statistic for the acoustic channel:
 *   Λ_acoustic = KL(P_baseline ‖ P_current)
 *
 * Large values mean the current spectrum is far from the shift-start baseline,
 * contributing evidence of H₁ ("something changed") over H₀ ("steady state").
 *
 * Both [baseline] and [current] must sum to 1.0 (normalized power spectra from
 * [RealFft.powerSpectrum] already satisfy this). The [epsilon] floor prevents
 * ln(0) without materially affecting the test statistic for well-formed inputs.
 */
internal object KlDivergence {

    /**
     * Compute KL(baseline ‖ current) = Σ baseline[k] · ln(baseline[k] / current[k]).
     *
     * @param baseline Reference distribution (I-frame mean power spectrum).
     * @param current  Query distribution (rolling window power spectrum).
     * @param epsilon  Floor applied to zero bins before division.
     * @return Non-negative KL divergence. Returns 0.0 if arrays are empty.
     */
    fun compute(
        baseline: FloatArray,
        current:  FloatArray,
        epsilon:  Float = 1e-10f,
    ): Float {
        require(baseline.size == current.size) {
            "baseline.size (${baseline.size}) != current.size (${current.size})"
        }
        if (baseline.isEmpty()) return 0f

        var kl = 0f
        for (k in baseline.indices) {
            val p = max(baseline[k], epsilon)
            val q = max(current[k],  epsilon)
            kl += p * ln(p / q)
        }
        return max(kl, 0f)   // clamp: floating-point noise can produce tiny negatives
    }
}
