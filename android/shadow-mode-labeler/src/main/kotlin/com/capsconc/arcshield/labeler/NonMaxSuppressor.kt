package com.capsconc.arcshield.labeler

import com.capsconc.arcshield.llr.CandidateWindow

/**
 * Reduces 57 600 gate-eval ticks per 8-hour shift to a manageable set of
 * distinct candidate events by keeping the highest-λ window in each
 * non-overlapping [suppressionNanos] bucket.
 *
 * Algorithm (O(n log n)):
 *  1. Sort by detectedAtNanos ascending.
 *  2. Walk forward; accumulate a running bucket maximum.
 *  3. When the next window falls outside the current bucket, flush the max and
 *     open a new bucket.
 *  4. Return the flushed peaks in chronological order.
 */
object NonMaxSuppressor {

    fun suppress(
        windows: List<CandidateWindow>,
        suppressionNanos: Long = 60_000_000_000L,   // 60 s default, matches W_post
    ): List<CandidateWindow> {
        if (windows.isEmpty()) return emptyList()

        val sorted = windows.sortedBy { it.detectedAtNanos }
        val result = mutableListOf<CandidateWindow>()

        var bucketStart = sorted.first().detectedAtNanos
        var bucketMax   = sorted.first()

        for (w in sorted.drop(1)) {
            if (w.detectedAtNanos - bucketStart < suppressionNanos) {
                if (w.lambda > bucketMax.lambda) bucketMax = w
            } else {
                result += bucketMax
                bucketStart = w.detectedAtNanos
                bucketMax   = w
            }
        }
        result += bucketMax   // flush final bucket

        return result   // already chronologically ordered
    }
}
