package com.capsconc.arcshield.codec

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Production [Mp4RealMuxer] backed by Android's [MediaMuxer].
 *
 * Container format: MPEG-4 (ISO BMFF). The output is a standard MP4 file.
 * True fragmented MP4 (fMP4 / CMAF) requires post-processing with mp4parser
 * or a custom BMFF writer — flagged as a Phase 2 upgrade if streaming ingest
 * requires it. For file-based corpus storage the standard MP4 format is
 * interoperable with ExoPlayer, FFmpeg, and MP4Parser.
 *
 * Metadata tracks: text/vtt MIME with JSON-UTF-8 payload in Phase 1.
 * Binary octet-stream metadata is a Phase 2 upgrade (requires API 29+).
 *
 * PTS conversion: [writeSample] and [writeMetaSample] accept elapsedRealtimeNanos
 * and convert to microseconds at the MediaMuxer.writeSampleData boundary.
 *
 * Thread safety: NOT thread-safe. All calls must arrive from the single mux-pipeline
 * coroutine / thread in core-capture.
 */
class AndroidMp4RealMuxer(outputFile: File) : Mp4RealMuxer {

    private val muxer = MediaMuxer(
        outputFile.absolutePath,
        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
    )

    // Reusable write buffer. Grown on demand; not thread-safe by design.
    private var buffer: ByteBuffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)

    override fun addVideoTrack(width: Int, height: Int, frameRate: Int, csd0: ByteArray): Int {
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height)
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        if (csd0.isNotEmpty()) fmt.setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
        return muxer.addTrack(fmt)
    }

    override fun addAudioTrack(sampleRate: Int, channelCount: Int, csd0: ByteArray): Int {
        val fmt = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount,
        )
        if (csd0.isNotEmpty()) fmt.setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
        return muxer.addTrack(fmt)
    }

    override fun addMetaTrack(trackType: TrackType): Int {
        // MediaFormat.createSubtitleFormat() requires API 28; build a MediaFormat manually
        // so we stay safe on our minSdk 26 baseline.
        val fmt = MediaFormat()
        fmt.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_TEXT_VTT)
        fmt.setString(MediaFormat.KEY_LANGUAGE, "und")
        return muxer.addTrack(fmt)
    }

    override fun start() = muxer.start()

    override fun writeSample(
        trackIndex: Int,
        presentationTimeNanos: Long,
        data: ByteArray,
        isKeyFrame: Boolean,
    ) {
        val buf = ensureCapacity(data.size)
        buf.put(data)
        buf.flip()
        val info = MediaCodec.BufferInfo()
        info.set(
            0,
            data.size,
            presentationTimeNanos / 1_000L,
            if (isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0,
        )
        muxer.writeSampleData(trackIndex, buf, info)
    }

    override fun writeMetaSample(
        trackIndex: Int,
        presentationTimeNanos: Long,
        payload: ByteArray,
    ) {
        val buf = ensureCapacity(payload.size)
        buf.put(payload)
        buf.flip()
        val info = MediaCodec.BufferInfo()
        info.set(0, payload.size, presentationTimeNanos / 1_000L, 0)
        muxer.writeSampleData(trackIndex, buf, info)
    }

    override fun stop()    = muxer.stop()
    override fun release() = muxer.release()

    private fun ensureCapacity(needed: Int): ByteBuffer {
        if (buffer.capacity() < needed) {
            buffer = ByteBuffer.allocateDirect(needed * 2)
        }
        buffer.clear()
        return buffer
    }
}
