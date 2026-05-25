package com.capsconc.arcshield.source.polar

import com.capsconc.arcshield.schema.biometric.BiometricChannel

/**
 * Supported Polar wearables. CLAUDE.md §8.3:
 *   H10  — validation-phase ground truth (ECG 130 Hz, chest strap)
 *   VERITY_SENSE — daily operational device (optical armband, no ECG morphology)
 *
 * Strategy: run both during calibration, characterise Verity-vs-H10 R-R drift,
 * then ship Verity Sense for daily wear with periodic H10 spot-checks.
 */
enum class PolarDeviceType(
    val sourceId: String,
    val capabilities: Set<BiometricChannel>,
    /** H10 buffers ~30 min of data internally on link loss; Verity Sense does not. */
    val supportsOfflineRecording: Boolean,
) {
    H10(
        sourceId                 = "polar_h10_v1",
        capabilities             = setOf(
            BiometricChannel.HEART_RATE,
            BiometricChannel.RR_INTERVALS,
            BiometricChannel.ECG_WAVEFORM,
            BiometricChannel.ACCELEROMETER,
        ),
        supportsOfflineRecording = true,
    ),

    VERITY_SENSE(
        sourceId                 = "polar_verity_sense_v1",
        capabilities             = setOf(
            BiometricChannel.HEART_RATE,
            BiometricChannel.RR_INTERVALS,   // PPG-derived; no ECG morphology
            BiometricChannel.ACCELEROMETER,
        ),
        supportsOfflineRecording = false,
    ),
}
