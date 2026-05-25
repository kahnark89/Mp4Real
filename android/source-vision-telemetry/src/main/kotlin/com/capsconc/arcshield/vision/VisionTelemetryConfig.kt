package com.capsconc.arcshield.vision

data class VisionTelemetryConfig(
    val channels: List<ChannelConfig>,
    val sampleIntervalMs: Long = 5_000L,
)
