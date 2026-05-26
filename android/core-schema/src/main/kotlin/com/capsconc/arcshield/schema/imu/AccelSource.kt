package com.capsconc.arcshield.schema.imu

import com.capsconc.arcshield.schema.biometric.AccelSample
import kotlinx.coroutines.flow.Flow

/**
 * Phone-side inertial accelerometer source — the vibration/accel track
 * (track 3, CLAUDE.md §3.1) and the Λ_accel input to the LLR gate.
 *
 * Separated from [com.capsconc.arcshield.schema.biometric.BiometricSource] so the
 * phone IMU (Gen 1) can feed Λ_accel even when no wearable is connected. Samples
 * are in milli-g (mG), timestamped with elapsedRealtimeNanos (CLAUDE.md §3.2).
 */
interface AccelSource {
    fun accelerometer(): Flow<AccelSample>
    val sourceId: String
}
