package com.ferik.mt5risk.core

import java.math.BigDecimal
import java.math.RoundingMode

/** Which volume limit, if any, changed the computed lot. */
enum class VolumeClamp { RAISED_TO_MIN, CAPPED_AT_MAX }

data class SizingRequest(
    val balance: BigDecimal,
    /** Risk in percent of balance, for example 1 for 1 %. */
    val riskPercent: BigDecimal,
    /** Volume currently in the ticket, whose loss MetaTrader 5 reports. */
    val currentVolume: BigDecimal,
    /** Absolute stop-loss loss MetaTrader 5 reports for [currentVolume], in account currency. */
    val slLoss: BigDecimal,
    val rule: VolumeRule,
)

data class SizingResult(
    val riskBudget: BigDecimal,
    /** Exact scaled volume before rounding or clamping. */
    val unrounded: BigDecimal,
    val volume: BigDecimal,
    val clamp: VolumeClamp?,
    /** True when even the minimum lot would lose more than the risk budget. */
    val minLotExceedsRisk: Boolean,
)

/**
 * Sizes a lot from MetaTrader 5's own stop-loss loss: loss is linear in volume at a fixed price, so
 * `new = current * budget / |loss|`. No symbol, tick value or contract data is used.
 */
object RiskEngine {

    private const val DIVISION_SCALE = 12

    fun riskBudget(balance: BigDecimal, riskPercent: BigDecimal): BigDecimal =
        balance.multiply(riskPercent).divide(BigDecimal(100), DIVISION_SCALE, RoundingMode.DOWN)

    fun size(request: SizingRequest): SizingResult {
        require(request.balance.signum() > 0) { "balance must be positive" }
        require(request.riskPercent.signum() > 0) { "riskPercent must be positive" }
        require(request.currentVolume.signum() > 0) { "currentVolume must be positive" }
        require(request.slLoss.signum() > 0) { "slLoss must be a positive magnitude" }

        val budget = riskBudget(request.balance, request.riskPercent)
        val unrounded = scaleVolume(request.currentVolume, budget, request.slLoss)
        return finish(budget, unrounded, request.rule)
    }

    /** `volume * budget / loss`, rounded toward zero so it can never overshoot the budget. */
    internal fun scaleVolume(volume: BigDecimal, budget: BigDecimal, loss: BigDecimal): BigDecimal =
        volume.multiply(budget).divide(loss, DIVISION_SCALE, RoundingMode.DOWN)

    internal fun finish(budget: BigDecimal, unrounded: BigDecimal, rule: VolumeRule): SizingResult {
        val minLotExceedsRisk = unrounded < rule.min
        val floored = rule.floorToStep(unrounded)
        return when {
            minLotExceedsRisk -> SizingResult(
                riskBudget = budget,
                unrounded = unrounded,
                volume = rule.min,
                clamp = VolumeClamp.RAISED_TO_MIN,
                minLotExceedsRisk = true,
            )
            floored > rule.max -> SizingResult(
                riskBudget = budget,
                unrounded = unrounded,
                volume = rule.floorToStep(rule.max),
                clamp = VolumeClamp.CAPPED_AT_MAX,
                minLotExceedsRisk = false,
            )
            else -> SizingResult(
                riskBudget = budget,
                unrounded = unrounded,
                volume = floored,
                clamp = null,
                minLotExceedsRisk = false,
            )
        }
    }
}
