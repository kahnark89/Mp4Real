package com.capsconc.arcshield.capture

import com.capsconc.arcshield.codec.EncodedSample
import com.capsconc.arcshield.codec.MetaSample

// Extracts a [CapturedWindow] from the five Phase 1 channel ring buffers.
// Pure logic — no Android dependencies, no coroutines. Callers are responsible
// for passing a fireTimeNanos from the LLR gate's CandidateWindow.detectedAtNanos.
class WindowExtractor(
    private val videoRing:     ChannelRingBuffer<EncodedSample>,
    private val audioRing:     ChannelRingBuffer<EncodedSample>,
    private val accelRing:     ChannelRingBuffer<MetaSample>,
    private val biometricRing: ChannelRingBuffer<MetaSample>,
    private val thermalRing:   ChannelRingBuffer<MetaSample>,
) {
    fun extractWindow(
        sessionStartNanos: Long,
        fireTimeNanos: Long,
        wPreNanos: Long,
        wPostNanos: Long,
        isIFrame: Boolean = false,
    ): CapturedWindow {
        val start = fireTimeNanos - wPreNanos
        val end   = fireTimeNanos + wPostNanos
        return CapturedWindow(
            sessionStartNanos = sessionStartNanos,
            windowStartNanos  = start,
            windowEndNanos    = end,
            isIFrame          = isIFrame,
            videoSamples      = videoRing.extract(start, end),
            audioSamples      = audioRing.extract(start, end),
            accelSamples      = accelRing.extract(start, end),
            biometricSamples  = biometricRing.extract(start, end),
            thermalSamples    = thermalRing.extract(start, end),
        )
    }
}
