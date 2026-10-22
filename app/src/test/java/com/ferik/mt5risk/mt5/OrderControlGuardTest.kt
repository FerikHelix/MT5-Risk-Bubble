package com.ferik.mt5risk.mt5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderControlGuardTest {

    private val market = Fixtures.load("expanded_market_sl_set")
    private val pending = Fixtures.load("expanded_pending_no_stops")
    private val chart = Fixtures.load("chart_no_ticket")

    private fun node(root: UiNode, id: String): UiNode =
        root.findFirst(id).also { assertNotNull("fixture should contain $id", it) }!!

    /** Minimal node for cases the recorded screens cannot show, such as a child inside a button. */
    private class FakeNode(
        override val bounds: Bounds,
        override val viewId: String? = null,
        override val children: List<UiNode> = emptyList(),
    ) : UiNode {
        override val packageName: String = Mt5Ids.PACKAGE
        override val text: String? = null
        override val className: String? = null
        override val isClickable: Boolean = true
        override val isEditable: Boolean = false
    }

    // --- click and set-text targets ---------------------------------------------------------

    @Test
    fun sellAndBuyAreForbiddenTargets() {
        assertTrue(OrderControlGuard.isForbiddenTarget(market, node(market, Mt5Ids.SELL_BTN)))
        assertTrue(OrderControlGuard.isForbiddenTarget(market, node(market, Mt5Ids.BUY_BTN)))
    }

    @Test
    fun placeIsAForbiddenTargetOnPendingTickets() {
        assertTrue(OrderControlGuard.isForbiddenTarget(pending, node(pending, Mt5Ids.PLACE_BTN)))
    }

    @Test
    fun oneClickTradingToggleIsForbidden() {
        val toggle = node(chart, Mt5Ids.MENU_CHART_ONE_CLICK_ORDER)
        assertTrue(OrderControlGuard.isForbiddenTarget(chart, toggle))
    }

    @Test
    fun theControlsTheDriverActuallyUsesAreAllowed() {
        val collapsed = Fixtures.load("collapsed_strip")
        assertFalse(OrderControlGuard.isForbiddenTarget(collapsed, node(collapsed, Mt5Ids.BTN_SUBMIT)))
        assertFalse(OrderControlGuard.isForbiddenTarget(market, node(market, Mt5Ids.VOLUME_INPUT)))
        assertFalse(OrderControlGuard.isForbiddenTarget(market, node(market, Mt5Ids.SL_INPUT)))
    }

    @Test
    fun aChildInsideAForbiddenControlIsForbiddenToo() {
        val sell = node(market, Mt5Ids.SELL_BTN)
        val inner = FakeNode(Bounds(sell.bounds.left + 10, sell.bounds.top + 10, sell.bounds.right - 10, sell.bounds.bottom - 10))
        assertTrue(OrderControlGuard.isForbiddenTarget(market, inner))
    }

    @Test
    fun aContainerHoldingAForbiddenControlIsNotForbiddenByThatAlone() {
        val sell = node(market, Mt5Ids.SELL_BTN)
        val container = FakeNode(Bounds(0, sell.bounds.top - 500, 1080, sell.bounds.bottom + 100))
        assertFalse(OrderControlGuard.isForbiddenTarget(market, container))
    }

    @Test
    fun forbiddenBoundsListsEveryOrderControlOnScreen() {
        // the one-click trading toggle sits in the toolbar on every chart screen
        assertEquals(3, OrderControlGuard.forbiddenBounds(market).size) // sell_btn, buy_btn, toggle
        assertEquals(2, OrderControlGuard.forbiddenBounds(pending).size) // place_btn, toggle
        assertEquals(1, OrderControlGuard.forbiddenBounds(chart).size) // toggle only
    }

    // --- gesture paths ----------------------------------------------------------------------

    @Test
    fun theCollapseSwipeThatWorkedOnDeviceIsAllowed() {
        // 930 -> 2000 down the middle of the screen, ending above the order buttons
        assertFalse(OrderControlGuard.pathCrossesForbidden(market, Point(540, 930), Point(540, 2000)))
    }

    @Test
    fun aSwipeEndingOnTheBuySellRowIsVetoed() {
        val sell = node(market, Mt5Ids.SELL_BTN)
        val onButtons = sell.bounds.centerY
        assertTrue(OrderControlGuard.pathCrossesForbidden(market, Point(300, 930), Point(300, onButtons)))
    }

    @Test
    fun aSwipeRunningThroughTheGapBetweenBuyAndSellIsVetoed() {
        // x = 540 sits between sell_btn (right edge 534) and buy_btn (left edge 546)
        assertTrue(OrderControlGuard.pathCrossesForbidden(market, Point(540, 930), Point(540, 2250)))
    }

    @Test
    fun aSwipePassingAcrossThePlaceButtonIsVetoed() {
        assertTrue(OrderControlGuard.pathCrossesForbidden(pending, Point(540, 1100), Point(540, 2250)))
    }

    @Test
    fun aSwipeEndingJustInsideTheSafetyMarginIsVetoed() {
        val sellTop = node(market, Mt5Ids.SELL_BTN).bounds.top
        val insideMargin = sellTop - OrderControlGuard.SAFETY_MARGIN_PX + 2
        assertTrue(OrderControlGuard.pathCrossesForbidden(market, Point(300, 930), Point(300, insideMargin)))
        val outsideMargin = sellTop - OrderControlGuard.SAFETY_MARGIN_PX - 2
        assertFalse(OrderControlGuard.pathCrossesForbidden(market, Point(300, 930), Point(300, outsideMargin)))
    }

    @Test
    fun aHorizontalStrokeAcrossTheButtonsIsVetoed() {
        val y = node(market, Mt5Ids.BUY_BTN).bounds.centerY
        assertTrue(OrderControlGuard.pathCrossesForbidden(market, Point(0, y), Point(1080, y)))
    }

    @Test
    fun withNoOrderControlsOnScreenEveryPathIsAllowed() {
        val tradeTab = Fixtures.load("trade_tab")
        assertTrue(OrderControlGuard.forbiddenBounds(tradeTab).isEmpty())
        assertFalse(
            OrderControlGuard.pathCrossesForbidden(
                Fixtures.load("trade_tab"),
                Point(540, 400),
                Point(540, 2000),
            ),
        )
    }
}
