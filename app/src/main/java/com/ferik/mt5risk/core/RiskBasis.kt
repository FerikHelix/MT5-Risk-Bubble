package com.ferik.mt5risk.core

import java.math.BigDecimal

/** What a risk percentage is measured against. */
enum class RiskBasisKind { BALANCE, DRAWDOWN }

/**
 * The prop-firm numbers the user entered once: the balance the challenge started at, and the maximum
 * drawdown it allows. [floor] is the level the account fails at, so the buffer above it is the money
 * that is still at stake.
 */
data class PropFirm(val startBalance: BigDecimal, val maxDrawdown: BigDecimal) {

    init {
        require(startBalance.signum() > 0) { "startBalance must be positive" }
        require(maxDrawdown.signum() > 0) { "maxDrawdown must be positive" }
        require(maxDrawdown < startBalance) { "maxDrawdown must be below the starting balance" }
    }

    /** Starting balance minus maximum drawdown: the equity level the firm fails the account at. */
    val floor: BigDecimal get() = startBalance.subtract(maxDrawdown)
}

/** The amount a risk percentage applies to, and which kind it is. */
data class SizingBase(val value: BigDecimal, val kind: RiskBasisKind) {

    /** True when the drawdown floor has been reached, so no positive budget can be derived. */
    val isExhausted: Boolean get() = kind == RiskBasisKind.DRAWDOWN && value.signum() <= 0
}

/** The basis the user picked, together with the prop-firm numbers when the drawdown basis is used. */
data class BasisSelection(
    val kind: RiskBasisKind = RiskBasisKind.BALANCE,
    val propFirm: PropFirm? = null,
)

/**
 * Resolves the base a risk percentage applies to. Pure: neither the balance cache nor the ticket is
 * touched. MT5's reported loss is still what turns the budget into lots; only the budget's base
 * changes here.
 */
object RiskBasis {

    /**
     * The balance when the balance basis is chosen, otherwise the balance above the drawdown floor,
     * capped at the maximum drawdown so that profit above the starting balance never raises the money
     * at risk beyond the chosen percentage of the drawdown the firm gave. When the drawdown basis is
     * selected but no prop-firm numbers have been saved, the balance is used.
     */
    fun resolve(balance: BigDecimal, kind: RiskBasisKind, propFirm: PropFirm?): SizingBase =
        if (kind == RiskBasisKind.DRAWDOWN && propFirm != null) {
            val buffer = balance.subtract(propFirm.floor).max(BigDecimal.ZERO)
            SizingBase(buffer.min(propFirm.maxDrawdown), RiskBasisKind.DRAWDOWN)
        } else {
            SizingBase(balance, RiskBasisKind.BALANCE)
        }
}
