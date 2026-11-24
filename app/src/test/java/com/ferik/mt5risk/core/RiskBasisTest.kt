package com.ferik.mt5risk.core

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskBasisTest {

    private fun d(text: String) = BigDecimal(text)

    /** A 10 000.00 challenge with a 500.00 maximum drawdown: it fails at 9 500.00. */
    private val challenge = PropFirm(startBalance = d("10000"), maxDrawdown = d("500"))

    @Test
    fun theFloorIsTheStartingBalanceMinusTheMaximumDrawdown() {
        assertEquals(0, d("9500").compareTo(challenge.floor))
    }

    @Test
    fun theStartOfAChallengeUsesTheWholeDrawdown() {
        val base = RiskBasis.resolve(d("10000"), RiskBasisKind.DRAWDOWN, challenge)

        assertEquals(RiskBasisKind.DRAWDOWN, base.kind)
        assertEquals(0, d("500").compareTo(base.value))
        assertFalse(base.isExhausted)
    }

    @Test
    fun afterALossTheBaseIsWhatIsLeftAboveTheFloor() {
        val base = RiskBasis.resolve(d("9600"), RiskBasisKind.DRAWDOWN, challenge)
        assertEquals(0, d("100").compareTo(base.value))
    }

    @Test
    fun profitAboveTheStartingBalanceIsCappedAtTheMaximumDrawdown() {
        val base = RiskBasis.resolve(d("11000"), RiskBasisKind.DRAWDOWN, challenge)

        // 1 500.00 above the floor, but the firm gave only 500.00 of drawdown.
        assertEquals(0, d("500").compareTo(base.value))
    }

    @Test
    fun atTheFloorTheBaseIsZeroAndExhausted() {
        val base = RiskBasis.resolve(d("9500"), RiskBasisKind.DRAWDOWN, challenge)

        assertEquals(0, BigDecimal.ZERO.compareTo(base.value))
        assertTrue(base.isExhausted)
    }

    @Test
    fun belowTheFloorThereIsStillNothingToRisk() {
        val base = RiskBasis.resolve(d("9000"), RiskBasisKind.DRAWDOWN, challenge)

        assertEquals(0, BigDecimal.ZERO.compareTo(base.value))
        assertTrue(base.isExhausted)
    }

    @Test
    fun theBalanceBasisIgnoresTheDrawdownNumbers() {
        val base = RiskBasis.resolve(d("9600"), RiskBasisKind.BALANCE, challenge)

        assertEquals(0, d("9600").compareTo(base.value))
        assertEquals(RiskBasisKind.BALANCE, base.kind)
        assertFalse(base.isExhausted)
    }

    @Test
    fun theDrawdownBasisWithoutNumbersFallsBackToTheBalance() {
        val base = RiskBasis.resolve(d("9600"), RiskBasisKind.DRAWDOWN, propFirm = null)

        assertEquals(0, d("9600").compareTo(base.value))
        assertEquals(RiskBasisKind.BALANCE, base.kind)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aZeroMaximumDrawdownIsRejected() {
        PropFirm(startBalance = d("10000"), maxDrawdown = BigDecimal.ZERO)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aDrawdownNotBelowTheStartingBalanceIsRejected() {
        PropFirm(startBalance = d("10000"), maxDrawdown = d("10000"))
    }
}
