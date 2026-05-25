package com.capsconc.arcshield.schema.telemetry

import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class TelemetrySample(
    val timestampNanos: Long,
    val channelId: String,
    val value: Double,
    val unit: String,
)

data class TelemetrySnapshot(
    val capturedAtNanos: Long,
    val channels: Map<String, TelemetrySample>,
)

interface PlcTelemetrySource {
    fun channel(channelId: String): Flow<TelemetrySample>
    suspend fun snapshotAt(t: Instant): TelemetrySnapshot
    val availableChannels: Set<String>
}
