package com.ferik.mt5risk.core

import java.math.BigDecimal

/** A reason the pass must stop without writing any volume. */
sealed interface Refusal {
    /** Short text for the user. */
    val message: String

    /** Neither the collapsed strip nor the expanded ticket is on screen. */
    data object TicketNotOpen : Refusal {
        override val message = "Open the order ticket first"
    }

    data class InvalidRiskPercent(val value: BigDecimal?) : Refusal {
        override val message = "Risk must be above 0 % and at most 100 %"
    }

    data class InvalidFixedRisk(val amount: BigDecimal?) : Refusal {
        override val message = "Fixed risk must be above 0"
    }

    data object NoBalance : Refusal {
        override val message = "No balance yet - open the Trade tab so it can be read"
    }

    /** The account is at or below the prop-firm drawdown floor, so there is nothing left to risk. */
    data object DrawdownExhausted : Refusal {
        override val message = "The drawdown floor has been reached - no room left to size"
    }

    data class StaleBalance(val balance: BigDecimal, val freeMargin: BigDecimal) : Refusal {
        override val message =
            "Saved balance ${balance.toPlainString()} is below free margin " +
                "${freeMargin.toPlainString()} - open the Trade tab to refresh it"
    }

    data class NoVolumeRule(val symbol: String) : Refusal {
        override val message = "Set the minimum lot for $symbol first"
    }

    data object NoStopLoss : Refusal {
        override val message = "Set a stop-loss first"
    }

    data object WrongSideStopLoss : Refusal {
        override val message = "Stop-loss is on the wrong side of the entry"
    }

    /** The ticket's volume is not in Lots, so the Volume field means something else. */
    data class VolumeUnitNotLots(val unit: String?) : Refusal {
        override val message = "Switch the ticket volume to Lots"
    }

    /** A required ticket field was missing or could not be parsed. */
    data class Unreadable(val field: String) : Refusal {
        override val message = "Couldn't read the order ticket ($field)"
    }
}

/**
 * Checks that decide whether a pass may write a volume at all. Nothing here touches the ticket.
 * The risk, symbol and rule are known from the collapsed strip, so they are checked first and the
 * sheet is only expanded when they pass.
 */
object Preconditions {

    private val HUNDRED = BigDecimal(100)

    /** Checks possible before the sheet is expanded. */
    fun beforeExpand(risk: RiskSetting?, symbol: String?, rule: VolumeRule?): Refusal? {
        when (risk) {
            null -> return Refusal.InvalidRiskPercent(null)
            is RiskSetting.Percent ->
                if (risk.value.signum() <= 0 || risk.value > HUNDRED) return Refusal.InvalidRiskPercent(risk.value)
            is RiskSetting.Fixed ->
                if (risk.amount.signum() <= 0) return Refusal.InvalidFixedRisk(risk.amount)
        }
        if (symbol.isNullOrBlank()) return Refusal.Unreadable("symbol_name")
        if (rule == null) return Refusal.NoVolumeRule(symbol)
        return null
    }

    /** The Volume field only means lots when the unit selector says Lots. */
    fun volumeUnit(unit: String?): Refusal? {
        if (unit == null) return Refusal.Unreadable("volume_mode_text")
        return if (unit.trim().equals("Lots", ignoreCase = true)) null else Refusal.VolumeUnitNotLots(unit)
    }

    /**
     * Checks that need the expanded ticket.
     *
     * @param balance cached or manual balance, null when none exists
     * @param freeMargin free margin from the ticket, null when unreadable
     * @param currentVolume current Volume field, null when unreadable
     * @param slPrice stop-loss field, null when unreadable, zero when no stop-loss is set
     * @param slLoss the loss label as read (negative for a real loss), null when the label is absent
     */
    fun afterRead(
        balance: BigDecimal?,
        freeMargin: BigDecimal?,
        currentVolume: BigDecimal?,
        slPrice: BigDecimal?,
        slLoss: BigDecimal?,
    ): Refusal? {
        if (balance == null || balance.signum() <= 0) return Refusal.NoBalance
        if (freeMargin == null) return Refusal.Unreadable("free_margin_value")
        if (balance < freeMargin) return Refusal.StaleBalance(balance, freeMargin)
        if (currentVolume == null || currentVolume.signum() <= 0) {
            return Refusal.Unreadable("volume_lots_input")
        }
        if (slPrice == null) return Refusal.Unreadable("sl_input")
        if (slPrice.signum() == 0 || slLoss == null) return Refusal.NoStopLoss
        if (slLoss.signum() >= 0) return Refusal.WrongSideStopLoss
        return null
    }
}
