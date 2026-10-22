package com.ferik.mt5risk.core

import java.math.BigDecimal

/** A money amount as MetaTrader 5 prints it, with the currency suffix when one was shown. */
data class Amount(val value: BigDecimal, val currency: String?)

/**
 * Parses amounts such as `5 000.00 USD`, `-54.51 USC` or a bare `5 000.00`.
 *
 * Accepts a regular, non-breaking, narrow non-breaking or thin space as the thousands separator
 * (always in groups of three digits), a point as the decimal separator and any alphabetic
 * currency suffix. Anything else returns null so callers can refuse instead of guessing.
 */
object AmountParser {

    private const val SPACES = "    "

    private val PATTERN = Regex(
        "^([+\\-\\u2212])?[$SPACES]*" +
            "((?:[0-9]+(?:[$SPACES][0-9]{3})*)(?:\\.[0-9]+)?|\\.[0-9]+)" +
            "[$SPACES]*([A-Za-z]{2,6})?$",
    )

    fun parse(text: String?): Amount? {
        if (text == null) return null
        val trimmed = text.trim { it.isWhitespace() || it in SPACES }
        val match = PATTERN.matchEntire(trimmed) ?: return null

        val sign = match.groupValues[1]
        val digits = match.groupValues[2].filterNot { it in SPACES }
        val currency = match.groupValues[3].ifEmpty { null }?.uppercase()

        val magnitude = digits.toBigDecimalOrNull() ?: return null
        val value = if (sign == "-" || sign == "−") magnitude.negate() else magnitude
        return Amount(value, currency)
    }
}
