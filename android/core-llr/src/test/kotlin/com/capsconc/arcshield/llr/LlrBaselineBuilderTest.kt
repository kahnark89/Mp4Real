package com.capsconc.arcshield.llr

import com.capsconc.arcshield.schema.biometric.AccelSample
import com.capsconc.arcshield.schema.biometric.HrSample
import com.capsconc.arcshield.schema.biometric.RrSample
import com.capsconc.arcshield.schema.capture.AudioFrame
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [buildBaseline]. Uses finite flows so the coroutineScope exits
 * naturally without advancing virtual time, and injects a clock lambda
 * (returns 0) to avoid Android SystemClock stubs.
 */
class LlrBaselineBuilderTest {

    private val testClock: () -> Long = { 0L }

    // ---- Helpers ---------------------------------------------------------------

    private fun audio(nanos: Long, value: Short = 100): AudioFrame =
        AudioFrame(nanos, ShortArray(1024) { value }, 48_000, 1)

    private fun accel(nanos: Long, x: Float = 100f) =
        AccelSample(nanos, x, 0f, 0f)

    private fun hr(nanos: Long, bpm: Int) = HrSample(nanos, bpm)
    private fun rr(nanos: Long, rrMs: Int) = RrSample(nanos, rrMs)

    // ---- Acoustic spectrum -----------------------------------------------------

    @Test
    fun `acoustic spectrum sums to approximately 1`() = runTest {
        val t = 1_000_000_000L
        val frames = flowOf(audio(t), audio(2 * t), audio(3 * t))
        val baseline = buildBaseline(
            audioFrames  = frames,
            accelSamples = emptyFlow(),
            clock        = testClock,
        )
        val sum = baseline.acousticSpectrum.sum()
        assertEquals("spectrum should sum to ~1.0", 1.0f, sum, 0.01f)
    }

    @Test
    fun `uniform fallback spectrum when no audio frames received`() = runTest {
        val baseline = buildBaseline(
            audioFrames  = emptyFlow(),
            accelSamples = emptyFlow(),
            clock        = testClock,
        )
        val nBins   = 1024 / 2 + 1
        val expected = 1f / nBins
        // All bins should equal 1/nBins in the fallback
        for (k in 0 until nBins) {
            assertEquals(expected, baseline.acousticSpectrum[k], 1e-6f)
        }
    }

    @Test
    fun `acoustic spectrum variance array matches spectrum size`() = runTest {
        val t = 1_000_000_000L
        val frames = flowOf(audio(t), audio(2 * t))
        val baseline = buildBaseline(
            audioFrames  = frames,
            accelSamples = emptyFlow(),
            clock        = testClock,
        )
        assertEquals(baseline.acousticSpectrum.size, baseline.acousticSpectrumVariance.size)
        // All variances should be floored to at least 1e-6
        for (v in baseline.acousticSpectrumVariance) {
            assertTrue("variance should be >= 1e-6, got $v", v >= 1e-6f)
        }
    }

    // ---- Accel baseline --------------------------------------------------------

    @Test
    fun `accel RMS baseline is non-zero from stable input`() = runTest {
        val t = 10_000_000L   // 10 ms spacing (100 Hz)
        val samples = (0 until 10).map { i -> accel(i * t, 100f) }
        val baseline = buildBaseline(
            audioFrames  = emptyFlow(),
            accelSamples = flowOf(*samples.toTypedArray()),
            clock        = testClock,
        )
        // 100 mG on X axis → magnitude = 100 mG → rolling RMS ≈ 100
        assertTrue("accel baseline should be non-zero", baseline.accelRmsBaseline > 0f)
    }

    @Test
    fun `accel RMS variance is floored when all samples are identical`() = runTest {
        val t = 10_000_000L
        val samples = (0 until 5).map { i -> accel(i * t, 50f) }
        val baseline = buildBaseline(
            audioFrames  = emptyFlow(),
            accelSamples = flowOf(*samples.toTypedArray()),
            clock        = testClock,
        )
        assertTrue("variance floor >= 1e-6", baseline.accelRmsVariance >= 1e-6f)
    }

    // ---- HR baseline -----------------------------------------------------------

    @Test
    fun `HR mean is correct from constant HR stream`() = runTest {
        val t = 1_000_000_000L
        val samples = (0..4).map { i -> hr(i * t, 72) }
        val baseline = buildBaseline(
            audioFrames  = emptyFlow(),
            accelSamples = emptyFlow(),
            hrSamples    = flowOf(*samples.toTypedArray()),
            clock        = testClock,
        )
        assertEquals(72f, baseline.hrBaselineBpm, 0.01f)
        assertTrue(baseline.biometricAvailable)
    }

