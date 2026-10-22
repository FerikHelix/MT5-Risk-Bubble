package com.ferik.mt5risk.rules

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ferik.mt5risk.core.SymbolVolumeRules
import com.ferik.mt5risk.core.VolumeRule
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persists the per-symbol volume rules the user answered once. Each rule is stored under the exact
 * symbol name as `min|max`; the step is always the minimum lot, so it is not stored separately.
 */
class SymbolRuleStore(private val dataStore: DataStore<Preferences>) {

    val rules: Flow<SymbolVolumeRules> = dataStore.data.map(::toRules)

    suspend fun current(): SymbolVolumeRules = rules.first()

    /** Save the answer to "minimum lot for this symbol?", replacing any earlier rule. */
    suspend fun saveMinLot(symbol: String, min: BigDecimal, max: BigDecimal = VolumeRule.DEFAULT_MAX) {
        require(symbol.isNotBlank()) { "symbol must not be blank" }
        val rule = VolumeRule.forMinLot(min, max) // validates min > 0 and max >= min
        dataStore.edit { it[keyFor(symbol)] = encode(rule) }
    }

    /** Change the minimum and maximum of an existing rule. */
    suspend fun edit(symbol: String, min: BigDecimal, max: BigDecimal) {
        check(current().isKnown(symbol)) { "no rule saved for $symbol" }
        saveMinLot(symbol, min, max)
    }

    /** Forget a symbol, so the next pass on it asks for the minimum lot again. */
    suspend fun delete(symbol: String) {
        dataStore.edit { it.remove(keyFor(symbol)) }
    }

    private fun toRules(prefs: Preferences): SymbolVolumeRules {
        var rules = SymbolVolumeRules()
        for ((key, value) in prefs.asMap()) {
            if (!key.name.startsWith(KEY_PREFIX)) continue
            val symbol = key.name.removePrefix(KEY_PREFIX)
            val rule = decode(value as? String ?: continue) ?: continue
            rules = rules.with(symbol, rule)
        }
        return rules
    }

    private companion object {
        const val KEY_PREFIX = "rule:"

        fun keyFor(symbol: String) = stringPreferencesKey(KEY_PREFIX + symbol)

        fun encode(rule: VolumeRule) = "${rule.min.toPlainString()}|${rule.max.toPlainString()}"

        fun decode(text: String): VolumeRule? {
            val parts = text.split('|')
            if (parts.size != 2) return null
            val min = parts[0].toBigDecimalOrNull() ?: return null
            val max = parts[1].toBigDecimalOrNull() ?: return null
            return runCatching { VolumeRule.forMinLot(min, max) }.getOrNull()
        }
    }
}
