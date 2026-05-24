package com.capsconc.arcshield.llr

/**
 * Runtime configuration for the LLR gate. All fields have Phase 1 defaults
 * suitable for shadow-mode calibration.
 *
 * τ calibration protocol (CLAUDE.md §4.3):
 *   1. Run with tau = 0.0 and shadowMode = true for two weeks.
 *   2. Hand-label CandidateWindow events via tools/shadow-mode-labeler.
 *   3. Tune tau to ~80% TP / ≤20% FP.
 *   4. Lock tau and set shadowMode = false to begin live capture.
 */
data class LlrConfig(
    /**
     * Detection threshold. Fire when Λ ≥ tau.
     * 0.0 during calibration (all events logged). Empirically tuned before Phase 1 lock.
     */
    val tau: Float = 0.0f,

    /**
     * Shadow mode: emit CandidateWindow events but do not trigger container writes.
     * Always true for the first two weeks of deployment.
     */
    val shadowMode: Boolean = true,

    /** FFT window size in samples. Must be a power of 2. */
    val fftSize: Int = 1024,

    /** Rolling accel RMS window, per CLAUDE.md §4.1. */
    val accelWindowMs: Long = 5_000L,

    /** How often the gate evaluates Λ. 500 ms gives a responsive signal without thrash. */
    val evalIntervalMs: Long = 500L,

    /** W_pre: pre-trigger window appended to captured container, per CLAUDE.md §3.3. */
    val preWindowMs: Long = 30_000L,

    /** W_post: post-trigger window, per CLAUDE.md §3.3. */
    val postWindowMs: Long = 60_000L,
)
