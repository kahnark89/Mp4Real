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

    // ------------------------------------------------------------------
    // Per-channel enable flags. All default to true except gaze, which
    // requires eye-tracking hardware not available in Phase 1 (Meta
    // Ray-Bans Gen 2). Setting a flag to false zeroes out that component's
    // contribution to Λ without affecting other channels.
    // Note: accel producer still runs when accelEnabled = false because
    // the rolling RMS is needed for activity-gating Λ_bio.
    // ------------------------------------------------------------------

    val acousticEnabled: Boolean = true,
    val accelEnabled:    Boolean = true,
    val motionEnabled:   Boolean = true,
    /** Off by default — requires eye-tracking HW. Enable via Gate Tuning screen when available. */
    val gazeEnabled:     Boolean = false,
    val hrEnabled:       Boolean = true,
    val rmssdEnabled:    Boolean = true,
    val hrvNlEnabled:    Boolean = true,

    // ------------------------------------------------------------------
    // Λ_bio: activity gating thresholds and gate factors.
    // High physical activity confounds cardiovascular HRV signal, so Λ_bio
    // is scaled down during moderate/vigorous work (CLAUDE.md §4.1).
    // Thresholds are phone-accel RMS in mG (phone carried in a shirt pocket).
    // ------------------------------------------------------------------

    /** Phone-accel RMS (mG) above which activity is classified as light. */
    val lightAccelThresholdMg: Float = 200f,

    /** Phone-accel RMS (mG) above which activity is classified as moderate. */
    val moderateAccelThresholdMg: Float = 500f,

    /** Phone-accel RMS (mG) above which activity is classified as vigorous. */
    val vigorousAccelThresholdMg: Float = 1_000f,

    /** Λ_bio scaling factor for light activity (0.0–1.0). */
    val lightGateFactor: Float = 0.8f,

    /** Λ_bio scaling factor for moderate activity (0.0–1.0). */
    val moderateGateFactor: Float = 0.4f,

    /** Λ_bio scaling factor for vigorous activity (0.0–1.0). */
    val vigorousGateFactor: Float = 0.1f,

    /**
     * Baseline gaze dwell (seconds). 2s = typical operator scan pattern;
     * sustained fixation above this baseline triggers Λ_gaze.
     * Only used when gazeEnabled = true.
     */
    val gazeDwellBaselineSec: Float = 2.0f,

    /**
     * Gaze dwell variance (s²). σ ≈ 1.4s with default 2.0; Λ_gaze fires
     * meaningfully when dwell > ~4–5s. Only used when gazeEnabled = true.
     */
    val gazeDwellVarianceSec: Float = 2.0f,
)
