package com.capsconc.arcshield.llr.internal

import com.capsconc.arcshield.schema.biometric.AccelSample
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Circular buffer that tracks accel magnitude RMS and variance over a sliding
 * time window (default 5 000 ms, per CLAUDE.md §4.1 "Accelerometer RMS over
 * a rolling 5-second window").
 *
 * Variance is tracked with Welford's online algorithm — no two-pass required,
 * numerically stable for float32 over a shift-length window.
 *
 * Thread safety: not thread-safe. Drive from a single coroutine.
 */
internal class RollingAccelRms(val windowMs: Long = 5_000L) {

    private data class Entry(val timestampNanos: Long, val magnitudeSq: Float)

    private val buffer = ArrayDeque<Entry>()

    // Welford running mean and M2 for variance
    private var count  = 0L
    private var mean   = 0.0
    private var m2     = 0.0

    data class RmsSnapshot(val rms: Float, val variance: Float)

    /**
     * Add a new sample and return the current RMS and variance over the window.
     * Old samples outside [windowMs] are evicted before computing.
     */
    fun update(sample: AccelSample): RmsSnapshot {
        val magnitudeSq = sample.xMg * sample.xMg +
                          sample.yMg * sample.yMg +
                          sample.zMg * sample.zMg

        buffer.addLast(Entry(sample.timestampNanos, magnitudeSq))

        // Welford update on new sample's sqrt magnitude
        val magnitude = sqrt(magnitudeSq)
        count++
        val delta  = magnitude - mean
        mean      += delta / count
        val delta2 = magnitude - mean
        m2        += delta * delta2

        // Evict samples outside the window
        val windowNanos = windowMs * 1_000_000L
        val cutoff      = sample.timestampNanos - windowNanos
        while (buffer.isNotEmpty() && buffer.first().timestampNanos < cutoff) {
            buffer.removeFirst()
        }

        // RMS over the retained window
        val windowMagSq = buffer.sumOf { it.magnitudeSq.toDouble() }
        val rms = if (buffer.isEmpty()) 0f else sqrt(windowMagSq / buffer.size).toFloat()

        val variance = if (count > 1) (m2 / (count - 1)).toFloat() else 0f
        return RmsSnapshot(rms = rms, variance = max(variance, 1e-6f))
    }

    fun currentRms(): Float {
        if (buffer.isEmpty()) return 0f
        val windowMagSq = buffer.sumOf { it.magnitudeSq.toDouble() }
        return sqrt(windowMagSq / buffer.size).toFloat()
    }

    fun reset() {
        buffer.clear()
        count = 0L; mean = 0.0; m2 = 0.0
    }
}
