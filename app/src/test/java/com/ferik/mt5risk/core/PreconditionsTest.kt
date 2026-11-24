package com.ferik.mt5risk.core

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreconditionsTest {

    private fun d(text: String) = BigDecimal(text)

    private val forex = VolumeRule.forMinLot(d("0.01"))

    private fun before(percent: String? = "1", symbol: String? = "GBPJPY", rule: VolumeRule? = forex) =
        Preconditions.beforeExpand(percent?.let { RiskSetting.Percent(d(it)) }, symbol, rule)

    private fun beforeFixed(amount: String, symbol: String? = "GBPJPY", rule: VolumeRule? = forex) =
        Preconditions.beforeExpand(RiskSetting.Fixed(d(amount)), symbol, rule)

    private fun after(
        balance: String? = "5000",
        freeMargin: String? = "5000",
        volume: String? = "0.01",
        slPrice: String? = "209.932",
        slLoss: String? = "-1.19",
    ) = Preconditions.afterRead(
        balance?.let(::d),
        freeMargin?.let(::d),
        volume?.let(::d),
        slPrice?.let(::d),
        slLoss?.let(::d),
    )

    // --- before expanding -------------------------------------------------------------------

    @Test
    fun validRiskSymbolAndRulePass() {
        assertNull(before())
        assertNull(before(percent = "0.5"))
        assertNull(before(percent = "100"))
    }

    @Test
    fun riskPercentMustBeAboveZeroAndAtMostOneHundred() {
        assertTrue(before(percent = "0") is Refusal.InvalidRiskPercent)
        assertTrue(before(percent = "-1") is Refusal.InvalidRiskPercent)
        assertTrue(before(percent = "100.01") is Refusal.InvalidRiskPercent)
        assertTrue(before(percent = null) is Refusal.InvalidRiskPercent)
    }

    @Test
    fun aFixedRiskMustBeAboveZero() {
        assertNull(beforeFixed("60"))
        assertTrue(beforeFixed("0") is Refusal.InvalidFixedRisk)
        assertTrue(beforeFixed("-5") is Refusal.InvalidFixedRisk)
    }

    @Test
    fun unreadableSymbolIsRefused() {
        assertEquals(Refusal.Unreadable("symbol_name"), before(symbol = null))
        assertEquals(Refusal.Unreadable("symbol_name"), before(symbol = "  "))
    }

    @Test
    fun unknownSymbolWithoutRuleIsRefusedByName() {
        assertEquals(Refusal.NoVolumeRule("SP500"), before(symbol = "SP500", rule = null))
    }

    @Test
    fun invalidRiskIsReportedBeforeMissingRule() {
        assertTrue(before(percent = "0", rule = null) is Refusal.InvalidRiskPercent)
    }

    // --- after reading the ticket -----------------------------------------------------------

    @Test
    fun healthyTicketPasses() {
        assertNull(after())
    }

    @Test
    fun missingBalanceIsRefused() {
        assertEquals(Refusal.NoBalance, after(balance = null))
    }

    @Test
    fun zeroOrNegativeBalanceIsTreatedAsNoBalance() {
        assertEquals(Refusal.NoBalance, after(balance = "0", freeMargin = "0"))
        assertEquals(Refusal.NoBalance, after(balance = "-5", freeMargin = "0"))
    }

    @Test
    fun balanceBelowFreeMarginIsStale() {
        val refusal = after(balance = "4000", freeMargin = "5000")
        assertEquals(Refusal.StaleBalance(d("4000"), d("5000")), refusal)
    }

    @Test
    fun balanceAboveFreeMarginIsAccepted() {
        assertNull(after(balance = "5000", freeMargin = "4439.63"))
    }

    @Test
    fun balanceEqualToFreeMarginIsAccepted() {
        assertNull(after(balance = "5000", freeMargin = "5000"))
    }

    @Test
    fun unreadableFreeMarginIsRefused() {
        assertEquals(Refusal.Unreadable("free_margin_value"), after(freeMargin = null))
    }

    @Test
    fun unreadableOrZeroVolumeIsRefused() {
        assertEquals(Refusal.Unreadable("volume_lots_input"), after(volume = null))
        assertEquals(Refusal.Unreadable("volume_lots_input"), after(volume = "0"))
    }

    @Test
    fun zeroStopLossMeansNoStopLoss() {
        assertEquals(Refusal.NoStopLoss, after(slPrice = "0", slLoss = null))
    }

    @Test
    fun missingLossLabelMeansNoStopLoss() {
        assertEquals(Refusal.NoStopLoss, after(slLoss = null))
    }

    @Test
    fun unreadableStopLossFieldIsRefused() {
        assertEquals(Refusal.Unreadable("sl_input"), after(slPrice = null))
    }

    @Test
    fun positiveOrZeroLossMeansWrongSide() {
        assertEquals(Refusal.WrongSideStopLoss, after(slLoss = "1.19"))
        assertEquals(Refusal.WrongSideStopLoss, after(slLoss = "0"))
    }

    @Test
    fun staleBalanceIsReportedBeforeStopLossProblems() {
        assertTrue(after(balance = "4000", freeMargin = "5000", slPrice = "0") is Refusal.StaleBalance)
    }

    @Test
    fun everyRefusalHasAUserFacingMessage() {
        val refusals = listOf(
            Refusal.InvalidRiskPercent(d("0")),
            Refusal.InvalidFixedRisk(d("0")),
            Refusal.NoBalance,
            Refusal.StaleBalance(d("4000"), d("5000")),
            Refusal.NoVolumeRule("SP500"),
            Refusal.NoStopLoss,
            Refusal.WrongSideStopLoss,
            Refusal.Unreadable("sl_input"),
        )
        refusals.forEach { assertTrue(it.message.isNotBlank()) }
        assertEquals("Set a stop-loss first", Refusal.NoStopLoss.message)
        assertEquals("Set the minimum lot for SP500 first", Refusal.NoVolumeRule("SP500").message)
    }
}
