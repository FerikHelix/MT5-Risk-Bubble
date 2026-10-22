package com.ferik.mt5risk.ui

import com.ferik.mt5risk.core.SymbolVolumeRules
import com.ferik.mt5risk.mt5.TicketReading
import com.ferik.mt5risk.mt5.snapshotOrNull
import java.math.BigDecimal

/** What the risk picker shows when the bubble is tapped. */
sealed interface PickerStep {
    /** The symbol has no volume rule yet: ask once for its minimum lot, then continue. */
    data class AskMinLot(val symbol: String) : PickerStep

    /** Ready to choose a risk percentage. */
    data class ChooseRisk(val symbol: String) : PickerStep

    /** Nothing to size against; show [message] instead of the picker. */
    data class Unavailable(val message: String) : PickerStep
}

object PickerLogic {

    /** The percentages offered as one-tap buttons. */
    val RISK_PRESETS: List<BigDecimal> = listOf(BigDecimal("0.5"), BigDecimal("1"), BigDecimal("2"))

    private val HUNDRED = BigDecimal(100)

    /**
     * Decide the first thing the picker shows. [reading] is null when the accessibility service is
     * not connected. The symbol comes from the visible strip, so no sheet has to open to learn it.
     */
    fun stepFor(reading: TicketReading?, rules: SymbolVolumeRules): PickerStep {
        val symbol = when (reading) {
            null -> return PickerStep.Unavailable("Turn on the accessibility service for MT5 Risk Bubble first")
            TicketReading.NotTicket -> return PickerStep.Unavailable("Open the order ticket first")
            is TicketReading.Unreadable -> return PickerStep.Unavailable("Couldn't read the order ticket (${reading.field})")
            is TicketReading.Collapsed -> reading.symbol
            is TicketReading.Expanded -> reading.snapshotOrNull()?.symbol
        }
        if (symbol.isNullOrBlank()) return PickerStep.Unavailable("Couldn't read the order ticket (symbol_name)")
        return if (rules.isKnown(symbol)) PickerStep.ChooseRisk(symbol) else PickerStep.AskMinLot(symbol)
    }

    /** A custom risk percentage: a number above 0 and at most 100, otherwise null. */
    fun parseCustomRisk(text: String?): BigDecimal? {
        val value = text?.trim()?.replace(',', '.')?.toBigDecimalOrNull() ?: return null
        return value.takeIf { it.signum() > 0 && it <= HUNDRED }
    }

    /** True when [preset] is the percentage the user used last, so the picker can highlight it. */
    fun isLastUsed(preset: BigDecimal, lastUsed: BigDecimal?): Boolean =
        lastUsed != null && preset.compareTo(lastUsed) == 0

    /** A custom manual balance: a positive number, otherwise null. */
    fun parseManualBalance(text: String?): BigDecimal? {
        val cleaned = text?.trim()?.replace(" ", "")?.replace(' ', ' ')?.replace(" ", "") ?: return null
        val value = cleaned.toBigDecimalOrNull() ?: return null
        return value.takeIf { it.signum() > 0 }
    }

    /** Label for a risk percentage button, for example `1 %` or `0.5 %`. */
    fun label(percent: BigDecimal): String = percent.stripTrailingZeros().toPlainString() + " %"
}
