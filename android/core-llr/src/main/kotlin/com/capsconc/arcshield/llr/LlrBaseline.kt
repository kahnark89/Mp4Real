package com.capsconc.arcshield.llr

/**
 * Statistical snapshot of all sensor channels captured during the I-frame
 * (60–120 seconds at shift start, PTS=0). This is the null hypothesis H₀
 * parameterization — the LLR gate computes Λ as deviations from this baseline.
 *
 * Build via [LlrBaselineBuilder] which accumulates samples over the I-frame
 * window and calls [build] at the end.
 *
 * Immutable after construction. Thread-safe to read from multiple coroutines.
 */
data class LlrBaseline(
    /** Mean normalized one-sided power spectrum over the I-frame (FFT_SIZE/2+1 bins). */
    val acousticSpectrum: FloatArray,

    /** Per-bin variance of the power spectrum over the I-frame. */
    val acousticSpectrumVariance: FloatArray,

    /** Mean accel magnitude (mG) over the I-frame. */
    val accelRmsBaseline: Float,

    /**
     * Variance of accel RMS over the I-frame. Used as the denominator in
     * the Gaussian-shift GLR statistic: Λ_accel = (rms − µ)² / (2σ²).
     * Floored to 1e-6 to prevent division by zero on a perfectly still line.
     */
    val accelRmsVariance: Float,

    val capturedAtNanos: Long,
    val durationMs:      Long,
) {
    // FloatArray doesn't implement structural equality.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LlrBaseline) return false
        return capturedAtNanos == other.capturedAtNanos
            && durationMs      == other.durationMs
            && accelRmsBaseline == other.accelRmsBaseline
            && acousticSpectrum.contentEquals(other.acousticSpectrum)
    }
    override fun hashCode(): Int =
        acousticSpectrum.contentHashCode() xor capturedAtNanos.hashCode()
}
