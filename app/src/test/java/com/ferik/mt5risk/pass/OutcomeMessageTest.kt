package com.ferik.mt5risk.pass

import com.ferik.mt5risk.core.Refusal
import com.ferik.mt5risk.core.RiskBasisKind
import com.ferik.mt5risk.core.RiskSetting
import com.ferik.mt5risk.core.VolumeClamp
import com.ferik.mt5risk.core.VolumeRule
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutcomeMessageTest {

    private fun d(text: String) = BigDecimal(text)

    private fun success(
        volumeText: String = "0.33",
        loss: String = "49.17",
        currency: String? = "USD",
        percent: String = "1",
        base: String = "5000",
        baseKind: RiskBasisKind = RiskBasisKind.BALANCE,
        clamp: VolumeClamp? = null,
        minLotExceedsRisk: Boolean = false,
        riskSetting: RiskSetting = RiskSetting.Percent(d(percent)),
    ) = PassOutcome.Success(
        symbol = "GBPJPY",
        volume = d(volumeText),
        volumeText = volumeText,
        riskSetting = riskSetting,
        base = d(base),
        baseKind = baseKind,
        observedLoss = d(loss),
        currency = currency,
        clamp = clamp,
        minLotExceedsRisk = minLotExceedsRisk,
        writes = 1,
    )

    @Test
    fun successShowsLotsLossAndPercentOfBalance() {
        val message = OutcomeMessage.of(success())
        assertEquals("0.33 lots - loss 49.17 USD (0.98 % of balance)", message.text)
        assertEquals(MessageSeverity.SUCCESS, message.severity)
        assertNull(message.offerResetRuleFor)
    }

    @Test
    fun successWithoutAKnownCurrencyOmitsIt() {
        assertEquals(
            "0.33 lots - loss 49.17 (0.98 % of balance)",
            OutcomeMessage.of(success(currency = null)).text,
        )
    }

    @Test
    fun centAccountAmountsUseTheirSuffix() {
        val message = OutcomeMessage.of(success(volumeText = "0.69", loss = "44.91", currency = "USC", base = "4506.77"))
        assertEquals("0.69 lots - loss 44.91 USC (1.00 % of balance)", message.text)
    }

    @Test
    fun largeAmountsUseSpaceThousands() {
        val message = OutcomeMessage.of(success(volumeText = "12.5", loss = "1234.50", base = "123450"))
        assertEquals("12.5 lots - loss 1\u00A0234.50 USD (1.00 % of balance)", message.text)
    }

    @Test
    fun aDrawdownBasisIsNamedInsteadOfTheBalance() {
        val message = OutcomeMessage.of(
            success(loss = "0.98", base = "100", baseKind = RiskBasisKind.DRAWDOWN),
        )
        assertEquals("0.33 lots - loss 0.98 USD (0.98 % of drawdown left)", message.text)
    }

    @Test
    fun anExhaustedDrawdownFloorHasItsOwnReason() {
        val message = OutcomeMessage.of(PassOutcome.Refused(Refusal.DrawdownExhausted))

        assertEquals(MessageSeverity.ERROR, message.severity)
        assertEquals("The drawdown floor has been reached - no room left to size", message.text)
    }

    @Test
    fun aFixedRiskIsShownAsAnAmountAsWellAsItsAchievedPercent() {
        val message = OutcomeMessage.of(
            success(loss = "58.00", riskSetting = RiskSetting.Fixed(d("60"))),
        )
        assertEquals("0.33 lots - loss 58.00 USD - risk 60.00 USD (1.16 % of balance)", message.text)
        assertEquals(MessageSeverity.SUCCESS, message.severity)
    }

    @Test
    fun minimumLotAboveAFixedRiskNamesTheAmount() {
        val message = OutcomeMessage.of(
            success(loss = "100.00", minLotExceedsRisk = true, riskSetting = RiskSetting.Fixed(d("60"))),
        )
        assertEquals(MessageSeverity.WARNING, message.severity)
        assertTrue(message.text, message.text.contains("Minimum lot already risks more than your 60.00 USD"))
    }

    @Test
    fun minimumLotAboveRiskIsAWarning() {
        val message = OutcomeMessage.of(success(volumeText = "0.01", loss = "100.00", minLotExceedsRisk = true))
        assertEquals(MessageSeverity.WARNING, message.severity)
        assertTrue(message.text, message.text.contains("Minimum lot already risks more than your 1 %"))
        assertTrue(message.text.startsWith("0.01 lots - loss 100.00 USD"))
    }

    @Test
    fun cappedAtMaximumIsAWarning() {
        val message = OutcomeMessage.of(success(volumeText = "100.00", clamp = VolumeClamp.CAPPED_AT_MAX))
        assertEquals(MessageSeverity.WARNING, message.severity)
        assertTrue(message.text.endsWith("Capped at the maximum volume"))
    }

    @Test
    fun refusalsShowTheirOwnReason() {
        val message = OutcomeMessage.of(PassOutcome.Refused(Refusal.NoStopLoss))
        assertEquals("Set a stop-loss first", message.text)
        assertEquals(MessageSeverity.ERROR, message.severity)
    }

    @Test
    fun unknownSymbolRefusalNamesTheSymbol() {
        val message = OutcomeMessage.of(PassOutcome.Refused(Refusal.NoVolumeRule("SP500")))
        assertEquals("Set the minimum lot for SP500 first", message.text)
    }

    @Test
    fun ruleMismatchExplainsAndOffersTheReset() {
        val message = OutcomeMessage.of(
            PassOutcome.RuleMismatch("SP500", VolumeRule.forMinLot(d("0.01")), wrote = d("0.33"), shown = d("0.3")),
        )
        assertEquals(MessageSeverity.ERROR, message.severity)
        assertEquals("SP500", message.offerResetRuleFor)
        assertTrue(message.text, message.text.contains("MT5 shows 0.3 instead of 0.33"))
        assertTrue(message.text.contains("Saved minimum lot for SP500 is 0.01"))
    }

    @Test
    fun exhaustedReportsTheLastObservedLossAndBudget() {
        val message = OutcomeMessage.of(
            PassOutcome.Exhausted(volume = d("0.28"), observedLoss = d("50.40"), budget = d("50"), currency = "USD"),
        )
        assertEquals(MessageSeverity.ERROR, message.severity)
        assertEquals("Still over budget after 3 tries: 50.40 USD at 0.28 lots, budget 50.00 USD.", message.text)
    }

    @Test
    fun failureShowsItsReason() {
        assertEquals("Couldn't set the volume", OutcomeMessage.of(PassOutcome.Failed("Couldn't set the volume")).text)
    }

    @Test
    fun aSheetThatCouldNotBeClosedIsMentioned() {
        val message = OutcomeMessage.of(PassResult(success(), collapseFailed = true))
        assertEquals(MessageSeverity.WARNING, message.severity)
        assertTrue(message.text.endsWith("The ticket could not be closed - swipe it down."))
    }

    @Test
    fun aCleanResultAddsNothing() {
        assertEquals(OutcomeMessage.of(success()), OutcomeMessage.of(PassResult(success())))
    }
}
