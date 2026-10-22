package com.ferik.mt5risk.balance

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class BalanceSource { TRADE_TAB, MANUAL }

/** The balance the risk percentage is applied to, with when it was captured and where it came from. */
data class CachedBalance(
    val value: BigDecimal,
    /** Currency when known (the Trade tab row itself carries none), for display only. */
    val currency: String?,
    val seenAtMillis: Long,
    val source: BalanceSource,
)

/**
 * Remembers the account balance, which MetaTrader 5 shows only on the Trade tab. Two slots: the
 * value last captured from the Trade tab, and an optional manual override. A newer Trade tab
 * capture replaces a manual value; clearing the manual value falls back to the captured one.
 */
class BalanceStore(
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The balance in effect: the manual value when set, otherwise the last Trade tab capture. */
    val balance: Flow<CachedBalance?> = dataStore.data.map(::toCached)

    suspend fun current(): CachedBalance? = balance.first()

    suspend fun recordFromTradeTab(value: BigDecimal, currency: String? = null) {
        require(value.signum() > 0) { "balance must be positive" }
        dataStore.edit {
            it[AUTO_VALUE] = value.toPlainString()
            it[AUTO_SEEN_AT] = clock()
            if (currency != null) it[AUTO_CURRENCY] = currency else it.remove(AUTO_CURRENCY)
            clearManual(it)
        }
    }

    suspend fun setManual(value: BigDecimal, currency: String? = null) {
        require(value.signum() > 0) { "balance must be positive" }
        dataStore.edit {
            it[MANUAL_VALUE] = value.toPlainString()
            it[MANUAL_SET_AT] = clock()
            if (currency != null) it[MANUAL_CURRENCY] = currency else it.remove(MANUAL_CURRENCY)
        }
    }

    suspend fun clearManual() {
        dataStore.edit(::clearManual)
    }

    private fun clearManual(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        prefs.remove(MANUAL_VALUE)
        prefs.remove(MANUAL_SET_AT)
        prefs.remove(MANUAL_CURRENCY)
    }

    private fun toCached(prefs: Preferences): CachedBalance? {
        prefs[MANUAL_VALUE]?.toBigDecimalOrNull()?.let { manual ->
            return CachedBalance(manual, prefs[MANUAL_CURRENCY], prefs[MANUAL_SET_AT] ?: 0L, BalanceSource.MANUAL)
        }
        val auto = prefs[AUTO_VALUE]?.toBigDecimalOrNull() ?: return null
        return CachedBalance(auto, prefs[AUTO_CURRENCY], prefs[AUTO_SEEN_AT] ?: 0L, BalanceSource.TRADE_TAB)
    }

    private companion object {
        val AUTO_VALUE = stringPreferencesKey("balance_auto_value")
        val AUTO_SEEN_AT = longPreferencesKey("balance_auto_seen_at")
        val AUTO_CURRENCY = stringPreferencesKey("balance_auto_currency")
        val MANUAL_VALUE = stringPreferencesKey("balance_manual_value")
        val MANUAL_SET_AT = longPreferencesKey("balance_manual_set_at")
        val MANUAL_CURRENCY = stringPreferencesKey("balance_manual_currency")
    }
}
