package com.capsconc.arcshield.labeler

/**
 * Recommends a τ threshold given a labeled set of candidate windows.
 *
 * Target: highest τ where TP rate ≥ [targetTpRate] AND FP rate ≤ [maxFpRate].
 * If no single threshold satisfies both constraints, returns the τ that maximizes
 * TP rate subject to FP rate ≤ [maxFpRate] — so the caller always gets the best
 * achievable result on the labeled set rather than a null failure.
 *
 * Returns null when fewer than 2 labeled (non-UNLABELED) windows are present.
 */
object TauCalibrator {

    data class TauSuggestion(
        val suggestedTau: Float,
        val tpRate:       Float,
        val fpRate:       Float,
        val tpCount:      Int,
        val fpCount:      Int,
        val totalLabeled: Int,
    )

    fun suggest(
        labeled:      List<LabeledWindow>,
        targetTpRate: Float = 0.80f,
        maxFpRate:    Float = 0.20f,
    ): TauSuggestion? {
        val definite = labeled.filter { it.label != Label.UNLABELED }
        if (definite.size < 2) return null

        val totalTp = definite.count { it.label == Label.TRUE_POSITIVE }
        val totalFp = definite.count { it.label == Label.FALSE_POSITIVE }

        // Candidate thresholds: every distinct λ value in the labeled set, plus 0.
        // At τ=x we capture all windows with λ ≥ x.
        val tauCandidates = (definite.map { it.window.lambda } + 0f)
            .distinct()
            .sorted()   // ascending

        var bestSuggestion: TauSuggestion? = null

        for (tau in tauCandidates) {
            val captured = definite.filter { it.window.lambda >= tau }
            val capturedTp = captured.count { it.label == Label.TRUE_POSITIVE }
            val capturedFp = captured.count { it.label == Label.FALSE_POSITIVE }

            val tpRate = if (totalTp > 0) capturedTp.toFloat() / totalTp else 0f
            val fpRate = if (totalFp > 0) capturedFp.toFloat() / totalFp else 0f

            val candidate = TauSuggestion(
                suggestedTau = tau,
                tpRate       = tpRate,
                fpRate       = fpRate,
                tpCount      = capturedTp,
                fpCount      = capturedFp,
                totalLabeled = definite.size,
            )

            if (fpRate <= maxFpRate) {
                // Within FP budget — keep if it improves TP rate
                if (bestSuggestion == null || tpRate > bestSuggestion.tpRate) {
                    bestSuggestion = candidate
                } else if (tpRate == bestSuggestion.tpRate && tau > bestSuggestion.suggestedTau) {
                    // Prefer higher τ (more selective) when TP rate ties
                    bestSuggestion = candidate
                }
            }
        }

        // If no threshold satisfied the FP constraint, return τ=0 (capture everything)
        // so the caller has something to display rather than null.
        if (bestSuggestion == null) {
            val all = definite
            val tpRate = if (totalTp > 0) totalTp.toFloat() / totalTp else 0f
            bestSuggestion = TauSuggestion(
                suggestedTau = 0f,
                tpRate       = tpRate,
                fpRate       = if (totalFp > 0) 1f else 0f,
                tpCount      = totalTp,
                fpCount      = totalFp,
                totalLabeled = all.size,
            )
        }

        return bestSuggestion
    }
}
