package com.capsconc.arcshield.app.ui

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capsconc.arcshield.app.BuildConfig
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
import com.capsconc.arcshield.schema.capture.CaptureSourceFactory
import com.capsconc.arcshield.schema.imu.AccelSource
import com.capsconc.arcshield.schema.telemetry.PlcTelemetrySource
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
    private val biometricSource: BiometricSource,
    private val accelSource: AccelSource,
    private val captureSourceFactory: CaptureSourceFactory,
    @Suppress("UnusedPrivateMember")
    private val plcTelemetrySource: PlcTelemetrySource,
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

    // ---- Internal session objects ----------------------------------------

    private var activeSession: CaptureSession? = null
    private var windowLog: CandidateWindowLog? = null

    // candidateWindows delegates to the active session's SharedFlow or empty.
    val candidateWindows: Flow<CandidateWindow>
        get() = activeSession?.candidateWindows ?: emptyFlow()

    // ---- Session lifecycle -----------------------------------------------

    // Called from MainActivity after permission grant.
    // lifecycleOwner is the Activity (used by CameraX for teardown).
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

                // CandidateWindowLog goes under shadow_mode/ so LabelerViewModel.listShiftLogs()
                // finds it automatically.
                val shadowDir = File(context.filesDir, "shadow_mode").also { it.mkdirs() }
                val logFile = File(shadowDir, "${timestamp}_$sessionId.ndjson")
                val captureSource = captureSourceFactory.create(lifecycleOwner)
                val muxer = AndroidMp4RealMuxer(outputFile)
                val sessionMetadata = SessionMetadata(
                    sessionId         = sessionId,
                    operatorId        = "operator_${BuildConfig.POLAR_DEVICE_ID.takeLast(4).ifBlank { "0000" }}",
                    facilityId        = BuildConfig.FACILITY_ID,
                    lineId            = BuildConfig.LINE_ID,
                    captureSourceId   = captureSource.sourceId,
                    biometricSourceId = biometricSource.sourceId,
                    sessionStartNanos = SystemClock.elapsedRealtimeNanos(),
                )
                val writer = Mp4RealWriter(muxer, sessionMetadata, outputFile)

                val log = CandidateWindowLog(logFile)
                log.writeHeader(sessionMetadata.sessionStartNanos)
                windowLog = log

                val config = CaptureSessionConfig(
                    outputDir        = outputDir,
                    sessionId        = sessionId,
                    operatorId       = sessionMetadata.operatorId,
                    facilityId       = BuildConfig.FACILITY_ID,
                    lineId           = BuildConfig.LINE_ID,
                    captureSourceId  = captureSource.sourceId,
                    biometricSourceId = biometricSource.sourceId,
                    llrConfig        = LlrConfig(shadowMode = true),
                    metaTracks       = listOf(
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

                // Collect candidate windows → log + count update.
                viewModelScope.launch {
                    session.candidateWindows.collect { window ->
                        try { log.append(window) } catch (_: Exception) {}
                        _candidateCount.value += 1
                    }
                }

                session.start()
                _sessionState.value = SessionState.Recording

            } catch (e: Exception) {
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
        }
    }

    fun resetToIdle() {
        _sessionState.value = SessionState.Idle
        _candidateCount.value = 0
    }

    override fun onCleared() {
        super.onCleared()
        activeSession?.close()
    }
}
