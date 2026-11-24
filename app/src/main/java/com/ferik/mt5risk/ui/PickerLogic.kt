package com.ferik.mt5risk.ui

import com.ferik.mt5risk.core.PropFirm
import com.ferik.mt5risk.core.RiskBasis
import com.ferik.mt5risk.core.RiskBasisKind
import com.ferik.mt5risk.core.RiskEngine
import com.ferik.mt5risk.core.RiskSetting
import com.ferik.mt5risk.core.SizingBase
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

    private val HUNDRED = BigDecimal(100)
    private val MAX_FIXED_RISK = BigDecimal("1000000")
    private val MAX_PROP_FIRM_AMOUNT = BigDecimal("1000000000")

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

    /** A risk percentage: a number above 0 and at most 100, otherwise null. */
    fun parseCustomRisk(text: String?): BigDecimal? {
        val value = text?.trim()?.replace(',', '.')?.toBigDecimalOrNull() ?: return null
        return value.takeIf { it.signum() > 0 && it <= HUNDRED }
    }

    /** A fixed risk amount in account currency: above 0 and at most 1 000 000, otherwise null. */
    fun parseFixedRisk(text: String?): BigDecimal? {
        val cleaned = text?.trim()?.replace(" ", "")?.replace(' ', ' ')?.replace(" ", "") ?: return null
        val value = cleaned.replace(',', '.').toBigDecimalOrNull() ?: return null
        return value.takeIf { it.signum() > 0 && it <= MAX_FIXED_RISK }
    }

    /** The setting a percentage or fixed entry stands for. */
    fun riskSetting(percent: BigDecimal?, fixed: BigDecimal?, fixedMode: Boolean): RiskSetting? =
        if (fixedMode) fixed?.let(RiskSetting::Fixed) else percent?.let(RiskSetting::Percent)

    /** A custom manual balance: a positive number, otherwise null. */
    fun parseManualBalance(text: String?): BigDecimal? {
        val cleaned = text?.trim()?.replace(" ", "")?.replace(' ', ' ')?.replace(" ", "") ?: return null
        val value = cleaned.toBigDecimalOrNull() ?: return null
        return value.takeIf { it.signum() > 0 }
    }

    /** A prop-firm starting balance: a positive, sane amount, otherwise null. */
    fun parsePropFirmStart(text: String?): BigDecimal? = parsePropFirmAmount(text)

    /** A prop-firm maximum drawdown: a positive, sane amount, otherwise null. */
    fun parsePropFirmDrawdown(text: String?): BigDecimal? = parsePropFirmAmount(text)

    private fun parsePropFirmAmount(text: String?): BigDecimal? {
        val cleaned = text?.trim()?.replace(" ", "") ?: return null
        val value = cleaned.replace(',', '.').toBigDecimalOrNull() ?: return null
        return value.takeIf { it.signum() > 0 && it <= MAX_PROP_FIRM_AMOUNT }
    }

    /** The prop-firm numbers both at once, or null while either is invalid or the pair is impossible. */
    fun propFirm(startText: String?, drawdownText: String?): PropFirm? {
        val start = parsePropFirmStart(startText) ?: return null
        val drawdown = parsePropFirmDrawdown(drawdownText) ?: return null
        if (drawdown >= start) return null
        return PropFirm(start, drawdown)
    }

    /** The amount the risk percentage would apply to, with its kind; null without a balance. */
    fun basePreview(balance: BigDecimal?, basis: RiskBasisKind, propFirm: PropFirm?): SizingBase? =
        balance?.let { RiskBasis.resolve(it, basis, propFirm) }

    /** The money a pass would risk: the fixed amount, or the percentage of the base. */
    fun previewRisk(
        percent: BigDecimal?,
        fixed: BigDecimal?,
        fixedMode: Boolean,
        base: BigDecimal?,
    ): BigDecimal? =
        if (fixedMode) {
            fixed
        } else {
            base?.let { value -> percent?.let { RiskEngine.riskBudget(value, it) } }
        }

    /** Label for the bubble, for example `1%` or `0.5%`. */
    fun label(percent: BigDecimal): String = percent.stripTrailingZeros().toPlainString() + " %"
}
