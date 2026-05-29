package com.capsconc.arcshield.llr

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Per-channel EWMA half-life configuration.
 * Half-lives are calibrated for an 8-hour PVC extrusion shift at Hollowell PPVC Line 1.
 * Shorter half-lives track faster-varying channels (accel); longer for slower physiology (RMSSD).
 */
data class AdaptiveBaselineConfig(
    val hrHalfLifeSec:       Float = 1_200f,                          // 20 min
    val rmssdHalfLifeSec:    Float = 1_800f,                          // 30 min
    val accelHalfLifeSec:    Float =   600f,                          // 10 min
    val acousticHalfLifeSec: Float = 1_500f,                          // 25 min
    val motionHalfLifeSec:   Float =   900f,                          // 15 min
    /** Rolling window for shadow-mode median gate. */
    val medianWindowNanos:   Long  = 30L * 60L * 1_000_000_000L,      // 30 min
    /** Flag drift when any channel mean shifts > this many initial-σ from the I-frame baseline. */
    val driftSigmaThreshold: Float = 2.0f,
)

/**
 * Per-tick sensor readings passed from the [llrGate] eval ticker to [AdaptiveLlrBaseline].
 * Nullable fields indicate that channel data is not yet available this tick.
 */
data class ChannelMeasurements(
    val timestampNanos: Long,
    /** Rolling accel RMS (mG). Always present; feeds activity gate regardless of accelEnabled. */
    val accelRms:    Float,
    /** Normalized power spectrum (fftSize/2+1 bins). Null until first audio frame arrives. */
    val spectrum:    FloatArray? = null,
    /** Current HR (BPM). Null if no BiometricSource is connected or no HR data yet. */
    val hrBpm:       Float?      = null,
    /** Current RMSSD (ms). Null when fewer than 2 R-R intervals have been received. */
    val rmssdMs:     Float?      = null,
    /** Current frame-to-frame MAD (Y-plane, 0–255). Null if no video source is connected. */
    val motionMad:   Float?      = null,
    val sd1Ms:       Float?      = null,
    val sd2Ms:       Float?      = null,
    /** Null when SampEn is NaN or unavailable. */
    val sampEn:      Float?      = null,
)

/**
 * Adaptive per-channel EWMA baseline for the LLR gate.
 *
 * Replaces the static I-frame [LlrBaseline] to prevent drift-driven false positives
 * over 8-hour shifts ("static baseline temporal drift", CURRENT_PHASE.md §7 Drift Watch).
 *
 * Two invariant safeguards prevent genuine events from collapsing into the null hypothesis:
 *
 * 1. **Suppression window** — After a gate fire, EWMA updates are hard-blocked for
 *    [config.postWindowMs] (call [suppress] from [llrGate] when the gate fires in
 *    production mode). This is the load-bearing invariant from CLAUDE.md §1 C1.
 *
 * 2. **Median gate** — Updates proceed only when the current lambda is strictly below
 *    the rolling median of the last 30 minutes. Events above the median are anomalies;
 *    absorbing them would bias the null hypothesis upward.
 *
 * **Thread safety**: NOT thread-safe. Must be called only from the eval ticker coroutine
 * within [llrGate] (single-coroutine eval loop).
 *
 * @param initialBaseline  The I-frame baseline from shift start. EWMA is seeded from this.
 * @param config           Half-life and gate configuration.
 */
