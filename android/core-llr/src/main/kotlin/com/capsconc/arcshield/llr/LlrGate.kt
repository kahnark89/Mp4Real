package com.capsconc.arcshield.llr

import android.os.SystemClock
import com.capsconc.arcshield.llr.internal.FrameDiffMotion
import com.capsconc.arcshield.llr.internal.KlDivergence
import com.capsconc.arcshield.llr.internal.RealFft
import com.capsconc.arcshield.llr.internal.RollingAccelRms
import com.capsconc.arcshield.llr.internal.RollingBioStats
import com.capsconc.arcshield.schema.biometric.AccelSample
import com.capsconc.arcshield.schema.biometric.HrSample
import com.capsconc.arcshield.schema.biometric.RrSample
import com.capsconc.arcshield.schema.capture.AudioFrame
import com.capsconc.arcshield.schema.capture.VideoFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 *   Λ_motion   — Gaussian-shift GLR on frame MAD (Y-plane)   ✅ active (0.0 when videoFrames = emptyFlow)
 *   Λ_gaze     — sustained dwell duration                    ✅ wired (0f until gaze source provides data)
 *
 * Phase 1 Λ_bio components:
 *   Λ_hr    — Gaussian-shift GLR on rolling HR mean          ✅ active
 *   Λ_rmssd — Gaussian-shift GLR on rolling RMSSD            ✅ active
 *   Λ_hrv_nl — SD1/SD2 + sample entropy nonlinear HRV        active when baseline.nonlinearHrvAvailable
 *   activity gate — accel-RMS activity class scales Λ_bio    ✅ active
 *
 * The combination rule Λ = Λ_env + Λ_bio assumes S_env ⊥ S_bio | E, Z
 * (CLAUDE.md §4.2). Do not change the combination rule without author sign-off.
 *
 * @param audioFrames  Flow of PCM frames from the phone microphone.
 * @param accelSamples Flow of accel samples (phone IMU or Polar onboard accel).
 * @param baseline     I-frame statistical snapshot from shift start.
 * @param config       Gate configuration including τ and shadow mode flag.
 * @param hrSamples    Flow of HR samples from [BiometricSource.heartRate].
 *                     Pass [emptyFlow] (default) when no BiometricSource is connected.
 * @param rrSamples    Flow of R-R intervals from [BiometricSource.rrIntervals].
 *                     Pass [emptyFlow] (default) when device doesn't support R-R.
 * @param videoFrames  Flow of NV21 frames from [CaptureSource.videoFrames].
 *                     Pass [emptyFlow] (default) when no camera source is connected.
 *                     When [baseline.motionAvailable] is false, Λ_motion is 0.0
 *                     regardless of this flow.
 * @param gazeDwellProvider Lambda returning the current gaze dwell duration in seconds.
 *                          Returns 0f by default (gaze not yet wired). Inject a real
 *                          provider when eye-tracking hardware is available (Meta Ray-Bans
 *                          Gen 2, Phase 2+). The provider is called on every eval tick.
 */
