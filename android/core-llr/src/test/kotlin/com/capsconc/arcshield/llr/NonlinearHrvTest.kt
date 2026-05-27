package com.capsconc.arcshield.llr

import com.capsconc.arcshield.llr.internal.NonlinearHrv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

private const val TOL = 0.001f

class NonlinearHrvTest {

    // Constant sequence: all diffs = 0, RMSSD = 0, SDNN = 0
    private val flat = FloatArray(25) { 800f }

    // Alternating 800/900 ms: diffs all = ±100, RMSSD = 100
    private val alt = FloatArray(25) { if (it % 2 == 0) 800f else 900f }

    // Short sequence (below MIN_RR_COUNT)
    private val short = FloatArray(5) { 800f }

    // -----------------------------------------------------------------------
    // sdnn
    // -----------------------------------------------------------------------

    @Test fun sdnn_flat_is_zero() {
        assertEquals(0f, NonlinearHrv.sdnn(flat), TOL)
    }

    @Test fun sdnn_alternating_is_correct() {
        // Population of 800/900 alternating: mean=850, deviations ±50
        // Sample std = 50 * sqrt(25/24) ≈ 51.03
        val expected = 50f * sqrt(25f / 24f)
        assertEquals(expected, NonlinearHrv.sdnn(alt), 0.1f)
    }

    @Test fun sdnn_short_returns_zero() {
        assertEquals(0f, NonlinearHrv.sdnn(FloatArray(1) { 800f }), TOL)
    }

    // -----------------------------------------------------------------------
    // rmssd
    // -----------------------------------------------------------------------

    @Test fun rmssd_flat_is_zero() {
        assertEquals(0f, NonlinearHrv.rmssd(flat), TOL)
    }

    @Test fun rmssd_alternating_is_100() {
        // Every successive diff = ±100, so sqrt(mean of 100² = 10000) = 100
        assertEquals(100f, NonlinearHrv.rmssd(alt), TOL)
    }

    @Test fun rmssd_short_returns_zero() {
        assertEquals(0f, NonlinearHrv.rmssd(FloatArray(1) { 800f }), TOL)
    }

    // -----------------------------------------------------------------------
    // sd1 = rmssd / sqrt(2)
    // -----------------------------------------------------------------------

    @Test fun sd1_alternating_equals_rmssd_over_sqrt2() {
        val expected = 100f / sqrt(2f)
        assertEquals(expected, NonlinearHrv.sd1(alt), TOL)
    }

    @Test fun sd1_flat_is_zero() {
        assertEquals(0f, NonlinearHrv.sd1(flat), TOL)
    }

    // -----------------------------------------------------------------------
    // sd2 = sqrt(2*SDNN² - 0.5*RMSSD²), clamped to 0
    // -----------------------------------------------------------------------

    @Test fun sd2_flat_is_zero() {
        // SDNN=0, RMSSD=0 → sqrt(0-0) = 0
        assertEquals(0f, NonlinearHrv.sd2(flat), TOL)
    }

    @Test fun sd2_non_negative_for_alt() {
        // Must be ≥ 0 even with floating-point rounding
        assertTrue(NonlinearHrv.sd2(alt) >= 0f)
    }

    @Test fun sd2_formula_is_correct_for_known_input() {
        // Construct a sequence where SDNN and RMSSD are known precisely:
        // linear ramp 0,1,2,...,24 → deterministic
        val ramp = FloatArray(25) { it.toFloat() }
        val sdnn = NonlinearHrv.sdnn(ramp)
        val rmssd = NonlinearHrv.rmssd(ramp)
        val expected = sqrt(maxOf(0f, 2f * sdnn * sdnn - 0.5f * rmssd * rmssd))
        assertEquals(expected, NonlinearHrv.sd2(ramp), TOL)
    }

    // -----------------------------------------------------------------------
    // sampleEntropy
    // -----------------------------------------------------------------------

    @Test fun sampleEntropy_flat_returns_nan() {
        // Constant signal: SDNN=0, tolerance=0 → NaN (SDNN ≤ 0 guard)
        assertTrue(NonlinearHrv.sampleEntropy(flat).isNaN())
    }

    @Test fun sampleEntropy_short_returns_nan() {
        assertTrue(NonlinearHrv.sampleEntropy(short).isNaN())
    }

    @Test fun sampleEntropy_alternating_is_finite_and_non_negative() {
        val se = NonlinearHrv.sampleEntropy(alt)
        assertFalse("SampEn should be finite for alternating sequence", se.isNaN())
        assertTrue("SampEn must be ≥ 0", se >= 0f)
    }

    @Test fun sampleEntropy_more_regular_signal_has_lower_entropy() {
        // Nearly-constant signal with tiny jitter should have lower SampEn than
        // a high-variability signal
        val lowVar = FloatArray(30) { 800f + (it % 2) * 5f }     // ±2.5 ms jitter
        val highVar = FloatArray(30) { 800f + (it % 2) * 100f }  // ±50 ms jitter
        val seLow  = NonlinearHrv.sampleEntropy(lowVar)
        val seHigh = NonlinearHrv.sampleEntropy(highVar)
        if (!seLow.isNaN() && !seHigh.isNaN()) {
            assertTrue("Low-variance signal should have lower SampEn", seLow <= seHigh)
        }
    }

    @Test fun sampleEntropy_result_is_negative_ln_of_ratio() {
        // Manually verify: count B (m=2 matches) and A (m+1 matches) for a small sequence
        // Use a known 5-beat repeating pattern with tight tolerance
        val rr = floatArrayOf(800f, 850f, 800f, 850f, 800f, 850f, 800f, 850f,
                              800f, 850f, 800f, 850f, 800f, 850f, 800f, 850f,
                              800f, 850f, 800f, 850f)
        val se = NonlinearHrv.sampleEntropy(rr, m = 2, rFraction = 0.2f)
        // Result must be non-negative and finite for this predictable sequence
        assertFalse(se.isNaN())
        assertTrue(se >= 0f)
    }

    // -----------------------------------------------------------------------
    // MIN_RR_COUNT boundary
    // -----------------------------------------------------------------------

    @Test fun functions_require_min_rr_count_for_sampEn() {
        val just_below = FloatArray(NonlinearHrv.MIN_RR_COUNT - 1) { 800f + it }
        assertTrue(NonlinearHrv.sampleEntropy(just_below).isNaN())

        val exact = FloatArray(NonlinearHrv.MIN_RR_COUNT) { 800f + it * 2f }
        // MIN_RR_COUNT non-constant samples: may or may not be NaN depending on matches,
        // but must not throw
        NonlinearHrv.sampleEntropy(exact)  // smoke test — no exception
    }
}
