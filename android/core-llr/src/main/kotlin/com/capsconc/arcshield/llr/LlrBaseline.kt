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

    // ------------------------------------------------------------------
    // Biometric baseline — populated from I-frame when a BiometricSource
    // is connected at shift start. All fields default to 0 / unavailable
    // when no biometric source was present during the I-frame capture.
    // ------------------------------------------------------------------

    /** Mean HR in BPM over the I-frame. 0f when biometricAvailable = false. */
    val hrBaselineBpm: Float = 0f,

    /**
     * Variance of HR in BPM² over the I-frame. Floored to 1e-6.
     * Denominator in Λ_hr = (hr_current − µ)² / (2σ²).
     */
    val hrVarianceBpm: Float = 1e-6f,

    /** Mean RMSSD in ms over the I-frame. 0f when biometricAvailable = false. */
    val rmssdBaselineMs: Float = 0f,

    /**
     * Variance of RMSSD in ms² over the I-frame. Floored to 1e-6.
     * Denominator in Λ_rmssd = (rmssd_current − µ)² / (2σ²).
     */
    val rmssdVarianceMs: Float = 1e-6f,

    /** True when HR/RMSSD baseline was captured from a live BiometricSource. */
    val biometricAvailable: Boolean = false,

    // ------------------------------------------------------------------
    // Nonlinear HRV baseline — populated when ≥ 20 R-R intervals were
    // captured during the I-frame. Requires H10 (raw R-R via PMD).
    // All fields default to 0 / NaN / false when unavailable.
    // ------------------------------------------------------------------

    /** Mean Poincaré SD1 (ms) over I-frame sub-windows. */
    val sd1BaselineMs: Float = 0f,
    /** Variance of SD1 across I-frame sub-windows. Floored to 1e-6. */
    val sd1VarianceMs: Float = 1e-6f,
    /** Mean Poincaré SD2 (ms) over I-frame sub-windows. */
    val sd2BaselineMs: Float = 0f,
    /** Variance of SD2 across I-frame sub-windows. Floored to 1e-6. */
    val sd2VarianceMs: Float = 1e-6f,
    /** Mean Sample Entropy over I-frame sub-windows. NaN when unavailable. */
    val sampEnBaseline: Float = Float.NaN,
    /** Variance of SampEn across I-frame sub-windows. Floored to 1e-6. */
    val sampEnVariance: Float = 1e-6f,
    /** True when nonlinear HRV baseline was captured (requires ≥ 20 R-R intervals). */
    val nonlinearHrvAvailable: Boolean = false,

    // ------------------------------------------------------------------
    // Motion baseline — populated when a CaptureSource video flow was
    // collected during the I-frame. All fields default to 0 / unavailable
    // when no video source was present.
    // ------------------------------------------------------------------

    /**
     * Mean frame-to-frame MAD (mean absolute difference on Y-plane, 0..255 range)
     * over the I-frame. 0f when motionAvailable = false.
     */
    val motionBaselineMad: Float = 0f,

    /**
     * Variance of frame MAD over the I-frame. Floored to 1e-6.
     * Denominator in Λ_motion = (mad_current − µ)² / (2σ²).
     */
    val motionVarianceMad: Float = 1e-6f,

    /** True when frame-diff motion baseline was captured from a live CaptureSource. */
    val motionAvailable: Boolean = false,
) {
    // FloatArray doesn't implement structural equality.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LlrBaseline) return false
        return capturedAtNanos    == other.capturedAtNanos
            && durationMs         == other.durationMs
            && accelRmsBaseline   == other.accelRmsBaseline
            && hrBaselineBpm      == other.hrBaselineBpm
            && rmssdBaselineMs    == other.rmssdBaselineMs
            && biometricAvailable == other.biometricAvailable
            && motionBaselineMad  == other.motionBaselineMad
            && motionAvailable    == other.motionAvailable
            && acousticSpectrum.contentEquals(other.acousticSpectrum)
    }
    override fun hashCode(): Int =
        acousticSpectrum.contentHashCode() xor capturedAtNanos.hashCode()
}
