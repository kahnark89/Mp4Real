package com.capsconc.arcshield.llr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AdaptiveLlrBaseline] and [AdaptiveBaselineConfig].
 *
 * All tests use explicit wall-clock timestamps to avoid any dependency on SystemClock.
 * The adaptive baseline is a pure-Kotlin class; no coroutines or Android stubs needed.
 */
class AdaptiveLlrBaselineTest {

    // ---- Helpers ----------------------------------------------------------------

    private val bins = 9  // small spectrum for test speed

    private fun baseline(
        accelMean: Float = 100f,
        accelVar:  Float = 100f,
        hrMean:    Float = 70f,
        hrVar:     Float = 25f,
        hasBio:    Boolean = true,
        hasMotion: Boolean = false,
    ) = LlrBaseline(
        acousticSpectrum         = FloatArray(bins) { 1f / bins },
        acousticSpectrumVariance = FloatArray(bins) { 0.01f },
        accelRmsBaseline         = accelMean,
        accelRmsVariance         = accelVar,
        capturedAtNanos          = 0L,
        durationMs               = 120_000L,
        hrBaselineBpm            = hrMean,
        hrVarianceBpm            = hrVar,
        rmssdBaselineMs          = 40f,
        rmssdVarianceMs          = 16f,
        biometricAvailable       = hasBio,
        motionBaselineMad        = 5f,
        motionVarianceMad        = 4f,
        motionAvailable          = hasMotion,
    )

    /** Config with very short half-lives so convergence tests run quickly (dt in test units). */
    private val fastConfig = AdaptiveBaselineConfig(
        hrHalfLifeSec       = 1f,
        rmssdHalfLifeSec    = 1f,
        accelHalfLifeSec    = 1f,
        acousticHalfLifeSec = 1f,
        motionHalfLifeSec   = 1f,
        medianWindowNanos   = 10_000_000_000L,  // 10 s
        driftSigmaThreshold = 2.0f,
    )

    private fun measurements(
        t: Long,
        accelRms: Float = 100f,
        spectrum: FloatArray? = FloatArray(9) { 1f / 9f },
        hrBpm: Float? = 70f,
        rmssdMs: Float? = 40f,
        motionMad: Float? = null,
    ) = ChannelMeasurements(
        timestampNanos = t,
        accelRms       = accelRms,
        spectrum       = spectrum,
        hrBpm          = hrBpm,
        rmssdMs        = rmssdMs,
        motionMad      = motionMad,
    )

    // =========================================================================
    // Snapshot
    // =========================================================================

    @Test
    fun `snapshot returns initial baseline before any updates`() {
        val base = baseline()
        val adaptive = AdaptiveLlrBaseline(base, fastConfig)
        val snap = adaptive.snapshot()
        assertEquals(base.accelRmsBaseline, snap.accelRmsBaseline, 1e-6f)
        assertEquals(base.hrBaselineBpm, snap.hrBaselineBpm, 1e-6f)
    }

    @Test
    fun `snapshot is cached — same instance returned when no update occurred`() {
        val adaptive = AdaptiveLlrBaseline(baseline(), fastConfig)
        // First call populates cache
        val snap1 = adaptive.snapshot()
        // Second call — dirty flag not set, should return exact same object
        val snap2 = adaptive.snapshot()
        assertSame(snap1, snap2)
    }

    @Test
    fun `snapshot is rebuilt after update`() {
        val adaptive = AdaptiveLlrBaseline(baseline(), fastConfig)
        val snap1 = adaptive.snapshot()
        // Single quiescent update (lambda=0 < historicalMedian=MAX_VALUE on first call)
        adaptive.updateIfQuiescent(0f, measurements(t = 1_000_000_000L, accelRms = 110f))
        val snap2 = adaptive.snapshot()
        assertNotSame("snapshot should be rebuilt after update", snap1, snap2)
    }

    @Test
    fun `acoustic spectrum sums to 1_0 after update`() {
        val adaptive = AdaptiveLlrBaseline(baseline(), fastConfig)
        // Push a different spectrum in — EWMA should still normalize
        val newSpec = FloatArray(bins) { (it + 1).toFloat() }  // un-normalized
        adaptive.updateIfQuiescent(0f, measurements(t = 1_000_000_000L, spectrum = newSpec))
        val snap = adaptive.snapshot()
        assertEquals("spectrum must normalize to 1.0", 1.0f, snap.acousticSpectrum.sum(), 1e-5f)
    }

