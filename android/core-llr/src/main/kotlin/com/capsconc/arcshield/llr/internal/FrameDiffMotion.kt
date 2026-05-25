package com.capsconc.arcshield.llr.internal

import com.capsconc.arcshield.schema.capture.VideoFrame

/**
 * Incremental frame-differencing motion detector.
 *
 * Computes the Mean Absolute Difference (MAD) on the Y-plane between the
 * current frame and the previous frame. Only every [subsample]-th pixel is
 * read, keeping CPU cost proportional to (width × height / subsample²).
 *
 * The Y-plane is the first [widthPx × heightPx] bytes of [VideoFrame.yuvData]
 * in NV21 layout. Reading luma only is correct here — luma carries texture
 * and motion energy; the chroma half-planes add noise for motion detection.
 *
 * Returns `null` for the first frame (no previous frame to diff against).
 * Thread-unsafe — call from a single coroutine.
 */
internal class FrameDiffMotion(private val subsample: Int = 4) {

    private var prevY: ByteArray? = null

    /**
     * Feed one [frame] and get back the MAD (0f..255f range).
     * Returns null when this is the first frame seen.
     */
    fun update(frame: VideoFrame): Float? {
        val ySize = frame.widthPx * frame.heightPx
        val curY  = frame.yuvData

        val prev = prevY
        // Keep only the luma portion for diffing
        prevY = curY.copyOf(ySize)

        if (prev == null) return null

        // Sub-sampled MAD over the Y-plane
        var sumAbs = 0L
        var count  = 0
        var i      = 0
        while (i < ySize) {
            val diff = (curY[i].toInt() and 0xFF) - (prev[i].toInt() and 0xFF)
            sumAbs += if (diff >= 0) diff.toLong() else (-diff).toLong()
            count++
            i += subsample
        }

        return if (count > 0) sumAbs.toFloat() / count.toFloat() else 0f
    }

    fun reset() {
        prevY = null
    }
}