fun llrGate(
    audioFrames:       Flow<AudioFrame>,
    accelSamples:      Flow<AccelSample>,
    baseline:          LlrBaseline,
    config:            LlrConfig,
    hrSamples:         Flow<HrSample>   = emptyFlow(),
    rrSamples:         Flow<RrSample>   = emptyFlow(),
    videoFrames:       Flow<VideoFrame> = emptyFlow(),
    gazeDwellProvider: () -> Float      = { 0f },
): Flow<CandidateWindow> = channelFlow {

    // Shared mutable state updated by producer coroutines, read by the eval ticker.
    // AtomicReference ensures safe publication across coroutine boundaries.
    val latestSpectrum = AtomicReference<FloatArray?>(null)
    val rollingRms     = RollingAccelRms(config.accelWindowMs)
    val latestRmsRef   = AtomicReference(0f)

    // Bio stats: protected by mutex since HR and RR can arrive concurrently.
    val bioStats     = RollingBioStats()
    val bioMutex     = Mutex()
    val latestBioRef = AtomicReference<RollingBioStats.BioSnapshot?>(null)

    // Motion: single-producer (video frames arrive sequentially from camera).
    val frameDiff    = FrameDiffMotion()
    val latestMadRef = AtomicReference<Float?>(null)

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

    // ---- HR producer ----------------------------------------------------
    launch {
        hrSamples.collect { sample ->
            val snap = bioMutex.withLock { bioStats.updateHr(sample) }
            latestBioRef.set(snap)
        }
    }

    // ---- RR producer ----------------------------------------------------
    launch {
        rrSamples.collect { sample ->
            val snap = bioMutex.withLock { bioStats.updateRr(sample) }
            latestBioRef.set(snap)
        }
    }

    // ---- Video producer -------------------------------------------------
    launch {
        videoFrames.collect { frame ->
            val mad = frameDiff.update(frame)
            if (mad != null) latestMadRef.set(mad)
        }
    }

    // ---- Eval ticker ----------------------------------------------------
    // Runs every evalIntervalMs. Reads latest computed features and emits
    // a CandidateWindow. In shadow mode, emits regardless of threshold.
    // In production mode, emits only when Λ ≥ τ.
    while (isActive) {
        delay(config.evalIntervalMs)

        val spectrum = latestSpectrum.get()

        val lambdaAcoustic: Float = if (config.acousticEnabled && spectrum != null) {
            KlDivergence.compute(baseline.acousticSpectrum, spectrum)
        } else 0f

        // Accel RMS is always computed for the activity gate even when accelEnabled = false.
        val rms          = latestRmsRef.get()
        val lambdaAccel: Float = if (config.accelEnabled) {
            val dev = rms - baseline.accelRmsBaseline
            max(0f, (dev * dev) / (2f * baseline.accelRmsVariance))
        } else 0f

        val mad          = latestMadRef.get()
        val lambdaMotion: Float = if (config.motionEnabled && mad != null && baseline.motionAvailable) {
            val dev = mad - baseline.motionBaselineMad
            max(0f, (dev * dev) / (2f * baseline.motionVarianceMad))
        } else 0f

        // Λ_gaze: sustained attention dwell on visual anchor.
        // gazeEnabled = false by default (no eye-tracking HW in Phase 1).
        // When enabled, gazeDwellProvider must supply real fixation durations.
        val rawDwell  = gazeDwellProvider()
        val lambdaGaze: Float = if (config.gazeEnabled
            && config.gazeDwellVarianceSec > 0f
            && rawDwell > config.gazeDwellBaselineSec
        ) {
            val dev = rawDwell - config.gazeDwellBaselineSec
            max(0f, (dev * dev) / (2f * config.gazeDwellVarianceSec))
        } else 0f

        // ---- Λ_bio -------------------------------------------------------
        val bioSnap     = latestBioRef.get()
        val activityGate: Float
        val lambdaBio: Float

        if (bioSnap != null && baseline.biometricAvailable) {
            // Activity gate: high physical exertion confounds HR/HRV signal.
            // Uses raw accel RMS regardless of accelEnabled so gating always works.
            activityGate = when {
                rms >= config.vigorousAccelThresholdMg -> config.vigorousGateFactor
                rms >= config.moderateAccelThresholdMg -> config.moderateGateFactor
                rms >= config.lightAccelThresholdMg    -> config.lightGateFactor
                else                                   -> 1.0f
            }

            val lambdaHr: Float = if (config.hrEnabled && bioSnap.hasHr) {
                val delta = bioSnap.hrMeanBpm - baseline.hrBaselineBpm
                max(0f, (delta * delta) / (2f * baseline.hrVarianceBpm))
            } else 0f

            val lambdaRmssd: Float = if (config.rmssdEnabled && bioSnap.hasRr && baseline.rmssdBaselineMs > 0f) {
                val delta = bioSnap.rmssdMs - baseline.rmssdBaselineMs
                max(0f, (delta * delta) / (2f * baseline.rmssdVarianceMs))
            } else 0f

            val lambdaHrvNl: Float = if (config.hrvNlEnabled && bioSnap.hasNonlinearHrv && baseline.nonlinearHrvAvailable) {
                val lambdaSd1: Float = if (baseline.sd1VarianceMs > 0f) {
                    val d = bioSnap.sd1Ms - baseline.sd1BaselineMs
                    max(0f, (d * d) / (2f * baseline.sd1VarianceMs))
                } else 0f

                val lambdaSd2: Float = if (baseline.sd2VarianceMs > 0f) {
                    val d = bioSnap.sd2Ms - baseline.sd2BaselineMs
                    max(0f, (d * d) / (2f * baseline.sd2VarianceMs))
                } else 0f

                val lambdaSampEn: Float = if (!bioSnap.sampEn.isNaN()
                    && !baseline.sampEnBaseline.isNaN()
                    && baseline.sampEnVariance > 0f
                ) {
                    val d = bioSnap.sampEn - baseline.sampEnBaseline
                    max(0f, (d * d) / (2f * baseline.sampEnVariance))
                } else 0f

                lambdaSd1 + lambdaSd2 + lambdaSampEn
            } else 0f

            lambdaBio = activityGate * (lambdaHr + lambdaRmssd + lambdaHrvNl)
        } else {
            activityGate = 1.0f
            lambdaBio    = 0f
        }

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
                activityGate     = activityGate,
                thresholdReached = thresholdReached,
                shadowMode       = config.shadowMode,
            ))
        }
    }
}
