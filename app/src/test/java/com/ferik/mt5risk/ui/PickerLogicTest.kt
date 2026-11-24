package com.ferik.mt5risk.ui

import com.ferik.mt5risk.core.PropFirm
import com.ferik.mt5risk.core.RiskBasisKind
import com.ferik.mt5risk.core.RiskSetting
import com.ferik.mt5risk.core.SymbolVolumeRules
import com.ferik.mt5risk.mt5.Fixtures
import com.ferik.mt5risk.mt5.TicketReader
import com.ferik.mt5risk.mt5.TicketReading
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerLogicTest {

    private fun d(text: String) = BigDecimal(text)

    private val rules = SymbolVolumeRules().withMinLot("GBPJPY", d("0.01"))

    private fun step(name: String) = PickerLogic.stepFor(TicketReader.read(Fixtures.load(name)), rules)

    // --- which step the picker opens on ------------------------------------------------------

    @Test
    fun aKnownSymbolGoesStraightToTheRiskChoices() {
        assertEquals(PickerStep.ChooseRisk("GBPJPY"), step("collapsed_hidden_ticket"))
        assertEquals(PickerStep.ChooseRisk("GBPJPY"), step("collapsed_strip"))
    }

    @Test
    fun anExpandedTicketTheUserOpenedAlsoUsesItsSymbol() {
        assertEquals(PickerStep.ChooseRisk("GBPJPY"), step("expanded_market_sl_set"))
    }

    @Test
    fun anUnknownSymbolAsksForItsMinimumLotFirst() {
        val onlyIndex = SymbolVolumeRules().withMinLot("SP500", d("0.1"))
        val result = PickerLogic.stepFor(TicketReader.read(Fixtures.load("collapsed_hidden_ticket")), onlyIndex)
        assertEquals(PickerStep.AskMinLot("GBPJPY"), result)
    }

    @Test
    fun noTicketOnScreenAsksTheUserToOpenIt() {
        val result = step("chart_no_ticket")
        assertEquals(PickerStep.Unavailable("Open the order ticket first"), result)
    }

    @Test
    fun theTradeTabIsNotATicketEither() {
        assertTrue(step("trade_tab") is PickerStep.Unavailable)
    }

    @Test
    fun aStripWhoseSymbolCannotBeReadIsUnavailable() {
        val reading = TicketReader.read(
            Fixtures.load("collapsed_strip") { it.replace("id/symbol_name", "id/renamed_by_an_mt5_update") },
        )
        val result = PickerLogic.stepFor(reading, rules)
        assertTrue(result is PickerStep.Unavailable)
        assertTrue((result as PickerStep.Unavailable).message.contains("symbol_name"))
    }

    @Test
    fun withoutTheAccessibilityServiceTheUserIsTold() {
        val result = PickerLogic.stepFor(null, rules)
        assertTrue((result as PickerStep.Unavailable).message.contains("accessibility service"))
    }

    @Test
    fun anUnreadableTicketNamesTheField() {
        val result = PickerLogic.stepFor(TicketReading.Unreadable("sl_input"), rules)
        assertEquals(PickerStep.Unavailable("Couldn't read the order ticket (sl_input)"), result)
    }

    // --- risk entry ---------------------------------------------------------------------------

    @Test
    fun aRiskMustBeAboveZeroAndAtMostOneHundred() {
        assertEquals(0, d("0.75").compareTo(PickerLogic.parseCustomRisk("0.75")!!))
        assertEquals(0, d("100").compareTo(PickerLogic.parseCustomRisk("100")!!))
        assertEquals(0, d("1.5").compareTo(PickerLogic.parseCustomRisk(" 1,5 ")!!))
        assertNull(PickerLogic.parseCustomRisk("0"))
        assertNull(PickerLogic.parseCustomRisk("-1"))
        assertNull(PickerLogic.parseCustomRisk("100.01"))
        assertNull(PickerLogic.parseCustomRisk("abc"))
        assertNull(PickerLogic.parseCustomRisk(""))
        assertNull(PickerLogic.parseCustomRisk(null))
    }

    @Test
    fun percentageLabelsDropTrailingZeros() {
        assertEquals("1 %", PickerLogic.label(d("1.00")))
        assertEquals("0.5 %", PickerLogic.label(d("0.50")))
        assertEquals("0.75 %", PickerLogic.label(d("0.75")))
    }

    @Test
    fun aFixedRiskAmountMustBePositiveAndBounded() {
        assertEquals(0, d("60").compareTo(PickerLogic.parseFixedRisk("60")!!))
        assertEquals(0, d("60.50").compareTo(PickerLogic.parseFixedRisk(" 60,50 ")!!))
        assertNull(PickerLogic.parseFixedRisk("0"))
        assertNull(PickerLogic.parseFixedRisk("-5"))
        assertNull(PickerLogic.parseFixedRisk("1000001"))
        assertNull(PickerLogic.parseFixedRisk("abc"))
        assertNull(PickerLogic.parseFixedRisk(null))
    }

    @Test
    fun theStagedRiskFollowsTheSelectedUnit() {
        assertEquals(
            RiskSetting.Percent(d("1")),
            PickerLogic.riskSetting(percent = d("1"), fixed = d("60"), fixedMode = false),
        )
        assertEquals(
            RiskSetting.Fixed(d("60")),
            PickerLogic.riskSetting(percent = d("1"), fixed = d("60"), fixedMode = true),
        )
        assertNull(PickerLogic.riskSetting(percent = null, fixed = d("60"), fixedMode = false))
        assertNull(PickerLogic.riskSetting(percent = d("1"), fixed = null, fixedMode = true))
    }

    // --- prop-firm numbers and the basis ------------------------------------------------------

    @Test
    fun propFirmNumbersMustBePositiveAndTheDrawdownMustBeBelowTheStart() {
        val propFirm = PickerLogic.propFirm("10000", "500")!!
        assertEquals(0, d("10000").compareTo(propFirm.startBalance))
        assertEquals(0, d("500").compareTo(propFirm.maxDrawdown))

        assertNull(PickerLogic.propFirm("0", "500"))
        assertNull(PickerLogic.propFirm("10000", "0"))
        assertNull(PickerLogic.propFirm("-10000", "500"))
        assertNull("drawdown must be below the start", PickerLogic.propFirm("10000", "10000"))
        assertNull(PickerLogic.propFirm("", "500"))
        assertNull(PickerLogic.propFirm("10000", null))
        assertNull(PickerLogic.propFirm("abc", "def"))
    }

    @Test
    fun aThousandsSpaceIsAcceptedInPropFirmNumbers() {
        assertEquals(0, d("10000").compareTo(PickerLogic.parsePropFirmStart("10 000")!!))
        assertEquals(0, d("500.50").compareTo(PickerLogic.parsePropFirmDrawdown(" 500,50 ")!!))
    }

    @Test
    fun theBaseIsTheBalanceOrTheDrawdownBuffer() {
        val propFirm = PropFirm(d("10000"), d("500"))

        val balance = PickerLogic.basePreview(d("9600"), RiskBasisKind.BALANCE, propFirm)!!
        assertEquals(0, d("9600").compareTo(balance.value))
        assertEquals(RiskBasisKind.BALANCE, balance.kind)

        val buffer = PickerLogic.basePreview(d("9600"), RiskBasisKind.DRAWDOWN, propFirm)!!
        assertEquals(0, d("100").compareTo(buffer.value))
        assertEquals(RiskBasisKind.DRAWDOWN, buffer.kind)

        assertNull("no balance, no base", PickerLogic.basePreview(null, RiskBasisKind.BALANCE, propFirm))
    }

    @Test
    fun thePreviewIsTheMoneyAtRisk() {
        assertEquals(0, d("50").compareTo(PickerLogic.previewRisk(d("1"), null, fixedMode = false, base = d("5000"))!!))
        assertEquals(0, d("60").compareTo(PickerLogic.previewRisk(d("1"), d("60"), fixedMode = true, base = d("5000"))!!))
        assertNull(PickerLogic.previewRisk(null, null, fixedMode = false, base = d("5000")))
        assertNull(PickerLogic.previewRisk(d("1"), null, fixedMode = false, base = null))
    }

    // --- manual balance -----------------------------------------------------------------------

    @Test
    fun aManualBalanceIsAPositiveNumberWithOrWithoutThousandsSpaces() {
        assertEquals(0, d("5000").compareTo(PickerLogic.parseManualBalance("5000")!!))
        assertEquals(0, d("5000.50").compareTo(PickerLogic.parseManualBalance("5 000.50")!!))
        assertEquals(0, d("4506.77").compareTo(PickerLogic.parseManualBalance("4 506.77")!!))
        assertNull(PickerLogic.parseManualBalance("0"))
        assertNull(PickerLogic.parseManualBalance("-5"))
        assertNull(PickerLogic.parseManualBalance("abc"))
        assertNull(PickerLogic.parseManualBalance(null))
    }
}
