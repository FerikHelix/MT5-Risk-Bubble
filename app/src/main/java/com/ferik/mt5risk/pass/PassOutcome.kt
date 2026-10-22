package com.ferik.mt5risk.pass

import com.ferik.mt5risk.core.Refusal
import com.ferik.mt5risk.core.VolumeClamp
import com.ferik.mt5risk.core.VolumeRule
import java.math.BigDecimal

/** How one lot-sizing pass ended. */
sealed interface PassOutcome {

    /** A volume was written and MetaTrader 5's own loss for it is within the budget. */
    data class Success(
        val symbol: String,
        val volume: BigDecimal,
        /** [volume] written with the symbol's number of decimals, exactly as put in the ticket. */
        val volumeText: String,
        val riskPercent: BigDecimal,
        val balance: BigDecimal,
        /** MT5's absolute stop-loss loss for [volume], as last read. */
        val observedLoss: BigDecimal,
        val currency: String?,
        val clamp: VolumeClamp?,
        /** True when even the minimum lot loses more than the risk budget. */
        val minLotExceedsRisk: Boolean,
        val writes: Int,
    ) : PassOutcome {
        val lossPercentOfBalance: BigDecimal
            get() = observedLoss.multiply(BigDecimal(100)).divide(balance, 2, java.math.RoundingMode.HALF_EVEN)
    }

    /** Nothing was written because a precondition failed. */
    data class Refused(val refusal: Refusal) : PassOutcome

    /** MT5 shows a different volume than the one written, so the stored rule is probably wrong. */
    data class RuleMismatch(
        val symbol: String,
        val rule: VolumeRule,
        val wrote: BigDecimal,
        val shown: BigDecimal,
    ) : PassOutcome

    /** Still over budget after the allowed writes. */
    data class Exhausted(
        val volume: BigDecimal,
        val observedLoss: BigDecimal,
        val budget: BigDecimal,
        val currency: String?,
    ) : PassOutcome

    /** Something went wrong that is not one of the cases above (for example the sheet would not open). */
    data class Failed(val reason: String) : PassOutcome
}

/** The outcome plus whether the sheet could be put back as the pass found it. */
data class PassResult(val outcome: PassOutcome, val collapseFailed: Boolean = false)
