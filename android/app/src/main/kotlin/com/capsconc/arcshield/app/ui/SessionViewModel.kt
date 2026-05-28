/*
 * Intellectual Property and Trademark Notice
 *
 * mp4Real™, ArcShield™, CIAER™, and CIAER+™ are trademarks of Capps Consulting
 * Company LLC. The multi-track cyber-physical capture architecture, the
 * application of log-likelihood ratio (LLR) gating to multimodal industrial
 * decision events, and the behavioral codebook discretization methods described
 * in this document are the proprietary intellectual property of Kahn Capps and
 * Capps Consulting Company LLC. Unauthorized commercial use, reproduction, or
 * implementation of the mp4Real™ container architecture or the CIAER™ and CIAER+™
 * schemas without explicit licensing is prohibited. All rights reserved.
 */
package com.capsconc.arcshield.app.ui

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capsconc.arcshield.app.di.NullBiometricSource
import com.capsconc.arcshield.app.settings.AccelSourceSetting
import com.capsconc.arcshield.app.settings.BiometricSourceSetting
import com.capsconc.arcshield.app.settings.SettingsRepository
import com.capsconc.arcshield.app.settings.VideoSourceSetting
import com.capsconc.arcshield.capture.CaptureSession
import com.capsconc.arcshield.capture.CaptureSessionConfig
import com.capsconc.arcshield.codec.AndroidMp4RealMuxer
import com.capsconc.arcshield.codec.Mp4RealWriter
import com.capsconc.arcshield.codec.SessionMetadata
import com.capsconc.arcshield.codec.TrackType
import com.capsconc.arcshield.labeler.CandidateWindowLog
import com.capsconc.arcshield.llr.CandidateWindow
import com.capsconc.arcshield.llr.LlrConfig
import com.capsconc.arcshield.schema.biometric.BiometricSource
import com.capsconc.arcshield.schema.capture.CaptureSource
import com.capsconc.arcshield.schema.imu.AccelSource
import com.capsconc.arcshield.source.camerax.CameraXCaptureSource
import com.capsconc.arcshield.source.imu.PhoneImuAccelSource
import com.capsconc.arcshield.source.meta.MetaRayBansCaptureSource
import com.capsconc.arcshield.source.polar.PolarBleBiometricSource
import com.capsconc.arcshield.source.polar.PolarDeviceType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "SessionVM"
    }

    // ---- Session state ---------------------------------------------------

    sealed class SessionState {
        object Idle      : SessionState()
        object Building  : SessionState()
        object Recording : SessionState()
        data class Finished(val metadata: SessionMetadata) : SessionState()
        data class Error(val message: String) : SessionState()
    }

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _candidateCount = MutableStateFlow(0)
    val candidateCount: StateFlow<Int> = _candidateCount.asStateFlow()

    private val _cameraPreview = MutableStateFlow<Preview?>(null)
    val cameraPreview: StateFlow<Preview?> = _cameraPreview.asStateFlow()

    // ---- Voice annotation state ------------------------------------------

    private val _voiceAnnotationActive = MutableStateFlow(false)
    val voiceAnnotationActive: StateFlow<Boolean> = _voiceAnnotationActive.asStateFlow()

    // ---- Session log path (for export) -----------------------------------

    private val _lastSessionLogPath = MutableStateFlow<String?>(null)
    val lastSessionLogPath: StateFlow<String?> = _lastSessionLogPath.asStateFlow()

    // ---- Console log (reads from AppLogger singleton) -------------------

    val appLogs: StateFlow<List<LogEntry>> = AppLogger.entries

    // ---- Internal session objects ----------------------------------------

    private var activeSession: CaptureSession? = null
    private var windowLog: CandidateWindowLog? = null

    val candidateWindows: Flow<CandidateWindow>
        get() = activeSession?.candidateWindows ?: emptyFlow()

    // ---- Session lifecycle -----------------------------------------------

    fun startSession(lifecycleOwner: LifecycleOwner) {
        if (_sessionState.value != SessionState.Idle) return

        _sessionState.value = SessionState.Building
        AppLogger.info(TAG, "Session start requested")

        viewModelScope.launch {
            try {
                val sessionId = UUID.randomUUID().toString().take(8)
                val timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                val outputDir = File(context.filesDir, "sessions").also { it.mkdirs() }
                val outputFile = File(outputDir, "session_${timestamp}_$sessionId.mp4")

                val shadowDir = File(context.filesDir, "shadow_mode").also { it.mkdirs() }
                val logFile = File(shadowDir, "${timestamp}_$sessionId.ndjson")
                _lastSessionLogPath.value = logFile.absolutePath

                AppLogger.info(TAG, "id=$sessionId facility=${settings.facilityId.value} line=${settings.lineId.value}")
                AppLogger.info(TAG, "Building I-frame baseline (~${settings.iFrameDurationS.value}s)…")

                val preview = if (settings.videoSource.value == VideoSourceSetting.PHONE_CAMERA) {
                    Preview.Builder().build().also { _cameraPreview.value = it }
                } else {
                    null
                }

                val biometricSource = makeBiometricSource()
                val accelSource     = makeAccelSource()
                val captureSource   = makeCaptureSource(lifecycleOwner, preview)

                AppLogger.debug(TAG, "Sources: video=${captureSource.sourceId} bio=${biometricSource.sourceId}")

                val muxer = AndroidMp4RealMuxer(outputFile)
                val sessionMetadata = SessionMetadata(
                    sessionId         = sessionId,
                    operatorId        = "operator_${settings.polarDeviceId.value.takeLast(4).ifBlank { "0000" }}",
                    facilityId        = settings.facilityId.value,
                    lineId            = settings.lineId.value,
                    captureSourceId   = captureSource.sourceId,
                    biometricSourceId = biometricSource.sourceId,
                    sessionStartNanos = SystemClock.elapsedRealtimeNanos(),
                )
                val writer = Mp4RealWriter(muxer, sessionMetadata, outputFile)

                val log = CandidateWindowLog(logFile)
                log.writeHeader(sessionMetadata.sessionStartNanos)
                windowLog = log

                val config = CaptureSessionConfig(
                    outputDir         = outputDir,
                    sessionId         = sessionId,
                    operatorId        = sessionMetadata.operatorId,
                    facilityId        = settings.facilityId.value,
                    lineId            = settings.lineId.value,
                    captureSourceId   = captureSource.sourceId,
                    biometricSourceId = biometricSource.sourceId,
                    iFrameDurationMs  = settings.iFrameDurationS.value * 1000L,
                    llrConfig         = LlrConfig(shadowMode = true),
                    metaTracks        = listOf(
                        TrackType.AccelMeta,
                        TrackType.BiometricMeta,
                        TrackType.ThermalMeta,
                    ),
                )

                val session = CaptureSession(
                    config, captureSource, biometricSource, writer,
                    accelSource = accelSource,
                )
                activeSession = session

                viewModelScope.launch {
                    session.candidateWindows.collect { window ->
                        try { log.append(window) } catch (_: Exception) {}
                        _candidateCount.value += 1
                        AppLogger.info(
                            "LLRGate",
                            "λ=${"%.2f".format(window.lambda)} env=${"%.2f".format(window.lambdaEnv)} bio=${"%.2f".format(window.lambdaBio)} thresh=${window.thresholdReached} #${_candidateCount.value}",
                        )
                    }
                }

                session.start()
                _sessionState.value = SessionState.Recording
                AppLogger.info(TAG, "LLR gate live — shadow mode — τ=${LlrConfig().tau}")

            } catch (e: Exception) {
                _cameraPreview.value = null
                val msg = e.message ?: "session start failed"
                AppLogger.error(TAG, msg)
                _sessionState.value = SessionState.Error(msg)
            }
        }
    }

    fun stopSession() {
        val session = activeSession ?: return
        try {
            val metadata = session.close()
            AppLogger.info(
                TAG,
                "Session stopped — ${_candidateCount.value} candidates — ε_sync ${metadata.epsSyncNanos / 1_000_000}ms",
            )
            _sessionState.value = SessionState.Finished(metadata)
        } catch (e: Exception) {
            val msg = e.message ?: "stop failed"
            AppLogger.error(TAG, msg)
            _sessionState.value = SessionState.Error(msg)
        } finally {
            activeSession = null
            windowLog     = null
            _cameraPreview.value = null
            _voiceAnnotationActive.value = false
        }
    }

    fun resetToIdle() {
        _sessionState.value = SessionState.Idle
        _candidateCount.value = 0
        _lastSessionLogPath.value = null
        AppLogger.info(TAG, "Reset to Idle")
    }

    // ---- Manual trigger (OPERATOR_INITIATED) --------------------------------
    // Creates a synthetic CandidateWindow with λ=0 and appends it to the session
    // log so it enters the debrief queue like any gate-fired window. The operator
    // use case: "I see something happening that the gate hasn't caught yet."

    fun manualTrigger() {
        if (_sessionState.value !is SessionState.Recording) return
        val now = SystemClock.elapsedRealtimeNanos()
        val syntheticWindow = CandidateWindow(
            detectedAtNanos  = now,
            lambda           = 0f,
            lambdaEnv        = 0f,
            lambdaAcoustic   = 0f,
            lambdaAccel      = 0f,
            lambdaMotion     = 0f,
            lambdaGaze       = 0f,
            lambdaBio        = 0f,
            activityGate     = 1f,
            thresholdReached = true,
            shadowMode       = true,
        )
        viewModelScope.launch {
            try { windowLog?.append(syntheticWindow) } catch (_: Exception) {}
            _candidateCount.value += 1
        }
        AppLogger.info("ManualTrigger", "OPERATOR_INITIATED window logged — count ${_candidateCount.value + 1}")
    }

    // ---- Voice annotation ---------------------------------------------------
    // Phase 1/2: toggle only — actual audio recording wired with VoiceElicitationManager (Phase 3).

    fun toggleVoiceAnnotation() {
        if (_sessionState.value !is SessionState.Recording) return
        val next = !_voiceAnnotationActive.value
        _voiceAnnotationActive.value = next
        if (next) {
            AppLogger.info("Voice", "Voice annotation recording started — attach to last candidate window")
        } else {
            AppLogger.info("Voice", "Voice annotation stopped")
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeSession?.close()
        _cameraPreview.value = null
    }

    // ---- Per-session source factories ------------------------------------

    private fun makeBiometricSource(): BiometricSource {
        val deviceId = settings.polarDeviceId.value
        return when (settings.biometricSource.value) {
            BiometricSourceSetting.NONE -> {
                AppLogger.debug(TAG, "BiometricSource: NONE")
                NullBiometricSource()
            }
            BiometricSourceSetting.POLAR_H10 -> {
                if (deviceId.isBlank()) {
                    AppLogger.warn(TAG, "POLAR_H10 selected but POLAR_DEVICE_ID blank — using NullBiometricSource")
                    return NullBiometricSource()
                }
                try {
                    AppLogger.info(TAG, "Connecting Polar H10 ($deviceId)…")
                    PolarBleBiometricSource.create(
                        context    = context,
                        deviceId   = deviceId,
                        deviceType = PolarDeviceType.H10,
                        scope      = viewModelScope,
                    ).also { it.connect() }
                } catch (e: Exception) {
                    AppLogger.warn(TAG, "Polar H10 init failed (${e.message}) — using NullBiometricSource")
                    NullBiometricSource()
                }
            }
            BiometricSourceSetting.POLAR_VERITY_SENSE -> {
                if (deviceId.isBlank()) {
                    AppLogger.warn(TAG, "POLAR_VERITY_SENSE selected but POLAR_DEVICE_ID blank — using NullBiometricSource")
                    return NullBiometricSource()
                }
                try {
                    AppLogger.info(TAG, "Connecting Polar Verity Sense ($deviceId)…")
                    PolarBleBiometricSource.create(
                        context    = context,
                        deviceId   = deviceId,
                        deviceType = PolarDeviceType.VERITY_SENSE,
                        scope      = viewModelScope,
                    ).also { it.connect() }
                } catch (e: Exception) {
                    AppLogger.warn(TAG, "Polar Verity Sense init failed (${e.message}) — using NullBiometricSource")
                    NullBiometricSource()
                }
            }
        }
    }

    private fun makeAccelSource(): AccelSource? = when (settings.accelSource.value) {
        AccelSourceSetting.PHONE_IMU -> {
            AppLogger.debug(TAG, "AccelSource: PhoneIMU")
            PhoneImuAccelSource(context)
        }
        AccelSourceSetting.POLAR -> {
            AppLogger.debug(TAG, "AccelSource: Polar (via BiometricSource.accelerometer())")
            null
        }
    }

    private fun makeCaptureSource(lifecycleOwner: LifecycleOwner, preview: Preview?): CaptureSource {
        return when (settings.videoSource.value) {
            VideoSourceSetting.PHONE_CAMERA -> {
                AppLogger.debug(TAG, "CaptureSource: CameraX")
                CameraXCaptureSource(context, lifecycleOwner, preview = preview)
            }
            VideoSourceSetting.GLASSES -> {
                val mac = settings.glassesDeviceMac.value
                if (MetaRayBansCaptureSource.isAvailable(context, mac)) {
                    AppLogger.info(TAG, "CaptureSource: Meta Ray-Bans ($mac)")
                    MetaRayBansCaptureSource(context, lifecycleOwner, mac)
                } else {
                    AppLogger.warn(TAG, "Meta Ray-Bans not available — falling back to CameraX")
                    CameraXCaptureSource(context, lifecycleOwner, preview = preview)
                }
            }
        }
    }
}
