package com.ferik.mt5risk.core

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AmountParserTest {

    private fun assertAmount(expectedValue: String, expectedCurrency: String?, text: String) {
        val amount = AmountParser.parse(text)
        assertNotNull("expected '$text' to parse", amount)
        assertEquals(
            "value of '$text'",
            0,
            BigDecimal(expectedValue).compareTo(amount!!.value),
        )
        assertEquals("currency of '$text'", expectedCurrency, amount.currency)
    }

    @Test
    fun parsesSpaceThousandsSeparatorWithCurrency() {
        assertAmount("5000.00", "USD", "5 000.00 USD")
    }

    @Test
    fun parsesNonBreakingSpaceVariants() {
        assertAmount("5000.00", "USD", "5 000.00 USD")
        assertAmount("5000.00", "USD", "5 000.00 USD")
        assertAmount("5000.00", "USD", "5 000.00 USD")
    }

    @Test
    fun parsesMultipleThousandsGroups() {
        assertAmount("1234567.89", "USD", "1 234 567.89 USD")
    }

    @Test
    fun parsesNegativeLossOnCentAccount() {
        assertAmount("-54.51", "USC", "-54.51 USC")
    }

    @Test
    fun parsesUnicodeMinus() {
        assertAmount("-1.19", "USD", "−1.19 USD")
    }

    @Test
    fun parsesExplicitPositiveSign() {
        assertAmount("1.30", "USD", "+1.30 USD")
    }

    @Test
    fun parsesBareNumbersWithoutCurrency() {
        assertAmount("5000.00", null, "5 000.00")
        assertAmount("0.33", null, "0.33")
        assertAmount("210.132", null, "210.132")
    }

    @Test
    fun parsesRegardlessOfSurroundingWhitespace() {
        assertAmount("2.67", "USD", "  2.67 USD  ")
    }

    @Test
    fun normalisesCurrencyToUpperCase() {
        assertAmount("2.67", "USD", "2.67 usd")
    }

    @Test
    fun parsesLeadingDecimalPoint() {
        assertAmount("0.5", null, ".5")
    }

    @Test
    fun rejectsNullAndBlank() {
        assertNull(AmountParser.parse(null))
        assertNull(AmountParser.parse(""))
        assertNull(AmountParser.parse("   "))
    }

    @Test
    fun rejectsGarbage() {
        assertNull(AmountParser.parse("abc"))
        assertNull(AmountParser.parse("-"))
        assertNull(AmountParser.parse("USD"))
        assertNull(AmountParser.parse("5 000.00 USD extra"))
        assertNull(AmountParser.parse("--5"))
    }

    @Test
    fun rejectsMalformedThousandsGroups() {
        assertNull(AmountParser.parse("5 00.00 USD"))
        assertNull(AmountParser.parse("1 2"))
    }

    @Test
    fun rejectsCommaSeparators() {
        assertNull(AmountParser.parse("1,234.50"))
        assertNull(AmountParser.parse("1,5"))
    }
}
