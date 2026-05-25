package com.capsconc.arcshield.codec

import java.io.File

/**
 * Container configuration. All durations tunable per facility — CLAUDE.md §3.3.
 * W_pre and W_post define the P-frame capture window around a gate-fire event.
 * iFrameDurationMs is the shift-start baseline (I-frame) recording length; the
 * LLR gate's null hypothesis H₀ is parameterised from this window.
 */
data class Mp4RealConfig(
    /** Pre-event window in ms. Default 30 s per CLAUDE.md §3.3. */
    val wPreMs: Long = 30_000L,

    /** Post-event window in ms. Default 60 s per CLAUDE.md §3.3. */
    val wPostMs: Long = 60_000L,

    /** I-frame baseline duration in ms. Valid range: 60 000–120 000 ms. */
    val iFrameDurationMs: Long = 90_000L,

    /** Directory where container (.mp4) and sidecar (.mp4real.json) are written. */
    val outputDir: File,

    val facilityId: String = "hollowell_ppvc_line1",
    val lineId: String = "ppvc_line_1",
)
