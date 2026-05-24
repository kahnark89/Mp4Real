package com.capsconc.arcshield.llr

import android.os.SystemClock
import com.capsconc.arcshield.llr.internal.KlDivergence
import com.capsconc.arcshield.llr.internal.RealFft
import com.capsconc.arcshield.llr.internal.RollingAccelRms
import com.capsconc.arcshield.schema.biometric.AccelSample
import com.capsconc.arcshield.schema.capture.AudioFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/**
 * LLR gate — the codec's rate-distortion gate. CLAUDE.md §1 (C1), §4.
 *
 * Evaluates Λ = Λ_env + Λ_bio on a timer. In shadow mode emits a
 * [CandidateWindow] on every tick. In production mode emits only when Λ ≥ τ.
 *
 * Phase 1 Λ_env components:
 *   Λ_acoustic — KL(baseline_spectrum ‖ rolling_spectrum)    ✅ active
 *   Λ_accel    — Gaussian-shift GLR on rolling 5s accel RMS  ✅ active
 *   Λ_motion   — frame-to-frame video energy                 ⚠ stub 0.0 (CameraX not wired)
 *   Λ_gaze     — sustained dwell duration                    ⚠ stub 0.0 (gaze not wired)
 *
 * Λ_bio = 0.0 until the next backlog item wires HR delta + HRV-RMSSD.
 *
 * The combination rule Λ = Λ_env + Λ_bio assumes S_env ⊥ S_bio | E, Z
 * (CLAUDE.md §4.2). Do not change the combination rule without author sign-off.
 *
 * @param audioFrames  Flow of PCM frames from the phone microphone.
 * @param accelSamples Flow of accel samples (phone IMU or Polar onboard accel).
 * @param baseline     I-frame statistical snapshot from shift start.
 * @param config       Gate configuration including τ and shadow mode flag.
 */
fun llrGate(
    audioFrames:  Flow<AudioFrame>,
    accelSamples: Flow<AccelSample>,
    baseline:     LlrBaseline,
    config:       LlrConfig,
): Flow<CandidateWindow> = channelFlow {

    // Shared mutable state updated by producer coroutines, read by the eval ticker.
    // AtomicReference ensures safe publication across coroutine boundaries.
    val latestSpectrum = AtomicReference<FloatArray?>(null)
    val rollingRms     = RollingAccelRms(config.accelWindowMs)
    val latestRmsRef   = AtomicReference(0f)

    // ---- Audio producer -------------------------------------------------
    launch {
        audioFrames.collect { frame ->
            val spectrum = RealFft.powerSpectrum(frame.samples, config.fftSize)
            latestSpectrum.set(spectrum)
        }
    }

    // ---- Accel producer -------------------------------------------------
    launch {
        accelSamples.collect { sample ->
            val snap = rollingRms.update(sample)
            latestRmsRef.set(snap.rms)
        }
    }

    // ---- Eval ticker ----------------------------------------------------
    // Runs every evalIntervalMs. Reads latest computed features and emits
    // a CandidateWindow. In shadow mode, emits regardless of threshold.
    // In production mode, emits only when Λ ≥ τ.
    while (isActive) {
        delay(config.evalIntervalMs)

        val spectrum = latestSpectrum.get()

        val lambdaAcoustic: Float = if (spectrum != null) {
            KlDivergence.compute(baseline.acousticSpectrum, spectrum)
        } else {
            0f
        }

        val rms        = latestRmsRef.get()
        val rmsDeviation = rms - baseline.accelRmsBaseline
        val lambdaAccel = max(
            0f,
            (rmsDeviation * rmsDeviation) / (2f * baseline.accelRmsVariance)
        )

        // Phase 1 stubs — wired in later backlog items
        val lambdaMotion = 0f   // TODO: frame-to-frame video energy (source-camerax)
        val lambdaGaze   = 0f   // TODO: sustained gaze dwell (gaze tracking)
        val lambdaBio    = 0f   // TODO: Λ_bio (HR delta + HRV-RMSSD)

        val lambdaEnv = lambdaAcoustic + lambdaAccel + lambdaMotion + lambdaGaze
        val lambda    = lambdaEnv + lambdaBio

        val thresholdReached = lambda >= config.tau

        if (config.shadowMode || thresholdReached) {
            send(CandidateWindow(
                detectedAtNanos  = SystemClock.elapsedRealtimeNanos(),
                lambda           = lambda,
                lambdaEnv        = lambdaEnv,
                lambdaAcoustic   = lambdaAcoustic,
                lambdaAccel      = lambdaAccel,
                lambdaMotion     = lambdaMotion,
                lambdaGaze       = lambdaGaze,
                lambdaBio        = lambdaBio,
                thresholdReached = thresholdReached,
                shadowMode       = config.shadowMode,
            ))
        }
    }
}