class AdaptiveLlrBaseline(
    initialBaseline: LlrBaseline,
    val config: AdaptiveBaselineConfig = AdaptiveBaselineConfig(),
) {

    companion object {
        private const val LN2           = 0.6931472f
        private const val VARIANCE_FLOOR = 1e-6f
    }

    // ---- EWMA state: scalar channels ----------------------------------------

    private var accelMean: Float = initialBaseline.accelRmsBaseline
    private var accelVar:  Float = initialBaseline.accelRmsVariance

    private var hrMean: Float = initialBaseline.hrBaselineBpm
    private var hrVar:  Float = initialBaseline.hrVarianceBpm

    private var rmssdMean: Float = initialBaseline.rmssdBaselineMs
    private var rmssdVar:  Float = initialBaseline.rmssdVarianceMs

    private var motionMean: Float = initialBaseline.motionBaselineMad
    private var motionVar:  Float = initialBaseline.motionVarianceMad

    private var sd1Mean:    Float = initialBaseline.sd1BaselineMs
    private var sd1Var:     Float = initialBaseline.sd1VarianceMs
    private var sd2Mean:    Float = initialBaseline.sd2BaselineMs
    private var sd2Var:     Float = initialBaseline.sd2VarianceMs
    private var sampEnMean: Float = initialBaseline.sampEnBaseline
    private var sampEnVar:  Float = initialBaseline.sampEnVariance

    // ---- EWMA state: acoustic spectrum (per-bin) ----------------------------

    private val acousticMean: FloatArray = initialBaseline.acousticSpectrum.copyOf()
    private val acousticVar:  FloatArray = initialBaseline.acousticSpectrumVariance.copyOf()

    // ---- Metadata from initial baseline (preserved across all snapshots) ----

    private val capturedAtNanos       = initialBaseline.capturedAtNanos
    private val biometricAvailable    = initialBaseline.biometricAvailable
    private val nonlinearHrvAvailable = initialBaseline.nonlinearHrvAvailable
    private val motionAvailable       = initialBaseline.motionAvailable

    // ---- Initial values for drift detection ---------------------------------

    private val initAccelMean = initialBaseline.accelRmsBaseline
    private val initAccelStd  = sqrt(max(VARIANCE_FLOOR, initialBaseline.accelRmsVariance))
    private val initHrMean    = initialBaseline.hrBaselineBpm
    private val initHrStd     = sqrt(max(VARIANCE_FLOOR, initialBaseline.hrVarianceBpm))

    // ---- Suppression window -------------------------------------------------

    private var suppressUntilNanos: Long = Long.MIN_VALUE

    // ---- Rolling lambda history for median gate (30-min window) -------------
    // Populated BEFORE adding the current entry so the median reflects history only.
    // Entry: (timestampNanos, lambda).

    private val lambdaHistory = ArrayDeque<Pair<Long, Float>>()

    // ---- EWMA update timestamp ----------------------------------------------

    private var lastEwmaUpdateNanos: Long = initialBaseline.capturedAtNanos

    // ---- Snapshot cache -----------------------------------------------------

    private var snapshotDirty           = false
    private var cachedSnapshot: LlrBaseline = initialBaseline

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Returns the current EWMA state as an [LlrBaseline] for use in the eval ticker.
     * The acoustic spectrum is re-normalized on each build so KL divergence remains valid.
     * Cached between updates; O(1) when the state has not changed since last call.
     */
    fun snapshot(): LlrBaseline {
        if (!snapshotDirty) return cachedSnapshot
        cachedSnapshot = LlrBaseline(
            acousticSpectrum          = normalizeSpectrum(acousticMean),
            acousticSpectrumVariance  = acousticVar.copyOf(),
            accelRmsBaseline          = accelMean,
            accelRmsVariance          = max(VARIANCE_FLOOR, accelVar),
            capturedAtNanos           = capturedAtNanos,
            durationMs                = 0L,
            hrBaselineBpm             = hrMean,
            hrVarianceBpm             = max(VARIANCE_FLOOR, hrVar),
            rmssdBaselineMs           = rmssdMean,
            rmssdVarianceMs           = max(VARIANCE_FLOOR, rmssdVar),
            biometricAvailable        = biometricAvailable,
            sd1BaselineMs             = sd1Mean,
            sd1VarianceMs             = max(VARIANCE_FLOOR, sd1Var),
            sd2BaselineMs             = sd2Mean,
            sd2VarianceMs             = max(VARIANCE_FLOOR, sd2Var),
            sampEnBaseline            = sampEnMean,
            sampEnVariance            = max(VARIANCE_FLOOR, sampEnVar),
            nonlinearHrvAvailable     = nonlinearHrvAvailable,
            motionBaselineMad         = motionMean,
            motionVarianceMad         = max(VARIANCE_FLOOR, motionVar),
            motionAvailable           = motionAvailable,
        )
        snapshotDirty = false
        return cachedSnapshot
    }

    /**
     * Block EWMA updates until [nowNanos] + [durationNanos].
     * Call this from [llrGate] when the gate fires in production mode
     * ([CandidateWindow.thresholdReached] = true and NOT in shadow mode).
     */
    fun suppress(nowNanos: Long, durationNanos: Long) {
        suppressUntilNanos = nowNanos + durationNanos
    }

    /**
     * Conditionally advance the EWMA baseline from [measurements].
     *
     * No-op when either safeguard is active:
     * - Inside the suppression window (within W_post of a triggered event).
     * - lambda ≥ rolling median lambda (current readings are above ambient background).
     *
     * @param lambda       Total Λ computed this eval tick (Λ_env + Λ_bio).
     * @param measurements Raw channel readings from the same eval tick.
     */
    fun updateIfQuiescent(lambda: Float, measurements: ChannelMeasurements) {
        val now = measurements.timestampNanos

        // Compute median from history *before* adding current entry so the gate
        // reflects the ambient background distribution, not the current reading.
        val historicalMedian = if (lambdaHistory.isEmpty()) Float.MAX_VALUE
                               else medianOf(lambdaHistory)

        // Maintain rolling history window
        lambdaHistory.addLast(now to lambda)
        val cutoff = now - config.medianWindowNanos
        while (lambdaHistory.isNotEmpty() && lambdaHistory.first().first < cutoff) {
            lambdaHistory.removeFirst()
        }

        // Guard 1: suppression window
        if (now < suppressUntilNanos) return

        // Guard 2: median gate — only adapt on quiescent ticks
        if (lambda >= historicalMedian) return

        val dtNanos = (now - lastEwmaUpdateNanos).coerceAtLeast(0L)
        val dtSec   = dtNanos / 1_000_000_000f
        lastEwmaUpdateNanos = now

        // Accel (always present)
        val alphaAccel = alpha(config.accelHalfLifeSec, dtSec)
        accelVar  = ewmaVar(accelVar, accelMean, measurements.accelRms, alphaAccel)
        accelMean = ewmaMean(accelMean, measurements.accelRms, alphaAccel)

        // Acoustic spectrum (per-bin)
        val sp = measurements.spectrum
        if (sp != null && sp.size == acousticMean.size) {
            val alphaA = alpha(config.acousticHalfLifeSec, dtSec)
            for (i in acousticMean.indices) {
                acousticVar[i]  = ewmaVar(acousticVar[i], acousticMean[i], sp[i], alphaA)
                acousticMean[i] = ewmaMean(acousticMean[i], sp[i], alphaA)
            }
        }

        // HR
        val hrBpm = measurements.hrBpm
        if (hrBpm != null && biometricAvailable) {
            val alphaHr = alpha(config.hrHalfLifeSec, dtSec)
            hrVar  = ewmaVar(hrVar, hrMean, hrBpm, alphaHr)
            hrMean = ewmaMean(hrMean, hrBpm, alphaHr)
        }

        // RMSSD
        val rmssdMs = measurements.rmssdMs
        if (rmssdMs != null && biometricAvailable) {
            val alphaR = alpha(config.rmssdHalfLifeSec, dtSec)
            rmssdVar  = ewmaVar(rmssdVar, rmssdMean, rmssdMs, alphaR)
            rmssdMean = ewmaMean(rmssdMean, rmssdMs, alphaR)
        }

        // Nonlinear HRV (SD1, SD2, SampEn — all R-R derived, use rmssd half-life)
        if (nonlinearHrvAvailable) {
            val alphaHrv = alpha(config.rmssdHalfLifeSec, dtSec)
            measurements.sd1Ms?.let { x ->
                sd1Var  = ewmaVar(sd1Var, sd1Mean, x, alphaHrv)
                sd1Mean = ewmaMean(sd1Mean, x, alphaHrv)
            }
            measurements.sd2Ms?.let { x ->
                sd2Var  = ewmaVar(sd2Var, sd2Mean, x, alphaHrv)
                sd2Mean = ewmaMean(sd2Mean, x, alphaHrv)
            }
            val se = measurements.sampEn
            if (se != null && !se.isNaN()) {
                sampEnVar  = ewmaVar(sampEnVar, sampEnMean, se, alphaHrv)
                sampEnMean = ewmaMean(sampEnMean, se, alphaHrv)
            }
        }

        // Motion (frame MAD)
        val mad = measurements.motionMad
        if (mad != null && motionAvailable) {
            val alphaM = alpha(config.motionHalfLifeSec, dtSec)
            motionVar  = ewmaVar(motionVar, motionMean, mad, alphaM)
            motionMean = ewmaMean(motionMean, mad, alphaM)
        }

        snapshotDirty = true
    }

    /**
     * Returns true when the accel or HR channel mean has drifted more than
     * [AdaptiveBaselineConfig.driftSigmaThreshold] σ from the initial I-frame baseline.
     * Use this to decide whether to write a fresh I-frame checkpoint to the container.
     */
    fun hasDriftedSignificantly(): Boolean {
        val accelDrift = abs(accelMean - initAccelMean) > config.driftSigmaThreshold * initAccelStd
        val hrDrift    = biometricAvailable &&
            abs(hrMean - initHrMean) > config.driftSigmaThreshold * initHrStd
        return accelDrift || hrDrift
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /** EWMA smoothing coefficient. α ∈ [0, 1]; α=0 means no update (dt=0 or infinite half-life). */
    private fun alpha(halfLifeSec: Float, dtSec: Float): Float {
        if (halfLifeSec <= 0f || dtSec <= 0f) return 0f
        return 1f - exp(-dtSec * LN2 / halfLifeSec)
    }

    /** Update EWMA mean. Uses the old mean; call AFTER ewmaVar. */
    private fun ewmaMean(mean: Float, x: Float, alpha: Float): Float =
        mean + alpha * (x - mean)

    /**
     * Update EWMA variance. Uses the old mean — must be called BEFORE updating the mean.
     * Formula: σ²_new = (1−α)(σ²_old + α·inc²), floored to [VARIANCE_FLOOR].
     */
    private fun ewmaVar(variance: Float, prevMean: Float, x: Float, alpha: Float): Float {
        val inc = x - prevMean
        return max(VARIANCE_FLOOR, (1f - alpha) * (variance + alpha * inc * inc))
    }

    private fun normalizeSpectrum(spec: FloatArray): FloatArray {
        val sum = spec.sum()
        if (sum <= 0f) return spec.copyOf()
        return FloatArray(spec.size) { i -> spec[i] / sum }
    }

    private fun medianOf(buffer: ArrayDeque<Pair<Long, Float>>): Float {
        val values = buffer.map { it.second }.sorted()
        val mid = values.size / 2
        return if (values.size % 2 == 0) (values[mid - 1] + values[mid]) / 2f
               else values[mid]
    }
}
