package com.capsconc.arcshield.codec

import kotlin.math.abs

/**
 * ε_sync measurement — CLAUDE.md §3.2.
 *
 * Target: ε_sync ≤ 100 ms.
 * Hard threshold: > 250 ms → lowSyncConfidence flag → surrounding window is
 * invalid for primitive matching but still enters the corpus.
 *
 * The phone monotonic clock (elapsedRealtimeNanos) anchors all tracks.
 * Polar PMD is anchored at first packet receipt and advanced by sample index.
 * Results are written to the .mp4real.json sidecar on container close.
 */
object EpsSyncMeasure {

    /** Target maximum ε_sync — CLAUDE.md §3.2. */
    const val TARGET_NS: Long = 100_000_000L      // 100 ms

    /** Threshold above which lowSyncConfidence = true — CLAUDE.md §3.2. */
    const val LOW_SYNC_NS: Long = 250_000_000L    // 250 ms

    data class SyncResult(
        val offsetNanos: Long,
        val absOffsetNanos: Long,
        /** True when absOffsetNanos > [LOW_SYNC_NS]. */
        val lowSyncConfidence: Boolean,
    )

    /**
     * Computes the signed offset between the phone monotonic clock and an
     * external device clock at the same instant. Both timestamps must be
     * expressed as elapsedRealtimeNanos-equivalent values (the external
     * device's is offset-corrected at first packet receipt).
     */
    fun measure(phoneNanos: Long, externalNanos: Long): SyncResult {
        val offset = externalNanos - phoneNanos
        val absOffset = abs(offset)
        return SyncResult(
            offsetNanos       = offset,
            absOffsetNanos    = absOffset,
            lowSyncConfidence = absOffset > LOW_SYNC_NS,
        )
    }

    /**
     * Combines multiple sync measurements (e.g., repeated NTP-style handshakes,
     * or measurements from multiple external devices) into a single result.
     * Uses the mean offset; lowSyncConfidence reflects the combined mean.
     */
    fun combine(results: List<SyncResult>): SyncResult {
        if (results.isEmpty()) return SyncResult(0L, 0L, false)
        val avgOffset = results.sumOf { it.offsetNanos } / results.size
        val absOffset = abs(avgOffset)
        return SyncResult(
            offsetNanos       = avgOffset,
            absOffsetNanos    = absOffset,
            lowSyncConfidence = absOffset > LOW_SYNC_NS,
        )
    }
}
