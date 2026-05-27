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

    // ---- Session state ----------------------------------------------------

    sealed class SessionState {
        object Idle      : SessionState()
        object Building  : SessionState()   // I-frame baseline in progress
        object Recording : SessionState()   // LLR gate live, shadow mode
        data class Finished(val metadata: SessionMetadata) : SessionState()
        data class Error(val message: String) : SessionState()
    }

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _candidateCount = MutableStateFlow(0)
    val candidateCount: StateFlow<Int> = _candidateCount.asStateFlow()

    private val _cameraPreview = MutableStateFlow<Preview?>(null)
    val cameraPreview: StateFlow<Preview?> = _cameraPreview.asStateFlow()

    // ---- Internal session objects ----------------------------------------

    private var activeSession: CaptureSession? = null
    private var windowLog: CandidateWindowLog? = null

    // candidateWindows delegates to the active session's SharedFlow or empty.
    val candidateWindows: Flow<CandidateWindow>
        get() = activeSession?.candidateWindows ?: emptyFlow()

    // ---- Session lifecycle -----------------------------------------------

    fun startSession(lifecycleOwner: LifecycleOwner) {
        if (_sessionState.value != SessionState.Idle) return

        _sessionState.value = SessionState.Building

        viewModelScope.launch {
            try {
                val sessionId = UUID.randomUUID().toString().take(8)
                val timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                val outputDir = File(context.filesDir, "sessions").also { it.mkdirs() }
                val outputFile = File(outputDir, "session_${timestamp}_$sessionId.mp4")

                val shadowDir = File(context.filesDir, "shadow_mode").also { it.mkdirs() }
                val logFile = File(shadowDir, "${timestamp}_$sessionId.ndjson")

                val preview = if (settings.videoSource.value == VideoSourceSetting.PHONE_CAMERA) {
                    Preview.Builder().build().also { _cameraPreview.value = it }
                } else {
                    null
                }

                val biometricSource = makeBiometricSource()
                val accelSource = makeAccelSource()
                val captureSource = makeCaptureSource(lifecycleOwner, preview)

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
                    }
                }

                session.start()
                _sessionState.value = SessionState.Recording

            } catch (e: Exception) {
                _cameraPreview.value = null
                _sessionState.value = SessionState.Error(e.message ?: "session failed")
            }
        }
    }

    fun stopSession() {
        val session = activeSession ?: return
        try {
            val metadata = session.close()
            _sessionState.value = SessionState.Finished(metadata)
        } catch (e: Exception) {
            _sessionState.value = SessionState.Error(e.message ?: "stop failed")
        } finally {
            activeSession = null
            windowLog = null
            _cameraPreview.value = null
        }
    }

    fun resetToIdle() {
        _sessionState.value = SessionState.Idle
        _candidateCount.value = 0
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
            BiometricSourceSetting.NONE -> NullBiometricSource()
            BiometricSourceSetting.POLAR_H10 -> {
                if (deviceId.isBlank()) return NullBiometricSource()
                try {
                    PolarBleBiometricSource.create(
                        context    = context,
                        deviceId   = deviceId,
                        deviceType = PolarDeviceType.H10,
                        scope      = viewModelScope,
                    ).also { it.connect() }
                } catch (_: Exception) {
                    NullBiometricSource()
                }
            }
            BiometricSourceSetting.POLAR_VERITY_SENSE -> {
                if (deviceId.isBlank()) return NullBiometricSource()
                try {
                    PolarBleBiometricSource.create(
                        context    = context,
                        deviceId   = deviceId,
                        deviceType = PolarDeviceType.VERITY_SENSE,
                        scope      = viewModelScope,
                    ).also { it.connect() }
                } catch (_: Exception) {
                    NullBiometricSource()
                }
            }
        }
    }

    private fun makeAccelSource(): AccelSource? = when (settings.accelSource.value) {
        AccelSourceSetting.PHONE_IMU -> PhoneImuAccelSource(context)
        // POLAR: let CaptureSession fall back to biometricSource.accelerometer()
        AccelSourceSetting.POLAR -> null
    }

    private fun makeCaptureSource(lifecycleOwner: LifecycleOwner, preview: Preview?): CaptureSource {
        return when (settings.videoSource.value) {
            VideoSourceSetting.PHONE_CAMERA -> CameraXCaptureSource(context, lifecycleOwner, preview = preview)
            VideoSourceSetting.GLASSES -> {
                val mac = settings.glassesDeviceMac.value
                if (MetaRayBansCaptureSource.isAvailable(context, mac)) {
                    MetaRayBansCaptureSource(context, lifecycleOwner, mac)
                } else {
                    CameraXCaptureSource(context, lifecycleOwner, preview = preview)
                }
            }
        }
    }
}
