package com.capsconc.arcshield.codec

/**
 * A single encoded video or audio sample ready for muxing.
 *
 * [presentationTimeNanos] is elapsedRealtimeNanos — the phone monotonic clock.
 * [AndroidMp4RealMuxer] converts to microseconds at the MediaMuxer boundary;
 * no consumer above the muxer layer ever touches µs directly.
 *
 * [isKeyFrame] must be true for HEVC IDR frames; the muxer sets the
 * BUFFER_FLAG_KEY_FRAME flag accordingly.
 */
data class EncodedSample(
    val presentationTimeNanos: Long,
    val data: ByteArray,
    val isKeyFrame: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncodedSample) return false
        return presentationTimeNanos == other.presentationTimeNanos
            && isKeyFrame == other.isKeyFrame
            && data.contentEquals(other.data)
    }
    override fun hashCode() = data.contentHashCode() xor presentationTimeNanos.hashCode()
}
