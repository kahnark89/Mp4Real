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
package com.capsconc.arcshield.app.settings

import android.content.Context
import android.content.SharedPreferences
import com.capsconc.arcshield.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BiometricSourceSetting { NONE, POLAR_H10, POLAR_VERITY_SENSE }
enum class VideoSourceSetting { PHONE_CAMERA, GLASSES }
enum class AccelSourceSetting { PHONE_IMU, POLAR }

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---- Claude API key ---------------------------------------------------

    private val _claudeApiKey = MutableStateFlow(
        prefs.getString(KEY_CLAUDE_API_KEY, BuildConfig.CLAUDE_API_KEY) ?: BuildConfig.CLAUDE_API_KEY
    )
    val claudeApiKey: StateFlow<String> = _claudeApiKey.asStateFlow()

    fun setClaudeApiKey(value: String) {
        prefs.edit().putString(KEY_CLAUDE_API_KEY, value).apply()
        _claudeApiKey.value = value
    }

    // ---- Polar device ID --------------------------------------------------

    private val _polarDeviceId = MutableStateFlow(
        prefs.getString(KEY_POLAR_DEVICE_ID, BuildConfig.POLAR_DEVICE_ID) ?: BuildConfig.POLAR_DEVICE_ID
    )
    val polarDeviceId: StateFlow<String> = _polarDeviceId.asStateFlow()

    fun setPolarDeviceId(value: String) {
        prefs.edit().putString(KEY_POLAR_DEVICE_ID, value).apply()
        _polarDeviceId.value = value
    }

    // ---- Biometric source -------------------------------------------------

    private val _biometricSource = MutableStateFlow(
        BiometricSourceSetting.valueOf(
            prefs.getString(KEY_BIOMETRIC_SOURCE, defaultBiometricSetting().name)
                ?: defaultBiometricSetting().name
        )
    )
    val biometricSource: StateFlow<BiometricSourceSetting> = _biometricSource.asStateFlow()

    fun setBiometricSource(value: BiometricSourceSetting) {
        prefs.edit().putString(KEY_BIOMETRIC_SOURCE, value.name).apply()
        _biometricSource.value = value
    }

    // ---- Video source -----------------------------------------------------

    private val _videoSource = MutableStateFlow(
        VideoSourceSetting.valueOf(
            prefs.getString(KEY_VIDEO_SOURCE, defaultVideoSetting().name)
                ?: defaultVideoSetting().name
        )
    )
    val videoSource: StateFlow<VideoSourceSetting> = _videoSource.asStateFlow()

    fun setVideoSource(value: VideoSourceSetting) {
        prefs.edit().putString(KEY_VIDEO_SOURCE, value.name).apply()
        _videoSource.value = value
    }

    // ---- Glasses device MAC -----------------------------------------------

    private val _glassesDeviceMac = MutableStateFlow(
        prefs.getString(KEY_GLASSES_MAC, BuildConfig.GLASSES_DEVICE_ID) ?: BuildConfig.GLASSES_DEVICE_ID
    )
    val glassesDeviceMac: StateFlow<String> = _glassesDeviceMac.asStateFlow()

    fun setGlassesDeviceMac(value: String) {
        prefs.edit().putString(KEY_GLASSES_MAC, value).apply()
        _glassesDeviceMac.value = value
    }

    // ---- Accel source -----------------------------------------------------

    private val _accelSource = MutableStateFlow(
        AccelSourceSetting.valueOf(
            prefs.getString(KEY_ACCEL_SOURCE, AccelSourceSetting.PHONE_IMU.name)
                ?: AccelSourceSetting.PHONE_IMU.name
        )
    )
    val accelSource: StateFlow<AccelSourceSetting> = _accelSource.asStateFlow()

    fun setAccelSource(value: AccelSourceSetting) {
        prefs.edit().putString(KEY_ACCEL_SOURCE, value.name).apply()
        _accelSource.value = value
    }

    // ---- I-frame duration (seconds, clamped 30–120) -----------------------

    private val _iFrameDurationS = MutableStateFlow(
        prefs.getInt(KEY_IFRAME_DURATION_S, DEFAULT_IFRAME_S).coerceIn(MIN_IFRAME_S, MAX_IFRAME_S)
    )
    val iFrameDurationS: StateFlow<Int> = _iFrameDurationS.asStateFlow()

    fun setIFrameDurationS(value: Int) {
        val clamped = value.coerceIn(MIN_IFRAME_S, MAX_IFRAME_S)
        prefs.edit().putInt(KEY_IFRAME_DURATION_S, clamped).apply()
        _iFrameDurationS.value = clamped
    }

    // ---- Facility ID ------------------------------------------------------

    private val _facilityId = MutableStateFlow(
        prefs.getString(KEY_FACILITY_ID, BuildConfig.FACILITY_ID) ?: BuildConfig.FACILITY_ID
    )
    val facilityId: StateFlow<String> = _facilityId.asStateFlow()

    fun setFacilityId(value: String) {
        prefs.edit().putString(KEY_FACILITY_ID, value).apply()
        _facilityId.value = value
    }

    // ---- Line ID ----------------------------------------------------------

    private val _lineId = MutableStateFlow(
        prefs.getString(KEY_LINE_ID, BuildConfig.LINE_ID) ?: BuildConfig.LINE_ID
    )
    val lineId: StateFlow<String> = _lineId.asStateFlow()

    fun setLineId(value: String) {
        prefs.edit().putString(KEY_LINE_ID, value).apply()
        _lineId.value = value
    }

    // ---- Helpers ----------------------------------------------------------

    private fun defaultBiometricSetting(): BiometricSourceSetting =
        if (BuildConfig.POLAR_DEVICE_ID.isNotBlank()) BiometricSourceSetting.POLAR_H10
        else BiometricSourceSetting.NONE

    private fun defaultVideoSetting(): VideoSourceSetting =
        if (BuildConfig.GLASSES_DEVICE_ID.isNotBlank()) VideoSourceSetting.GLASSES
        else VideoSourceSetting.PHONE_CAMERA

    companion object {
        private const val PREFS_NAME          = "arcshield_settings"
        private const val KEY_CLAUDE_API_KEY  = "claude_api_key"
        private const val KEY_POLAR_DEVICE_ID = "polar_device_id"
        private const val KEY_BIOMETRIC_SOURCE = "biometric_source"
        private const val KEY_VIDEO_SOURCE    = "video_source"
        private const val KEY_GLASSES_MAC     = "glasses_device_mac"
        private const val KEY_ACCEL_SOURCE    = "accel_source"
        private const val KEY_IFRAME_DURATION_S = "iframe_duration_s"
        private const val KEY_FACILITY_ID     = "facility_id"
        private const val KEY_LINE_ID         = "line_id"
        const val MIN_IFRAME_S                = 30
        const val MAX_IFRAME_S                = 120
        const val DEFAULT_IFRAME_S            = 90
    }
}
