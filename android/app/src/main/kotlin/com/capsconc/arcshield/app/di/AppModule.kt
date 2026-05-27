package com.capsconc.arcshield.app.di

import android.content.Context
import com.capsconc.arcshield.app.BuildConfig
import com.capsconc.arcshield.llm.claude.ClaudeVisionClient
import com.capsconc.arcshield.schema.biometric.BiometricSource
import com.capsconc.arcshield.schema.imu.AccelSource
import com.capsconc.arcshield.schema.llm.LlmClient
import com.capsconc.arcshield.schema.telemetry.PlcTelemetrySource
import com.capsconc.arcshield.source.imu.PhoneImuAccelSource
import com.capsconc.arcshield.source.polar.PolarBleBiometricSource
import com.capsconc.arcshield.source.polar.PolarDeviceType
import com.capsconc.arcshield.vision.HollowellChannelPresets
import com.capsconc.arcshield.vision.VisionTelemetrySource
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
// (e.g. VisionTelemetrySource polling).
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ---- BiometricSource -----------------------------------------------
    // H10 via Polar BLE SDK (PMD protocol). CLAUDE.md §8.
    // POLAR_DEVICE_ID is read from local.properties at build time.
    @Provides @Singleton
    fun provideBiometricSource(
        @ApplicationContext ctx: Context,
        @ApplicationScope scope: CoroutineScope,
    ): BiometricSource {
        val source = PolarBleBiometricSource.create(
            context    = ctx,
            deviceId   = BuildConfig.POLAR_DEVICE_ID,
            deviceType = PolarDeviceType.H10,
            scope      = scope,
        )
        source.connect()
        return source
    }

    // ---- AccelSource ---------------------------------------------------
    // Phone IMU (SensorManager) drives Λ_accel and the accel track. This is the
    // Gen 1 vibration source (CLAUDE.md §3.1 track 3); it replaces the Polar
    // onboard accel that fed accel before the biometric path was decoupled.
    @Provides @Singleton
    fun provideAccelSource(@ApplicationContext ctx: Context): AccelSource =
        PhoneImuAccelSource(ctx)

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