    // =========================================================================
    // EWMA convergence
    // =========================================================================

    @Test
    fun `accel mean converges toward new ambient value`() {
        val adaptive = AdaptiveLlrBaseline(baseline(accelMean = 100f), fastConfig)
        val target = 200f
        var t = 1_000_000_000L  // 1 s

        // Feed low-lambda readings (< historical median) for several half-lives
        // Each step is 1 s = 1 half-life, so α ≈ 0.5 per step.
        repeat(20) {
            adaptive.updateIfQuiescent(0f, measurements(t = t, accelRms = target))
            t += 1_000_000_000L
        }

        val snap = adaptive.snapshot()
        // After 20 half-lives of EWMA (many 0.5-α steps), mean should be within 1 mG of target
        assertEquals("accel mean should converge to target", target, snap.accelRmsBaseline, 1f)
    }

    @Test
    fun `hr mean converges toward new resting HR`() {
        val adaptive = AdaptiveLlrBaseline(baseline(hrMean = 70f), fastConfig)
        val target = 55f
        var t = 1_000_000_000L

        repeat(20) {
            adaptive.updateIfQuiescent(0f, measurements(t = t, hrBpm = target))
            t += 1_000_000_000L
        }

        assertEquals("HR mean should converge to target", target, adaptive.snapshot().hrBaselineBpm, 1f)
    }

    @Test
    fun `accel variance stays above floor when signal is constant`() {
        val adaptive = AdaptiveLlrBaseline(baseline(accelVar = 100f), fastConfig)
        var t = 1_000_000_000L

        repeat(30) {
            adaptive.updateIfQuiescent(0f, measurements(t = t, accelRms = 100f))
            t += 1_000_000_000L
        }

        assertTrue(
            "variance must remain above VARIANCE_FLOOR",
            adaptive.snapshot().accelRmsVariance >= 1e-6f,
        )
    }

    @Test
    fun `zero delta time produces no EWMA change`() {
        val base = baseline(accelMean = 100f)
        val adaptive = AdaptiveLlrBaseline(base, fastConfig)
        // Both measurements at the same timestamp (dt = 0 → alpha = 0)
        adaptive.updateIfQuiescent(0f, measurements(t = 0L, accelRms = 999f))
        adaptive.updateIfQuiescent(0f, measurements(t = 0L, accelRms = 999f))
        // Mean should be unchanged since alpha=0 on duplicate timestamps
        assertEquals(100f, adaptive.snapshot().accelRmsBaseline, 1e-4f)
    }

    // =========================================================================
    // Suppression window
    // =========================================================================

    @Test
    fun `suppress blocks EWMA update for specified duration`() {
        val adaptive = AdaptiveLlrBaseline(baseline(accelMean = 100f), fastConfig)
        val fireNanos    = 5_000_000_000L  // fire at t=5s
        val postWindowNs = 60_000_000_000L // 60s suppression

        adaptive.suppress(fireNanos, postWindowNs)

        // Attempt update at t=10s (inside suppression window)
        adaptive.updateIfQuiescent(0f, measurements(t = 10_000_000_000L, accelRms = 999f))
        assertEquals("update inside suppression window should be blocked",
            100f, adaptive.snapshot().accelRmsBaseline, 1e-4f)
    }

    @Test
    fun `suppress expires and updates resume after window`() {
        val adaptive = AdaptiveLlrBaseline(baseline(accelMean = 100f), fastConfig)
        val fireNanos    = 0L
        val postWindowNs = 2_000_000_000L  // 2s suppression

        // Warm up history with a few quiescent readings before suppression
        adaptive.updateIfQuiescent(0f, measurements(t = 0L, accelRms = 100f))

        adaptive.suppress(fireNanos, postWindowNs)

        // Inside window: blocked
        adaptive.updateIfQuiescent(0f, measurements(t = 1_000_000_000L, accelRms = 999f))
        assertEquals(100f, adaptive.snapshot().accelRmsBaseline, 1f)

        // Outside window (t = 3s): should update
        adaptive.updateIfQuiescent(0f, measurements(t = 3_000_000_000L, accelRms = 999f))
        assertTrue("update after suppression window should advance the mean",
            adaptive.snapshot().accelRmsBaseline > 100f)
    }

