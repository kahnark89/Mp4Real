package com.capsconc.arcshield.labeler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NonMaxSuppressorTest {

    @Test
    fun `empty input returns empty list`() {
        assertEquals(emptyList<Any>(), NonMaxSuppressor.suppress(emptyList()))
    }

    @Test
    fun `single window is returned unchanged`() {
        val w = makeCandidateWindow(1.0f, nanos = 1_000_000_000L)
        assertEquals(listOf(w), NonMaxSuppressor.suppress(listOf(w)))
    }

    @Test
    fun `two windows within suppression window — only highest lambda kept`() {
        val suppress = 60_000_000_000L   // 60 s
        val w1 = makeCandidateWindow(0.3f, nanos = 0L)
        val w2 = makeCandidateWindow(0.8f, nanos = 10_000_000_000L)  // 10 s later
        val result = NonMaxSuppressor.suppress(listOf(w1, w2), suppress)
        assertEquals(1, result.size)
        assertEquals(0.8f, result[0].lambda, 0f)
    }

    @Test
    fun `two windows beyond suppression window — both kept`() {
        val suppress = 60_000_000_000L
        val w1 = makeCandidateWindow(0.5f, nanos = 0L)
        val w2 = makeCandidateWindow(0.7f, nanos = 61_000_000_000L)  // 61 s later
        val result = NonMaxSuppressor.suppress(listOf(w1, w2), suppress)
        assertEquals(2, result.size)
    }

    @Test
    fun `result is in chronological order`() {
        val suppress = 60_000_000_000L
        val w1 = makeCandidateWindow(0.2f, nanos = 0L)
        val w2 = makeCandidateWindow(0.9f, nanos = 61_000_000_000L)
        val w3 = makeCandidateWindow(0.5f, nanos = 122_000_000_000L)
        val result = NonMaxSuppressor.suppress(listOf(w3, w1, w2), suppress)
        assertEquals(3, result.size)
        assertTrue(result[0].detectedAtNanos <= result[1].detectedAtNanos)
        assertTrue(result[1].detectedAtNanos <= result[2].detectedAtNanos)
    }

    @Test
    fun `8-hour shift at 500ms — suppressed to at most 480 events`() {
        val intervalNanos = 500_000_000L    // 500 ms
        val shiftNanos    = 8L * 3600 * 1_000_000_000L
        val ticks         = (shiftNanos / intervalNanos).toInt()   // 57 600

        val windows = (0 until ticks).map { i ->
            makeCandidateWindow(lambda = (i % 100).toFloat() / 100f,
                nanos = i * intervalNanos)
        }

        val result = NonMaxSuppressor.suppress(windows, suppressionNanos = 60_000_000_000L)
        assertTrue("Expected ≤ 480 events, got ${result.size}", result.size <= 480)
    }
}
