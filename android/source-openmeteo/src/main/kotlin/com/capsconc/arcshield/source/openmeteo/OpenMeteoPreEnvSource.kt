package com.capsconc.arcshield.source.openmeteo

import android.os.SystemClock
import com.capsconc.arcshield.schema.preenv.PreEnvSnapshot
import com.capsconc.arcshield.schema.preenv.PreEnvSource
import com.capsconc.arcshield.schema.preenv.TempSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * [PreEnvSource] implementation using the Open-Meteo free weather API.
 *
 * Phase 1 proxy: ambient temperature from the nearest weather station.
 * Gen 2 upgrade: replace with BT IR thermometer at the line measurement point.
 * The upgrade is a DI binding change; no structural rewrite required.
 *
 * Hollowell Industries, Helena-West Helena, AR:
 *   latitude  = 34.5312° N
 *   longitude = −90.5973° W
 *
 * @param latitudeDeg  Facility latitude in decimal degrees.
 * @param longitudeDeg Facility longitude in decimal degrees.
 * @param pollIntervalMs How often ambientTemp() emits a new reading (default 5 min).
 */
class OpenMeteoPreEnvSource(
    latitudeDeg: Double  = HOLLOWELL_LAT,
    longitudeDeg: Double = HOLLOWELL_LON,
    private val pollIntervalMs: Long = 5 * 60 * 1_000L,
) : PreEnvSource {

    override val sourceId: String = "open_meteo_v1"

    private val apiClient = OpenMeteoApiClient(latitudeDeg, longitudeDeg)

    /**
     * Captures a single baseline reading for the shift-start I-frame.
     * Makes one API call; result is cached by the API client for [pollIntervalMs].
     */
    override suspend fun captureBaseline(shiftPhase: String?): PreEnvSnapshot {
        val tempF = runCatching { apiClient.currentTempF() }.getOrNull()
        return PreEnvSnapshot(
            capturedAtNanos  = SystemClock.elapsedRealtimeNanos(),
            ambientTempF     = tempF,
            shiftPhase       = shiftPhase,
            materialBatchId  = null,  // operator-provided; not available here
            crewStateTag     = null,  // operator-provided; not available here
            sourceId         = sourceId,
        )
    }

    /**
     * Emits current ambient temperature on a poll interval.
     * Emits the cached value immediately, then polls.
     * Errors from the API are swallowed and logged; the flow does not terminate on
     * transient network failure.
     */
    override fun ambientTemp(): Flow<TempSample> = flow {
        while (true) {
            val tempF = runCatching { apiClient.currentTempF() }.getOrNull()
            if (tempF != null) {
                emit(TempSample(
                    timestampNanos = SystemClock.elapsedRealtimeNanos(),
                    tempF          = tempF,
                    source         = sourceId,
                ))
            }
            delay(pollIntervalMs)
        }
    }

    companion object {
        const val HOLLOWELL_LAT = 34.5312
        const val HOLLOWELL_LON = -90.5973
    }
}
