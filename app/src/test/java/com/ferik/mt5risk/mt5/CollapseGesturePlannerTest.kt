package com.ferik.mt5risk.mt5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapseGesturePlannerTest {

    private fun plan(name: String, edit: (String) -> String = { it }) =
        CollapseGesturePlanner.plan(Fixtures.load(name, edit))

    @Test
    fun marketTicketSwipesDownTheMiddleFromJustAboveTheHeader() {
        val path = plan("expanded_market_sl_set")!!
        // symbol row top is 990 in this recording; the swipe that worked on the phone started at 930
        assertEquals(Point(540, 930), path.start)
        assertEquals(540, path.end.x)
        assertTrue(path.end.y > path.start.y)
    }

    @Test
    fun theSwipeEndsAboveTheBuyAndSellRow() {
        val path = plan("expanded_market_sl_set")!!
        val root = Fixtures.load("expanded_market_sl_set")
        val sellTop = root.findFirst(Mt5Ids.SELL_BTN)!!.bounds.top
        assertTrue("ends at ${path.end.y}, sell starts at $sellTop", path.end.y <= sellTop - CollapseGesturePlanner.CLEARANCE_PX)
        assertFalse(OrderControlGuard.pathCrossesForbidden(root, path.start, path.end))
    }

    @Test
    fun pendingTicketSwipeStaysAboveThePlaceButton() {
        val root = Fixtures.load("expanded_pending_no_stops")
        val path = CollapseGesturePlanner.plan(root)!!
        val placeTop = root.findFirst(Mt5Ids.PLACE_BTN)!!.bounds.top
        assertTrue(path.end.y < placeTop - OrderControlGuard.SAFETY_MARGIN_PX)
        assertFalse(OrderControlGuard.pathCrossesForbidden(root, path.start, path.end))
    }

    @Test
    fun theLayoutWithTheKeyboardOpenIsStillSafe() {
        // recorded while the soft keyboard had pushed the sheet up
        val root = Fixtures.load("expanded_market_sl_typed")
        val path = CollapseGesturePlanner.plan(root)!!
        assertFalse(OrderControlGuard.pathCrossesForbidden(root, path.start, path.end))
        assertTrue(path.end.y - path.start.y >= CollapseGesturePlanner.MIN_SWIPE_PX)
    }

    @Test
    fun everyExpandedFixtureGetsASafePlan() {
        for (name in Fixtures.ALL.filter { it.startsWith("expanded") }) {
            val root = Fixtures.load(name)
            val path = CollapseGesturePlanner.plan(root)
            assertNotNull("no plan for $name", path)
            assertFalse(name, OrderControlGuard.pathCrossesForbidden(root, path!!.start, path.end))
        }
    }

    @Test
    fun noHeaderMeansNoPlan() {
        assertNull(plan("collapsed_strip") { it.replace("id/symbol_selector", "id/renamed") })
        assertNull(plan("chart_no_ticket"))
        assertNull(plan("trade_tab"))
    }

    @Test
    fun aTooShortRunBeforeTheButtonsIsRefusedInsteadOfCrossingThem() {
        // pretend the order buttons sit right under the header: there is no room for a real swipe
        val cramped = plan("expanded_market_sl_set") {
            it.replace("[44,2183][534,2263]", "[44,1100][534,1200]").replace("[546,2183][1036,2263]", "[546,1100][1036,1200]")
        }
        assertNull(cramped)
    }
}
