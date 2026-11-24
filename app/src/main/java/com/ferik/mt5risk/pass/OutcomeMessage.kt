package com.ferik.mt5risk.pass

import com.ferik.mt5risk.balance.BalanceDisplay
import com.ferik.mt5risk.core.RiskBasisKind
import com.ferik.mt5risk.core.RiskSetting
import com.ferik.mt5risk.core.VolumeClamp
import com.ferik.mt5risk.core.VolumeRule
import java.math.BigDecimal

enum class MessageSeverity { SUCCESS, WARNING, ERROR }

/** What the user is shown after a pass, readable over the chart without opening the ticket. */
data class OutcomeMessage(
    val severity: MessageSeverity,
    val text: String,
    /** Set when the message offers to re-ask the symbol's minimum lot. */
    val offerResetRuleFor: String? = null,
) {
    companion object {

        fun of(result: PassResult): OutcomeMessage {
            val base = of(result.outcome)
            if (!result.collapseFailed) return base
            return base.copy(
                severity = if (base.severity == MessageSeverity.SUCCESS) MessageSeverity.WARNING else base.severity,
                text = base.text + " The ticket could not be closed - swipe it down.",
            )
        }

        fun of(outcome: PassOutcome): OutcomeMessage = when (outcome) {
            is PassOutcome.Success -> success(outcome)
            is PassOutcome.Refused -> OutcomeMessage(MessageSeverity.ERROR, outcome.refusal.message)
            is PassOutcome.RuleMismatch -> ruleMismatch(outcome)
            is PassOutcome.Exhausted -> OutcomeMessage(
                MessageSeverity.ERROR,
                "Still over budget after ${com.ferik.mt5risk.core.VerifyLoop.MAX_WRITES} tries: " +
                    "${money(outcome.observedLoss, outcome.currency)} at ${outcome.volume.toPlainString()} lots, " +
                    "budget ${money(outcome.budget, outcome.currency)}.",
            )
            is PassOutcome.Failed -> OutcomeMessage(MessageSeverity.ERROR, outcome.reason)
        }

        private fun success(outcome: PassOutcome.Success): OutcomeMessage {
            val percent = outcome.lossPercentOfBase.toPlainString()
            val baseLabel = when (outcome.baseKind) {
                RiskBasisKind.BALANCE -> "balance"
                RiskBasisKind.DRAWDOWN -> "drawdown left"
            }
            val riskSuffix = when (val risk = outcome.riskSetting) {
                is RiskSetting.Percent -> ""
                is RiskSetting.Fixed -> " - risk ${money(risk.amount, outcome.currency)}"
            }
            val text = StringBuilder(
                "${outcome.volumeText} lots - loss ${money(outcome.observedLoss, outcome.currency)}" +
                    "$riskSuffix ($percent % of $baseLabel)",
            )
            var severity = MessageSeverity.SUCCESS
            if (outcome.minLotExceedsRisk) {
                severity = MessageSeverity.WARNING
                val target = when (val risk = outcome.riskSetting) {
                    is RiskSetting.Percent -> "${plain(risk.value)} %"
                    is RiskSetting.Fixed -> money(risk.amount, outcome.currency)
                }
                text.append(". Minimum lot already risks more than your $target")
            }
            if (outcome.clamp == VolumeClamp.CAPPED_AT_MAX) {
                severity = MessageSeverity.WARNING
                text.append(". Capped at the maximum volume")
            }
            return OutcomeMessage(severity, text.toString())
        }

        private fun ruleMismatch(outcome: PassOutcome.RuleMismatch): OutcomeMessage = OutcomeMessage(
            severity = MessageSeverity.ERROR,
            text = "MT5 shows ${outcome.shown.toPlainString()} instead of ${outcome.wrote.toPlainString()}. " +
                "Saved minimum lot for ${outcome.symbol} is ${ruleMin(outcome.rule)} - re-set it.",
            offerResetRuleFor = outcome.symbol,
        )

        private fun ruleMin(rule: VolumeRule) = rule.min.toPlainString()

        private fun money(value: BigDecimal, currency: String?): String {
            val amount = BalanceDisplay.formatAmount(value)
            return if (currency != null) "$amount $currency" else amount
        }

        private fun plain(value: BigDecimal) = value.stripTrailingZeros().toPlainString()
    }
}
