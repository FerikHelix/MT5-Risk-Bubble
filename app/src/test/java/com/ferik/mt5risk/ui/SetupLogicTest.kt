package com.ferik.mt5risk.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupLogicTest {

    private fun statuses(
        overlay: SetupStatus,
        accessibility: SetupStatus,
        others: SetupStatus = SetupStatus.CHECK_MANUALLY,
    ): Map<SetupItem, SetupStatus> =
        SetupItem.entries.associateWith { others } +
            (SetupItem.OVERLAY to overlay) + (SetupItem.ACCESSIBILITY to accessibility)

    @Test
    fun theBubbleCanStartOnlyWhenOverlayAndAccessibilityAreBothReady() {
        assertTrue(SetupLogic.canStart(statuses(SetupStatus.READY, SetupStatus.READY)))
        assertFalse(SetupLogic.canStart(statuses(SetupStatus.READY, SetupStatus.MISSING)))
        assertFalse(SetupLogic.canStart(statuses(SetupStatus.MISSING, SetupStatus.READY)))
        assertFalse(SetupLogic.canStart(statuses(SetupStatus.MISSING, SetupStatus.MISSING)))
    }

    @Test
    fun theOptionalStepsNeverBlockStarting() {
        val missingOthers = statuses(SetupStatus.READY, SetupStatus.READY, others = SetupStatus.MISSING)
        assertTrue(SetupLogic.canStart(missingOthers))
    }

    @Test
    fun anEmptyStatusMapCannotStart() {
        assertFalse(SetupLogic.canStart(emptyMap()))
    }

    @Test
    fun missingRequiredListsOnlyWhatBlocksStarting() {
        val missing = SetupLogic.missingRequired(statuses(SetupStatus.READY, SetupStatus.MISSING))
        assertEquals(listOf(SetupItem.ACCESSIBILITY), missing)
        assertTrue(SetupLogic.missingRequired(statuses(SetupStatus.READY, SetupStatus.READY)).isEmpty())
    }

    @Test
    fun onlyOverlayAndAccessibilityAreRequired() {
        assertEquals(
            listOf(SetupItem.OVERLAY, SetupItem.ACCESSIBILITY),
            SetupItem.entries.filter { it.required },
        )
    }

    @Test
    fun theStartButtonSaysWhatWillHappen() {
        val ready = statuses(SetupStatus.READY, SetupStatus.READY)
        val blocked = statuses(SetupStatus.READY, SetupStatus.MISSING)
        assertEquals("Start the bubble", SetupLogic.startButtonLabel(ready, running = false))
        assertEquals("Stop the bubble", SetupLogic.startButtonLabel(ready, running = true))
        assertEquals("Finish the required steps first", SetupLogic.startButtonLabel(blocked, running = false))
    }

    @Test
    fun theDisclosureStatesWhatTheAppReadsAndNeverDoes() {
        val text = SetupLogic.DISCLOSURE
        assertTrue(text.contains("only the MetaTrader 5 screen"))
        assertTrue(text.contains("never presses Buy, Sell or Place"))
        assertTrue(text.contains("Volume field"))
    }

    @Test
    fun theRestrictedSettingsHintIsOnTheAccessibilityRow() {
        assertTrue(SetupItem.ACCESSIBILITY.why.contains("Allow restricted settings"))
    }
}
