package com.capsconc.arcshield.capture

import org.junit.Assert.*
import org.junit.Test

class EpsSyncCoordinatorTest {

    @Test
    fun `no sync points recorded — currentSync returns zero result`() {
        val coord = EpsSyncCoordinator()
        val sync = coord.currentSync()
        assertEquals(0L, sync.offsetNanos)
        assertEquals(0L, sync.absOffsetNanos)
        assertFalse(sync.lowSyncConfidence)
    }

    @Test
    fun `recordSyncPoint captures offset correctly`() {
        val coord = EpsSyncCoordinator()
        coord.recordSyncPoint(phoneNanos = 1_000_000_000L, externalNanos = 1_050_000_000L)
        val sync = coord.currentSync()
        assertEquals(50_000_000L, sync.offsetNanos)
        assertEquals(50_000_000L, sync.absOffsetNanos)
        assertFalse(sync.lowSyncConfidence)
    }

    @Test
    fun `multiple sync points are combined by average`() {
        val coord = EpsSyncCoordinator()
        coord.recordSyncPoint(0L, 100_000_000L)    // +100ms
        coord.recordSyncPoint(0L,  60_000_000L)    // +60ms
        val sync = coord.currentSync()
        // Mean = (100ms + 60ms) / 2 = 80ms
        assertEquals(80_000_000L, sync.offsetNanos)
        assertFalse(sync.lowSyncConfidence)
    }

    @Test
    fun `isSyncDue is true when no sync has been recorded`() {
        val coord = EpsSyncCoordinator(syncIntervalMs = 300_000L)
        assertTrue(coord.isSyncDue())
    }

    @Test
    fun `isSyncDue is false just after a sync point`() {
        var fakeNanos = 0L
        val coord = EpsSyncCoordinator(syncIntervalMs = 300_000L, clock = { fakeNanos })
        coord.recordSyncPoint(fakeNanos, fakeNanos)
        fakeNanos += 100_000_000_000L  // +100 seconds (well within 5-minute interval)
        assertFalse(coord.isSyncDue())
    }

    @Test
    fun `isSyncDue becomes true after sync interval elapses`() {
        var fakeNanos = 0L
        val coord = EpsSyncCoordinator(syncIntervalMs = 300_000L, clock = { fakeNanos })
        coord.recordSyncPoint(fakeNanos, fakeNanos)
        fakeNanos += 400_000_000_000L  // +400 seconds > 5-minute interval
        assertTrue(coord.isSyncDue())
    }

    @Test
    fun `reset clears all measurements and resets sync due state`() {
        val coord = EpsSyncCoordinator()
        coord.recordSyncPoint(0L, 200_000_000L)   // 200ms offset
        coord.reset()

        val sync = coord.currentSync()
        assertEquals(0L, sync.offsetNanos)
        assertTrue(coord.isSyncDue())
    }

    @Test
    fun `large offset flags low sync confidence`() {
        val coord = EpsSyncCoordinator()
        coord.recordSyncPoint(0L, 300_000_000L)   // 300ms > 250ms threshold
        val sync = coord.currentSync()
        assertTrue(sync.lowSyncConfidence)
        assertEquals(300_000_000L, sync.absOffsetNanos)
    }
}
