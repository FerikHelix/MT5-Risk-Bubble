package com.ferik.mt5risk.core

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifyLoopTest {

    private fun d(text: String) = BigDecimal(text)

    private val forex = VolumeRule.forMinLot(d("0.01"))
    private val budget = d("50")

    private fun decide(writes: Int, volume: String, loss: String, rule: VolumeRule = forex) =
        VerifyLoop.decide(writes, d(volume), d(loss), budget, rule)

    private fun assertRetry(expected: String, decision: VerifyDecision, rule: VolumeRule = forex) {
        assertTrue("expected Retry but was $decision", decision is VerifyDecision.Retry)
        assertEquals(expected, rule.format((decision as VerifyDecision.Retry).volume))
    }

    @Test
    fun withinBudgetIsAccepted() {
        val decision = decide(1, "0.33", "48.65")
        assertEquals(VerifyDecision.Accept(d("0.33"), d("48.65")), decision)
    }

    @Test
    fun lossExactlyOnBudgetIsAccepted() {
        assertTrue(decide(1, "0.33", "50.00") is VerifyDecision.Accept)
    }

    @Test
    fun underUsedBudgetIsAcceptedNotRaised() {
        // 40 of 50 used because rounding went down: no attempt to raise the lot again
        assertTrue(decide(1, "0.26", "40.00") is VerifyDecision.Accept)
    }

    @Test
    fun priceDriftOverBudgetRetriesFromTheLatestPair() {
        // observed on device: 0.33 lots read -52.26 USD; 0.33 * 50 / 52.26 = 0.3157 -> 0.31
        assertRetry("0.31", decide(1, "0.33", "52.26"))
    }

    @Test
    fun convergesInOneCorrection() {
        assertRetry("0.31", decide(1, "0.33", "52.26"))
        assertTrue(decide(2, "0.31", "49.10") is VerifyDecision.Accept)
    }

    @Test
    fun marginallyOverBudgetStepsDownOneStep() {
        assertRetry("0.32", decide(1, "0.33", "50.01"))
    }

    @Test
    fun exhaustedAfterThreeWritesWhenStillOver() {
        val decision = decide(VerifyLoop.MAX_WRITES, "0.30", "51.00")
        assertEquals(VerifyDecision.Exhausted(d("0.30"), d("51.00")), decision)
    }

    @Test
    fun secondWriteStillGetsACorrection() {
        assertRetry("0.29", decide(2, "0.30", "51.00"))
    }

    @Test
    fun atMinimumLotAndOverBudgetReportsMinLotOverBudget() {
        val decision = decide(1, "0.01", "60.00")
        assertEquals(VerifyDecision.MinLotOverBudget(d("0.01"), d("60.00")), decision)
    }

    @Test
    fun minimumLotTakesPriorityOverExhaustion() {
        assertTrue(decide(3, "0.01", "60.00") is VerifyDecision.MinLotOverBudget)
    }

    @Test
    fun correctionNeverGoesBelowTheMinimum() {
        // 0.02 * 50 / 200 = 0.005 floors to 0.00; retry at the minimum instead
        assertRetry("0.01", decide(1, "0.02", "200.00"))
    }

    @Test
    fun indexRuleCorrectsInTenths() {
        val index = VolumeRule.forMinLot(d("0.1"))
        // 3.3 * 50 / 53 = 3.113 -> 3.1
        assertRetry("3.1", decide(1, "3.3", "53.00", index), index)
    }

    @Test
    fun threeWriteSequenceMatchesTheAcceptanceCriterion() {
        // write 1 over, write 2 over, write 3 over -> give up with the observed numbers
        assertRetry("0.31", decide(1, "0.33", "52.26"))
        assertRetry("0.30", decide(2, "0.31", "51.50"))
        val last = decide(3, "0.30", "50.90")
        assertTrue(last is VerifyDecision.Exhausted)
    }
}
