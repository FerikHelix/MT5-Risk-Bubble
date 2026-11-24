package com.ferik.mt5risk.core

import java.math.BigDecimal

/** Which entry the picker shows: a percentage of balance or a fixed amount in account currency. */
enum class RiskMode { PERCENT, FIXED }

/** How the user sized a pass: a percentage of balance, or a fixed amount in account currency. */
sealed interface RiskSetting {

    data class Percent(val value: BigDecimal) : RiskSetting

    data class Fixed(val amount: BigDecimal) : RiskSetting

    /**
     * The budget (absolute money at risk) this setting asks for at [balance]. Percentage budgets are
     * capped by [RiskEngine.riskBudget]; fixed budgets are used as they are.
     */
    fun budget(balance: BigDecimal): BigDecimal = when (this) {
        is Percent -> RiskEngine.riskBudget(balance, value)
        is Fixed -> amount
    }
}
