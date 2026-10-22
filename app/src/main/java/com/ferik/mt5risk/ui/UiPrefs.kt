package com.ferik.mt5risk.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Where the bubble sits on screen, in pixels from the top-left corner. */
data class BubblePosition(val x: Int, val y: Int)

/** Small things the UI remembers between runs: the last risk chosen and where the bubble was left. */
class UiPrefs(private val dataStore: DataStore<Preferences>) {

    val lastRisk: Flow<BigDecimal?> = dataStore.data.map { it[LAST_RISK]?.toBigDecimalOrNull() }

    suspend fun lastRiskNow(): BigDecimal? = lastRisk.first()

    suspend fun setLastRisk(percent: BigDecimal) {
        dataStore.edit { it[LAST_RISK] = percent.stripTrailingZeros().toPlainString() }
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
        val DISCLOSURE_ACCEPTED = booleanPreferencesKey("disclosure_accepted")
        val BUBBLE_X = intPreferencesKey("bubble_x")
        val BUBBLE_Y = intPreferencesKey("bubble_y")
    }
}
