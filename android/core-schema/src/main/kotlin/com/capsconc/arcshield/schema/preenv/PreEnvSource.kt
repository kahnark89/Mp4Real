package com.capsconc.arcshield.schema.preenv

import kotlinx.coroutines.flow.Flow

/** Timestamp in elapsedRealtimeNanos (phone monotonic clock). */
data class TempSample(
    val timestampNanos: Long,
    val tempF: Float,
    val source: String,  // "open_meteo" | "bt_ir_thermometer" | "manual"
)

/**
 * Baseline snapshot taken at shift start (60–120 s of all-channel baseline).
 * Maps to the pre_env phase in the CIAER+ schema (CLAUDE.md §2.3).
 */
data class PreEnvSnapshot(
    val capturedAtNanos: Long,
    val ambientTempF: Float?,
    val shiftPhase: String?,        // "startup" | "steady_state" | "shutdown"
    val materialBatchId: String?,
    val crewStateTag: String?,      // "full_crew" | "short_staffed"
    val sourceId: String,
)

/**
 * Provider of pre-shift environmental baseline data.
 *
 * Gen 1 implementation: Open-Meteo ambient temperature proxy.
 * Gen 2 implementation: BT IR thermometer from line-adjacent measurement point.
 *
 * All implementations expose ambient temperature as a [Flow] for continuous
 * monitoring; a one-shot [captureBaseline] for shift-start I-frame construction.
 */
interface PreEnvSource {
    suspend fun captureBaseline(shiftPhase: String? = null): PreEnvSnapshot
    fun ambientTemp(): Flow<TempSample>
    val sourceId: String
}
