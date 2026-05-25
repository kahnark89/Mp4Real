package com.capsconc.arcshield.llr

import com.capsconc.arcshield.llr.internal.RollingBioStats
import com.capsconc.arcshield.schema.biometric.HrSample
import com.capsconc.arcshield.schema.biometric.RrSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class RollingBioStatsTest {

    private fun hrAt(nanos: Long, bpm: Int) = HrSample(nanos, bpm)
    private fun rrAt(nanos: Long, rrMs: Int) = RrSample(nanos, rrMs)

    // -----------------------------------------------------------------------
    // HR stats
    // -----------------------------------------------------------------------

    @Test
    fun `single HR sample — mean equals that sample, hasHr is true`() {
        val stats = RollingBioStats()
        val snap = stats.updateHr(hrAt(0L, 72))
        assertEquals(72f, snap.hrMeanBpm, 0.01f)
        assertTrue(snap.hasHr)
    }

    @Test
    fun `HR mean is correct over multiple samples`() {
        val stats = RollingBioStats()
        val t = 1_000_000_000L
        stats.updateHr(hrAt(0L,    60))
        stats.updateHr(hrAt(t,     80))
        val snap = stats.updateHr(hrAt(2 * t, 100))
        assertEquals(80f, snap.hrMeanBpm, 0.01f)
    }

    @Test
    fun `HR variance is positive with varying samples`() {
        val stats = RollingBioStats()
        val t = 1_000_000_000L
        stats.updateHr(hrAt(0L, 60))
        val snap = stats.updateHr(hrAt(t, 90))
        assertTrue("variance should be positive", snap.hrVarianceBpm > 0f)
    }

    @Test
    fun `HR variance floored to 1e-6 for single sample`() {
        val stats = RollingBioStats()
        val snap = stats.updateHr(hrAt(0L, 75))
        assertEquals(1e-6f, snap.hrVarianceBpm, 0f)
    }

    @Test
    fun `HR samples outside window are evicted`() {
        val windowMs = 60_000L   // 1-minute window for test
        val stats = RollingBioStats(windowMs)

        val windowNanos = windowMs * 1_000_000L

        // Old sample at t=0 with anomalous HR
        stats.updateHr(hrAt(0L, 200))

        // Quiet samples well past the window
        val baseNanos = windowNanos * 2
        val t = 5_000_000_000L   // 5s spacing
        stats.updateHr(hrAt(baseNanos,         70))
        stats.updateHr(hrAt(baseNanos + t,      72))
        val snap = stats.updateHr(hrAt(baseNanos + 2 * t, 74))

        // The 200 bpm spike should have been evicted
        assertTrue("mean should be near 72, got ${snap.hrMeanBpm}", snap.hrMeanBpm < 100f)
    }

    @Test
    fun `empty bio stats returns hasHr=false hasRr=false`() {
        val stats = RollingBioStats()
        val snap = stats.currentSnapshot()
        assertFalse(snap.hasHr)
        assertFalse(snap.hasRr)
        assertEquals(0f, snap.hrMeanBpm, 0f)
        assertEquals(0f, snap.rmssdMs, 0f)
    }

    // -----------------------------------------------------------------------
    // RMSSD
    // -----------------------------------------------------------------------

    @Test
    fun `RMSSD is zero with fewer than 2 RR samples`() {
        val stats = RollingBioStats()
        val snap = stats.updateRr(rrAt(0L, 800))
        assertFalse(snap.hasRr)
        assertEquals(0f, snap.rmssdMs, 0f)
    }

    @Test
    fun `RMSSD of identical RR intervals is zero`() {
        val stats = RollingBioStats()
        val t = 800_000_000L   // 800 ms spacing
        repeat(5) { i ->
            stats.updateRr(rrAt(i * t, 800))
        }
        val snap = stats.currentSnapshot()
        assertTrue(snap.hasRr)
        assertEquals(0f, snap.rmssdMs, 0.01f)
    }

    @Test
    fun `RMSSD is correct for known alternating RR pattern`() {
        // Alternating 900ms / 700ms → successive diffs all 200ms
        // RMSSD = sqrt(mean(200^2)) = 200
        val stats = RollingBioStats()
        val t = 900_000_000L
        val intervals = listOf(900, 700, 900, 700, 900)
        intervals.forEachIndexed { i, rr -> stats.updateRr(rrAt(i.toLong() * t, rr)) }
        val snap = stats.currentSnapshot()
        assertEquals(200f, snap.rmssdMs, 1f)
    }

    @Test
    fun `RMSSD calculation matches manual formula`() {
        // RR = [800, 820, 790, 810] → diffs = [20, -30, 20] → sq = [400, 900, 400]
        // RMSSD = sqrt((400+900+400)/3) = sqrt(566.67) ≈ 23.8
        val expected = sqrt((400.0 + 900.0 + 400.0) / 3.0).toFloat()
        val stats = RollingBioStats()
        val t = 900_000_000L
        listOf(800, 820, 790, 810).forEachIndexed { i, rr ->
            stats.updateRr(rrAt(i.toLong() * t, rr))
        }
        val snap = stats.currentSnapshot()
        assertEquals(expected, snap.rmssdMs, 0.1f)
    }

    @Test
    fun `RR samples outside window are evicted, RMSSD recomputes over remaining samples`() {
        val windowMs = 30_000L
        val stats = RollingBioStats(windowMs)
        val windowNanos = windowMs * 1_000_000L

        // Old volatile RR samples at t=0 — alternating 200ms apart → high RMSSD
        val tOld = 1_000_000_000L
        listOf(500, 900, 500, 900).forEachIndexed { i, rr ->
            stats.updateRr(rrAt(i.toLong() * tOld, rr))
        }

        // New stable samples well past the window
        val base = windowNanos * 2
        val tNew = 900_000_000L
        listOf(800, 800, 800, 800).forEachIndexed { i, rr ->
            stats.updateRr(rrAt(base + i.toLong() * tNew, rr))
        }

        val snap = stats.currentSnapshot()
        // Old high-variance samples evicted; RMSSD over stable 800ms intervals should be 0
        assertEquals(0f, snap.rmssdMs, 0.5f)
    }

    // -----------------------------------------------------------------------
    // Reset
    // -----------------------------------------------------------------------

    @Test
    fun `reset clears all buffers`() {
        val stats = RollingBioStats()
        stats.updateHr(hrAt(0L, 72))
        stats.updateRr(rrAt(0L, 800))
        stats.reset()
        val snap = stats.currentSnapshot()
        assertFalse(snap.hasHr)
        assertFalse(snap.hasRr)
    }
}
