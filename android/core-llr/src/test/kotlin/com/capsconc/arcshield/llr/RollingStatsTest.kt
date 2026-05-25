package com.capsconc.arcshield.llr

import com.capsconc.arcshield.llr.internal.RollingAccelRms
import com.capsconc.arcshield.schema.biometric.AccelSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class RollingStatsTest {

    private fun sample(nanos: Long, x: Float, y: Float = 0f, z: Float = 0f) =
        AccelSample(nanos, x, y, z)

    @Test
    fun `single sample RMS equals its magnitude`() {
        val rms = RollingAccelRms(5_000)
        val snap = rms.update(sample(0L, 3f, 4f, 0f))   // magnitude = 5
        assertEquals(5f, snap.rms, 0.01f)
    }

    @Test
    fun `stable stream produces stable RMS`() {
        val rms    = RollingAccelRms(5_000)
        val tNanos = 1_000_000_000L   // 1 second
        var last   = RollingAccelRms.RmsSnapshot(0f, 0f)
        for (i in 0 until 20) {
            last = rms.update(sample(i * tNanos, 100f, 0f, 0f))
        }
        assertEquals(100f, last.rms, 1f)
    }

    @Test
    fun `samples outside window are evicted`() {
        val windowMs = 2_000L
        val rms      = RollingAccelRms(windowMs)

        // Add an old high-value sample well outside the window
        rms.update(sample(0L, 1000f))

        // Add recent quiet samples starting 5 seconds later
        val baseNanos = 5_000_000_000L   // 5 s in nanos
        var last = RollingAccelRms.RmsSnapshot(0f, 0f)
        for (i in 0 until 10) {
            last = rms.update(sample(baseNanos + i * 200_000_000L, 1f))
        }

        // Old spike should be outside the 2 s window; RMS should be near 1
        assertTrue("RMS after eviction should be near 1, got ${last.rms}", last.rms < 5f)
    }

    @Test
    fun `variance is non-negative`() {
        val rms = RollingAccelRms(5_000)
        for (i in 0 until 10) {
            val snap = rms.update(sample(i * 100_000_000L, i.toFloat()))
            assertTrue(snap.variance >= 0f)
        }
    }
}
