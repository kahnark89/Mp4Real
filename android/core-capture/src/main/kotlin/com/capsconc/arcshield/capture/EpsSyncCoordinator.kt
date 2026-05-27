package com.capsconc.arcshield.capture

import com.capsconc.arcshield.codec.EpsSyncMeasure

// Manages the NTP-style ε_sync measurement schedule per CLAUDE.md §3.2.
//
// At session start and every [syncIntervalMs], record a sync point by
// calling recordSyncPoint(phoneNanos, externalNanos). The coordinator
// combines all measurements and exposes the current offset for the
// session sidecar.
//
// Thread-safe: all state is guarded by synchronized(this).
class EpsSyncCoordinator(
    private val syncIntervalMs: Long = 300_000L,
    private val clock: () -> Long = System::nanoTime,
) {
    private val measurements = mutableListOf<EpsSyncMeasure.SyncResult>()
    private var lastSyncNanos: Long = 0L
    private var hasSynced: Boolean = false

    // Record a clock pair from a single handshake round-trip.
    // phoneNanos: elapsedRealtimeNanos on the phone side.
    // externalNanos: timestamp from the external sensor (Polar PMD, NTP, etc.)
    //   converted to the same monotonic epoch.
    fun recordSyncPoint(phoneNanos: Long, externalNanos: Long) {
        val result = EpsSyncMeasure.measure(phoneNanos, externalNanos)
        synchronized(this) {
            measurements.add(result)
            lastSyncNanos = phoneNanos
            hasSynced = true
        }
    }

    // Combined ε_sync from all recorded measurements. Zero result when no
    // sync point has been recorded yet — caller should treat that as "unknown".
    fun currentSync(): EpsSyncMeasure.SyncResult =
        synchronized(this) { EpsSyncMeasure.combine(measurements) }

    // True when no sync has been recorded yet, or the last sync is older
    // than syncIntervalMs. Call from the Polar reconnect handler and
    // from the periodic re-sync timer in CaptureSession.
    fun isSyncDue(): Boolean {
        val now = clock()
        return synchronized(this) {
            !hasSynced || (now - lastSyncNanos) >= syncIntervalMs * 1_000_000L
        }
    }

    fun reset() {
        synchronized(this) {
            measurements.clear()
            lastSyncNanos = 0L
            hasSynced = false
        }
    }
}
