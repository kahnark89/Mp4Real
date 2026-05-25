package com.capsconc.arcshield.llr

import android.os.SystemClock
import com.capsconc.arcshield.llr.internal.FrameDiffMotion
import com.capsconc.arcshield.llr.internal.RealFft
import com.capsconc.arcshield.llr.internal.RollingAccelRms
import com.capsconc.arcshield.schema.biometric.AccelSample
import com.capsconc.arcshield.schema.biometric.HrSample
import com.capsconc.arcshield.schema.biometric.RrSample
import com.capsconc.arcshield.schema.capture.AudioFrame
import com.capsconc.arcshield.schema.capture.VideoFrame
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Collects sensor data over the I-frame period and returns a fully-populated
 * [LlrBaseline] for use by [llrGate] for the rest of the shift.
 *
 * Trigger this once at shift start (operator-initiated in Phase 1). The function
 * runs for [durationMs] (default 90 s), collecting all provided flows concurrently,
 * then computes H₀ statistics and returns. If a flow completes before the timeout
 * (e.g. [emptyFlow] for optional bio channels), that channel contributes zero samples
 * and its baseline fields are zero / unavailable.
 *
 * CLAUDE.md §3.3 (I-frame semantics) / §11 (Phase 1 acceptance criteria).
 *
 * @param clock Injectable monotonic timestamp source. Default uses
 *   [SystemClock.elapsedRealtimeNanos]; override in JVM tests to avoid Android stubs.
 */
suspend fun buildBaseline(
    audioFrames:  Flow<AudioFrame>,
    accelSamples: Flow<AccelSample>,
    config:       LlrConfig        = LlrConfig(),
    hrSamples:    Flow<HrSample>   = emptyFlow(),
    rrSamples:    Flow<RrSample>   = emptyFlow(),
    videoFrames:  Flow<VideoFrame> = emptyFlow(),
    durationMs:   Long             = 90_000L,
    clock:        () -> Long       = { SystemClock.elapsedRealtimeNanos() },
): LlrBaseline {

    val startNanos = clock()

    // ---- Accumulators (mutated inside coroutineScope, read after it exits) ------

    val spectralAcc = SpectralAccumulator(config.fftSize)
    val rollingRms  = RollingAccelRms(config.accelWindowMs)

    // Welford online stats for the rolling-RMS time series (accel baseline)
    var accelCount = 0L
    var accelMean  = 0.0
    var accelM2    = 0.0

    val hrValues: MutableList<Int>  = mutableListOf()
    val rrValues: MutableList<Int>  = mutableListOf()

    // Motion MAD accumulator (Welford online)
    val motionDiff = FrameDiffMotion()
    var motionCount = 0L
    var motionMean  = 0.0
    var motionM2    = 0.0

    // ---- Collect all flows concurrently for durationMs --------------------------
    // withTimeoutOrNull cancels all launchers when the timer fires.
    // Finite test flows complete naturally before the timer; both paths are safe.
    withTimeoutOrNull(durationMs) {
        coroutineScope {
            launch {
                audioFrames.collect { frame ->
                    val spec = RealFft.powerSpectrum(frame.samples, config.fftSize)
                    spectralAcc.update(spec)
                }
            }
            launch {
                accelSamples.collect { sample ->
                    val snap = rollingRms.update(sample)
                    accelCount++
                    val d  = snap.rms.toDouble() - accelMean
                    accelMean  += d / accelCount
                    val d2 = snap.rms.toDouble() - accelMean
                    accelM2 += d * d2
                }
            }
            launch { hrSamples.collect { hrValues.add(it.bpm) } }
            launch { rrSamples.collect { rrValues.add(it.rrMs) } }
            launch {
                videoFrames.collect { frame ->
                    val mad = motionDiff.update(frame)
                    if (mad != null) {
                        motionCount++
                        val d  = mad.toDouble() - motionMean
                        motionMean  += d / motionCount
                        val d2 = mad.toDouble() - motionMean
                        motionM2 += d * d2
                    }
                }
            }
        }
    }

    val endNanos     = clock()
    val capturedMs   = (endNanos - startNanos) / 1_000_000L

    // ---- Build final stats -------------------------------------------------------

    val (meanSpec, varSpec) = spectralAcc.build()

    val accelRmsBaseline = accelMean.toFloat()
    val accelRmsVariance = if (accelCount > 1)
        max((accelM2 / (accelCount - 1)).toFloat(), 1e-6f)
    else 1e-6f

    val hasBio   = hrValues.isNotEmpty()
    val hrMean   = if (hasBio) hrValues.average().toFloat() else 0f
    val hrVar: Float = if (hrValues.size > 1) {
        val ss = hrValues.sumOf { bpm -> (bpm.toDouble() - hrMean).let { it * it } }
        max((ss / (hrValues.size - 1)).toFloat(), 1e-6f)
    } else 1e-6f

    val (rmssdMean, rmssdVar) = computeRmssdStats(rrValues)

    val hasMotion         = motionCount > 0
    val motionBaselineMad = motionMean.toFloat()
    val motionVarianceMad = if (motionCount > 1)
        max((motionM2 / (motionCount - 1)).toFloat(), 1e-6f)
    else 1e-6f

    return LlrBaseline(
        acousticSpectrum         = meanSpec,
        acousticSpectrumVariance = varSpec,
        accelRmsBaseline         = accelRmsBaseline,
        accelRmsVariance         = accelRmsVariance,
        capturedAtNanos          = startNanos,
        durationMs               = capturedMs,
        hrBaselineBpm            = hrMean,
        hrVarianceBpm            = hrVar,
        rmssdBaselineMs          = rmssdMean,
        rmssdVarianceMs          = rmssdVar,
        biometricAvailable       = hasBio,
        motionBaselineMad        = motionBaselineMad,
        motionVarianceMad        = motionVarianceMad,
        motionAvailable          = hasMotion,
    )
}

