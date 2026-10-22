package com.ferik.mt5risk.core

import java.math.BigDecimal

/**
 * Immutable set of per-symbol [VolumeRule]s, keyed by the exact symbol name MetaTrader 5 shows,
 * so `GBPJPY` and `GBPJPY.pc` are separate. A symbol with no rule is unknown and must be asked
 * about before any volume is written.
 */
class SymbolVolumeRules private constructor(private val rules: Map<String, VolumeRule>) {

    constructor() : this(emptyMap())

    fun ruleFor(symbol: String): VolumeRule? = rules[symbol]

    fun isKnown(symbol: String): Boolean = symbol in rules

    /** All saved rules, ordered by symbol name, for the settings list. */
    fun all(): List<Pair<String, VolumeRule>> = rules.entries.sortedBy { it.key }.map { it.key to it.value }

    /** Save or replace a rule. */
    fun with(symbol: String, rule: VolumeRule): SymbolVolumeRules {
        require(symbol.isNotBlank()) { "symbol must not be blank" }
        return SymbolVolumeRules(rules + (symbol to rule))
    }

    fun without(symbol: String): SymbolVolumeRules = SymbolVolumeRules(rules - symbol)

    /** Save the rule implied by the user's answer to "minimum lot for this symbol?". */
    fun withMinLot(symbol: String, min: BigDecimal, max: BigDecimal = VolumeRule.DEFAULT_MAX): SymbolVolumeRules =
        with(symbol, VolumeRule.forMinLot(min, max))

    companion object {
        /** The presets offered in the first-time question. */
        val MIN_LOT_CHOICES: List<BigDecimal> = listOf(BigDecimal("0.01"), BigDecimal("0.1"), BigDecimal("1"))

        /** Parses the user's custom minimum-lot answer; null unless it is a positive number. */
        fun parseMinLot(text: String?): BigDecimal? {
            val value = text?.trim()?.toBigDecimalOrNull() ?: return null
            return value.takeIf { it.signum() > 0 }
        }

        fun of(vararg entries: Pair<String, VolumeRule>): SymbolVolumeRules =
            SymbolVolumeRules(entries.toMap())
    }
}
