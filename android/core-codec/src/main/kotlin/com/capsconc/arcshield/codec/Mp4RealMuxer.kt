package com.capsconc.arcshield.codec

/**
 * Interface over Android's MediaMuxer, extracted so [Mp4RealWriter] is testable
 * on the JVM without Android stubs. Production code uses [AndroidMp4RealMuxer].
 * Tests use a fake/stub implementation.
 *
 * All timestamp parameters are elapsedRealtimeNanos. Conversion to microseconds
 * for MediaMuxer.writeSampleData is the responsibility of the implementing class.
 *
 * Track registration must complete before [start] is called — this mirrors the
 * MediaMuxer contract exactly.
 */
interface Mp4RealMuxer {

    /**
     * Registers a video track (HEVC / H.265).
     * [csd0] is the codec-specific data buffer (SPS/PPS); pass an empty array
     * if the encoder has not yet emitted it (it can be written as a sample later).
     * Returns the muxer track index for subsequent [writeSample] calls.
     */
    fun addVideoTrack(
        width: Int,
        height: Int,
        frameRate: Int = 30,
        csd0: ByteArray = ByteArray(0),
    ): Int

    /**
     * Registers an audio track (AAC-LC).
     * [csd0] is the AudioSpecificConfig buffer. Returns the muxer track index.
     */
    fun addAudioTrack(
        sampleRate: Int = 48_000,
        channelCount: Int = 1,
        csd0: ByteArray = ByteArray(0),
    ): Int

    /**
     * Registers a timed-metadata track. Phase 1 uses JSON-UTF-8 payloads written
     * as text/vtt — readable, debuggable, API 26+ safe. Returns the muxer track index.
     */
    fun addMetaTrack(trackType: TrackType): Int

    /** Opens the muxer. All tracks must be registered before this call. */
    fun start()

    /**
     * Writes one encoded video or audio sample.
     * [presentationTimeNanos] is elapsedRealtimeNanos.
     */
    fun writeSample(
        trackIndex: Int,
        presentationTimeNanos: Long,
        data: ByteArray,
        isKeyFrame: Boolean = false,
    )

    /**
     * Writes one timed-metadata sample (JSON payload) to a meta track.
     * [presentationTimeNanos] is elapsedRealtimeNanos.
     */
    fun writeMetaSample(
        trackIndex: Int,
        presentationTimeNanos: Long,
        payload: ByteArray,
    )

    /** Finalises the container. Must be called before [release]. */
    fun stop()

    /** Releases native resources. Must be called after [stop]. */
    fun release()
}
