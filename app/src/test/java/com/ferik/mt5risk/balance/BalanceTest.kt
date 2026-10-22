package com.ferik.mt5risk.balance

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ferik.mt5risk.core.Preconditions
import com.ferik.mt5risk.core.Refusal
import com.ferik.mt5risk.mt5.Fixtures
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceTest {

    private fun d(text: String) = BigDecimal(text)

    private class Harness {
        private val storage = InMemoryPreferencesDataStore()
        var now = 1_000_000L

        fun newStore() = BalanceStore(storage) { now }

        /** Simulates the process dying and starting again on the same storage. */
        fun restart(): BalanceStore = newStore()
    }

    private fun withStore(block: suspend (Harness, BalanceStore) -> Unit) {
        val harness = Harness()
        runBlocking { block(harness, harness.newStore()) }
    }

    /** One write to a real DataStore file, then a new DataStore on the same file, as after a restart. */
    @Test
    fun persistsToARealDataStoreFileAcrossARestart() {
        val dir: File = Files.createTempDirectory("balance-test").toFile()
        val file = File(dir, "balance.preferences_pb")
        var scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            runBlocking {
                val first = BalanceStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })) { 42_000L }
                first.recordFromTradeTab(d("4506.77"), "USC")

                scope.cancel()
                scope = CoroutineScope(Job() + Dispatchers.Unconfined)
                val second = BalanceStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })) { 99L }

                val cached = second.current()!!
                assertEquals(0, d("4506.77").compareTo(cached.value))
                assertEquals(42_000L, cached.seenAtMillis)
                assertEquals("USC", cached.currency)
            }
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    // --- BalanceStore (task 5.1) ------------------------------------------------------------

    @Test
    fun startsEmpty() = withStore { _, store ->
        assertNull(store.current())
    }

    @Test
    fun savesAndLoadsATradeTabBalanceWithItsTime() = withStore { harness, store ->
        harness.now = 1_234_567L
        store.recordFromTradeTab(d("5000.00"), "USD")

        val cached = store.current()!!
        assertEquals(0, d("5000.00").compareTo(cached.value))
        assertEquals("USD", cached.currency)
        assertEquals(1_234_567L, cached.seenAtMillis)
        assertEquals(BalanceSource.TRADE_TAB, cached.source)
    }

    @Test
    fun survivesARestartWithItsOriginalTimestamp() = withStore { harness, store ->
        harness.now = 42_000L
        store.recordFromTradeTab(d("4506.77"), "USC")

        harness.now = 99_999_999L
        val afterRestart = harness.restart().current()!!

        assertEquals(0, d("4506.77").compareTo(afterRestart.value))
        assertEquals(42_000L, afterRestart.seenAtMillis)
        assertEquals("USC", afterRestart.currency)
    }

    @Test
    fun manualBalanceIsUsedAndLabelledManual() = withStore { _, store ->
        store.setManual(d("5000"))
        val cached = store.current()!!
        assertEquals(0, d("5000").compareTo(cached.value))
        assertEquals(BalanceSource.MANUAL, cached.source)
    }

    @Test
    fun manualBalanceOverridesACapturedOneUntilCleared() = withStore { _, store ->
        store.recordFromTradeTab(d("5000.00"))
        store.setManual(d("3000"))
        assertEquals(0, d("3000").compareTo(store.current()!!.value))

        store.clearManual()
        val fallback = store.current()!!
        assertEquals(0, d("5000.00").compareTo(fallback.value))
        assertEquals(BalanceSource.TRADE_TAB, fallback.source)
    }

    @Test
    fun aNewerTradeTabValueReplacesTheManualOne() = withStore { _, store ->
        store.setManual(d("3000"))
        store.recordFromTradeTab(d("5120.50"))

        val cached = store.current()!!
        assertEquals(0, d("5120.50").compareTo(cached.value))
        assertEquals(BalanceSource.TRADE_TAB, cached.source)
    }

    @Test
    fun manualBalanceSurvivesARestart() = withStore { harness, store ->
        store.setManual(d("2500"))
        val cached = harness.restart().current()!!
        assertEquals(0, d("2500").compareTo(cached.value))
        assertEquals(BalanceSource.MANUAL, cached.source)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsANonPositiveCapturedBalance() = withStore { _, store ->
        store.recordFromTradeTab(d("0"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsANonPositiveManualBalance() = withStore { _, store ->
        store.setManual(d("-1"))
    }

    // --- BalanceWatcher (task 5.2) ----------------------------------------------------------

    @Test
    fun tradeTabFixtureYieldsTheBalanceRow() {
        assertEquals(0, d("5000.00").compareTo(TradeTabReader.readBalance(Fixtures.load("trade_tab"))!!))
    }

    @Test
    fun openingTheTradeTabUpdatesTheCache() = withStore { harness, store ->
        harness.now = 777L
        val recorded = BalanceWatcher(store).onWindowChanged(Fixtures.load("trade_tab"), currencyHint = "USD")

        assertTrue(recorded)
        val cached = store.current()!!
        assertEquals(0, d("5000.00").compareTo(cached.value))
        assertEquals("USD", cached.currency)
        assertEquals(777L, cached.seenAtMillis)
    }

    @Test
    fun otherScreensDoNotTouchTheCache() = withStore { _, store ->
        store.recordFromTradeTab(d("4800.00"))
        val watcher = BalanceWatcher(store)

        for (name in listOf("chart_no_ticket", "collapsed_strip", "expanded_market_sl_set")) {
            assertFalse(watcher.onWindowChanged(Fixtures.load(name)))
        }
        assertEquals(0, d("4800.00").compareTo(store.current()!!.value))
    }

    @Test
    fun anUnreadableBalanceRowKeepsThePreviousValue() = withStore { _, store ->
        store.recordFromTradeTab(d("4800.00"))
        val unreadable = Fixtures.load("trade_tab") { it.replace("text=\"5 000.00\" resource-id=\"net.metaquotes.metatrader5:id/balance\"", "text=\"n/a\" resource-id=\"net.metaquotes.metatrader5:id/balance\"") }

        assertFalse(BalanceWatcher(store).onWindowChanged(unreadable))
        assertEquals(0, d("4800.00").compareTo(store.current()!!.value))
    }

    @Test
    fun aHiddenBalanceRowIsNotRecorded() = withStore { _, store ->
        val hidden = Fixtures.load("trade_tab") {
            it.replace("resource-id=\"net.metaquotes.metatrader5:id/balance\"", "resource-id=\"net.metaquotes.metatrader5:id/balance\" visible-to-user=\"false\"")
        }
        assertFalse(BalanceWatcher(store).onWindowChanged(hidden))
        assertNull(store.current())
    }

    @Test
    fun theNodeTextCanBeRecordedDirectly() = withStore { harness, store ->
        harness.now = 555L
        val watcher = BalanceWatcher(store)

        assertTrue(watcher.onBalanceText("4 506.77", currencyHint = "USC"))
        val cached = store.current()!!
        assertEquals(0, d("4506.77").compareTo(cached.value))
        assertEquals("USC", cached.currency)
        assertEquals(555L, cached.seenAtMillis)

        assertFalse(watcher.onBalanceText("n/a"))
        assertFalse(watcher.onBalanceText(null))
        assertFalse(watcher.onBalanceText("0.00"))
        assertEquals(0, d("4506.77").compareTo(store.current()!!.value))
    }

    @Test
    fun aRenamedBalanceRowIsIgnored() = withStore { _, store ->
        val renamed = Fixtures.load("trade_tab") { it.replace("id/balance\"", "id/renamed_by_an_mt5_update\"") }
        assertFalse(BalanceWatcher(store).onWindowChanged(renamed))
        assertNull(store.current())
    }

    // --- Staleness and display (task 5.3) ---------------------------------------------------

    @Test
    fun aCapturedBalanceBelowFreeMarginIsRefusedAsStale() = withStore { _, store ->
        store.recordFromTradeTab(d("4000.00"))
        val refusal = Preconditions.afterRead(
            balance = store.current()!!.value,
            freeMargin = d("5000.00"),
            currentVolume = d("0.01"),
            slPrice = d("209.932"),
            slLoss = d("-1.19"),
        )
        assertEquals(Refusal.StaleBalance(d("4000.00"), d("5000.00")), refusal)
    }

    @Test
    fun aCapturedBalanceAboveFreeMarginIsAccepted() = withStore { _, store ->
        store.recordFromTradeTab(d("5000.00"))
        val refusal = Preconditions.afterRead(
            balance = store.current()!!.value,
            freeMargin = d("4439.63"),
            currentVolume = d("0.01"),
            slPrice = d("209.932"),
            slLoss = d("-1.19"),
        )
        assertNull(refusal)
    }

    @Test
    fun formatsAmountsLikeMetaTrader() {
        assertEquals("5 000.00", BalanceDisplay.formatAmount(d("5000")))
        assertEquals("4 506.77", BalanceDisplay.formatAmount(d("4506.77")))
        assertEquals("1 234 567.80", BalanceDisplay.formatAmount(d("1234567.8")))
        assertEquals("999.50", BalanceDisplay.formatAmount(d("999.5")))
        assertEquals("0.00", BalanceDisplay.formatAmount(d("0")))
    }

    @Test
    fun describesTheAgeOfTheBalance() {
        val minute = 60_000L
        assertEquals("just now", BalanceDisplay.age(0, 30_000))
        assertEquals("3 min ago", BalanceDisplay.age(0, 3 * minute + 5_000))
        assertEquals("2 h ago", BalanceDisplay.age(0, 2 * 60 * minute + minute))
        assertEquals("3 d ago", BalanceDisplay.age(0, 3 * 24 * 60 * minute))
        assertEquals("just now", BalanceDisplay.age(500, 100)) // clock skew never goes negative
    }

    @Test
    fun theLabelShowsBalanceCurrencyAndAge() {
        val captured = CachedBalance(d("5000"), "USD", seenAtMillis = 0, source = BalanceSource.TRADE_TAB)
        assertEquals("Balance 5 000.00 USD, seen 3 min ago", BalanceDisplay.format(captured, 3 * 60_000L))

        val noCurrency = captured.copy(currency = null)
        assertEquals("Balance 5 000.00, seen just now", BalanceDisplay.format(noCurrency, 1_000L))

        val manual = captured.copy(source = BalanceSource.MANUAL)
        assertEquals("Balance 5 000.00 USD (manual)", BalanceDisplay.format(manual, 9_999_999L))
        assertNotNull(manual)
    }
}
