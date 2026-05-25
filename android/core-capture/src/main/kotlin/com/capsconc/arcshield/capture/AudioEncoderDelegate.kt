package com.capsconc.arcshield.capture

import com.capsconc.arcshield.codec.EncodedSample
import com.capsconc.arcshield.schema.capture.AudioFrame
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

// Encoder interface extracted for JVM testability. Production impl: MediaCodecAudioEncoder.
interface AudioEncoderDelegate {
    // Encoded AAC frames. Active after start().
    val encodedFrames: Flow<EncodedSample>

    // CSD-0 bytes (AAC AudioSpecificConfig). Required before calling
    // Mp4RealWriter.addAudioTrack() with real CSD.
    val csd0: Deferred<ByteArray>

    fun start()

    // Submit a raw PCM buffer for encoding. Non-blocking; frames are queued.
    suspend fun encode(frame: AudioFrame)

    fun release()
}
