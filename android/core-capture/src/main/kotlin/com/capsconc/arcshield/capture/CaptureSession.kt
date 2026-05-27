package com.capsconc.arcshield.capture

import android.os.SystemClock
import com.capsconc.arcshield.codec.EncodedSample
import com.capsconc.arcshield.codec.MetaSample
import com.capsconc.arcshield.codec.Mp4RealWriter
import com.capsconc.arcshield.codec.SessionMetadata
import com.capsconc.arcshield.codec.TrackType
import com.capsconc.arcshield.llr.CandidateWindow
import com.capsconc.arcshield.llr.LlrBaseline
import com.capsconc.arcshield.llr.buildBaseline
import com.capsconc.arcshield.llr.llrGate
import com.capsconc.arcshield.schema.biometric.BiometricSource
import com.capsconc.arcshield.schema.capture.CaptureSource
import com.capsconc.arcshield.schema.imu.AccelSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

// Orchestrates the full Phase 1 capture pipeline:
//   sensor sources → encoders → ring buffers
//   ring buffers + LLR gate → windowed mp4Real container writes
//
// Lifecycle:
//   1. Construct with DI-supplied components.
//   2. Call start() once (suspends for the I-frame period, then returns).
//   3. Collect candidateWindows for shadow-mode labeling.
//   4. Call close() to finalize the container and receive SessionMetadata.
//
// Not thread-safe: all methods must be called from the same coroutine.
class CaptureSession(
    private val config: CaptureSessionConfig,
    private val captureSource: CaptureSource,
    private val biometricSource: BiometricSource,
    private val writer: Mp4RealWriter,
    // Phone-IMU accel. When provided it feeds Λ_accel and the accel track;
    // otherwise accel falls back to the BiometricSource's onboard accelerometer.
    private val accelSource: AccelSource? = null,
    private val videoEncoder: VideoEncoderDelegate =
        MediaCodecVideoEncoder(config.videoWidth, config.videoHeight, config.frameRateFps,
            config.videoTargetBitrateBps),
    private val audioEncoder: AudioEncoderDelegate =
        MediaCodecAudioEncoder(config.audioSampleRateHz, config.audioChannelCount),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    private val videoRing = ChannelRingBuffer<EncodedSample>(config.ringCapacityNanos)
        { it.presentationTimeNanos }
    private val audioRing = ChannelRingBuffer<EncodedSample>(config.ringCapacityNanos)
        { it.presentationTimeNanos }
    private val accelRing = ChannelRingBuffer<MetaSample>(config.ringCapacityNanos)
        { it.presentationTimeNanos }
    private val bioRing   = ChannelRingBuffer<MetaSample>(config.ringCapacityNanos)
        { it.presentationTimeNanos }
    private val thermalRing = ChannelRingBuffer<MetaSample>(config.ringCapacityNanos)
        { it.presentationTimeNanos }

    private val extractor = WindowExtractor(videoRing, audioRing, accelRing, bioRing, thermalRing)

    val epsSync = EpsSyncCoordinator(config.epsSyncIntervalMs)

    private val _candidateWindows = MutableSharedFlow<CandidateWindow>(extraBufferCapacity = 64)
    val candidateWindows: Flow<CandidateWindow> = _candidateWindows.asSharedFlow()

    private var sessionStartNanos: Long = 0L

    // Starts the session: builds the I-frame baseline (suspends for iFrameDurationMs),
    // initializes the muxer with CSD from encoders, writes the I-frame to the container,
    // then launches the live LLR gate in a background coroutine.
    suspend fun start() {
        sessionStartNanos = SystemClock.elapsedRealtimeNanos()

        // ---- 1. Start encoders and shared flows ----------------------------

        videoEncoder.start()
        audioEncoder.start()

        val sharedVideo = captureSource.videoFrames()
            .shareIn(scope, SharingStarted.Eagerly, replay = 0)
        val sharedAudio = captureSource.audioFrames()
            .shareIn(scope, SharingStarted.Eagerly, replay = 0)
        val sharedAccel = (accelSource?.accelerometer() ?: biometricSource.accelerometer())
            .shareIn(scope, SharingStarted.Eagerly, replay = 0)
        val sharedHr    = biometricSource.heartRate()
            .shareIn(scope, SharingStarted.Eagerly, replay = 0)
        val sharedRr    = biometricSource.rrIntervals()
            .shareIn(scope, SharingStarted.Eagerly, replay = 0)

        biometricSource.registerSyncListener { phone, ext ->
            epsSync.recordSyncPoint(phone, ext)
        }

        // ---- 2. Wire encoder outputs → ring buffers -----------------------

        scope.launch {
            captureSource.videoFrames().collect { videoEncoder.encode(it) }
        }
        scope.launch {
            captureSource.audioFrames().collect { audioEncoder.encode(it) }
        }
        scope.launch { videoEncoder.encodedFrames.collect { videoRing.offer(it) } }
        scope.launch { audioEncoder.encodedFrames.collect { audioRing.offer(it) } }

        // ---- 3. Wire biometric/accel → ring buffers (JSON meta payloads) --

        scope.launch {
            sharedAccel.collect { s ->
                accelRing.offer(MetaSample(
                    trackType            = TrackType.AccelMeta,
                    presentationTimeNanos = s.timestampNanos,
                    payload              = """{"x":${s.xMg},"y":${s.yMg},"z":${s.zMg}}""".toByteArray(),
                ))
            }
        }
        scope.launch {
            sharedHr.collect { s ->
                bioRing.offer(MetaSample(
                    trackType            = TrackType.BiometricMeta,
                    presentationTimeNanos = s.timestampNanos,
                    payload              = """{"bpm":${s.bpm}}""".toByteArray(),
                ))
            }
        }
        scope.launch {
            biometricSource.rrIntervals().collect { s ->
                bioRing.offer(MetaSample(
                    trackType            = TrackType.BiometricMeta,
                    presentationTimeNanos = s.timestampNanos,
                    payload              = """{"rr_ms":${s.rrMs}}""".toByteArray(),
                ))
            }
        }

        // ---- 4. Build I-frame baseline (suspends for iFrameDurationMs) ----

        val baseline: LlrBaseline = buildBaseline(
            audioFrames  = sharedAudio,
            accelSamples = sharedAccel,
            config       = config.llrConfig,
            hrSamples    = sharedHr,
            rrSamples    = sharedRr,
            videoFrames  = sharedVideo,
            durationMs   = config.iFrameDurationMs,
        )

        // ---- 5. Initialize muxer with CSD from encoders -------------------

        config.metaTracks.forEach { writer.addMetaTrack(it) }
        writer.addVideoTrack(
            width        = config.videoWidth,
            height       = config.videoHeight,
            frameRate    = config.frameRateFps,
            csd0         = videoEncoder.csd0.await(),
        )
        writer.addAudioTrack(
            sampleRate   = config.audioSampleRateHz,
            channelCount = config.audioChannelCount,
            csd0         = audioEncoder.csd0.await(),
        )
        writer.start(sessionStartNanos)

        // ---- 6. Write I-frame at PTS=0 ------------------------------------

        val iframeEndNanos = SystemClock.elapsedRealtimeNanos()
        val iframe = extractor.extractWindow(
            sessionStartNanos = sessionStartNanos,
            fireTimeNanos     = iframeEndNanos,
            wPreNanos         = config.iFrameDurationNanos,
            wPostNanos        = 0L,
            isIFrame          = true,
        )
        if (!config.llrConfig.shadowMode) {
            muxWindow(iframe)
        }

        // ---- 7. Launch live gate in background ----------------------------

        scope.launch {
            llrGate(
                audioFrames  = sharedAudio,
                accelSamples = sharedAccel,
                baseline     = baseline,
                config       = config.llrConfig,
                hrSamples    = sharedHr,
                rrSamples    = sharedRr,
                videoFrames  = sharedVideo,
            ).collect { window ->
                _candidateWindows.tryEmit(window)
                if (!config.llrConfig.shadowMode) {
                    val captured = extractor.extractWindow(
                        sessionStartNanos = sessionStartNanos,
                        fireTimeNanos     = window.detectedAtNanos,
                        wPreNanos         = config.wPreNanos,
                        wPostNanos        = config.wPostNanos,
                    )
                    muxWindow(captured)
                }
            }
        }

    }

    fun close(): SessionMetadata {
        biometricSource.registerSyncListener(null)
        videoEncoder.release()
        audioEncoder.release()
        val sync = epsSync.currentSync()
        return writer.close(finalEpsSyncNanos = sync.absOffsetNanos)
    }

    // Writes all samples in the window to the muxer, adjusting PTS to be
    // relative to sessionStartNanos so the container starts at PTS=0.
    private fun muxWindow(window: CapturedWindow) {
        val offset = sessionStartNanos

        window.videoSamples.forEach { s ->
            writer.writeSample(
                TrackType.Video,
                s.presentationTimeNanos - offset,
                s.data,
                s.isKeyFrame,
            )
        }
        window.audioSamples.forEach { s ->
            writer.writeSample(TrackType.Audio, s.presentationTimeNanos - offset, s.data)
        }
        window.accelSamples.forEach { s ->
            writer.writeMetaSample(TrackType.AccelMeta, s.presentationTimeNanos - offset, s.payload)
        }
        window.biometricSamples.forEach { s ->
            writer.writeMetaSample(TrackType.BiometricMeta, s.presentationTimeNanos - offset, s.payload)
        }
        window.thermalSamples.forEach { s ->
            writer.writeMetaSample(TrackType.ThermalMeta, s.presentationTimeNanos - offset, s.payload)
        }
    }
}