    // =========================================================================
    // Median gate
    // =========================================================================

    @Test
    fun `first call always updates (empty history → historicalMedian = MAX_VALUE)`() {
        val adaptive = AdaptiveLlrBaseline(baseline(accelMean = 100f), fastConfig)
        // Any finite lambda < MAX_VALUE, so update proceeds on first call
        adaptive.updateIfQuiescent(500f, measurements(t = 1_000_000_000L, accelRms = 200f))
        assertTrue("first call should always update",
            adaptive.snapshot().accelRmsBaseline > 100f)
    }

    @Test
    fun `high lambda above median blocks update`() {
        val adaptive = AdaptiveLlrBaseline(baseline(accelMean = 100f), fastConfig)
        var t = 1_000_000_000L

        // Build median history with lambda=1.0
        repeat(10) {
            adaptive.updateIfQuiescent(1.0f, measurements(t = t, accelRms = 100f))
            t += 1_000_000_000L
        }
        val meanAfterQuiescent = adaptive.snapshot().accelRmsBaseline

        // Now send a high-lambda tick (lambda >> median of ~1.0) — should be blocked
        adaptive.updateIfQuiescent(100f, measurements(t = t, accelRms = 999f))
        assertEquals("update above rolling median should be blocked",
            meanAfterQuiescent, adaptive.snapshot().accelRmsBaseline, 1e-4f)
    }

    @Test
    fun `low lambda below median allows update`() {
        val adaptive = AdaptiveLlrBaseline(baseline(accelMean = 100f), fastConfig)
        var t = 1_000_000_000L

        // Build median history with lambda=10.0
        repeat(10) {
            adaptive.updateIfQuiescent(10f, measurements(t = t, accelRms = 100f))
            t += 1_000_000_000L
        }

        // Now send a low-lambda tick (lambda=0.5 < median ~10) — should proceed
        adaptive.updateIfQuiescent(0.5f, measurements(t = t, accelRms = 200f))
        assertTrue("update below rolling median should be allowed",
            adaptive.snapshot().accelRmsBaseline > 100f)
    }

    // =========================================================================
    // Drift detection
    // =========================================================================

    @Test
    fun `hasDriftedSignificantly false when mean near initial`() {
        val adaptive = AdaptiveLlrBaseline(baseline(accelMean = 100f, accelVar = 100f), fastConfig)
        // No updates; mean is exactly at initial value
        assertFalse(adaptive.hasDriftedSignificantly())
    }

    @Test
    fun `hasDriftedSignificantly true after large accel shift`() {
        // accelVar=100 → σ=10; driftThreshold=2 → must drift by >20 from initial 100
        val adaptive = AdaptiveLlrBaseline(
            baseline(accelMean = 100f, accelVar = 100f),
            fastConfig.copy(driftSigmaThreshold = 2.0f),
        )
        var t = 1_000_000_000L

        // Drive mean toward 200 (100 mG above initial, σ=10, threshold = 20)
        repeat(30) {
            adaptive.updateIfQuiescent(0f, measurements(t = t, accelRms = 200f))
            t += 1_000_000_000L
        }

        assertTrue("significant accel drift should be detected", adaptive.hasDriftedSignificantly())
    }

    // =========================================================================
    // No biometric / no motion paths
    // =========================================================================

    @Test
    fun `baseline without biometric skips hr and rmssd updates`() {
        val base = baseline(hasBio = false)
        val adaptive = AdaptiveLlrBaseline(base, fastConfig)

        adaptive.updateIfQuiescent(0f, measurements(t = 1_000_000_000L, hrBpm = 999f, rmssdMs = 999f))

        // hrMean and rmssdMean should remain at initial values since biometricAvailable=false
        assertEquals(base.hrBaselineBpm, adaptive.snapshot().hrBaselineBpm, 1e-4f)
        assertEquals(base.rmssdBaselineMs, adaptive.snapshot().rmssdBaselineMs, 1e-4f)
    }

    @Test
    fun `baseline without motion skips mad update`() {
        val base = baseline(hasMotion = false)
        val adaptive = AdaptiveLlrBaseline(base, fastConfig)

        adaptive.updateIfQuiescent(0f, measurements(t = 1_000_000_000L, motionMad = 999f))

        assertEquals(base.motionBaselineMad, adaptive.snapshot().motionBaselineMad, 1e-4f)
    }
}
