package com.capsconc.arcshield.capture

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaFormat.MIMETYPE_VIDEO_HEVC
import com.capsconc.arcshield.codec.EncodedSample
import com.capsconc.arcshield.schema.capture.VideoFrame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

// H.265 (HEVC) encoder backed by the platform MediaCodec async callback API.
// Not thread-safe — start(), encode(), release() must be called from the
// same coroutine dispatcher (typically Dispatchers.IO).
class MediaCodecVideoEncoder(
    private val width: Int,
    private val height: Int,
    private val frameRateFps: Int = 30,
    private val bitrateBps: Int = 4_000_000,
) : VideoEncoderDelegate {

    private lateinit var codec: MediaCodec
    private val _encodedFrames = Channel<EncodedSample>(Channel.UNLIMITED)
    private val _csd0 = CompletableDeferred<ByteArray>()
    private val pendingFrames = Channel<VideoFrame>(capacity = 8)

    override val encodedFrames: Flow<EncodedSample> = _encodedFrames.receiveAsFlow()
    override val csd0: Deferred<ByteArray> = _csd0

    override fun start() {
        val format = MediaFormat.createVideoFormat(MIMETYPE_VIDEO_HEVC, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRateFps)
            setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }

        codec = MediaCodec.createEncoderByType(MIMETYPE_VIDEO_HEVC)
        codec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                val frame = pendingFrames.tryReceive().getOrNull() ?: return
                val buf = codec.getInputBuffer(index) ?: return
                buf.clear()
                buf.put(frame.yuvData)
                codec.queueInputBuffer(
                    index, 0, frame.yuvData.size,
                    frame.timestampNanos / 1_000L, 0,
                )
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo,
            ) {
                // CSD packets have BUFFER_FLAG_CODEC_CONFIG set; skip them since
                // the CSD is already captured via onOutputFormatChanged.
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
                        isKeyFrame           = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0,
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

    override suspend fun encode(frame: VideoFrame) {
        pendingFrames.send(frame)
    }

    override fun release() {
        codec.stop()
        codec.release()
        _encodedFrames.close()
    }
}
