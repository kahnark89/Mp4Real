package com.capsconc.arcshield.app.di

import com.capsconc.arcshield.schema.biometric.AccelSample
import com.capsconc.arcshield.schema.biometric.BiometricChannel
import com.capsconc.arcshield.schema.biometric.BiometricGap
import com.capsconc.arcshield.schema.biometric.BiometricSource
import com.capsconc.arcshield.schema.biometric.EcgSample
import com.capsconc.arcshield.schema.biometric.EdaSample
import com.capsconc.arcshield.schema.biometric.HrSample
import com.capsconc.arcshield.schema.biometric.RrSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

// No-op BiometricSource for Λ_env-only shadow sessions (no Polar device paired).
// Every channel is an empty flow, so the LLR gate runs on Λ_env alone and
// Λ_bio = 0 — buildBaseline() and llrGate() already handle empty flows by design.
// Swap this binding for PolarBleBiometricSource (source-polar) to enable H10 capture.
class NullBiometricSource : BiometricSource {
    override fun heartRate():     Flow<HrSample>      = emptyFlow()
    override fun rrIntervals():   Flow<RrSample>      = emptyFlow()
    override fun ecgWaveform():   Flow<EcgSample>     = emptyFlow()
    override fun accelerometer(): Flow<AccelSample>   = emptyFlow()
    override fun edaWaveform():    Flow<EdaSample>    = emptyFlow()
    override fun gaps():          Flow<BiometricGap>  = emptyFlow()
    override val capabilities:    Set<BiometricChannel> = emptySet()
    override val sourceId:        String              = "null_biometric_v1"
}
