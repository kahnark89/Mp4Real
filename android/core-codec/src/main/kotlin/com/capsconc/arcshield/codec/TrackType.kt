package com.capsconc.arcshield.codec

/**
 * The seven tracks defined in the mp4Real container — CLAUDE.md §3.1, FIG. 6.
 * Phase 1 ships the first five (Video through ThermalMeta).
 * VoiceMeta and PlcTelemetryMeta are wired in Phase 1 and Phase 2+ respectively.
 *
 * nominalIndex matches the track order in CLAUDE.md §3.1. The actual muxer track
 * index returned from [Mp4RealMuxer.addVideoTrack] / [addMetaTrack] may differ
 * if tracks are added out of order — always use the returned index for writes.
 */
sealed class TrackType {
    abstract val nominalIndex: Int
    abstract val description: String

    object Video            : TrackType() {
        override val nominalIndex = 0
        override val description  = "pov_video_hevc"
    }
    object Audio            : TrackType() {
        override val nominalIndex = 1
        override val description  = "acoustic_aac"
    }
    object AccelMeta        : TrackType() {
        override val nominalIndex = 2
        override val description  = "vibration_accel_meta"
    }
    object BiometricMeta    : TrackType() {
        override val nominalIndex = 3
        override val description  = "biometric_polar_meta"
    }
    object ThermalMeta      : TrackType() {
        override val nominalIndex = 4
        override val description  = "thermal_meta"
    }
    object VoiceMeta        : TrackType() {
        override val nominalIndex = 5
        override val description  = "voice_annotation_meta"
    }
    /** Gen 2+ — wired when PLC API surface is mapped (Phase 3). */
    object PlcTelemetryMeta : TrackType() {
        override val nominalIndex = 6
        override val description  = "plc_telemetry_meta"
    }

    companion object {
        /** The five Phase 1 tracks in canonical order. */
        val phase1Tracks: List<TrackType> =
            listOf(Video, Audio, AccelMeta, BiometricMeta, ThermalMeta)
    }
}
