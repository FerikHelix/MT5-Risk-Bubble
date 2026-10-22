package com.ferik.mt5risk.core

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskEngineTest {

    private fun d(text: String) = BigDecimal(text)

    private val forex = VolumeRule.forMinLot(d("0.01"))
    private val index = VolumeRule.forMinLot(d("0.1"))

    private fun request(
        balance: String = "5000",
        percent: String = "1",
        volume: String,
        loss: String,
        rule: VolumeRule = forex,
    ) = SizingRequest(d(balance), d(percent), d(volume), d(loss), rule)

    private fun assertVolume(expected: String, result: SizingResult, rule: VolumeRule) {
        assertEquals(expected, rule.format(result.volume))
    }

    @Test
    fun riskBudgetIsPercentOfBalance() {
        assertEquals(0, d("50").compareTo(RiskEngine.riskBudget(d("5000"), d("1"))))
        assertEquals(0, d("22.5338").compareTo(RiskEngine.riskBudget(d("4506.76"), d("0.5"))))
    }

    @Test
    fun spikeNumbers_oneStandardPercentOfFiveThousand_givesZeroPointThirtyThree() {
        // Observed on device: 0.01 lots lost 1.49 USD at the stop-loss.
        val result = RiskEngine.size(request(volume = "0.01", loss = "1.49"))

        assertEquals(0, d("50").compareTo(result.riskBudget))
        assertTrue(result.unrounded > d("0.3355") && result.unrounded < d("0.3356"))
        assertVolume("0.33", result, forex)
        assertNull(result.clamp)
        assertFalse(result.minLotExceedsRisk)
    }

    @Test
    fun floorsAndNeverRoundsUp() {
        // unrounded is 0.3399...: must give 0.33, not 0.34
        val almost = RiskEngine.size(request(volume = "1", loss = "147.1"))
        assertTrue(almost.unrounded < d("0.34"))
        assertVolume("0.33", almost, forex)

        // an exact multiple of the step is kept as is: 0.34 * 50 / 50 = 0.34
        val clean = RiskEngine.size(request(volume = "0.34", loss = "50"))
        assertVolume("0.34", clean, forex)
    }

    @Test
    fun centAccountScalesInAccountCurrency() {
        // 0.84 lots lost 54.51 USC; balance 4506.77 USC, 1 % = 45.0677 USC
        val result = RiskEngine.size(request(balance = "4506.77", volume = "0.84", loss = "54.51"))
        assertTrue(result.unrounded > d("0.694") && result.unrounded < d("0.695"))
        assertVolume("0.69", result, forex)
    }

    @Test
    fun indexRuleStepsInTenths() {
        // 50 / 14.84 = 3.369...  ->  3.3 on a 0.1 step
        val result = RiskEngine.size(request(volume = "1", loss = "14.84", rule = index))
        assertVolume("3.3", result, index)
        assertNull(result.clamp)
    }

    @Test
    fun belowMinimumIsRaisedToMinimumWithWarning() {
        // 0.1 * 50 / 83.33 = 0.06  <  min 0.1
        val result = RiskEngine.size(request(volume = "0.1", loss = "83.33", rule = index))
        assertVolume("0.1", result, index)
        assertEquals(VolumeClamp.RAISED_TO_MIN, result.clamp)
        assertTrue(result.minLotExceedsRisk)
    }

    @Test
    fun exactlyTheMinimumIsNotAWarning() {
        // 0.1 * 50 / 50 = 0.1 == min
        val result = RiskEngine.size(request(volume = "0.1", loss = "50", rule = index))
        assertVolume("0.1", result, index)
        assertNull(result.clamp)
        assertFalse(result.minLotExceedsRisk)
    }

    @Test
    fun aboveMaximumIsCappedAtMaximum() {
        // tiny loss per lot -> huge lot
        val result = RiskEngine.size(request(volume = "1", loss = "0.01"))
        assertVolume("100.00", result, forex)
        assertEquals(VolumeClamp.CAPPED_AT_MAX, result.clamp)
        assertFalse(result.minLotExceedsRisk)
    }

    @Test
    fun customMaximumIsRespected() {
        val rule = VolumeRule.forMinLot(d("0.01"), max = d("50"))
        val result = RiskEngine.size(request(volume = "1", loss = "0.5", rule = rule))
        assertVolume("50.00", result, rule)
        assertEquals(VolumeClamp.CAPPED_AT_MAX, result.clamp)
    }

    @Test
    fun wholeLotRuleHasNoDecimals() {
        val whole = VolumeRule.forMinLot(d("1"))
        val result = RiskEngine.size(request(volume = "1", loss = "14.84", rule = whole))
        assertVolume("3", result, whole)
    }

    @Test
    fun ruleNormalisesTrailingZerosInMinLot() {
        val rule = VolumeRule.forMinLot(d("0.10"))
        assertEquals(0, d("0.1").compareTo(rule.step))
        assertEquals("3.3", rule.format(rule.floorToStep(d("3.37"))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveLoss() {
        RiskEngine.size(request(volume = "0.01", loss = "0"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveBalance() {
        RiskEngine.size(request(balance = "0", volume = "0.01", loss = "1"))
    }
}
