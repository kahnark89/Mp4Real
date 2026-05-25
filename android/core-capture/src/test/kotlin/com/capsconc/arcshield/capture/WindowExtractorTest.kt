package com.capsconc.arcshield.capture

import com.capsconc.arcshield.codec.EncodedSample
import com.capsconc.arcshield.codec.MetaSample
import com.capsconc.arcshield.codec.TrackType
import org.junit.Assert.*
import org.junit.Test

class WindowExtractorTest {

    private fun videoRing() = ChannelRingBuffer<EncodedSample>(Long.MAX_VALUE) { it.presentationTimeNanos }
    private fun metaRing()  = ChannelRingBuffer<MetaSample>(Long.MAX_VALUE) { it.presentationTimeNanos }

    private fun makeExtractor(
        videoRing: ChannelRingBuffer<EncodedSample> = videoRing(),
        audioRing: ChannelRingBuffer<EncodedSample> = videoRing(),
        accelRing: ChannelRingBuffer<MetaSample>    = metaRing(),
        bioRing:   ChannelRingBuffer<MetaSample>    = metaRing(),
        thermalRing: ChannelRingBuffer<MetaSample>  = metaRing(),
    ) = WindowExtractor(videoRing, audioRing, accelRing, bioRing, thermalRing)

    @Test
    fun `extractWindow returns samples in the w_pre to w_post interval`() {
        val video = videoRing()
        video.offer(EncodedSample(500_000_000L, byteArrayOf(1), isKeyFrame = true))   // 0.5s — in W_pre
        video.offer(EncodedSample(1_000_000_000L, byteArrayOf(2)))                    // 1.0s — fire time
        video.offer(EncodedSample(2_000_000_000L, byteArrayOf(3)))                    // 2.0s — in W_post
        video.offer(EncodedSample(5_000_000_000L, byteArrayOf(4)))                    // 5.0s — outside window

        val extractor = makeExtractor(videoRing = video)
        val window = extractor.extractWindow(
            sessionStartNanos = 0L,
            fireTimeNanos     = 1_000_000_000L,
            wPreNanos         = 1_000_000_000L,   // 1s
            wPostNanos        = 2_000_000_000L,   // 2s
        )

        assertEquals(3, window.videoSamples.size)
        assertTrue(window.videoSamples.none { it.presentationTimeNanos == 5_000_000_000L })
    }

    @Test
    fun `extractWindow marks I-frame correctly`() {
        val extractor = makeExtractor()
        val pframe = extractor.extractWindow(0L, 1_000_000_000L, 500_000_000L, 500_000_000L)
        val iframe = extractor.extractWindow(0L, 1_000_000_000L, 500_000_000L, 0L, isIFrame = true)

        assertFalse(pframe.isIFrame)
        assertTrue(iframe.isIFrame)
    }

    @Test
    fun `extractWindow with empty rings returns isEmpty window`() {
        val extractor = makeExtractor()
        val window = extractor.extractWindow(0L, 1_000_000_000L, 500_000_000L, 500_000_000L)
        assertTrue(window.isEmpty)
    }

    @Test
    fun `extractWindow routes meta samples to correct track lists`() {
        val accel = metaRing()
        val bio   = metaRing()
        val accelTs = 1_000_000_000L
        val bioTs   = 1_200_000_000L

        accel.offer(MetaSample(TrackType.AccelMeta, accelTs, """{"x":0}""".toByteArray()))
        bio.offer(MetaSample(TrackType.BiometricMeta, bioTs, """{"bpm":72}""".toByteArray()))

        val extractor = makeExtractor(accelRing = accel, bioRing = bio)
        val window = extractor.extractWindow(0L, 1_500_000_000L, 2_000_000_000L, 500_000_000L)

        assertEquals(1, window.accelSamples.size)
        assertEquals(1, window.biometricSamples.size)
        assertTrue(window.thermalSamples.isEmpty())
        assertEquals(accelTs, window.accelSamples[0].presentationTimeNanos)
        assertEquals(bioTs, window.biometricSamples[0].presentationTimeNanos)
    }

    @Test
    fun `windowStartNanos and windowEndNanos are computed from fire time and window sizes`() {
        val extractor = makeExtractor()
        val window = extractor.extractWindow(
            sessionStartNanos = 0L,
            fireTimeNanos     = 10_000_000_000L,
            wPreNanos         = 3_000_000_000L,
            wPostNanos        = 5_000_000_000L,
        )
        assertEquals(7_000_000_000L, window.windowStartNanos)
        assertEquals(15_000_000_000L, window.windowEndNanos)
        assertEquals(0L, window.sessionStartNanos)
    }
}
