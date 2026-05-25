package com.capsconc.arcshield.capture

import com.capsconc.arcshield.codec.EncodedSample
import com.capsconc.arcshield.schema.capture.VideoFrame
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

// Encoder interface extracted so CaptureSession can be wired in JVM tests
// without Android SDK (MediaCodec). Production impl: MediaCodecVideoEncoder.
interface VideoEncoderDelegate {
    // Encoded H.265 frames. Active after start().
    val encodedFrames: Flow<EncodedSample>

    // Resolves to the CSD-0 bytes (HEVC VPS/SPS/PPS) once the encoder emits
    // its first onOutputFormatChanged callback. Required before calling
    // Mp4RealWriter.addVideoTrack() with real CSD.
    val csd0: Deferred<ByteArray>

    fun start()

    // Submit a raw frame for encoding. Non-blocking; frames are queued.
    suspend fun encode(frame: VideoFrame)

    fun release()
}
