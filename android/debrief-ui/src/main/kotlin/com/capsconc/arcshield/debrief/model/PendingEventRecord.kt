/*
 * Intellectual Property and Trademark Notice
 *
 * mp4Real™, ArcShield™, CIAER™, and CIAER+™ are trademarks of Capps Consulting
 * Company LLC. The multi-track cyber-physical capture architecture, the
 * application of log-likelihood ratio (LLR) gating to multimodal industrial
 * decision events, and the behavioral codebook discretization methods described
 * in this document are the proprietary intellectual property of Kahn Capps and
 * Capps Consulting Company LLC. Unauthorized commercial use, reproduction, or
 * implementation of the mp4Real™ container architecture or the CIAER™ and CIAER+™
 * schemas without explicit licensing is prohibited. All rights reserved.
 */

package com.capsconc.arcshield.debrief.model

import kotlinx.serialization.Serializable

/**
 * An event candidate awaiting HITL (Human-In-The-Loop) completion.
 *
 * Created by importing a [com.capsconc.arcshield.llr.CandidateWindow] from the shadow-mode
 * NDJSON log. All CIAER+ fields that require operator judgment start as null and are filled
 * in via the [com.capsconc.arcshield.debrief.ui.EventAnnotationScreen].
 *
 * When all required fields are non-null (and modelRevision is present when hypothesisConfirmed=false),
 * the record is considered complete and can be submitted to the backend corpus.
 */
@Serializable
data class PendingEventRecord(
    /** UUID v4 string assigned at import time. */
    val eventId: String,
    /** Date-stamped session identifier — matches the NDJSON log file name without extension. */
    val sessionId: String,
    val facilityId: String,
    val lineId: String,
    val operatorId: String,
    /** elapsedRealtimeNanos at the moment the LLR gate fired (from [CandidateWindow]). */
    val detectedAtNanos: Long,
    /** Total Λ = Λ_env + Λ_bio at gate evaluation time. */
    val lambda: Float,
    /** Λ_env component (acoustic + accel + motion + gaze). */
    val lambdaEnv: Float,
    /** Λ_bio component (HR-delta + RMSSD-deviation). */
    val lambdaBio: Float,
    /** True when ε_sync exceeded 250 ms in the surrounding window. */
    val lowSyncConfidence: Boolean = false,

    // -------------------------------------------------------------------------
    // HITL-completed fields — null until the operator fills them in
    // -------------------------------------------------------------------------

    /**
     * Controlled-vocabulary failure mode tag from the domain ontology.
     * See FAILURE_MODE_TAGS in [com.capsconc.arcshield.debrief.model.CiaerVocabulary].
     */
    val failureModeTag: String? = null,

    /**
     * Rasmussen SRK level: "SKILL" | "RULE" | "KNOWLEDGE".
     * See CLAUDE.md §2.3 INTUITION.
     */
    val srkLevel: String? = null,

    /** Operator's stated causal hypothesis for what triggered this event. */
    val causalHypothesis: String? = null,

    /**
     * Primary action type chosen by the operator.
     * One of the values in [com.capsconc.arcshield.debrief.model.CiaerVocabulary.ACTION_TYPES].
     */
    val actionType: String? = null,

    /** Operator's rationale for the chosen action. */
    val actionRationale: String? = null,

    /**
     * Whether the pre-event prediction matched the observed effect.
     * "CONFIRMED" | "PARTIAL" | "DISCONFIRMED" | "INDETERMINATE"
     */
    val predictionMatch: String? = null,

    /**
     * Event outcome tag.
     * "PROBLEM_PREVENTED" | "PROBLEM_MITIGATED" | "NO_CHANGE" | "ESCALATED" | "FAILED"
     */
    val outcomeTag: String? = null,

    /**
     * Whether the operator's causal hypothesis was confirmed by the effect.
     * When false, [modelRevision] is REQUIRED (schema invariant, CLAUDE.md §2.4).
     */
    val hypothesisConfirmed: Boolean? = null,

    /**
     * Required when [hypothesisConfirmed] is false.
     * Operator's revised understanding after disconfirmation — high-value learning input.
     */
    val modelRevision: String? = null,

    /**
     * Product quality impact assessed after the event.
     * "NO_IMPACT" | "MINOR_DEVIATION" | "MAJOR_DEVIATION" | "SCRAP"
     */
    val productQualityImpact: String? = null,

    /**
     * Operator-assigned graph weight for this event (0.0–1.0, default 0.5).
     * Influences retrieval weighting in the Twin advisory layer.
     */
    val graphWeight: Float = 0.5f,

    /**
     * Rejected alternatives considered during this event.
     * REQUIRED at KNOWLEDGE SRK level per CLAUDE.md §2.4 shadow_actions invariant.
     */
    val shadowActions: List<ShadowActionRecord> = emptyList(),
)

/**
 * A single rejected action alternative.
 *
 * Provides the behavioral cloning system with signal on what was considered and rejected,
 * not just the chosen action. See CLAUDE.md §2.3 SHADOW_ACTIONS.
 */
@Serializable
data class ShadowActionRecord(
    /** The action type that was considered but rejected. */
    val actionType: String,
    /** Operator's rationale for rejecting this alternative. */
    val rejectionRationale: String,
    /** Operator's confidence in the rejection decision (0.0–1.0). */
    val confidenceInRejection: Float,
)

/**
 * Controlled-vocabulary constants used across CIAER+ form fields.
 *
 * New failure mode tags require explicit registration in the domain ontology — do not
 * add free-text guesses from LLM inference (CLAUDE.md §12, item 4).
 */
object CiaerVocabulary {
    val FAILURE_MODE_TAGS = listOf(
        "material_segregation_funnel_flow",
        "die_drool",
        "melt_temp_high",
        "melt_temp_low",
        "motor_amp_spike",
        "screw_speed_surge",
        "line_speed_instability",
        "cooling_insufficiency",
        "pressure_spike_head",
        "output_rate_drop",
    )

    val SRK_LEVELS = listOf("SKILL", "RULE", "KNOWLEDGE")

    val ACTION_TYPES = listOf(
        "PARAMETER_ADJUST",
        "MECHANICAL_INSPECT",
        "MATERIAL_INTERVENE",
        "PROCESS_HALT",
        "MONITOR_HOLD",
        "ESCALATE",
    )

    val PREDICTION_MATCH_OPTIONS = listOf(
        "CONFIRMED",
        "PARTIAL",
        "DISCONFIRMED",
        "INDETERMINATE",
    )

    val OUTCOME_TAGS = listOf(
        "PROBLEM_PREVENTED",
        "PROBLEM_MITIGATED",
        "NO_CHANGE",
        "ESCALATED",
        "FAILED",
    )

    val PRODUCT_QUALITY_IMPACTS = listOf(
        "NO_IMPACT",
        "MINOR_DEVIATION",
        "MAJOR_DEVIATION",
        "SCRAP",
    )

    val TRIGGER_SOURCES = listOf(
        "OPERATOR_MANUAL",
        "GAZE_DWELL",
        "BIOMETRIC",
        "ANOMALY_DETECT",
        "SCHEDULED",
    )
}
