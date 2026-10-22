package com.ferik.mt5risk.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Volume limits of one symbol. The step equals the minimum lot (forex 0.01 moves 0.01, 0.02, ...;
 * an index with minimum 0.1 moves 0.1, 0.2, ...), and the maximum defaults to [DEFAULT_MAX].
 */
data class VolumeRule(
    val min: BigDecimal,
    val step: BigDecimal,
    val max: BigDecimal,
) {
    init {
        require(min.signum() > 0) { "min must be positive" }
        require(step.signum() > 0) { "step must be positive" }
        require(max >= min) { "max must not be below min" }
    }

    private val displayScale: Int = step.stripTrailingZeros().scale().coerceAtLeast(0)

    /** Largest multiple of [step] that is not above [volume]; never rounds up. */
    fun floorToStep(volume: BigDecimal): BigDecimal {
        val steps = volume.divideToIntegralValue(step).setScale(0, RoundingMode.DOWN)
        return steps.multiply(step).setScale(displayScale, RoundingMode.UNNECESSARY)
    }

    /** Express [volume] with the same number of decimals the step uses, for writing to the ticket. */
    fun format(volume: BigDecimal): String =
        volume.setScale(displayScale, RoundingMode.DOWN).toPlainString()

    companion object {
        val DEFAULT_MAX: BigDecimal = BigDecimal("100")

        /** Rule where the step equals the minimum lot, as the user described. */
        fun forMinLot(min: BigDecimal, max: BigDecimal = DEFAULT_MAX): VolumeRule {
            val normalised = min.stripTrailingZeros().let {
                if (it.scale() < 0) it.setScale(0) else it
            }
            return VolumeRule(min = normalised, step = normalised, max = max)
        }
    }
}
