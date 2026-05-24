package com.capsconc.arcshield.llr

/**
 * A gate evaluation result. In shadow mode every eval tick produces one of these;
 * in production mode only events with [thresholdReached] = true are emitted.
 *
 * The [tools/shadow-mode-labeler] reads a serialized stream of these and presents
 * them for operator hand-labeling (TP / FP). The labeled set drives τ calibration
 * per CLAUDE.md §4.3.
 *
 * Component breakdown is included so the labeler can show which channel drove
 * each candidate event — useful for diagnosing false-positive sources.
 */
data class CandidateWindow(
    /** elapsedRealtimeNanos at moment of gate evaluation. */
    val detectedAtNanos:  Long,

    /** Total Λ = Λ_env + Λ_bio. */
    val lambda:           Float,

    /** Λ_env = Λ_acoustic + Λ_accel + Λ_motion + Λ_gaze. */
    val lambdaEnv:        Float,

    /** KL(baseline_spectrum ‖ current_spectrum). */
    val lambdaAcoustic:   Float,

    /** (rms − µ_baseline)² / (2σ²_baseline). Gaussian-shift GLR. */
    val lambdaAccel:      Float,

    /** Frame-to-frame video energy. 0.0 until source-camerax is wired (Phase 1). */
    val lambdaMotion:     Float,

    /** Sustained gaze dwell on anchor. 0.0 until gaze tracking is wired (Phase 1). */
    val lambdaGaze:       Float,

    /** HR delta + HRV-RMSSD ratio. 0.0 until Λ_bio is wired (next backlog item). */
    val lambdaBio:        Float,

    /** True when lambda ≥ config.tau. This is what the labeler marks TP or FP. */
    val thresholdReached: Boolean,

    val shadowMode:       Boolean,
)
