package com.capsconc.arcshield.capture

import com.capsconc.arcshield.codec.EncodedSample
import org.junit.Assert.*
import org.junit.Test

class ChannelRingBufferTest {

    private fun ring(capacityMs: Long = 5_000L) =
        ChannelRingBuffer<EncodedSample>(capacityMs * 1_000_000L) { it.presentationTimeNanos }

    private fun sample(tsMs: Long, keyFrame: Boolean = false) =
        EncodedSample(tsMs * 1_000_000L, byteArrayOf(tsMs.toByte()), keyFrame)

    // ---- offer / extract ---------------------------------------------------

    @Test
    fun `extract returns samples within the requested window`() {
        val buf = ring()
        buf.offer(sample(1_000L))
        buf.offer(sample(2_000L))
        buf.offer(sample(3_000L))

        val got = buf.extract(1_000_000_000L, 2_000_000_000L)
        assertEquals(2, got.size)
        assertEquals(1_000_000_000L, got[0].presentationTimeNanos)
        assertEquals(2_000_000_000L, got[1].presentationTimeNanos)
    }

    @Test
    fun `extract returns empty list when no samples in range`() {
        val buf = ring()
        buf.offer(sample(1_000L))

        assertTrue(buf.extract(5_000_000_000L, 6_000_000_000L).isEmpty())
    }

    @Test
    fun `extract is inclusive on both endpoints`() {
        val buf = ring()
        buf.offer(sample(1_000L))
        buf.offer(sample(3_000L))

        val got = buf.extract(1_000_000_000L, 3_000_000_000L)
        assertEquals(2, got.size)
    }

    // ---- capacity eviction -------------------------------------------------

    @Test
    fun `old samples are evicted when capacity is exceeded`() {
        val buf = ring(capacityMs = 2_000L)  // 2-second window
        buf.offer(sample(1_000L))
        buf.offer(sample(2_000L))
        buf.offer(sample(3_000L))
        buf.offer(sample(4_000L))  // ts=1s is now older than (4s − 2s = 2s)

        val got = buf.extract(0L, Long.MAX_VALUE)
        assertFalse("ts=1s should have been evicted", got.any { it.presentationTimeNanos == 1_000_000_000L })
        assertEquals(3, got.size)  // 2s, 3s, 4s remain
    }

    @Test
    fun `single sample in buffer is never evicted alone`() {
        val buf = ring(capacityMs = 100L)
        buf.offer(sample(99_999L))  // far older than capacity

        assertEquals(1, buf.size)
    }

    // ---- ordering ----------------------------------------------------------

    @Test
    fun `extract returns samples sorted by timestamp regardless of offer order`() {
        val buf = ring()
        buf.offer(sample(3_000L))
        buf.offer(sample(1_000L))
        buf.offer(sample(2_000L))

        val got = buf.extract(0L, Long.MAX_VALUE)
        assertEquals(3, got.size)
        assertTrue("Expected ascending order",
            got.zipWithNext().all { (a, b) -> a.presentationTimeNanos <= b.presentationTimeNanos })
    }

    // ---- size / clear ------------------------------------------------------

    @Test
    fun `size reflects current sample count`() {
        val buf = ring()
        assertEquals(0, buf.size)
        buf.offer(sample(1_000L))
        buf.offer(sample(2_000L))
        assertEquals(2, buf.size)
    }

    @Test
    fun `clear removes all samples`() {
        val buf = ring()
        buf.offer(sample(1_000L))
        buf.clear()
        assertEquals(0, buf.size)
        assertTrue(buf.extract(0L, Long.MAX_VALUE).isEmpty())
    }
}
