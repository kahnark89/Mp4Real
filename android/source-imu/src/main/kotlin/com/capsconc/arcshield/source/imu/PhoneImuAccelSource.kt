package com.capsconc.arcshield.source.imu

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.capsconc.arcshield.schema.biometric.AccelSample
import com.capsconc.arcshield.schema.imu.AccelSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Phone-IMU [AccelSource] backed by Android [SensorManager] (TYPE_ACCELEROMETER).
 *
 * Samples are converted to milli-g (mG) — the same unit the Polar onboard
 * accelerometer reports — so the Λ_accel baseline/gate math and the activity-gate
 * thresholds in LlrConfig stay consistent regardless of which source feeds accel.
 * Gravity is included (≈1000 mG at rest); the LLR gate operates on deviation from
 * the I-frame baseline, so the constant gravity term cancels.
 *
 * Timestamps are elapsedRealtimeNanos at callback receipt — the same phone clock
 * anchor every other source uses (CLAUDE.md §3.2).
 *
 * Cold flow: the listener registers on collection and unregisters on cancellation.
 */
class PhoneImuAccelSource(
    private val context: Context,
    private val samplingPeriodUs: Int = 10_000,   // ~100 Hz, the §3.1 hardware ceiling
) : AccelSource {

    override val sourceId: String = "phone_imu_v1"

    override fun accelerometer(): Flow<AccelSample> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            close()   // no accelerometer on this device — emit nothing, close cleanly
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(
                    AccelSample(
                        timestampNanos = SystemClock.elapsedRealtimeNanos(),
                        xMg = event.values[0] * MS2_TO_MG,
                        yMg = event.values[1] * MS2_TO_MG,
                        zMg = event.values[2] * MS2_TO_MG,
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, samplingPeriodUs)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    private companion object {
        // 1 g = 9.80665 m/s²  →  mG = (m/s²) / 9.80665 * 1000.
        const val MS2_TO_MG = 1000f / 9.80665f
    }
}
