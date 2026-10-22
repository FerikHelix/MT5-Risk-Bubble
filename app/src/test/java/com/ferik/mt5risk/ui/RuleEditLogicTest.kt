package com.ferik.mt5risk.ui

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleEditLogicTest {

    private fun d(text: String) = BigDecimal(text)

    @Test
    fun aMaximumRaisedOrLoweredAboveTheMinimumIsAccepted() {
        val edit = RuleEditLogic.parse("0.01", "50")!!
        assertEquals(0, d("0.01").compareTo(edit.min))
        assertEquals(0, d("50").compareTo(edit.max))
    }

    @Test
    fun aMaximumEqualToTheMinimumIsAccepted() {
        assertEquals(0, d("1").compareTo(RuleEditLogic.parse("1", "1")!!.max))
    }

    @Test
    fun aMaximumBelowTheMinimumIsRejected() {
        assertNull(RuleEditLogic.parse("0.1", "0.05"))
    }

    @Test
    fun zeroNegativeAndNonNumbersAreRejected() {
        assertNull(RuleEditLogic.parse("0", "100"))
        assertNull(RuleEditLogic.parse("-0.1", "100"))
        assertNull(RuleEditLogic.parse("0.1", "0"))
        assertNull(RuleEditLogic.parse("abc", "100"))
        assertNull(RuleEditLogic.parse("0.1", ""))
        assertNull(RuleEditLogic.parse(null, null))
    }

    @Test
    fun theListLineDropsTrailingZeros() {
        assertEquals(
            "min 0.01, step 0.01, max 100",
            RuleEditLogic.summary(d("0.01"), d("0.01"), d("100.00")),
        )
        assertEquals("min 0.1, step 0.1, max 50", RuleEditLogic.summary(d("0.10"), d("0.10"), d("50")))
    }
}
