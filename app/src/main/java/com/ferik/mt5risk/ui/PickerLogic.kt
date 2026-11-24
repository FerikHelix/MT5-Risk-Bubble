package com.ferik.mt5risk.ui

import com.ferik.mt5risk.balance.BalanceDisplay
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
import java.math.RoundingMode

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

    /** The one sentence under the risk field, plus the drawdown detail line when that base is used. */
    data class Explanation(val line: String, val detail: String?)

    /** What the risk field currently means, in money: `1 % of 4 776.77 = 47.77 USD at risk`. */
    fun explanation(
        risk: RiskSetting?,
        base: SizingBase?,
        currency: String?,
        propFirm: PropFirm?,
    ): Explanation? {
        if (risk == null || base == null) return null
        val line = when (risk) {
            is RiskSetting.Percent -> {
                val budget = RiskEngine.riskBudget(base.value, risk.value)
                "${plain(risk.value)} % of ${money(base.value, currency)} = " +
                    "${money(budget, currency)} at risk"
            }
            is RiskSetting.Fixed -> {
                val percent = if (base.value.signum() > 0) {
                    risk.amount.multiply(HUNDRED).divide(base.value, 2, RoundingMode.HALF_EVEN)
                } else {
                    null
                }
                if (percent != null) {
                    "fixed ${money(risk.amount, currency)} at risk - ${percent.toPlainString()} % " +
                        "of ${baseName(base.kind)}"
                } else {
                    "fixed ${money(risk.amount, currency)} at risk"
                }
            }
        }
        val detail = if (base.kind == RiskBasisKind.DRAWDOWN && propFirm != null) {
            drawdownDetail(propFirm, base.value)
        } else {
            null
        }
        return Explanation(line, detail)
    }

    /** `floor 1 000.00 - 4 776.77 of 5 000.00 left`, without repeating the account currency. */
    fun drawdownDetail(propFirm: PropFirm, buffer: BigDecimal?): String =
        "floor ${BalanceDisplay.formatAmount(propFirm.floor)} - " +
            "${buffer?.let(BalanceDisplay::formatAmount) ?: "-"} " +
            "of ${BalanceDisplay.formatAmount(propFirm.maxDrawdown)} left"

    /** The risk page's summary row: which base is in use, what is left, and whether auto-apply is on. */
    fun summary(base: SizingBase?, currency: String?, autoApply: Boolean): String {
        val baseText = when {
            base == null -> "No balance"
            base.kind == RiskBasisKind.BALANCE -> "Balance ${money(base.value, currency)}"
            else -> "Drawdown ${money(base.value, currency)} left"
        }
        return "$baseText - auto-apply ${if (autoApply) "on" else "off"}"
    }

    private fun baseName(kind: RiskBasisKind): String = when (kind) {
        RiskBasisKind.BALANCE -> "balance"
        RiskBasisKind.DRAWDOWN -> "drawdown"
    }

    private fun plain(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

    private fun money(value: BigDecimal, currency: String?): String {
        val amount = BalanceDisplay.formatAmount(value)
        return if (currency != null) "$amount $currency" else amount
    }

    /** Label for the bubble, for example `1%` or `0.5%`. */
    fun label(percent: BigDecimal): String = percent.stripTrailingZeros().toPlainString() + " %"
}
