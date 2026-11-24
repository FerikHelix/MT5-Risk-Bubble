package com.ferik.mt5risk.ui

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ferik.mt5risk.balance.InMemoryPreferencesDataStore
import com.ferik.mt5risk.core.RiskBasisKind
import com.ferik.mt5risk.core.RiskMode
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPrefsTest {

    private fun store() = InMemoryPreferencesDataStore()

    @Test
    fun freshStoreHasTheDocumentedDefaults() = runTest {
        val prefs = UiPrefs(store())

        assertTrue("auto-apply defaults on", prefs.autoApplyNow())
        assertEquals(RiskMode.PERCENT, prefs.riskModeNow())
        assertEquals(RiskBasisKind.BALANCE, prefs.riskBasisNow())
        assertNull(prefs.fixedRiskNow())
        assertNull(prefs.lastRiskNow())
        assertNull(prefs.propFirmStartBalance.first())
        assertNull(prefs.propFirmMaxDrawdown.first())
        assertNull(prefs.propFirmNow())
    }

    @Test
    fun everySettingRoundTripsThroughTheSameStore() = runTest {
        val prefs = UiPrefs(store())

        prefs.setAutoApply(false)
        prefs.setRiskMode(RiskMode.FIXED)
        prefs.setFixedRisk(BigDecimal("60.00"))
        prefs.setLastRisk(BigDecimal("0.50"))
        prefs.setRiskBasis(RiskBasisKind.DRAWDOWN)
        prefs.setPropFirm(startBalance = BigDecimal("10000"), maxDrawdown = BigDecimal("500"))

        assertEquals(false, prefs.autoApplyNow())
        assertEquals(RiskMode.FIXED, prefs.riskModeNow())
        assertEquals(0, BigDecimal("60").compareTo(prefs.fixedRiskNow()))
        assertEquals(0, BigDecimal("0.5").compareTo(prefs.lastRiskNow()))
        assertEquals(RiskBasisKind.DRAWDOWN, prefs.riskBasisNow())
        assertEquals(0, BigDecimal("10000").compareTo(prefs.propFirmStartBalance.first()))
        assertEquals(0, BigDecimal("500").compareTo(prefs.propFirmMaxDrawdown.first()))
        assertEquals(0, BigDecimal("9500").compareTo(prefs.propFirmNow()!!.floor))
    }

    @Test
    fun settingsSurviveARestartOnTheSameStorage() = runTest {
        val dataStore = store()
        UiPrefs(dataStore).apply {
            setAutoApply(false)
            setRiskMode(RiskMode.FIXED)
            setFixedRisk(BigDecimal("75"))
            setRiskBasis(RiskBasisKind.DRAWDOWN)
            setPropFirm(startBalance = BigDecimal("20000"), maxDrawdown = BigDecimal("1000"))
        }

        val reloaded = UiPrefs(dataStore)
        assertEquals(false, reloaded.autoApplyNow())
        assertEquals(RiskMode.FIXED, reloaded.riskModeNow())
        assertEquals(0, BigDecimal("75").compareTo(reloaded.fixedRiskNow()))
        assertEquals(RiskBasisKind.DRAWDOWN, reloaded.riskBasisNow())
        assertEquals(0, BigDecimal("1000").compareTo(reloaded.propFirmNow()!!.maxDrawdown))
    }

    @Test
    fun anUnknownStoredValueFallsBackToTheDefault() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("risk_mode")] = "NOT_A_MODE"
            prefs[stringPreferencesKey("risk_basis")] = "NOT_A_BASIS"
        }

        val prefs = UiPrefs(dataStore)
        assertEquals(RiskMode.PERCENT, prefs.riskModeNow())
        assertEquals(RiskBasisKind.BALANCE, prefs.riskBasisNow())
    }

    @Test
    fun anImpossiblePairOfPropFirmNumbersIsTreatedAsUnset() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("prop_firm_start_balance")] = "10000"
            prefs[stringPreferencesKey("prop_firm_max_drawdown")] = "10000"
        }

        assertNull(UiPrefs(dataStore).propFirmNow())
    }

    @Test
    fun onlyOneOfTheTwoPropFirmNumbersIsNotEnough() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("prop_firm_start_balance")] = "10000"
        }

        assertNull(UiPrefs(dataStore).propFirmNow())
    }
}
