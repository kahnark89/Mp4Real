package com.capsconc.arcshield.llm.claude

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Base64
import com.capsconc.arcshield.schema.capture.VideoFrame
import java.io.ByteArrayOutputStream

// Converts a NV21 VideoFrame to a JPEG byte array, then to a Base64 string
// suitable for the Anthropic API image content block.
//
// NV21 is the format produced by CameraXCaptureSource. JPEG quality 85 gives
// good gauge legibility at ~3× smaller payload than uncompressed YUV.
internal object VideoFrameEncoder {

    private const val JPEG_QUALITY = 85

    fun toJpegBase64(frame: VideoFrame): String {
        val yuv = YuvImage(frame.yuvData, ImageFormat.NV21, frame.widthPx, frame.heightPx, null)
        val out = ByteArrayOutputStream(frame.widthPx * frame.heightPx / 4)
        yuv.compressToJpeg(Rect(0, 0, frame.widthPx, frame.heightPx), JPEG_QUALITY, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
