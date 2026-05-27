package com.capsconc.arcshield.source.camerax

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.capsconc.arcshield.schema.capture.AudioFrame
import com.capsconc.arcshield.schema.capture.CaptureSource
import com.capsconc.arcshield.schema.capture.VideoFrame
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Phase 1 [CaptureSource] backed by CameraX + AudioRecord.
 *
 * Video: CameraX ImageAnalysis (YUV_420_888 → NV21) at 30 fps / 1080p.
 * Audio: AudioRecord 48 kHz mono, 480-sample (~10 ms) buffers.
 *
 * Both flows emit [elapsedRealtimeNanos] timestamps, consistent with the
 * phone-side clock anchor (CLAUDE.md §3.2 ε_sync rules). The flows are
 * cold — no resources are allocated until collection begins.
 *
 * Consumers must hold a [LifecycleOwner] reference that is in the STARTED
 * state before collecting [videoFrames]; the CameraX provider binds to that
 * lifecycle and tears down automatically on STOPPED.
 *
 * All hardware access is coordinated through [CaptureSource] — no consumer
 * holds a reference to CameraX or AudioRecord directly (CLAUDE.md §9).
 */
class CameraXCaptureSource(
    private val context:        Context,
    private val lifecycleOwner: LifecycleOwner,
    private val lensFacing:     Int = CameraSelector.LENS_FACING_BACK,
) : CaptureSource {

    override val sourceId: String = "phone_cameraX_v1"

    // ---- Video ------------------------------------------------------------------

    override fun videoFrames(): Flow<VideoFrame> = callbackFlow {
        val executor = Executors.newSingleThreadExecutor()

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(1920, 1080))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        imageAnalysis.setAnalyzer(executor) { proxy ->
            val frame = proxy.toVideoFrame()
            proxy.close()
            trySend(frame)
        }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
        }, ContextCompat.getMainExecutor(context))

        awaitClose {
            val provider = ProcessCameraProvider.getInstance(context).get()
            provider.unbindAll()
            executor.shutdown()
        }
    }

    // ---- Audio ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    override fun audioFrames(): Flow<AudioFrame> = channelFlow {
        val audioManager  = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sampleRate    = 48_000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding      = AudioFormat.ENCODING_PCM_16BIT
        val samplesPerBuf = 480                                     // ~10 ms per frame
        val bytesBufSize  = samplesPerBuf * 2                       // 16-bit = 2 bytes/sample

        // Route audio input/output through paired Bluetooth earbuds when available.
        // isBluetoothScoAvailableOffCall = true means a BT SCO device is paired and
        // the phone supports SCO outside of calls (required for in-app mic capture).
        val scoAvailable = audioManager.isBluetoothScoAvailableOffCall
        if (scoAvailable) {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            // Give the SCO link time to establish before AudioRecord starts.
            delay(SCO_CONNECT_DELAY_MS)
        }

        val minBuf  = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        val bufSize = maxOf(bytesBufSize * 4, minBuf)               // ring buffer ≥ minBuf

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,          // prefers SCO mic when active
            sampleRate,
            channelConfig,
            encoding,
            bufSize,
        )

        recorder.startRecording()

        val buf = ShortArray(samplesPerBuf)
        try {
            while (isActive) {
                val read = recorder.read(buf, 0, samplesPerBuf)
                if (read > 0) {
                    trySend(AudioFrame(
                        timestampNanos = SystemClock.elapsedRealtimeNanos(),
                        samples        = buf.copyOf(read),
                        sampleRateHz   = sampleRate,
                        channelCount   = 1,
                    ))
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
            if (scoAvailable) {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
        }
    }

    companion object {
        private const val SCO_CONNECT_DELAY_MS = 500L
    }
}

// ---- ImageProxy → NV21 conversion -------------------------------------------

/**
 * Converts a YUV_420_888 [ImageProxy] to a [VideoFrame] in NV21 layout.
 *
 * NV21 layout: Y-plane (width × height bytes) followed by interleaved
 * V/U bytes ((width × height / 2) bytes). We copy the Y-plane first, then
 * interleave V and U from planes[2] and planes[1] respectively.
 *
 * Row-stride padding is stripped: rows wider than [ImageProxy.width] are
 * trimmed to [width] bytes before copy.
 */
private fun ImageProxy.toVideoFrame(): VideoFrame {
    val w      = width
    val h      = height
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val nv21 = ByteArray(w * h * 3 / 2)

    // Copy Y-plane, stripping row-stride padding.
    val yBuf     = yPlane.buffer
    val yStride  = yPlane.rowStride
    if (yStride == w) {
        yBuf.get(nv21, 0, w * h)
    } else {
        for (row in 0 until h) {
            yBuf.position(row * yStride)
            yBuf.get(nv21, row * w, w)
        }
    }

    // Interleave V/U into the chroma plane (NV21 = V first, then U).
    val uvPixelStride = vPlane.pixelStride
    val uvRowStride   = vPlane.rowStride
    val vBuf = vPlane.buffer
    val uBuf = uPlane.buffer
    var dstIdx = w * h
    val uvRows = h / 2
    val uvCols = w / 2
    for (row in 0 until uvRows) {
        for (col in 0 until uvCols) {
            val srcIdx = row * uvRowStride + col * uvPixelStride
            vBuf.position(srcIdx); nv21[dstIdx++] = vBuf.get()
            uBuf.position(srcIdx); nv21[dstIdx++] = uBuf.get()
        }
    }

    return VideoFrame(
        timestampNanos = SystemClock.elapsedRealtimeNanos(),
        widthPx        = w,
        heightPx       = h,
        yuvData        = nv21,
    )
}
