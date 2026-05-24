package com.capsconc.arcshield.llr

import com.capsconc.arcshield.llr.internal.RealFft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class RealFftTest {

    @Test
    fun `power spectrum sums to 1`() {
        val pcm = ShortArray(1024) { (Short.MAX_VALUE * sin(2 * PI * 100 * it / 48000.0)).toInt().toShort() }
        val spectrum = RealFft.powerSpectrum(pcm, fftSize = 1024)
        val total = spectrum.sum()
        assertEquals("spectrum should sum to 1.0", 1.0f, total, 1e-4f)
    }

    @Test
    fun `pure sine has dominant bin`() {
        val fftSize   = 1024
        val sampleRate = 48000
        val freqHz     = 1000
        // Generate exactly freqHz * fftSize / sampleRate cycles → bin = freqHz * fftSize / sampleRate
        val targetBin  = freqHz * fftSize / sampleRate  // = ~21
        val pcm = ShortArray(fftSize) { i ->
            (Short.MAX_VALUE * sin(2 * PI * freqHz * i / sampleRate)).toInt().toShort()
        }
        val spectrum = RealFft.powerSpectrum(pcm, fftSize)
        val domBin   = spectrum.indices.maxByOrNull { spectrum[it] }!!
        // Allow ±1 bin for spectral leakage
        assertTrue(
            "dominant bin $domBin should be near $targetBin",
            kotlin.math.abs(domBin - targetBin) <= 1
        )
    }

    @Test
    fun `zero input returns uniform distribution`() {
        val pcm      = ShortArray(1024) { 0 }
        val spectrum = RealFft.powerSpectrum(pcm, fftSize = 1024)
        // All bins are epsilon, total ≈ 1.0
        assertEquals(1.0f, spectrum.sum(), 1e-4f)
        // No bin dominates
        val max = spectrum.max()
        val min = spectrum.min()
        assertTrue("zero input should be nearly uniform, max=$max min=$min", max / min < 2.0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-power-of-2 fftSize throws`() {
        RealFft.powerSpectrum(ShortArray(1024), fftSize = 1000)
    }
}
