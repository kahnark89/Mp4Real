package com.capsconc.arcshield.capture

import com.capsconc.arcshield.codec.EncodedSample
import com.capsconc.arcshield.codec.MetaSample

// All samples from a single [t − W_pre, t + W_post] window, ready to be
// handed to Mp4RealWriter. PTS values are absolute elapsedRealtimeNanos;
// CaptureSession subtracts sessionStartNanos before writing to the muxer.
data class CapturedWindow(
    val sessionStartNanos: Long,
    val windowStartNanos: Long,
    val windowEndNanos: Long,
    val isIFrame: Boolean,
    val videoSamples: List<EncodedSample>,
    val audioSamples: List<EncodedSample>,
    val accelSamples: List<MetaSample>,
    val biometricSamples: List<MetaSample>,
    val thermalSamples: List<MetaSample>,
) {
    val isEmpty: Boolean
        get() = videoSamples.isEmpty()
            && audioSamples.isEmpty()
            && accelSamples.isEmpty()
            && biometricSamples.isEmpty()
            && thermalSamples.isEmpty()
}
