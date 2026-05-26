package com.capsconc.arcshield.app.di

import com.capsconc.arcshield.app.BuildConfig
import com.capsconc.arcshield.llm.claude.ClaudeVisionClient
import com.capsconc.arcshield.schema.biometric.BiometricSource
import com.capsconc.arcshield.schema.llm.LlmClient
import com.capsconc.arcshield.schema.telemetry.PlcTelemetrySource
import com.capsconc.arcshield.vision.HollowellChannelPresets
import com.capsconc.arcshield.vision.VisionTelemetrySource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
    // Phase 1 Λ_env-only shadow sessions run without a Polar device, so the
    // biometric channel is a no-op source (Λ_bio = 0). To enable H10 / Verity
    // Sense capture, add `implementation(project(":source-polar"))` back to the
    // app module and bind PolarBleBiometricSource here instead.
    @Provides @Singleton
    fun provideBiometricSource(): BiometricSource = NullBiometricSource()

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
