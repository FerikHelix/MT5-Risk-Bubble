package com.ferik.mt5risk.balance

import java.math.BigDecimal
import java.math.RoundingMode

/** Text the risk picker shows so the user can see which balance the percentage applies to. */
object BalanceDisplay {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** `5 000.00`: two decimals, a space as the thousands separator, as MetaTrader 5 prints it. */
    fun formatAmount(value: BigDecimal): String {
        val plain = value.setScale(2, RoundingMode.HALF_EVEN).abs().toPlainString()
        val integer = plain.substringBefore('.')
        // Non-breaking so an amount is one token: Compose may wrap text at a regular space.
        val grouped = integer.reversed().chunked(3).joinToString("\u00A0").reversed()
        val sign = if (value.signum() < 0) "-" else ""
        return "$sign$grouped.${plain.substringAfter('.')}"
    }

    fun age(seenAtMillis: Long, nowMillis: Long): String {
        val elapsed = (nowMillis - seenAtMillis).coerceAtLeast(0)
        return when {
            elapsed < MINUTE -> "just now"
            elapsed < HOUR -> "${elapsed / MINUTE} min ago"
            elapsed < DAY -> "${elapsed / HOUR} h ago"
            else -> "${elapsed / DAY} d ago"
        }
    }

    /** For example `Balance 5 000.00 USD, seen 3 min ago`, or `Balance 5 000.00 (manual)`. */
    fun format(balance: CachedBalance, nowMillis: Long): String {
        val amount = formatAmount(balance.value)
        val withCurrency = balance.currency?.let { "$amount $it" } ?: amount
        return when (balance.source) {
            BalanceSource.MANUAL -> "Balance $withCurrency (manual)"
            BalanceSource.TRADE_TAB -> "Balance $withCurrency, seen ${age(balance.seenAtMillis, nowMillis)}"
        }
    }
}
