package com.ferik.mt5risk.core

import java.math.BigDecimal

/** What to do after reading MetaTrader 5's loss for a volume that was just written. */
sealed interface VerifyDecision {
    /** MT5's own loss is within the budget; the pass is done. */
    data class Accept(val volume: BigDecimal, val observedLoss: BigDecimal) : VerifyDecision

    /** Over budget (the price drifted): write [volume] and read the loss again. */
    data class Retry(val volume: BigDecimal) : VerifyDecision

    /** Already at the minimum lot and still over budget, so nothing lower can be written. */
    data class MinLotOverBudget(val volume: BigDecimal, val observedLoss: BigDecimal) : VerifyDecision

    /** Out of write attempts and still over budget. */
    data class Exhausted(val volume: BigDecimal, val observedLoss: BigDecimal) : VerifyDecision
}

/**
 * Closed loop on MT5's recomputed loss. Each correction rescales from the freshest
 * (volume, loss) pair, because the loss uses the live price and drifts between reads.
 * Rounding down can leave the budget slightly under-used; that is accepted.
 */
object VerifyLoop {

    /** Total volume writes allowed in one pass, the initial write included. */
    const val MAX_WRITES = 3

    /**
     * @param writesDone number of volume writes performed so far in this pass (at least 1)
     * @param volume the volume that was just written
     * @param observedLoss MT5's absolute stop-loss loss re-read after that write
     * @param budget the risk budget in account currency
     */
    fun decide(
        writesDone: Int,
        volume: BigDecimal,
        observedLoss: BigDecimal,
        budget: BigDecimal,
        rule: VolumeRule,
    ): VerifyDecision {
        require(writesDone >= 1) { "decide is called after at least one write" }
        require(observedLoss.signum() > 0) { "observedLoss must be a positive magnitude" }

        if (observedLoss <= budget) return VerifyDecision.Accept(volume, observedLoss)
        if (volume <= rule.min) return VerifyDecision.MinLotOverBudget(volume, observedLoss)
        if (writesDone >= MAX_WRITES) return VerifyDecision.Exhausted(volume, observedLoss)

        val scaled = RiskEngine.scaleVolume(volume, budget, observedLoss)
        val next = rule.floorToStep(scaled).coerceAtLeast(rule.min)
        return VerifyDecision.Retry(next)
    }
}
