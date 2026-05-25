package com.capsconc.arcshield.codec

import org.junit.Assert.*
import org.junit.Test

class EpsSyncMeasureTest {

    @Test
    fun `zero offset — no low sync confidence`() {
        val r = EpsSyncMeasure.measure(1_000_000_000L, 1_000_000_000L)
        assertEquals(0L, r.offsetNanos)
        assertEquals(0L, r.absOffsetNanos)
        assertFalse(r.lowSyncConfidence)
    }

    @Test
    fun `50ms offset — within target, no flag`() {
        val r = EpsSyncMeasure.measure(1_000_000_000L, 1_050_000_000L)
        assertEquals(50_000_000L, r.offsetNanos)
        assertEquals(50_000_000L, r.absOffsetNanos)
        assertFalse(r.lowSyncConfidence)
    }

    @Test
    fun `exactly 100ms — at target boundary, no flag`() {
        val r = EpsSyncMeasure.measure(0L, EpsSyncMeasure.TARGET_NS)
        assertFalse(r.lowSyncConfidence)
    }

    @Test
    fun `exactly 250ms — at threshold boundary, not yet flagged (strict gt)`() {
        val r = EpsSyncMeasure.measure(0L, EpsSyncMeasure.LOW_SYNC_NS)
        assertFalse(r.lowSyncConfidence)
    }

    @Test
    fun `250ms plus 1ns — exceeds threshold, flagged`() {
        val r = EpsSyncMeasure.measure(0L, EpsSyncMeasure.LOW_SYNC_NS + 1L)
        assertTrue(r.lowSyncConfidence)
    }

    @Test
    fun `300ms offset — low sync confidence set`() {
        val r = EpsSyncMeasure.measure(0L, 300_000_000L)
        assertTrue(r.lowSyncConfidence)
        assertEquals(300_000_000L, r.absOffsetNanos)
    }

    @Test
    fun `negative offset — external clock behind phone`() {
        val r = EpsSyncMeasure.measure(1_000_000_000L, 800_000_000L)
        assertEquals(-200_000_000L, r.offsetNanos)
        assertEquals(200_000_000L, r.absOffsetNanos)
        assertTrue(r.lowSyncConfidence)
    }

    @Test
    fun `combine empty list — returns zero result`() {
        val r = EpsSyncMeasure.combine(emptyList())
        assertEquals(0L, r.offsetNanos)
        assertEquals(0L, r.absOffsetNanos)
        assertFalse(r.lowSyncConfidence)
    }

    @Test
    fun `combine two measurements — averages offsets`() {
        val results = listOf(
            EpsSyncMeasure.measure(0L, 100_000_000L),   // +100ms
            EpsSyncMeasure.measure(0L,  60_000_000L),   // +60ms
        )
        val combined = EpsSyncMeasure.combine(results)
        assertEquals(80_000_000L, combined.offsetNanos)  // mean of 100ms and 60ms
        assertFalse(combined.lowSyncConfidence)
    }

    @Test
    fun `combine large offsets — flags low sync confidence on mean`() {
        val results = listOf(
            EpsSyncMeasure.measure(0L, 300_000_000L),   // +300ms
            EpsSyncMeasure.measure(0L, 400_000_000L),   // +400ms
        )
        val combined = EpsSyncMeasure.combine(results)
        assertEquals(350_000_000L, combined.offsetNanos)
        assertTrue(combined.lowSyncConfidence)
    }
}
