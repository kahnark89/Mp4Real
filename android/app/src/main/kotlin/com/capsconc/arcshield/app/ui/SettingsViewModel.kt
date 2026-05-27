/*
 * Intellectual Property and Trademark Notice
 *
 * mp4Real™, ArcShield™, CIAER™, and CIAER+™ are trademarks of Capps Consulting
 * Company LLC. The multi-track cyber-physical capture architecture, the
 * application of log-likelihood ratio (LLR) gating to multimodal industrial
 * decision events, and the behavioral codebook discretization methods described
 * in this document are the proprietary intellectual property of Kahn Capps and
 * Capps Consulting Company LLC. Unauthorized commercial use, reproduction, or
 * implementation of the mp4Real™ container architecture or the CIAER™ and CIAER+™
 * schemas without explicit licensing is prohibited. All rights reserved.
 */
package com.capsconc.arcshield.app.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import com.capsconc.arcshield.app.settings.AccelSourceSetting
import com.capsconc.arcshield.app.settings.BiometricSourceSetting
import com.capsconc.arcshield.app.settings.SettingsRepository
import com.capsconc.arcshield.app.settings.VideoSourceSetting
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val settings: SettingsRepository,
) : ViewModel() {

    val phoneCameraAvailable: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    val phoneMicAvailable: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

    val phoneImuAvailable: Boolean = run {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }

    @SuppressLint("MissingPermission")
    fun isGlassesBonded(): Boolean {
        val mac = settings.glassesDeviceMac.value
        if (mac.isBlank()) return false
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter ?: return false
            adapter.bondedDevices?.any { it.address.equals(mac, ignoreCase = true) } == true
        } catch (_: Exception) {
            false
        }
    }

    fun setClaudeApiKey(value: String) = settings.setClaudeApiKey(value)
    fun setPolarDeviceId(value: String) = settings.setPolarDeviceId(value)
    fun setBiometricSource(value: BiometricSourceSetting) = settings.setBiometricSource(value)
    fun setVideoSource(value: VideoSourceSetting) = settings.setVideoSource(value)
    fun setGlassesDeviceMac(value: String) = settings.setGlassesDeviceMac(value)
    fun setAccelSource(value: AccelSourceSetting) = settings.setAccelSource(value)
    fun setIFrameDurationS(value: Int) = settings.setIFrameDurationS(value)
    fun setFacilityId(value: String) = settings.setFacilityId(value)
    fun setLineId(value: String) = settings.setLineId(value)
}
