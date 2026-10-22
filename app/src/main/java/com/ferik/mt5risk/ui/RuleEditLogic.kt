package com.ferik.mt5risk.ui

import com.ferik.mt5risk.core.SymbolVolumeRules
import java.math.BigDecimal

/** Validation for editing a saved symbol rule in settings. */
object RuleEditLogic {

    data class Edit(val min: BigDecimal, val max: BigDecimal)

    /**
     * The minimum must be above 0 and the maximum must not be below it. Returns null for anything
     * else so the dialog can keep its Save button disabled instead of storing a rule that cannot work.
     */
    fun parse(minText: String?, maxText: String?): Edit? {
        val min = SymbolVolumeRules.parseMinLot(minText) ?: return null
        val max = SymbolVolumeRules.parseMinLot(maxText) ?: return null
        return if (max >= min) Edit(min, max) else null
    }

    /** One line for the list, for example `min 0.01, step 0.01, max 100`. */
    fun summary(min: BigDecimal, step: BigDecimal, max: BigDecimal): String =
        "min ${min.stripTrailingZeros().toPlainString()}, " +
            "step ${step.stripTrailingZeros().toPlainString()}, " +
            "max ${max.stripTrailingZeros().toPlainString()}"
}
