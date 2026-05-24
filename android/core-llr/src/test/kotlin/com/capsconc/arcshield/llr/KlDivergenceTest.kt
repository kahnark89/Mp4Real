package com.capsconc.arcshield.llr

import com.capsconc.arcshield.llr.internal.KlDivergence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KlDivergenceTest {

    @Test
    fun `identical distributions produce zero`() {
        val dist = floatArrayOf(0.25f, 0.25f, 0.25f, 0.25f)
        assertEquals(0f, KlDivergence.compute(dist, dist), 1e-5f)
    }

    @Test
    fun `divergent distributions produce positive value`() {
        val baseline = floatArrayOf(0.5f, 0.25f, 0.25f)
        val current  = floatArrayOf(0.1f, 0.5f,  0.4f)
        assertTrue(KlDivergence.compute(baseline, current) > 0f)
    }

    @Test
    fun `result is non-negative for all inputs`() {
        val a = floatArrayOf(0.7f, 0.2f, 0.1f)
        val b = floatArrayOf(0.1f, 0.7f, 0.2f)
        assertTrue(KlDivergence.compute(a, b) >= 0f)
        assertTrue(KlDivergence.compute(b, a) >= 0f)
    }

    @Test
    fun `zero bins handled by epsilon without exception`() {
        val baseline = floatArrayOf(0.5f, 0.5f, 0f)
        val current  = floatArrayOf(0f,   0.5f, 0.5f)
        val kl = KlDivergence.compute(baseline, current)
        assertTrue(kl >= 0f)
    }

    @Test
    fun `empty arrays return zero`() {
        assertEquals(0f, KlDivergence.compute(floatArrayOf(), floatArrayOf()), 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mismatched sizes throw`() {
        KlDivergence.compute(floatArrayOf(0.5f, 0.5f), floatArrayOf(1f))
    }
}
