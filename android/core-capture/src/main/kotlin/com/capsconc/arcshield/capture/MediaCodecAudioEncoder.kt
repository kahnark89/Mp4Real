package com.capsconc.arcshield.capture

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaFormat.MIMETYPE_AUDIO_AAC
import com.capsconc.arcshield.codec.EncodedSample
import com.capsconc.arcshield.schema.capture.AudioFrame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.nio.ByteOrder

// AAC encoder backed by the platform MediaCodec async callback API.
// Input: 16-bit PCM mono (ShortArray from AudioRecord).
// Output: Flow<EncodedSample> of raw AAC ADTS frames.
class MediaCodecAudioEncoder(
    private val sampleRateHz: Int = 48_000,
    private val channelCount: Int = 1,
    private val bitrateBps: Int = 128_000,
) : AudioEncoderDelegate {

    private lateinit var codec: MediaCodec
    private val _encodedFrames = Channel<EncodedSample>(Channel.UNLIMITED)
    private val _csd0 = CompletableDeferred<ByteArray>()
    private val pendingFrames = Channel<AudioFrame>(capacity = 16)

    override val encodedFrames: Flow<EncodedSample> = _encodedFrames.receiveAsFlow()
    override val csd0: Deferred<ByteArray> = _csd0

    override fun start() {
        val format = MediaFormat.createAudioFormat(MIMETYPE_AUDIO_AAC, sampleRateHz, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
        }

        codec = MediaCodec.createEncoderByType(MIMETYPE_AUDIO_AAC)
        codec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                val frame = pendingFrames.tryReceive().getOrNull() ?: return
                val buf = codec.getInputBuffer(index) ?: return
                buf.clear()
                // Convert ShortArray PCM to little-endian byte array
                val pcmBytes = ByteArray(frame.samples.size * 2)
                val bbuf = java.nio.ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
                frame.samples.forEach { bbuf.putShort(it) }
                buf.put(pcmBytes)
                codec.queueInputBuffer(
                    index, 0, pcmBytes.size,
                    frame.timestampNanos / 1_000L, 0,
                )
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo,
            ) {
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    codec.releaseOutputBuffer(index, false)
                    return
                }
                val buf = codec.getOutputBuffer(index)
                if (buf == null) { codec.releaseOutputBuffer(index, false); return }
                val data = ByteArray(info.size)
                buf.position(info.offset)
                buf.get(data)
                _encodedFrames.trySend(
                    EncodedSample(
                        presentationTimeNanos = info.presentationTimeUs * 1_000L,
                        data                 = data,
                        isKeyFrame           = false,
                    )
                )
                codec.releaseOutputBuffer(index, false)
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                _encodedFrames.close(e)
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                val raw = format.getByteBuffer("csd-0")
                val csd = if (raw != null) ByteArray(raw.remaining()).also { raw.get(it) }
                          else ByteArray(0)
                _csd0.complete(csd)
            }
        })

        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    override suspend fun encode(frame: AudioFrame) {
        pendingFrames.send(frame)
    }

    override fun release() {
        codec.stop()
        codec.release()
        _encodedFrames.close()
    }
}
