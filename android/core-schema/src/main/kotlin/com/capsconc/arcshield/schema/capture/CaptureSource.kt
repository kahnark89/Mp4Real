package com.capsconc.arcshield.schema.capture

import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// Audio
// ---------------------------------------------------------------------------

/**
 * One buffer of raw PCM audio. sampleRateHz is always 48 000 for the phone mic.
 * channelCount is 1 (mono). Timestamps are elapsedRealtimeNanos.
 */
data class AudioFrame(
    val timestampNanos: Long,
    val samples:        ShortArray,
    val sampleRateHz:   Int,
    val channelCount:   Int,
) {
    // ShortArray doesn't implement structural equality — provide it manually.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFrame) return false
        return timestampNanos == other.timestampNanos
            && sampleRateHz   == other.sampleRateHz
            && channelCount   == other.channelCount
            && samples.contentEquals(other.samples)
    }
    override fun hashCode(): Int = samples.contentHashCode() xor timestampNanos.hashCode()
}

// ---------------------------------------------------------------------------
// Video
// ---------------------------------------------------------------------------

/**
 * One video frame in NV21 (YUV 4:2:0 semi-planar) format.
 * Full codec/format detail lives in source-camerax; this type is the
 * minimal shared contract for consumers that only need timestamps and size.
 */
data class VideoFrame(
    val timestampNanos: Long,
    val widthPx:        Int,
    val heightPx:       Int,
    val yuvData:        ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VideoFrame) return false
        return timestampNanos == other.timestampNanos
            && widthPx  == other.widthPx
            && heightPx == other.heightPx
            && yuvData.contentEquals(other.yuvData)
    }
    override fun hashCode(): Int = yuvData.contentHashCode() xor timestampNanos.hashCode()
}

// ---------------------------------------------------------------------------
// CaptureSource interface — CLAUDE.md §9
// ---------------------------------------------------------------------------

interface CaptureSource {
    fun videoFrames(): Flow<VideoFrame>
    fun audioFrames(): Flow<AudioFrame>
    val sourceId: String   // "phone_cameraX_v1" | "meta_raybans_v1"
}

interface CaptureSourceFactory {
    fun create(lifecycleOwner: LifecycleOwner): CaptureSource
}
