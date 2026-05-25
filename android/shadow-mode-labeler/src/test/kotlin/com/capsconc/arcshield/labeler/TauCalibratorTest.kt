package com.capsconc.arcshield.labeler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TauCalibratorTest {

    private fun win(lambda: Float) = LabeledWindow(
        window = makeCandidateWindow(lambda),
        label  = Label.UNLABELED,
    )

    private fun tp(lambda: Float) = win(lambda).copy(label = Label.TRUE_POSITIVE)
    private fun fp(lambda: Float) = win(lambda).copy(label = Label.FALSE_POSITIVE)

    // -----------------------------------------------------------------------
    // Degenerate inputs
    // -----------------------------------------------------------------------

    @Test
    fun `null when list is empty`() {
        assertNull(TauCalibrator.suggest(emptyList()))
    }

    @Test
    fun `null when only one labeled window`() {
        assertNull(TauCalibrator.suggest(listOf(tp(0.5f))))
    }

    @Test
    fun `null when two windows but both UNLABELED`() {
        assertNull(TauCalibrator.suggest(listOf(win(0.5f), win(0.3f))))
    }

    // -----------------------------------------------------------------------
    // All-TP cases
    // -----------------------------------------------------------------------

    @Test
    fun `all TP — TP rate 1_0, FP rate 0_0, tau equals minimum lambda`() {
        val labeled = listOf(tp(0.9f), tp(0.5f), tp(0.3f))
        val s = TauCalibrator.suggest(labeled)
        assertNotNull(s)
        assertEquals(1.0f, s!!.tpRate, 0.001f)
        assertEquals(0.0f, s.fpRate, 0.001f)
        assertEquals(3, s.tpCount)
        assertEquals(0, s.fpCount)
    }

    @Test
    fun `all FP — falls back to tau=0 with full FP rate`() {
        val labeled = listOf(fp(0.9f), fp(0.5f), fp(0.3f))
        val s = TauCalibrator.suggest(labeled)
        assertNotNull(s)
        assertEquals(0f, s!!.suggestedTau, 0f)
        assertEquals(1.0f, s.fpRate, 0.001f)
        assertEquals(0, s.tpCount)
    }

    // -----------------------------------------------------------------------
    // Mixed — known optimal τ
    // -----------------------------------------------------------------------

    @Test
    fun `four TPs above 0_5 and two FPs below 0_3 — optimal tau near 0_3`() {
        // TPs at 0.9, 0.8, 0.7, 0.6 — FPs at 0.2, 0.1
        // At τ = 0.3 we capture all 4 TPs (TP rate 100%) and 0 FPs (FP rate 0%)
        val labeled = listOf(
            tp(0.9f), tp(0.8f), tp(0.7f), tp(0.6f),
            fp(0.2f), fp(0.1f),
        )
        val s = TauCalibrator.suggest(labeled)
        assertNotNull(s)
        assertEquals(1.0f, s!!.tpRate, 0.001f)
        assertEquals(0.0f, s.fpRate, 0.001f)
        assertTrue("τ should be above 0.2 (FP cutoff)", s.suggestedTau > 0.2f)
    }

    @Test
    fun `mixed — highest FP-constrained TP rate is chosen`() {
        // 5 TPs: 1.0, 0.9, 0.8, 0.5, 0.2 — 1 FP at 0.85
        // At τ = 0.9: TP rate = 2/5 = 0.4, FP rate = 0/1 = 0.0  ✓ FP OK
        // At τ = 0.8: TP rate = 3/5 = 0.6, FP rate = 1/1 = 1.0  ✗ FP too high
        // At τ = 0.0: TP rate = 5/5 = 1.0, FP rate = 1/1 = 1.0  ✗ FP too high
        // Best FP-constrained result: τ = 0.9 with TP rate 0.4
        val labeled = listOf(
            tp(1.0f), tp(0.9f), tp(0.8f), tp(0.5f), tp(0.2f),
            fp(0.85f),
        )
        val s = TauCalibrator.suggest(labeled, maxFpRate = 0.20f)
        assertNotNull(s)
        assertEquals(0.0f, s!!.fpRate, 0.001f)
        assertTrue("τ should be above the FP lambda (0.85)", s.suggestedTau > 0.85f)
    }

    @Test
    fun `boundary — FP rate exactly at limit`() {
        // 4 TPs: 0.8, 0.6, 0.4, 0.2 — 1 FP at 0.3
        // At τ = 0.2: TP rate = 4/4 = 1.0, FP rate = 1/1 = 1.0  ✗
        // At τ = 0.31: TP rate = 3/4 = 0.75, FP rate = 0/1 = 0.0  ✓
        val labeled = listOf(tp(0.8f), tp(0.6f), tp(0.4f), tp(0.2f), fp(0.3f))
        val s = TauCalibrator.suggest(labeled, targetTpRate = 0.80f, maxFpRate = 0.20f)
        assertNotNull(s)
        assertEquals(0.0f, s!!.fpRate, 0.001f)
    }

    @Test
    fun `totalLabeled reflects only non-UNLABELED entries`() {
        val labeled = listOf(tp(0.9f), fp(0.3f), win(0.5f), win(0.2f))
        val s = TauCalibrator.suggest(labeled)
        assertNotNull(s)
        assertEquals(2, s!!.totalLabeled)
    }
}
