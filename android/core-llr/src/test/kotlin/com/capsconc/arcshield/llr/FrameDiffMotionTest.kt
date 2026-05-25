package com.capsconc.arcshield.llr

import com.capsconc.arcshield.llr.internal.FrameDiffMotion
import com.capsconc.arcshield.schema.capture.VideoFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Tests for [FrameDiffMotion]. No Android stubs needed — pure JVM.
 */
class FrameDiffMotionTest {

    private fun frame(w: Int, h: Int, yValue: Byte): VideoFrame {
        // NV21: first w*h bytes are Y-plane, remainder is UV chroma
        val nv21 = ByteArray(w * h * 3 / 2) { if (it < w * h) yValue else 0 }
        return VideoFrame(timestampNanos = 0L, widthPx = w, heightPx = h, yuvData = nv21)
    }

    // ---- First-frame null ---------------------------------------------------

    @Test
    fun `first frame returns null`() {
        val diff = FrameDiffMotion()
        assertNull(diff.update(frame(8, 8, 100)))
    }

    // ---- Zero diff on identical frames -------------------------------------

    @Test
    fun `identical frames produce MAD of zero`() {
        val diff = FrameDiffMotion(subsample = 1)
        diff.update(frame(8, 8, 50))
        val mad = diff.update(frame(8, 8, 50))!!
        assertEquals(0f, mad, 0f)
    }

    // ---- Known diff value --------------------------------------------------

    @Test
    fun `uniform constant diff matches expected MAD`() {
        // Y-plane: 64 bytes (8×8). First frame all 0, second all 100.
        // Every pixel diff = 100 → MAD = 100.
        val diff = FrameDiffMotion(subsample = 1)
        diff.update(frame(8, 8, 0))
        val mad = diff.update(frame(8, 8, 100))!!
        assertEquals(100f, mad, 0.5f)
    }

    @Test
    fun `subsampling does not change uniform diff result`() {
        // Uniform diff: subsample shouldn't affect the mean
        val diff = FrameDiffMotion(subsample = 4)
        diff.update(frame(16, 16, 0))
        val mad = diff.update(frame(16, 16, 80))!!
        assertEquals(80f, mad, 1f)
    }

    // ---- Reset -------------------------------------------------------------

    @Test
    fun `reset makes next frame return null again`() {
        val diff = FrameDiffMotion()
        diff.update(frame(8, 8, 50))
        diff.reset()
        assertNull(diff.update(frame(8, 8, 50)))
    }

    @Test
    fun `after reset second frame gives correct MAD`() {
        val diff = FrameDiffMotion(subsample = 1)
        diff.update(frame(8, 8, 10))
        diff.reset()
        diff.update(frame(8, 8, 30))
        val mad = diff.update(frame(8, 8, 50))!!
        // All pixels went from 30 to 50 → diff = 20
        assertEquals(20f, mad, 0.5f)
    }

    // ---- MAD stays non-negative --------------------------------------------

    @Test
    fun `MAD is non-negative when second frame is darker`() {
        val diff = FrameDiffMotion(subsample = 1)
        diff.update(frame(8, 8, 200.toByte()))
        val mad = diff.update(frame(8, 8, 50))!!
        assertTrue("MAD should be non-negative, got $mad", mad >= 0f)
        // Expected: abs(50 - 200) = 150 (after unsigned interpretation)
        // 200 as unsigned = 200, 50 as unsigned = 50 → diff = 150
        assertEquals(150f, mad, 1f)
    }

    // ---- Unsigned byte handling -------------------------------------------

    @Test
    fun `Y-plane bytes treated as unsigned 0-255`() {
        // Byte 0xFF = -1 signed = 255 unsigned, Byte 0x00 = 0
        // diff should be 255, not -1
        val diff = FrameDiffMotion(subsample = 1)
        diff.update(frame(4, 4, 0xFF.toByte()))
        val mad = diff.update(frame(4, 4, 0x00.toByte()))!!
        assertEquals(255f, mad, 1f)
    }
}
