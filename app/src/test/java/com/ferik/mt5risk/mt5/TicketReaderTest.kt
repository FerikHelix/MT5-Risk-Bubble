package com.ferik.mt5risk.mt5

import com.ferik.mt5risk.core.Preconditions
import com.ferik.mt5risk.core.Refusal
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketReaderTest {

    private fun d(text: String) = BigDecimal(text)

    private fun expanded(name: String, edit: (String) -> String = { it }): TicketSnapshot {
        val reading = TicketReader.read(Fixtures.load(name, edit))
        assertTrue("expected Expanded for $name but was $reading", reading is TicketReading.Expanded)
        return (reading as TicketReading.Expanded).ticket
    }

    // --- fixtures ---------------------------------------------------------------------------

    @Test
    fun everyRecordedFixtureLoadsAsAnMt5Tree() {
        for (name in Fixtures.ALL) {
            val root = Fixtures.load(name)
            val nodes = root.walk().toList()
            assertTrue("$name should have nodes", nodes.size > 20)
            assertTrue(
                "$name should only contain MetaTrader 5 nodes",
                nodes.drop(1).all { it.packageName == Mt5Ids.PACKAGE },
            )
        }
    }

    @Test
    fun uiNodeExposesIdsTextAndBounds() {
        val root = Fixtures.load("expanded_market_sl_set")
        val volume = root.findFirst(Mt5Ids.VOLUME_INPUT)
        assertNotNull(volume)
        assertEquals("net.metaquotes.metatrader5:id/volume_lots_input", volume!!.viewId)
        assertEquals("0.01", volume.text)
        assertTrue(volume.isEditable)
        assertEquals(Bounds(440, 1298, 715, 1419), volume.bounds)
    }

    // --- screen classification --------------------------------------------------------------

    @Test
    fun collapsedStripGivesTheSymbolWithoutExpanding() {
        val reading = TicketReader.read(Fixtures.load("collapsed_strip"))
        assertEquals(TicketReading.Collapsed("GBPJPY", hidden = null, hiddenProblem = Mt5Ids.VOLUME_INPUT), reading)
    }

    /**
     * Observed on the device: while the sheet is collapsed MT5 keeps the whole expanded ticket in the
     * accessibility tree, off screen, with inverted bounds. It must not be mistaken for an open ticket.
     */
    private fun hiddenTicketNodes(): String =
        """
        <node text="18.69" resource-id="net.metaquotes.metatrader5:id/volume_lots_input" class="android.widget.EditText"
              package="net.metaquotes.metatrader5" clickable="true" visible-to-user="false" bounds="[440,2411][715,2400]"/>
        <node text="Lots" resource-id="net.metaquotes.metatrader5:id/volume_mode_text" class="android.widget.TextView"
              package="net.metaquotes.metatrader5" clickable="false" visible-to-user="false" bounds="[850,2445][926,2400]"/>
        <node text="5 000.00 USD" resource-id="net.metaquotes.metatrader5:id/free_margin_value" class="android.widget.TextView"
              package="net.metaquotes.metatrader5" clickable="false" visible-to-user="false" bounds="[855,2686][1036,2400]"/>
        <node text="Sell" resource-id="net.metaquotes.metatrader5:id/sell_btn" class="android.widget.Button"
              package="net.metaquotes.metatrader5" clickable="true" visible-to-user="false" bounds="[44,3295][534,2400]"/>
        """.trimIndent()

    private fun collapsedWithHiddenTicket() =
        Fixtures.load("collapsed_strip") { it.replace("</hierarchy>", hiddenTicketNodes() + "</hierarchy>") }

    @Test
    fun anIncompleteHiddenTicketIsStillJustTheStripAndNamesWhatBlockedIt() {
        // volume, unit, free margin and sell are hidden but there is no stop-loss field at all
        val reading = TicketReader.read(collapsedWithHiddenTicket())
        assertEquals(TicketReading.Collapsed("GBPJPY", hidden = null, hiddenProblem = Mt5Ids.SL_INPUT), reading)
    }

    // --- direct mode: the live fields behind the collapsed strip -----------------------------

    private fun collapsed(name: String, edit: (String) -> String = { it }): TicketReading.Collapsed {
        val reading = TicketReader.read(Fixtures.load(name, edit))
        assertTrue("expected Collapsed for $name but was $reading", reading is TicketReading.Collapsed)
        return reading as TicketReading.Collapsed
    }

    @Test
    fun theHiddenTicketBehindTheStripIsReadWithoutExpanding() {
        val strip = collapsed("collapsed_hidden_ticket")
        assertEquals("GBPJPY", strip.symbol)
        assertNull(strip.hiddenProblem)

        val ticket = strip.hidden!!
        assertEquals("GBPJPY", ticket.symbol)
        assertEquals(0, d("0.72").compareTo(ticket.volume))
        assertEquals("Lots", ticket.volumeUnit)
        assertEquals(0, d("210.000").compareTo(ticket.slPrice))
        assertEquals(0, d("-42.60").compareTo(ticket.slLoss!!.value))
        assertEquals("USD", ticket.slLoss!!.currency)
        assertEquals(0, d("5000.00").compareTo(ticket.freeMargin.value))
        assertEquals(0, d("192.46").compareTo(ticket.margin!!.value))
        assertFalse(ticket.isPending)
    }

    @Test
    fun snapshotOrNullGivesTheSameValuesWhetherCollapsedOrExpanded() {
        val hidden = TicketReader.read(Fixtures.load("collapsed_hidden_ticket")).snapshotOrNull()!!
        val open = TicketReader.read(Fixtures.load("expanded_market_sl_set")).snapshotOrNull()!!
        assertEquals("GBPJPY", hidden.symbol)
        assertEquals("GBPJPY", open.symbol)
        assertNull(TicketReader.read(Fixtures.load("chart_no_ticket")).snapshotOrNull())
        assertNull(TicketReader.read(Fixtures.load("collapsed_strip")).snapshotOrNull())
    }

    @Test
    fun hiddenTicketWithoutStopLevelsMeansNoStopLoss() {
        val ticket = collapsed("collapsed_hidden_no_stops").hidden!!
        assertEquals(0, BigDecimal.ZERO.compareTo(ticket.slPrice))
        assertNull(ticket.slLoss)
        val refusal = Preconditions.afterRead(
            balance = d("5000"),
            freeMargin = ticket.freeMargin.value,
            currentVolume = ticket.volume,
            slPrice = ticket.slPrice,
            slLoss = ticket.slLoss?.value,
        )
        assertEquals(Refusal.NoStopLoss, refusal)
    }

    @Test
    fun anUnparseableHiddenLossLabelSendsThePassToTheFallback() {
        val strip = collapsed("collapsed_hidden_ticket") { it.replace("text=\"-42.60 USD\"", "text=\"n/a\"") }
        assertNull(strip.hidden)
        assertEquals(Mt5Ids.SL_CALC_LOSS, strip.hiddenProblem)
        assertEquals("GBPJPY", strip.symbol)
    }

    @Test
    fun aHiddenTicketWithAMissingFieldSendsThePassToTheFallback() {
        val strip = collapsed("collapsed_hidden_ticket") {
            it.replace("id/free_margin_value", "id/renamed_by_an_mt5_update")
        }
        assertNull(strip.hidden)
        assertEquals(Mt5Ids.FREE_MARGIN_VALUE, strip.hiddenProblem)
    }

    @Test
    fun aStripWithNoHiddenTicketAtAllSendsThePassToTheFallback() {
        val strip = collapsed("collapsed_strip")
        assertNull(strip.hidden)
        assertEquals(Mt5Ids.VOLUME_INPUT, strip.hiddenProblem)
    }

    @Test
    fun theVisibleTicketWinsWhenTheUserHasItExpanded() {
        // an expanded ticket must be read in place, never treated as the hidden one
        val reading = TicketReader.read(Fixtures.load("expanded_market_sl_set"))
        assertTrue(reading is TicketReading.Expanded)
    }

    @Test
    fun hiddenOrderButtonsDoNotCountAsOnScreenControls() {
        val root = collapsedWithHiddenTicket()
        assertEquals(1, OrderControlGuard.forbiddenBounds(root).size) // only the toolbar toggle
    }

    @Test
    fun aHiddenVolumeFieldCannotBeReadAsTheTicket() {
        val onlyHidden = Fixtures.load("chart_no_ticket") { it.replace("</hierarchy>", hiddenTicketNodes() + "</hierarchy>") }
        assertEquals(TicketReading.NotTicket, TicketReader.read(onlyHidden))
    }

    @Test
    fun chartWithoutATicketIsNotATicket() {
        assertEquals(TicketReading.NotTicket, TicketReader.read(Fixtures.load("chart_no_ticket")))
    }

    @Test
    fun tradeTabIsNotATicket() {
        assertEquals(TicketReading.NotTicket, TicketReader.read(Fixtures.load("trade_tab")))
    }

    @Test
    fun nodesFromAnotherAppAreIgnored() {
        val reading = TicketReader.read(
            Fixtures.load("expanded_market_sl_set") {
                it.replace("package=\"net.metaquotes.metatrader5\"", "package=\"com.example.other\"")
            },
        )
        assertEquals(TicketReading.NotTicket, reading)
    }

    // --- expanded market ticket with a stop-loss --------------------------------------------

    @Test
    fun expandedMarketTicketWithStopLossIsReadInFull() {
        val ticket = expanded("expanded_market_sl_set")

        assertEquals("GBPJPY", ticket.symbol)
        assertEquals(0, d("0.01").compareTo(ticket.volume))
        assertEquals("Lots", ticket.volumeUnit)
        assertEquals(0, d("209.932").compareTo(ticket.slPrice))
        assertEquals(0, d("-1.19").compareTo(ticket.slLoss!!.value))
        assertEquals("USD", ticket.slLoss!!.currency)
        assertEquals(0, d("5000.00").compareTo(ticket.freeMargin.value))
        assertEquals("USD", ticket.freeMargin.currency)
        assertEquals(0, d("2.68").compareTo(ticket.margin!!.value))
        assertEquals(0, d("210.098").compareTo(ticket.bid!!))
        assertEquals(0, d("210.119").compareTo(ticket.ask!!))
        assertFalse(ticket.isPending)
        assertEquals("USD", ticket.currency)
    }

    @Test
    fun typedStopLossFixtureGivesTheLossObservedOnDevice() {
        val ticket = expanded("expanded_market_sl_typed")
        assertEquals(0, d("210.100").compareTo(ticket.slPrice))
        assertEquals(0, d("-1.49").compareTo(ticket.slLoss!!.value))
    }

    // --- no stop-loss -----------------------------------------------------------------------

    @Test
    fun marketTicketWithOnlyAddStopLevelsMeansNoStopLoss() {
        val ticket = expanded("expanded_market_no_stops")
        assertEquals(0, BigDecimal.ZERO.compareTo(ticket.slPrice))
        assertNull(ticket.slLoss)
    }

    @Test
    fun stopFieldsAtZeroMeanNoStopLoss() {
        val ticket = expanded("expanded_pending_stops_zero")
        assertEquals(0, BigDecimal.ZERO.compareTo(ticket.slPrice))
        assertNull(ticket.slLoss)
    }

    // --- pending ticket ---------------------------------------------------------------------

    @Test
    fun pendingTicketIsReadWithTheSameFields() {
        val ticket = expanded("expanded_pending_no_stops")
        assertTrue(ticket.isPending)
        assertEquals("GBPJPY", ticket.symbol)
        assertEquals(0, d("0.01").compareTo(ticket.volume))
        assertEquals(0, d("5000.00").compareTo(ticket.freeMargin.value))
    }

    // --- unreadable fields ------------------------------------------------------------------

    @Test
    fun missingFreeMarginIsUnreadable() {
        val reading = TicketReader.read(
            Fixtures.load("expanded_market_sl_set") {
                it.replace("id/free_margin_value", "id/renamed_by_an_mt5_update")
            },
        )
        assertEquals(TicketReading.Unreadable(Mt5Ids.FREE_MARGIN_VALUE), reading)
    }

    @Test
    fun unparseableLossLabelIsUnreadable() {
        val reading = TicketReader.read(
            Fixtures.load("expanded_market_sl_set") { it.replace("text=\"-1.19 USD\"", "text=\"n/a\"") },
        )
        assertEquals(TicketReading.Unreadable(Mt5Ids.SL_CALC_LOSS), reading)
    }

    @Test
    fun missingStopLossFieldAndNoAddButtonIsUnreadable() {
        val reading = TicketReader.read(
            Fixtures.load("expanded_market_sl_set") { it.replace("id/sl_input", "id/renamed_by_an_mt5_update") },
        )
        assertEquals(TicketReading.Unreadable(Mt5Ids.SL_INPUT), reading)
    }

    @Test
    fun missingSymbolIsUnreadable() {
        val reading = TicketReader.read(
            Fixtures.load("expanded_market_sl_set") { it.replace("id/symbol_name", "id/renamed_by_an_mt5_update") },
        )
        assertEquals(TicketReading.Unreadable(Mt5Ids.SYMBOL_NAME), reading)
    }

    @Test
    fun collapsedStripWithoutSymbolGivesNullSymbol() {
        val reading = TicketReader.read(
            Fixtures.load("collapsed_strip") { it.replace("id/symbol_name", "id/renamed_by_an_mt5_update") },
        )
        assertEquals(TicketReading.Collapsed(null, hidden = null, hiddenProblem = Mt5Ids.VOLUME_INPUT), reading)
    }

    @Test
    fun symbolFromTheCollapsedStripFeedsThePreExpandCheck() {
        val collapsed = TicketReader.read(Fixtures.load("collapsed_strip")) as TicketReading.Collapsed
        val rule = com.ferik.mt5risk.core.VolumeRule.forMinLot(d("0.01"))
        assertNull(Preconditions.beforeExpand(d("1"), collapsed.symbol, rule))
        assertEquals(
            Refusal.NoVolumeRule("GBPJPY"),
            Preconditions.beforeExpand(d("1"), collapsed.symbol, null),
        )

        val unreadable = TicketReader.read(
            Fixtures.load("collapsed_strip") { it.replace("id/symbol_name", "id/renamed_by_an_mt5_update") },
        ) as TicketReading.Collapsed
        assertEquals(Refusal.Unreadable("symbol_name"), Preconditions.beforeExpand(d("1"), unreadable.symbol, rule))
    }

    // --- volume unit (task 4.2) -------------------------------------------------------------

    @Test
    fun lotsUnitIsAccepted() {
        val ticket = expanded("expanded_market_sl_set")
        assertNull(Preconditions.volumeUnit(ticket.volumeUnit))
    }

    @Test
    fun nonLotsUnitProducesTheRefusal() {
        val ticket = expanded("expanded_market_sl_set") { it.replace("text=\"Lots\"", "text=\"Amount\"") }
        assertEquals("Amount", ticket.volumeUnit)
        assertEquals(Refusal.VolumeUnitNotLots("Amount"), Preconditions.volumeUnit(ticket.volumeUnit))
        assertEquals("Switch the ticket volume to Lots", Refusal.VolumeUnitNotLots("Amount").message)
    }

    @Test
    fun missingUnitSelectorIsUnreadable() {
        assertEquals(Refusal.Unreadable("volume_mode_text"), Preconditions.volumeUnit(null))
    }

    // --- feeds the precondition checks the way the pass will ---------------------------------

    @Test
    fun healthyTicketPassesPreconditionsWithAMatchingBalance() {
        val ticket = expanded("expanded_market_sl_set")
        val refusal = Preconditions.afterRead(
            balance = d("5000.00"),
            freeMargin = ticket.freeMargin.value,
            currentVolume = ticket.volume,
            slPrice = ticket.slPrice,
            slLoss = ticket.slLoss?.value,
        )
        assertNull(refusal)
    }

    @Test
    fun ticketWithoutStopLossIsRefusedByPreconditions() {
        val ticket = expanded("expanded_market_no_stops")
        val refusal = Preconditions.afterRead(
            balance = d("5000.00"),
            freeMargin = ticket.freeMargin.value,
            currentVolume = ticket.volume,
            slPrice = ticket.slPrice,
            slLoss = ticket.slLoss?.value,
        )
        assertEquals(Refusal.NoStopLoss, refusal)
    }
}
