package com.capsconc.arcshield.app.di

import android.content.Context
import com.capsconc.arcshield.app.BuildConfig
import com.capsconc.arcshield.llm.claude.ClaudeVisionClient
import com.capsconc.arcshield.schema.biometric.BiometricSource
import com.capsconc.arcshield.schema.llm.LlmClient
import com.capsconc.arcshield.schema.telemetry.PlcTelemetrySource
import com.capsconc.arcshield.source.polar.PolarBleBiometricSource
import com.capsconc.arcshield.source.polar.PolarDeviceType
import com.capsconc.arcshield.vision.HollowellChannelPresets
import com.capsconc.arcshield.vision.VisionTelemetrySource
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiDefaultImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

// Marks the application-lifetime CoroutineScope used by long-running sources
// (Polar BLE reconnect loop, VisionTelemetrySource polling).
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ---- Polar BLE API ------------------------------------------------
    // PolarBleApiDefaultImpl requires a Context and a set of feature flags.
    // Singleton: the SDK maintains one BLE state machine per process.

    @Provides @Singleton
    fun providePolarBleApi(@ApplicationContext ctx: Context): PolarBleApi =
        PolarBleApiDefaultImpl.defaultImplementation(
            ctx,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SENSOR_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
            )
        )

    // ---- BiometricSource -----------------------------------------------
    // Phase 1 validation device: H10 (ECG at 130 Hz).
    // POLAR_DEVICE_ID is injected from local.properties — never hard-coded.
    // DeviceType auto-detects H10 vs. Verity Sense from the device ID format;
    // H10 is preferred for the calibration phase per CLAUDE.md §8.3.

    @Provides @Singleton
    fun provideBiometricSource(
        polarBleApi: PolarBleApi,
        @ApplicationScope scope: CoroutineScope,
    ): BiometricSource = PolarBleBiometricSource(
        api        = polarBleApi,
        deviceId   = BuildConfig.POLAR_DEVICE_ID.ifBlank { "00000000" },
        deviceType = PolarDeviceType.H10,
        scope      = scope,
    )

    // ---- LlmClient ----------------------------------------------------
    // ClaudeVisionClient is used by VisionTelemetrySource for optical R_phys
    // extraction (CLAUDE.md §W-008 rationale).  API key from local.properties.

    @Provides @Singleton
    fun provideLlmClient(): LlmClient =
        ClaudeVisionClient(apiKey = BuildConfig.CLAUDE_API_KEY)

    // ---- PlcTelemetrySource -------------------------------------------
    // VisionTelemetrySource replaces a direct PLC API for Phase 1–2.
    // Conforms to the same PlcTelemetrySource interface so the swap is
    // a DI-binding change when the PLC API becomes available (Phase 3).

    @Provides @Singleton
    fun providePlcTelemetrySource(llmClient: LlmClient): PlcTelemetrySource =
        VisionTelemetrySource(
            config    = HollowellChannelPresets.ppvcLine1,
            llmClient = llmClient,
        )
}
