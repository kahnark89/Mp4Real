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

    // ---- Gate tuning — τ threshold ----------------------------------------

    private val _gateTau = MutableStateFlow(
        prefs.getFloat(KEY_GATE_TAU, DEFAULT_GATE_TAU)
    )
    val gateTau: StateFlow<Float> = _gateTau.asStateFlow()

    fun setGateTau(value: Float) {
        val clamped = value.coerceIn(MIN_GATE_TAU, MAX_GATE_TAU)
        prefs.edit().putFloat(KEY_GATE_TAU, clamped).apply()
        _gateTau.value = clamped
    }

    // ---- Gate tuning — channel enables ------------------------------------

    private val _acousticEnabled = MutableStateFlow(prefs.getBoolean(KEY_ACOUSTIC_ENABLED, true))
    val acousticEnabled: StateFlow<Boolean> = _acousticEnabled.asStateFlow()
    fun setAcousticEnabled(v: Boolean) { prefs.edit().putBoolean(KEY_ACOUSTIC_ENABLED, v).apply(); _acousticEnabled.value = v }

    private val _accelEnabled = MutableStateFlow(prefs.getBoolean(KEY_ACCEL_ENABLED, true))
    val accelEnabled: StateFlow<Boolean> = _accelEnabled.asStateFlow()
    fun setAccelEnabled(v: Boolean) { prefs.edit().putBoolean(KEY_ACCEL_ENABLED, v).apply(); _accelEnabled.value = v }

    private val _motionEnabled = MutableStateFlow(prefs.getBoolean(KEY_MOTION_ENABLED, true))
    val motionEnabled: StateFlow<Boolean> = _motionEnabled.asStateFlow()
    fun setMotionEnabled(v: Boolean) { prefs.edit().putBoolean(KEY_MOTION_ENABLED, v).apply(); _motionEnabled.value = v }

    private val _gazeEnabled = MutableStateFlow(prefs.getBoolean(KEY_GAZE_ENABLED, false))
    val gazeEnabled: StateFlow<Boolean> = _gazeEnabled.asStateFlow()
    fun setGazeEnabled(v: Boolean) { prefs.edit().putBoolean(KEY_GAZE_ENABLED, v).apply(); _gazeEnabled.value = v }

    private val _hrEnabled = MutableStateFlow(prefs.getBoolean(KEY_HR_ENABLED, true))
    val hrEnabled: StateFlow<Boolean> = _hrEnabled.asStateFlow()
    fun setHrEnabled(v: Boolean) { prefs.edit().putBoolean(KEY_HR_ENABLED, v).apply(); _hrEnabled.value = v }

    private val _rmssdEnabled = MutableStateFlow(prefs.getBoolean(KEY_RMSSD_ENABLED, true))
    val rmssdEnabled: StateFlow<Boolean> = _rmssdEnabled.asStateFlow()
    fun setRmssdEnabled(v: Boolean) { prefs.edit().putBoolean(KEY_RMSSD_ENABLED, v).apply(); _rmssdEnabled.value = v }

    private val _hrvNlEnabled = MutableStateFlow(prefs.getBoolean(KEY_HRV_NL_ENABLED, true))
    val hrvNlEnabled: StateFlow<Boolean> = _hrvNlEnabled.asStateFlow()
    fun setHrvNlEnabled(v: Boolean) { prefs.edit().putBoolean(KEY_HRV_NL_ENABLED, v).apply(); _hrvNlEnabled.value = v }

    // ---- Gate tuning — activity gate thresholds (mG) ----------------------

    private val _lightAccelThresholdMg = MutableStateFlow(prefs.getFloat(KEY_LIGHT_ACCEL_MG, 200f))
    val lightAccelThresholdMg: StateFlow<Float> = _lightAccelThresholdMg.asStateFlow()
    fun setLightAccelThresholdMg(v: Float) { prefs.edit().putFloat(KEY_LIGHT_ACCEL_MG, v).apply(); _lightAccelThresholdMg.value = v }

    private val _moderateAccelThresholdMg = MutableStateFlow(prefs.getFloat(KEY_MODERATE_ACCEL_MG, 500f))
    val moderateAccelThresholdMg: StateFlow<Float> = _moderateAccelThresholdMg.asStateFlow()
    fun setModerateAccelThresholdMg(v: Float) { prefs.edit().putFloat(KEY_MODERATE_ACCEL_MG, v).apply(); _moderateAccelThresholdMg.value = v }

    private val _vigorousAccelThresholdMg = MutableStateFlow(prefs.getFloat(KEY_VIGOROUS_ACCEL_MG, 1000f))
    val vigorousAccelThresholdMg: StateFlow<Float> = _vigorousAccelThresholdMg.asStateFlow()
    fun setVigorousAccelThresholdMg(v: Float) { prefs.edit().putFloat(KEY_VIGOROUS_ACCEL_MG, v).apply(); _vigorousAccelThresholdMg.value = v }

    // ---- Gate tuning — activity gate factors (0–1) ------------------------

    private val _lightGateFactor = MutableStateFlow(prefs.getFloat(KEY_LIGHT_GATE_FACTOR, 0.8f))
    val lightGateFactor: StateFlow<Float> = _lightGateFactor.asStateFlow()
    fun setLightGateFactor(v: Float) { prefs.edit().putFloat(KEY_LIGHT_GATE_FACTOR, v.coerceIn(0f, 1f)).apply(); _lightGateFactor.value = v.coerceIn(0f, 1f) }

    private val _moderateGateFactor = MutableStateFlow(prefs.getFloat(KEY_MODERATE_GATE_FACTOR, 0.4f))
    val moderateGateFactor: StateFlow<Float> = _moderateGateFactor.asStateFlow()
    fun setModerateGateFactor(v: Float) { prefs.edit().putFloat(KEY_MODERATE_GATE_FACTOR, v.coerceIn(0f, 1f)).apply(); _moderateGateFactor.value = v.coerceIn(0f, 1f) }

    private val _vigorousGateFactor = MutableStateFlow(prefs.getFloat(KEY_VIGOROUS_GATE_FACTOR, 0.1f))
    val vigorousGateFactor: StateFlow<Float> = _vigorousGateFactor.asStateFlow()
    fun setVigorousGateFactor(v: Float) { prefs.edit().putFloat(KEY_VIGOROUS_GATE_FACTOR, v.coerceIn(0f, 1f)).apply(); _vigorousGateFactor.value = v.coerceIn(0f, 1f) }

    // ---- Gate tuning — gaze parameters ------------------------------------

    private val _gazeDwellBaselineSec = MutableStateFlow(prefs.getFloat(KEY_GAZE_BASELINE_SEC, 2.0f))
    val gazeDwellBaselineSec: StateFlow<Float> = _gazeDwellBaselineSec.asStateFlow()
    fun setGazeDwellBaselineSec(v: Float) { prefs.edit().putFloat(KEY_GAZE_BASELINE_SEC, v).apply(); _gazeDwellBaselineSec.value = v }

    private val _gazeDwellVarianceSec = MutableStateFlow(prefs.getFloat(KEY_GAZE_VARIANCE_SEC, 2.0f))
    val gazeDwellVarianceSec: StateFlow<Float> = _gazeDwellVarianceSec.asStateFlow()
    fun setGazeDwellVarianceSec(v: Float) { prefs.edit().putFloat(KEY_GAZE_VARIANCE_SEC, v.coerceAtLeast(0.1f)).apply(); _gazeDwellVarianceSec.value = v.coerceAtLeast(0.1f) }

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

        // Gate tuning keys
        private const val KEY_GATE_TAU            = "gate_tau"
        private const val KEY_ACOUSTIC_ENABLED    = "gate_acoustic_enabled"
        private const val KEY_ACCEL_ENABLED       = "gate_accel_enabled"
        private const val KEY_MOTION_ENABLED      = "gate_motion_enabled"
        private const val KEY_GAZE_ENABLED        = "gate_gaze_enabled"
        private const val KEY_HR_ENABLED          = "gate_hr_enabled"
        private const val KEY_RMSSD_ENABLED       = "gate_rmssd_enabled"
        private const val KEY_HRV_NL_ENABLED      = "gate_hrv_nl_enabled"
        private const val KEY_LIGHT_ACCEL_MG      = "gate_light_accel_mg"
        private const val KEY_MODERATE_ACCEL_MG   = "gate_moderate_accel_mg"
        private const val KEY_VIGOROUS_ACCEL_MG   = "gate_vigorous_accel_mg"
        private const val KEY_LIGHT_GATE_FACTOR   = "gate_light_factor"
        private const val KEY_MODERATE_GATE_FACTOR = "gate_moderate_factor"
        private const val KEY_VIGOROUS_GATE_FACTOR = "gate_vigorous_factor"
        private const val KEY_GAZE_BASELINE_SEC   = "gate_gaze_baseline_sec"
        private const val KEY_GAZE_VARIANCE_SEC   = "gate_gaze_variance_sec"

        const val MIN_GATE_TAU     = 0.0f
        const val MAX_GATE_TAU     = 10.0f
        const val DEFAULT_GATE_TAU = 0.0f
    }
}