// ---- Internal helpers -------------------------------------------------------

/**
 * Per-bin Welford online accumulator for the acoustic power spectrum.
 * Tracks mean and variance of each FFT bin across all accumulated frames.
 * Falls back to a uniform distribution when no frames were received.
 */
private class SpectralAccumulator(fftSize: Int) {
    private val nBins = fftSize / 2 + 1
    private val mean  = FloatArray(nBins)
    private val m2    = FloatArray(nBins)
    private var count = 0L

    fun update(spectrum: FloatArray) {
        require(spectrum.size == nBins) {
            "spectrum.size (${spectrum.size}) != expected $nBins"
        }
        count++
        for (k in 0 until nBins) {
            val d  = spectrum[k] - mean[k]
            mean[k] += d / count.toFloat()
            val d2 = spectrum[k] - mean[k]
            m2[k]  += d * d2
        }
    }

    /** Returns (meanSpectrum, varianceSpectrum). Fallback uniform when count = 0. */
    fun build(): Pair<FloatArray, FloatArray> {
        if (count == 0L) {
            val u = FloatArray(nBins) { 1f / nBins }
            return Pair(u, FloatArray(nBins) { 1e-6f })
        }
        val variance = FloatArray(nBins) { k ->
            if (count > 1) max((m2[k] / (count - 1).toFloat()), 1e-6f) else 1e-6f
        }
        return Pair(mean.copyOf(), variance)
    }
}

/**
 * Computes (mean RMSSD, variance of RMSSD) from a flat list of R-R intervals.
 *
 * Mean RMSSD is computed over all intervals. Variance is estimated from
 * overlapping 20-beat sub-windows (stride 10 beats), which gives ~10 estimates
 * over the 90-second I-frame at a typical resting HR (~112 beats). Returns
 * (0f, 1e-6f) when fewer than 2 R-R intervals are available.
 */
private fun computeRmssdStats(rrList: List<Int>): Pair<Float, Float> {
    if (rrList.size < 2) return Pair(0f, 1e-6f)

    val overall = rmssdOf(rrList)

    // Sub-window RMSSD estimates for variance
    val winBeats    = 20
    val strideBeats = 10
    val subRmssds   = mutableListOf<Float>()
    var start = 0
    while (start + winBeats <= rrList.size) {
        subRmssds.add(rmssdOf(rrList.subList(start, start + winBeats)))
        start += strideBeats
    }

    val variance: Float = if (subRmssds.size > 1) {
        val m  = subRmssds.average().toFloat()
        val ss = subRmssds.sumOf { r -> (r - m).toDouble() * (r - m) }
        max((ss / (subRmssds.size - 1)).toFloat(), 1e-6f)
    } else 1e-6f

    return Pair(overall, variance)
}

private fun rmssdOf(rr: List<Int>): Float {
    if (rr.size < 2) return 0f
    var ss = 0.0
    for (i in 1 until rr.size) {
        val d = (rr[i] - rr[i - 1]).toDouble()
        ss += d * d
    }
    return sqrt(ss / (rr.size - 1)).toFloat()
}
