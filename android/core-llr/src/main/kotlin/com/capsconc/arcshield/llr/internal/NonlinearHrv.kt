package com.capsconc.arcshield.llr.internal

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pure functions for nonlinear HRV analysis from R-R interval sequences.
 *
 * All inputs are R-R intervals in milliseconds as FloatArray.
 * All outputs are in the same unit as the input (ms) unless noted.
 *
 * Used by [RollingBioStats] to populate [BioSnapshot.sd1Ms], [BioSnapshot.sd2Ms],
 * and [BioSnapshot.sampEn] when the rolling R-R buffer has ≥ MIN_RR_COUNT samples.
 *
 * References:
 *   SD1/SD2: Brennan et al. 2001 "Do Existing Measures of Poincaré Plot Geometry
 *            Reflect Nonlinear Features of Heart Rate Variability?"
 *   SampEn:  Richman & Moorman 2000 "Physiological time-series analysis using
 *            approximate entropy and sample entropy"
 */
internal object NonlinearHrv {

    /** Minimum R-R count for nonlinear HRV computation. */
    const val MIN_RR_COUNT = 20

    /**
     * Standard deviation of the R-R interval sequence (SDNN, ms).
     * Returns 0f for sequences shorter than 2.
     */
    fun sdnn(rr: FloatArray): Float {
        if (rr.size < 2) return 0f
        val mean = rr.average().toFloat()
        val variance = rr.fold(0.0) { acc, v ->
            val d = v.toDouble() - mean
            acc + d * d
        } / (rr.size - 1)
        return sqrt(variance).toFloat()
    }

    /**
     * RMSSD (ms) — root mean square of successive R-R differences.
     * Returns 0f for sequences shorter than 2.
     */
    fun rmssd(rr: FloatArray): Float {
        if (rr.size < 2) return 0f
        var sumSq = 0.0
        for (i in 1 until rr.size) {
            val d = (rr[i] - rr[i - 1]).toDouble()
            sumSq += d * d
        }
        return sqrt(sumSq / (rr.size - 1)).toFloat()
    }

    /**
     * Poincaré SD1 (ms) — short-term HRV, proxy for respiratory sinus arrhythmia.
     * SD1 = RMSSD / sqrt(2).
     */
    fun sd1(rr: FloatArray): Float = rmssd(rr) / sqrt(2f)

    /**
     * Poincaré SD2 (ms) — long-term HRV, proxy for sympathetic modulation.
     * SD2 = sqrt(max(0, 2*SDNN² - 0.5*RMSSD²)).
     * Clamped to 0 to avoid sqrt of negative from floating-point rounding.
     */
    fun sd2(rr: FloatArray): Float {
        val s = sdnn(rr)
        val r = rmssd(rr)
        return sqrt(max(0f, 2f * s * s - 0.5f * r * r))
    }

    /**
     * Sample Entropy — SampEn(m=2, r=rFraction*SDNN).
     *
     * Returns Float.NaN when:
     *   - fewer than [MIN_RR_COUNT] samples
     *   - SDNN ≤ 0 (constant signal)
     *   - no template matches at length m (undefined by convention)
     *
     * O(N²) but N is bounded by the rolling window size (~75–300 beats for 1–4 min),
     * so wall time is sub-millisecond for typical window sizes.
     *
     * @param rFraction Tolerance as a fraction of SDNN. Default 0.2 (canonical).
     */
    fun sampleEntropy(rr: FloatArray, m: Int = 2, rFraction: Float = 0.2f): Float {
        if (rr.size < MIN_RR_COUNT || rr.size < m + 2) return Float.NaN
        val s = sdnn(rr)
        if (s <= 0f) return Float.NaN
        val tolerance = rFraction * s

        val n = rr.size
        var templateMatches  = 0   // B: templates of length m that match
        var extendedMatches  = 0   // A: templates of length m+1 that match

        for (i in 0 until n - m) {
            for (j in i + 1 until n - m) {
                // Check template of length m
                var matchesM = true
                for (k in 0 until m) {
                    if (abs(rr[i + k] - rr[j + k]) > tolerance) {
                        matchesM = false
                        break
                    }
                }
                if (matchesM) {
                    templateMatches++
                    // Check extension to length m+1
                    if (abs(rr[i + m] - rr[j + m]) <= tolerance) {
                        extendedMatches++
                    }
                }
            }
        }

        if (templateMatches == 0) return Float.NaN
        return -ln(extendedMatches.toFloat() / templateMatches.toFloat())
    }
}