    @Test
    fun `HR mean is average of varying samples`() = runTest {
        val t = 1_000_000_000L
        val baseline = buildBaseline(
            audioFrames  = emptyFlow(),
            accelSamples = emptyFlow(),
            hrSamples    = flowOf(hr(0L, 60), hr(t, 80), hr(2 * t, 100)),
            clock        = testClock,
        )
        // (60+80+100)/3 = 80
        assertEquals(80f, baseline.hrBaselineBpm, 0.01f)
    }

    @Test
    fun `HR variance is positive for varying HR samples`() = runTest {
        val t = 1_000_000_000L
        val baseline = buildBaseline(
            audioFrames  = emptyFlow(),
            accelSamples = emptyFlow(),
            hrSamples    = flowOf(hr(0L, 60), hr(t, 80), hr(2 * t, 100)),
            clock        = testClock,
        )
        assertTrue("HR variance should be positive", baseline.hrVarianceBpm > 1e-6f)
    }

    @Test
    fun `biometricAvailable is false when HR flow is empty`() = runTest {
        val baseline = buildBaseline(
            audioFrames  = emptyFlow(),
            accelSamples = emptyFlow(),
            hrSamples    = emptyFlow(),
            clock        = testClock,
        )
        assertFalse(baseline.biometricAvailable)
        assertEquals(0f, baseline.hrBaselineBpm, 0f)
        assertEquals(1e-6f, baseline.hrVarianceBpm, 0f)
    }

    // ---- RMSSD baseline --------------------------------------------------------

    @Test
    fun `RMSSD baseline is zero when fewer than 2 RR samples`() = runTest {
        val baseline = buildBaseline(
            audioFrames  = emptyFlow(),
            accelSamples = emptyFlow(),
            rrSamples    = flowOf(rr(0L, 800)),
            clock        = testClock,
        )
        assertEquals(0f, baseline.rmssdBaselineMs, 0f)
    }

    @Test
    fun `RMSSD baseline is zero for stable RR intervals`() = runTest {
        val t = 800_000_000L
        val samples = (0 until 5).map { i -> rr(i * t, 800) }
        val baseline = buildBaseline(
            audioFrames  = emptyFlow(),
            accelSamples = emptyFlow(),
            rrSamples    = flowOf(*samples.toTypedArray()),
            clock        = testClock,
        )
        assertEquals(0f, baseline.rmssdBaselineMs, 0.01f)
    }

    @Test
    fun `RMSSD baseline is correct for alternating RR pattern`() = runTest {
        // Alternating 900 / 700 ms → all successive diffs = ±200 → RMSSD = 200
        val t = 900_000_000L
        val rrs = listOf(900, 700, 900, 700, 900, 700).mapIndexed { i, v -> rr(i * t, v) }
        val baseline = buildBaseline(
            audioFrames  = emptyFlow(),
            accelSamples = emptyFlow(),
            rrSamples    = flowOf(*rrs.toTypedArray()),
            clock        = testClock,
        )
        assertEquals(200f, baseline.rmssdBaselineMs, 1f)
    }

    @Test
    fun `RMSSD variance is floored when too few sub-windows exist`() = runTest {
        // Only 3 RR intervals → fewer than 20-beat window → single variance estimate
        val t = 900_000_000L
        val baseline = buildBaseline(
            audioFrames  = emptyFlow(),
            accelSamples = emptyFlow(),
            rrSamples    = flowOf(rr(0L, 800), rr(t, 820), rr(2 * t, 800)),
            clock        = testClock,
        )
        assertTrue("variance floor >= 1e-6", baseline.rmssdVarianceMs >= 1e-6f)
    }

    // ---- All-channels combined -------------------------------------------------

    @Test
    fun `all channels collected simultaneously`() = runTest {
        val t = 1_000_000_000L
        val baseline = buildBaseline(
            audioFrames  = flowOf(audio(t)),
            accelSamples = flowOf(accel(t, 80f)),
            hrSamples    = flowOf(hr(t, 75)),
            rrSamples    = flowOf(rr(0L, 800), rr(t, 820)),
            clock        = testClock,
        )
        assertTrue(baseline.biometricAvailable)
        assertEquals(75f, baseline.hrBaselineBpm, 0.01f)
        assertTrue(baseline.accelRmsBaseline > 0f)
        assertEquals(1.0f, baseline.acousticSpectrum.sum(), 0.01f)
    }
}
