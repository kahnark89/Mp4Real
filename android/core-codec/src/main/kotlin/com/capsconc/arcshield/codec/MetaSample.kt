package com.capsconc.arcshield.codec

/**
 * A single timed-metadata sample for non-video/audio tracks.
 *
 * [payload] is JSON-UTF-8 in Phase 1 — readable, debuggable, no extra deps.
 * Binary encoding (octet-stream) is a Phase 2 upgrade once API 29 is a
 * safe baseline and corpus volume makes text overhead visible.
 *
 * [presentationTimeNanos] follows the same elapsedRealtimeNanos convention
 * as [EncodedSample].
 */
data class MetaSample(
    val trackType: TrackType,
    val presentationTimeNanos: Long,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MetaSample) return false
        return trackType == other.trackType
            && presentationTimeNanos == other.presentationTimeNanos
            && payload.contentEquals(other.payload)
    }
    override fun hashCode() = payload.contentHashCode() xor presentationTimeNanos.hashCode()
}
