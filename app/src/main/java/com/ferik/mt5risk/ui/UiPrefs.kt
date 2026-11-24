package com.ferik.mt5risk.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ferik.mt5risk.core.PropFirm
import com.ferik.mt5risk.core.RiskBasisKind
import com.ferik.mt5risk.core.RiskMode
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Where the bubble sits on screen, in pixels from the top-left corner. */
data class BubblePosition(val x: Int, val y: Int)

/** Small things the UI remembers between runs: risk choices, auto-apply and where the bubble was left. */
class UiPrefs(private val dataStore: DataStore<Preferences>) {

    val lastRisk: Flow<BigDecimal?> = dataStore.data.map { it[LAST_RISK]?.toBigDecimalOrNull() }

    suspend fun lastRiskNow(): BigDecimal? = lastRisk.first()

    suspend fun setLastRisk(percent: BigDecimal) {
        dataStore.edit { it[LAST_RISK] = percent.stripTrailingZeros().toPlainString() }
    }

    /** True (the default) when moving the stop-loss should re-size the lot without a tap. */
    val autoApply: Flow<Boolean> = dataStore.data.map { it[AUTO_APPLY] ?: true }

    suspend fun autoApplyNow(): Boolean = autoApply.first()

    suspend fun setAutoApply(enabled: Boolean) {
        dataStore.edit { it[AUTO_APPLY] = enabled }
    }

    /** Which entry the picker shows. */
    val riskMode: Flow<RiskMode> = dataStore.data.map { prefs ->
        runCatching { RiskMode.valueOf(prefs[RISK_MODE] ?: RiskMode.PERCENT.name) }
            .getOrDefault(RiskMode.PERCENT)
    }

    suspend fun riskModeNow(): RiskMode = riskMode.first()

    suspend fun setRiskMode(mode: RiskMode) {
        dataStore.edit { it[RISK_MODE] = mode.name }
    }

    /** The last fixed amount in account currency, null until the user enters one. */
    val fixedRisk: Flow<BigDecimal?> = dataStore.data.map { it[FIXED_RISK]?.toBigDecimalOrNull() }

    suspend fun fixedRiskNow(): BigDecimal? = fixedRisk.first()

    suspend fun setFixedRisk(amount: BigDecimal) {
        dataStore.edit { it[FIXED_RISK] = amount.stripTrailingZeros().toPlainString() }
    }

    /** The balance the challenge started at, null until the user enters the prop-firm numbers. */
    val propFirmStartBalance: Flow<BigDecimal?> = dataStore.data.map { it[PROP_FIRM_START]?.toBigDecimalOrNull() }

    /** The maximum drawdown the firm allows, null until the user enters the prop-firm numbers. */
    val propFirmMaxDrawdown: Flow<BigDecimal?> = dataStore.data.map { it[PROP_FIRM_MAX_DD]?.toBigDecimalOrNull() }

    /** Both prop-firm numbers at once, or null while either is missing or they are not a valid pair. */
    val propFirm: Flow<PropFirm?> = dataStore.data.map { prefs ->
        val start = prefs[PROP_FIRM_START]?.toBigDecimalOrNull() ?: return@map null
        val maxDrawdown = prefs[PROP_FIRM_MAX_DD]?.toBigDecimalOrNull() ?: return@map null
        runCatching { PropFirm(start, maxDrawdown) }.getOrNull()
    }

    suspend fun propFirmNow(): PropFirm? = propFirm.first()

    suspend fun setPropFirm(startBalance: BigDecimal, maxDrawdown: BigDecimal) {
        dataStore.edit {
            it[PROP_FIRM_START] = startBalance.stripTrailingZeros().toPlainString()
            it[PROP_FIRM_MAX_DD] = maxDrawdown.stripTrailingZeros().toPlainString()
        }
    }

    /** Which base the risk percentage applies to; the balance until the user picks otherwise. */
    val riskBasis: Flow<RiskBasisKind> = dataStore.data.map { prefs ->
        runCatching { RiskBasisKind.valueOf(prefs[RISK_BASIS] ?: RiskBasisKind.BALANCE.name) }
            .getOrDefault(RiskBasisKind.BALANCE)
    }

    suspend fun riskBasisNow(): RiskBasisKind = riskBasis.first()

    suspend fun setRiskBasis(kind: RiskBasisKind) {
        dataStore.edit { it[RISK_BASIS] = kind.name }
    }

    /** Whether the user has read what the accessibility service does and what it never does. */
    val disclosureAccepted: Flow<Boolean> = dataStore.data.map { it[DISCLOSURE_ACCEPTED] == true }

    suspend fun acceptDisclosure() {
        dataStore.edit { it[DISCLOSURE_ACCEPTED] = true }
    }

    suspend fun bubblePosition(): BubblePosition? {
        val prefs = dataStore.data.first()
        val x = prefs[BUBBLE_X] ?: return null
        val y = prefs[BUBBLE_Y] ?: return null
        return BubblePosition(x, y)
    }

    suspend fun setBubblePosition(position: BubblePosition) {
        dataStore.edit {
            it[BUBBLE_X] = position.x
            it[BUBBLE_Y] = position.y
        }
    }

    private companion object {
        val LAST_RISK = stringPreferencesKey("last_risk_percent")
        val AUTO_APPLY = booleanPreferencesKey("auto_apply")
        val RISK_MODE = stringPreferencesKey("risk_mode")
        val FIXED_RISK = stringPreferencesKey("fixed_risk")
        val PROP_FIRM_START = stringPreferencesKey("prop_firm_start_balance")
        val PROP_FIRM_MAX_DD = stringPreferencesKey("prop_firm_max_drawdown")
        val RISK_BASIS = stringPreferencesKey("risk_basis")
        val DISCLOSURE_ACCEPTED = booleanPreferencesKey("disclosure_accepted")
        val BUBBLE_X = intPreferencesKey("bubble_x")
        val BUBBLE_Y = intPreferencesKey("bubble_y")
    }
}
