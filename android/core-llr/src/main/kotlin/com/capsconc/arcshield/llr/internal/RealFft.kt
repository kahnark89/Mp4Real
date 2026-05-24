package com.capsconc.arcshield.llr.internal

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cooley-Tukey radix-2 DIT FFT on real inputs.
 *
 * Operates in-place on a complex flat array [re0, im0, re1, im1, ...] of length 2*N.
 * N must be a power of 2. The public entry point [powerSpectrum] handles the
 * ShortArray → Float conversion, zero-padding, FFT, and normalization.
 *
 * No external dependencies. Pure Kotlin integer/float arithmetic.
 * Kay (1998) GLR approximation for composite hypotheses operates on the
 * one-sided normalized power spectrum returned by [powerSpectrum].
 */
internal object RealFft {

    /**
     * Compute the normalized one-sided power spectrum of a PCM frame.
     *
     * @param pcm    Raw 16-bit PCM samples (mono). Truncated to [fftSize] or zero-padded.
     * @param fftSize Number of FFT points — must be a power of 2 (default 1024).
     * @return FloatArray of length [fftSize]/2 + 1 summing to 1.0 (probability distribution).
     *         Safe to pass directly to [KlDivergence.compute].
     */
    fun powerSpectrum(pcm: ShortArray, fftSize: Int = 1024): FloatArray {
        require(fftSize > 0 && fftSize and (fftSize - 1) == 0) {
            "fftSize must be a power of 2, got $fftSize"
        }

        // Build interleaved complex array [re0, im0, re1, im1, ...]
        val c = FloatArray(fftSize * 2)
        val len = minOf(pcm.size, fftSize)
        for (i in 0 until len) {
            c[2 * i] = pcm[i].toFloat()
            // c[2*i+1] = 0f (imaginary part already zero)
        }

        fftInPlace(c, fftSize)

        // One-sided power spectrum: |X[k]|² for k in 0..N/2
        val nBins   = fftSize / 2 + 1
        val power   = FloatArray(nBins)
        for (k in 0 until nBins) {
            val re = c[2 * k]
            val im = c[2 * k + 1]
            power[k] = re * re + im * im
        }

        // Normalize to a probability distribution (sum to 1.0), with epsilon floor
        // so zero bins don't cause ln(0) in KL divergence downstream.
        val epsilon = 1e-10f
        val total   = power.sum() + epsilon * nBins
        for (k in 0 until nBins) {
            power[k] = (power[k] + epsilon) / total
        }
        return power
    }

    // -----------------------------------------------------------------------
    // In-place Cooley-Tukey radix-2 DIT FFT
    // Input:  c[2k] = real part of sample k, c[2k+1] = imaginary part
    // Output: c holds DFT result in bit-reversed order
    // -----------------------------------------------------------------------

    private fun fftInPlace(c: FloatArray, n: Int) {
        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val ri = 2 * i; val rj = 2 * j
                var tmp = c[ri];  c[ri]   = c[rj];   c[rj]   = tmp
                tmp     = c[ri+1]; c[ri+1] = c[rj+1]; c[rj+1] = tmp
            }
        }

        // Butterfly stages
        var len = 2
        while (len <= n) {
            val halfLen  = len / 2
            val wRe      = cos(2.0 * PI / len).toFloat()
            val wIm      = (-sin(2.0 * PI / len)).toFloat()
            var i        = 0
            while (i < n) {
                var uRe = 1f; var uIm = 0f
                for (jj in 0 until halfLen) {
                    val evenRe = c[2 * (i + jj)]
                    val evenIm = c[2 * (i + jj) + 1]
                    val oddRe  = c[2 * (i + jj + halfLen)]
                    val oddIm  = c[2 * (i + jj + halfLen) + 1]

                    val tRe = uRe * oddRe - uIm * oddIm
                    val tIm = uRe * oddIm + uIm * oddRe

                    c[2 * (i + jj)]           = evenRe + tRe
                    c[2 * (i + jj) + 1]       = evenIm + tIm
                    c[2 * (i + jj + halfLen)]  = evenRe - tRe
                    c[2 * (i + jj + halfLen) + 1] = evenIm - tIm

                    val newURe = uRe * wRe - uIm * wIm
                    uIm = uRe * wIm + uIm * wRe
                    uRe = newURe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
