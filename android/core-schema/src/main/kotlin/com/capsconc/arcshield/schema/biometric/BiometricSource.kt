package com.capsconc.arcshield.schema.biometric

import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// Capabilities
// ---------------------------------------------------------------------------

enum class BiometricChannel {
    HEART_RATE,
    RR_INTERVALS,   // Beat-to-beat R-R intervals. Empty flow on optical-only devices.
    ECG_WAVEFORM,   // Raw ECG µV samples at 130 Hz. H10 only.
    ACCELEROMETER,
    EDA_WAVEFORM,   // Electrodermal activity. No Polar device supports this.
                    // Channel defined here so the interface is stable when Emotibit is wired.
}

// ---------------------------------------------------------------------------
// Gap signalling — CLAUDE.md §8.5
// Never silently interpolate across a BLE dropout. Downstream consumers
// (LLR gate, codebook matcher) must see the gap, not smoothed-over data.
// ---------------------------------------------------------------------------

enum class GapReason { BLE_DROPOUT, DEVICE_LOST, SYNC_LOST, UNKNOWN }

/**
 * Explicit dropout record. lowSyncConfidence is set when durationMs ≥ 4 000 ms —
 * the ε_sync threshold at which the surrounding capture window is flagged unreliable.
 */
data class BiometricGap(
    val startNanos:        Long,
    val endNanos:          Long,
    val durationMs:        Long,
    val reason:            GapReason,
    val lowSyncConfidence: Boolean,   // true when durationMs >= 4_000
)

// ---------------------------------------------------------------------------
// Sample types
// All timestamps are elapsedRealtimeNanos — phone monotonic clock.
// Never System.currentTimeMillis(). See CLAUDE.md §3.2 on ε_sync.
// ---------------------------------------------------------------------------

data class HrSample(val timestampNanos: Long, val bpm: Int)

/** Single R-R interval in milliseconds, derived from ECG (H10) or PPG (Verity Sense). */
data class RrSample(val timestampNanos: Long, val rrMs: Int)

/** One ECG sample in microvolts. H10 streams at 130 Hz. */
data class EcgSample(val timestampNanos: Long, val microVolts: Int)

data class AccelSample(
    val timestampNanos: Long,
    val xMg: Float,
    val yMg: Float,
    val zMg: Float,
)

/** EDA in microsiemens. Polar devices always emit empty flow for this channel. */
data class EdaSample(val timestampNanos: Long, val microSiemens: Float)

// ---------------------------------------------------------------------------
// BiometricSource interface — CLAUDE.md §9
//
// All sources expose Flows of timestamped samples anchored to elapsedRealtimeNanos.
// Every channel returns an empty Flow if the device does not support it.
// No consumer may hold a reference to a concrete hardware class.
// The Gen 1 → Gen 2 swap (H10/Verity Sense → future devices) is a DI binding
// change, not a structural rewrite.
// ---------------------------------------------------------------------------

interface BiometricSource {
    fun heartRate():     Flow<HrSample>
    fun rrIntervals():   Flow<RrSample>      // empty if device doesn't support
    fun ecgWaveform():   Flow<EcgSample>     // empty if device doesn't support (Verity Sense)
    fun accelerometer(): Flow<AccelSample>
    fun edaWaveform():   Flow<EdaSample>     // always empty from Polar devices
    fun gaps():          Flow<BiometricGap>  // explicit gap events, never silenced
    val capabilities:    Set<BiometricChannel>
    val sourceId:        String              // "polar_h10_v1" | "polar_verity_sense_v1" | ...
}
