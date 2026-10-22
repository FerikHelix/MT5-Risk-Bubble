package com.ferik.mt5risk.ui

import com.ferik.mt5risk.core.SymbolVolumeRules
import com.ferik.mt5risk.mt5.Fixtures
import com.ferik.mt5risk.mt5.TicketReader
import com.ferik.mt5risk.mt5.TicketReading
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // --- risk percentages ---------------------------------------------------------------------

    @Test
    fun theThreePresetsAreHalfOneAndTwoPercent() {
        assertEquals(listOf("0.5", "1", "2"), PickerLogic.RISK_PRESETS.map { it.toPlainString() })
    }

    @Test
    fun aCustomRiskMustBeAboveZeroAndAtMostOneHundred() {
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
    fun theLastUsedPercentageIsHighlightedWhateverItsFormatting() {
        assertTrue(PickerLogic.isLastUsed(d("1"), d("1.00")))
        assertTrue(PickerLogic.isLastUsed(d("0.5"), d("0.50")))
        assertFalse(PickerLogic.isLastUsed(d("2"), d("1")))
        assertFalse(PickerLogic.isLastUsed(d("1"), null))
    }

    @Test
    fun percentageLabelsDropTrailingZeros() {
        assertEquals("1 %", PickerLogic.label(d("1.00")))
        assertEquals("0.5 %", PickerLogic.label(d("0.50")))
        assertEquals("0.75 %", PickerLogic.label(d("0.75")))
    }

    // --- manual balance -----------------------------------------------------------------------

    @Test
    fun aManualBalanceIsAPositiveNumberWithOrWithoutThousandsSpaces() {
        assertEquals(0, d("5000").compareTo(PickerLogic.parseManualBalance("5000")!!))
        assertEquals(0, d("5000.50").compareTo(PickerLogic.parseManualBalance("5 000.50")!!))
        assertEquals(0, d("4506.77").compareTo(PickerLogic.parseManualBalance("4 506.77")!!))
        assertNull(PickerLogic.parseManualBalance("0"))
        assertNull(PickerLogic.parseManualBalance("-5"))
        assertNull(PickerLogic.parseManualBalance("abc"))
        assertNull(PickerLogic.parseManualBalance(null))
    }
}
