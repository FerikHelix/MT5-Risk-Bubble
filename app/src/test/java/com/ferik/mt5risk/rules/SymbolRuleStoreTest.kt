package com.ferik.mt5risk.rules

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ferik.mt5risk.balance.InMemoryPreferencesDataStore
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

class SymbolRuleStoreTest {

    private fun d(text: String) = BigDecimal(text)

    private fun run(block: suspend (InMemoryPreferencesDataStore, SymbolRuleStore) -> Unit) {
        val storage = InMemoryPreferencesDataStore()
        runBlocking { block(storage, SymbolRuleStore(storage)) }
    }

    @Test
    fun startsWithNoRules() = run { _, store ->
        assertTrue(store.current().all().isEmpty())
        assertNull(store.current().ruleFor("GBPJPY"))
    }

    @Test
    fun savesTheAnswerAsMinStepMaxRule() = run { _, store ->
        store.saveMinLot("GBPJPY", d("0.01"))

        val rule = store.current().ruleFor("GBPJPY")!!
        assertEquals(0, d("0.01").compareTo(rule.min))
        assertEquals(0, d("0.01").compareTo(rule.step))
        assertEquals(0, d("100").compareTo(rule.max))
    }

    @Test
    fun indexRuleKeepsItsTenthStep() = run { _, store ->
        store.saveMinLot("SP500", d("0.1"))
        val rule = store.current().ruleFor("SP500")!!
        assertEquals("3.3", rule.format(rule.floorToStep(d("3.37"))))
    }

    @Test
    fun rulesSurviveARestartOnTheSameStorage() = run { storage, store ->
        store.saveMinLot("GBPJPY", d("0.01"))
        store.saveMinLot("SP500", d("0.1"), max = d("50"))

        val restarted = SymbolRuleStore(storage).current()
        assertEquals(listOf("GBPJPY", "SP500"), restarted.all().map { it.first })
        assertEquals(0, d("50").compareTo(restarted.ruleFor("SP500")!!.max))
    }

    @Test
    fun editingAMaximumChangesLaterRules() = run { _, store ->
        store.saveMinLot("GBPJPY", d("0.01"))
        store.edit("GBPJPY", min = d("0.01"), max = d("50"))
        assertEquals(0, d("50").compareTo(store.current().ruleFor("GBPJPY")!!.max))
    }

    @Test
    fun editingTheMinimumMovesTheStepToo() = run { _, store ->
        store.saveMinLot("SP500", d("0.1"))
        store.edit("SP500", min = d("1"), max = d("100"))
        val rule = store.current().ruleFor("SP500")!!
        assertEquals(0, d("1").compareTo(rule.min))
        assertEquals(0, d("1").compareTo(rule.step))
    }

    @Test
    fun deletingARuleMakesTheSymbolUnknownAgain() = run { _, store ->
        store.saveMinLot("SP500", d("0.1"))
        store.saveMinLot("GBPJPY", d("0.01"))

        store.delete("SP500")

        val rules = store.current()
        assertFalse(rules.isKnown("SP500"))
        assertNotNull(rules.ruleFor("GBPJPY"))
    }

    @Test
    fun deletingAnUnknownSymbolIsHarmless() = run { _, store ->
        store.delete("NOPE")
        assertTrue(store.current().all().isEmpty())
    }

    @Test
    fun keysAreExactSoNameVariantsAreSeparate() = run { _, store ->
        store.saveMinLot("GBPJPY", d("0.01"))
        store.saveMinLot("GBPJPY.pc", d("0.1"))

        val rules = store.current()
        assertEquals(0, d("0.01").compareTo(rules.ruleFor("GBPJPY")!!.min))
        assertEquals(0, d("0.1").compareTo(rules.ruleFor("GBPJPY.pc")!!.min))
        assertNull(rules.ruleFor("gbpjpy"))
    }

    @Test
    fun symbolsWithUnusualCharactersRoundTrip() = run { _, store ->
        store.saveMinLot("US500.cash", d("0.1"))
        store.saveMinLot("#AAPL", d("1"))
        store.saveMinLot("EUR/USD m", d("0.01"))
        assertEquals(
            listOf("#AAPL", "EUR/USD m", "US500.cash"),
            store.current().all().map { it.first },
        )
    }

    @Test
    fun savingAgainReplacesTheEarlierRule() = run { _, store ->
        store.saveMinLot("SP500", d("0.1"))
        store.saveMinLot("SP500", d("1"))
        assertEquals(1, store.current().all().size)
        assertEquals(0, d("1").compareTo(store.current().ruleFor("SP500")!!.min))
    }

    @Test
    fun rejectsBadInput() = run { _, store ->
        assertFailsWith<IllegalArgumentException> { store.saveMinLot(" ", d("0.01")) }
        assertFailsWith<IllegalArgumentException> { store.saveMinLot("X", d("0")) }
        assertFailsWith<IllegalArgumentException> { store.saveMinLot("X", d("-0.1")) }
        assertFailsWith<IllegalArgumentException> { store.saveMinLot("X", d("1"), max = d("0.5")) }
        assertTrue(store.current().all().isEmpty())
    }

    @Test
    fun editingAnUnknownSymbolIsRejected() = run { _, store ->
        assertFailsWith<IllegalStateException> { store.edit("NOPE", d("0.01"), d("100")) }
    }

    @Test
    fun aCorruptStoredValueIsSkippedNotFatal() = run { storage, store ->
        store.saveMinLot("GBPJPY", d("0.01"))
        storage.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                set(androidx.datastore.preferences.core.stringPreferencesKey("rule:BAD"), "garbage")
            }
        }
        val rules = store.current()
        assertNull(rules.ruleFor("BAD"))
        assertNotNull(rules.ruleFor("GBPJPY"))
    }

    @Test
    fun persistsToARealDataStoreFileAcrossARestart() {
        val dir: File = Files.createTempDirectory("rules-test").toFile()
        val file = File(dir, "rules.preferences_pb")
        var scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            runBlocking {
                SymbolRuleStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
                    .saveMinLot("SP500", d("0.1"))

                scope.cancel()
                scope = CoroutineScope(Job() + Dispatchers.Unconfined)
                val reloaded = SymbolRuleStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))

                assertEquals(0, d("0.1").compareTo(reloaded.current().ruleFor("SP500")!!.min))
            }
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    private inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit) {
        try {
            block()
        } catch (expected: Throwable) {
            if (expected is T) return
            throw AssertionError("expected ${T::class.simpleName} but got $expected", expected)
        }
        throw AssertionError("expected ${T::class.simpleName} but nothing was thrown")
    }
}
