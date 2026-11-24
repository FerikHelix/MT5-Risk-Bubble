package com.ferik.mt5risk.ui

import java.math.BigDecimal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlChangeDetectorTest {

    private fun d(text: String) = BigDecimal(text)

    private val debounce = 700L

    private fun detector() = SlChangeDetector(debounceMillis = debounce)

    @Test
    fun theFirstReadOnlySeedsTheBaseline() {
        val detector = detector()
        assertFalse(detector.observe("GBPJPY", d("209.932"), nowMillis = 0))
    }

    @Test
    fun theSameValueNeverFires() {
        val detector = detector()
        detector.observe("GBPJPY", d("209.932"), 0)
        assertFalse(detector.observe("GBPJPY", d("209.932"), 1_000))
        assertFalse(detector.observe("GBPJPY", d("209.932"), 2_000))
    }

    @Test
    fun aSettledMoveFiresExactlyOnce() {
        val detector = detector()
        detector.observe("GBPJPY", d("209.932"), 0)

        assertFalse("the move itself must not fire yet", detector.observe("GBPJPY", d("210.100"), 100))
        assertFalse("still inside the debounce", detector.observe("GBPJPY", d("210.100"), 500))
        assertTrue("settled", detector.observe("GBPJPY", d("210.100"), 800))
        assertFalse("only one pass per move", detector.observe("GBPJPY", d("210.100"), 1_500))
    }

    @Test
    fun rapidChangesCollapseIntoOnePassAfterTheLast() {
        val detector = detector()
        detector.observe("GBPJPY", d("209.932"), 0)

        detector.observe("GBPJPY", d("210.000"), 100)
        detector.observe("GBPJPY", d("210.050"), 200)
        detector.observe("GBPJPY", d("210.100"), 300)

        assertFalse("only 400 ms since the last change", detector.observe("GBPJPY", d("210.100"), 700))
        assertTrue(detector.observe("GBPJPY", d("210.100"), 1_000))
        assertFalse(detector.observe("GBPJPY", d("210.100"), 2_000))
    }

    @Test
    fun anotherMoveAfterAPassFiresAgain() {
        val detector = detector()
        detector.observe("GBPJPY", d("209.932"), 0)
        detector.observe("GBPJPY", d("210.100"), 100)
        assertTrue(detector.observe("GBPJPY", d("210.100"), 800))

        detector.observe("GBPJPY", d("209.500"), 900)
        assertTrue(detector.observe("GBPJPY", d("209.500"), 1_600))
    }

    @Test
    fun aNewSymbolReseedsInsteadOfFiring() {
        val detector = detector()
        detector.observe("GBPJPY", d("209.932"), 0)

        assertFalse(detector.observe("SP500", d("5000"), 100))
        assertFalse(detector.observe("SP500", d("5000"), 1_000))
    }

    @Test
    fun clearingTheStopLossReseedsAndDoesNotFire() {
        val detector = detector()
        detector.observe("GBPJPY", d("209.932"), 0)

        assertFalse(detector.observe("GBPJPY", BigDecimal.ZERO, 100))
        assertFalse(detector.observe("GBPJPY", BigDecimal.ZERO, 1_000))
    }

    @Test
    fun seedingAfterAPassSuppressesTheMoveItCaused() {
        val detector = detector()
        detector.observe("GBPJPY", d("209.932"), 0)
        detector.observe("GBPJPY", d("210.100"), 100)

        detector.seed("GBPJPY", d("210.100"))
        assertFalse(detector.observe("GBPJPY", d("210.100"), 1_000))
    }
}
