package com.capsconc.arcshield.llr.internal

import com.capsconc.arcshield.schema.biometric.HrSample
import com.capsconc.arcshield.schema.biometric.RrSample
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Rolling biometric statistics over a sliding time window, used to compute
 * Λ_bio components in the LLR gate (CLAUDE.md §4.1).
 *
 * Tracks:
 *  - Rolling HR mean and variance (Gaussian-shift GLR input)
 *  - Rolling RMSSD over successive R-R intervals (HRV-RMSSD ratio GLR input)
 *
 * Thread safety: NOT thread-safe. Drive from a single coroutine.
 * In [LlrGate] this is protected by a [kotlinx.coroutines.sync.Mutex].
 */
internal class RollingBioStats(val windowMs: Long = 5L * 60L * 1_000L) {

    private data class HrEntry(val timestampNanos: Long, val bpm: Int)
    private data class RrEntry(val timestampNanos: Long, val rrMs: Int)

    private val hrBuffer = ArrayDeque<HrEntry>()
    private val rrBuffer = ArrayDeque<RrEntry>()

    data class BioSnapshot(
        /** Mean HR in BPM over the rolling window. 0f if no samples yet. */
        val hrMeanBpm: Float,
        /** Variance of HR in BPM² over the rolling window. Floored to 1e-6. */
        val hrVarianceBpm: Float,
        /** RMSSD over successive R-R intervals in the rolling window. 0f if < 2 RR samples. */
        val rmssdMs: Float,
        val hasHr: Boolean,
        val hasRr: Boolean,
    )

    fun updateHr(sample: HrSample): BioSnapshot {
        val cutoff = sample.timestampNanos - windowMs * 1_000_000L
        while (hrBuffer.isNotEmpty() && hrBuffer.first().timestampNanos < cutoff) {
            hrBuffer.removeFirst()
        }
        hrBuffer.addLast(HrEntry(sample.timestampNanos, sample.bpm))
        return currentSnapshot()
    }

    fun updateRr(sample: RrSample): BioSnapshot {
        val cutoff = sample.timestampNanos - windowMs * 1_000_000L
        while (rrBuffer.isNotEmpty() && rrBuffer.first().timestampNanos < cutoff) {
            rrBuffer.removeFirst()
        }
        rrBuffer.addLast(RrEntry(sample.timestampNanos, sample.rrMs))
        return currentSnapshot()
    }

    fun currentSnapshot(): BioSnapshot {
        val hasHr = hrBuffer.isNotEmpty()
        val hrMean: Float
        val hrVariance: Float
        if (hasHr) {
            hrMean = hrBuffer.sumOf { it.bpm.toDouble() }.toFloat() / hrBuffer.size
            hrVariance = if (hrBuffer.size > 1) {
                val sumSqDiff = hrBuffer.sumOf { e ->
                    val d = e.bpm.toDouble() - hrMean
                    d * d
                }
                max((sumSqDiff / (hrBuffer.size - 1)).toFloat(), 1e-6f)
            } else 1e-6f
        } else {
            hrMean = 0f
            hrVariance = 1e-6f
        }

        val hasRr = rrBuffer.size >= 2
        val rmssdMs: Float = if (hasRr) {
            var sumSqDiff = 0.0
            for (i in 1 until rrBuffer.size) {
                val diff = (rrBuffer[i].rrMs - rrBuffer[i - 1].rrMs).toDouble()
                sumSqDiff += diff * diff
            }
            sqrt(sumSqDiff / (rrBuffer.size - 1)).toFloat()
        } else 0f

        return BioSnapshot(
            hrMeanBpm     = hrMean,
            hrVarianceBpm = hrVariance,
            rmssdMs       = rmssdMs,
            hasHr         = hasHr,
            hasRr         = hasRr,
        )
    }

    fun reset() {
        hrBuffer.clear()
        rrBuffer.clear()
    }
}
