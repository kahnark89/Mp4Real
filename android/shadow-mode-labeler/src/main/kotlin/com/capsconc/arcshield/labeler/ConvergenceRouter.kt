package com.capsconc.arcshield.labeler

/**
 * Outcome routing for multi-operator offline playback review (W-015).
 *
 * Convergence:  secondary operator agrees with primary label
 *               → status = CONFIRMED; graph_weight of the matched primitive
 *                  is incremented on the backend when the record is ingested.
 *
 * Divergence:   secondary operator recommends a different path
 *               → status = INDETERMINATE (partitioned to Q'); the chain is
 *                  written to .labels.json with provenance_class = HUMAN but
 *                  will not update C until R_phys arrives (CLAUDE.md §6).
 *
 * Phase 1: primary label = the first label assigned to a window by any operator
 * in this labeling session. Subsequent labels are compared against it.
 */
object ConvergenceRouter {

    data class RoutingResult(
        val status:         String,   // "CONFIRMED" | "INDETERMINATE"
        val provenanceClass: String,  // always "HUMAN" for human-labeled windows
    )

    /**
     * Determines routing for a window that has been re-labeled by a secondary reviewer.
     *
     * [primaryLabel]   = the label from the first reviewer (or this session's initial label)
     * [secondaryLabel] = the label just applied by the secondary reviewer
     */
    fun route(primaryLabel: Label, secondaryLabel: Label): RoutingResult {
        val converges = primaryLabel == secondaryLabel
        return RoutingResult(
            status          = if (converges) "CONFIRMED" else "INDETERMINATE",
            provenanceClass = "HUMAN",
        )
    }

    /**
     * First-pass labeling (single operator, no prior label to compare against).
     * Defaults to CONFIRMED so single-operator sessions produce clean exports.
     */
    fun firstPass(): RoutingResult = RoutingResult(
        status          = "CONFIRMED",
        provenanceClass = "HUMAN",
    )
}
