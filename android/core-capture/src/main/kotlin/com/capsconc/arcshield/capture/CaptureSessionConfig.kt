package com.capsconc.arcshield.capture

import com.capsconc.arcshield.codec.TrackType
import com.capsconc.arcshield.llr.LlrConfig
import java.io.File

data class CaptureSessionConfig(
    val outputDir: File,
    val sessionId: String,
    val operatorId: String,
    val facilityId: String,
    val lineId: String,
    val captureSourceId: String,
    val biometricSourceId: String,
    val videoWidth: Int = 1920,
    val videoHeight: Int = 1080,
    val frameRateFps: Int = 30,
    val videoTargetBitrateBps: Int = 4_000_000,
    val audioSampleRateHz: Int = 48_000,
    val audioChannelCount: Int = 1,
    val wPreMs: Long = 30_000L,
    val wPostMs: Long = 60_000L,
    val iFrameDurationMs: Long = 90_000L,
    // Ring must hold at least W_pre + W_post plus a margin so the gate can
    // always find a full window on either side of the fire time.
    val ringBufferCapacityMs: Long = 210_000L,
    val llrConfig: LlrConfig = LlrConfig(),
    val epsSyncIntervalMs: Long = 300_000L,
    val metaTracks: List<TrackType> = listOf(
        TrackType.AccelMeta,
        TrackType.BiometricMeta,
        TrackType.ThermalMeta,
    ),
) {
    val ringCapacityNanos: Long  get() = ringBufferCapacityMs * 1_000_000L
    val wPreNanos: Long          get() = wPreMs * 1_000_000L
    val wPostNanos: Long         get() = wPostMs * 1_000_000L
    val iFrameDurationNanos: Long get() = iFrameDurationMs * 1_000_000L
}
