package com.ferik.mt5risk.core

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolVolumeRulesTest {

    private fun d(text: String) = BigDecimal(text)

    private fun rulesWithForexAndIndex() = SymbolVolumeRules()
        .withMinLot("GBPJPY", d("0.01"))
        .withMinLot("SP500", d("0.1"))

    private fun size(rules: SymbolVolumeRules, symbol: String, volume: String, loss: String): SizingResult {
        val rule = rules.ruleFor(symbol)!!
        return RiskEngine.size(SizingRequest(d("5000"), d("1"), d(volume), d(loss), rule))
    }

    @Test
    fun forexRuleIsMinPointZeroOneStepPointZeroOneMaxHundred() {
        val rule = rulesWithForexAndIndex().ruleFor("GBPJPY")!!
        assertEquals(0, d("0.01").compareTo(rule.min))
        assertEquals(0, d("0.01").compareTo(rule.step))
        assertEquals(0, d("100").compareTo(rule.max))
    }

    @Test
    fun forexFloorsToTheCent() {
        // observed on device: 0.01 lots lose 1.49 -> 0.3356 -> 0.33
        val result = size(rulesWithForexAndIndex(), "GBPJPY", "0.01", "1.49")
        assertEquals("0.33", rulesWithForexAndIndex().ruleFor("GBPJPY")!!.format(result.volume))
    }

    @Test
    fun indexRuleStepsInTenths() {
        val rules = rulesWithForexAndIndex()
        val rule = rules.ruleFor("SP500")!!
        assertEquals(0, d("0.1").compareTo(rule.step))
        // 50 / 14.84 = 3.369 -> 3.3
        assertEquals("3.3", rule.format(size(rules, "SP500", "1", "14.84").volume))
        // the only values below 0.4 are 0.1, 0.2, 0.3
        assertEquals(0, d("0.3").compareTo(rule.floorToStep(d("0.39"))))
        assertEquals(0, d("0.2").compareTo(rule.floorToStep(d("0.29"))))
    }

    @Test
    fun indexBelowMinimumGivesMinimumWithWarning() {
        val rules = rulesWithForexAndIndex()
        val result = size(rules, "SP500", "0.1", "83.33")
        assertEquals("0.1", rules.ruleFor("SP500")!!.format(result.volume))
        assertTrue(result.minLotExceedsRisk)
        assertEquals(VolumeClamp.RAISED_TO_MIN, result.clamp)
    }

    @Test
    fun maximumDefaultsToOneHundredAndIsEditable() {
        val rules = rulesWithForexAndIndex()
        val capped = size(rules, "GBPJPY", "1", "0.01")
        assertEquals(VolumeClamp.CAPPED_AT_MAX, capped.clamp)
        assertEquals(0, d("100").compareTo(capped.volume))

        val lowered = rules.withMinLot("GBPJPY", d("0.01"), max = d("50"))
        val loweredResult = size(lowered, "GBPJPY", "1", "0.01")
        assertEquals(0, d("50").compareTo(loweredResult.volume))
    }

    @Test
    fun symbolNamesMatchExactly() {
        val rules = SymbolVolumeRules()
            .withMinLot("GBPJPY", d("0.01"))
            .withMinLot("GBPJPY.pc", d("0.1"))

        assertEquals(0, d("0.01").compareTo(rules.ruleFor("GBPJPY")!!.min))
        assertEquals(0, d("0.1").compareTo(rules.ruleFor("GBPJPY.pc")!!.min))
        assertNull(rules.ruleFor("gbpjpy"))
        assertNull(rules.ruleFor("GBPJPY.PC"))
        assertNull(rules.ruleFor("GBPJPY "))
    }

    @Test
    fun unknownSymbolHasNoRuleAndRequestsNoWrite() {
        val rules = rulesWithForexAndIndex()
        assertNull(rules.ruleFor("XAUUSD"))
        assertFalse(rules.isKnown("XAUUSD"))

        val refusal = Preconditions.beforeExpand(d("1"), "XAUUSD", rules.ruleFor("XAUUSD"))
        assertEquals(Refusal.NoVolumeRule("XAUUSD"), refusal)
    }

    @Test
    fun knownSymbolPassesThePreExpandCheck() {
        val rules = rulesWithForexAndIndex()
        assertNull(Preconditions.beforeExpand(d("1"), "SP500", rules.ruleFor("SP500")))
    }

    @Test
    fun withReplacesAndWithoutDeletes() {
        var rules = rulesWithForexAndIndex()
        rules = rules.withMinLot("SP500", d("1"))
        assertEquals(0, d("1").compareTo(rules.ruleFor("SP500")!!.min))

        rules = rules.without("SP500")
        assertNull(rules.ruleFor("SP500"))
        assertNotNull(rules.ruleFor("GBPJPY"))
    }

    @Test
    fun rulesAreImmutableValues() {
        val original = rulesWithForexAndIndex()
        original.without("GBPJPY")
        original.withMinLot("XAUUSD", d("0.01"))
        assertNotNull(original.ruleFor("GBPJPY"))
        assertNull(original.ruleFor("XAUUSD"))
    }

    @Test
    fun allListsRulesSortedByName() {
        val names = rulesWithForexAndIndex().all().map { it.first }
        assertEquals(listOf("GBPJPY", "SP500"), names)
    }

    @Test
    fun customMinLotAnswerIsParsedAndValidated() {
        assertEquals(0, d("0.05").compareTo(SymbolVolumeRules.parseMinLot("0.05")!!))
        assertEquals(0, d("0.1").compareTo(SymbolVolumeRules.parseMinLot(" 0.1 ")!!))
        assertNull(SymbolVolumeRules.parseMinLot("0"))
        assertNull(SymbolVolumeRules.parseMinLot("-0.1"))
        assertNull(SymbolVolumeRules.parseMinLot("abc"))
        assertNull(SymbolVolumeRules.parseMinLot(""))
        assertNull(SymbolVolumeRules.parseMinLot(null))
    }

    @Test
    fun presetsAreZeroPointZeroOneZeroPointOneAndOne() {
        assertEquals(
            listOf("0.01", "0.1", "1"),
            SymbolVolumeRules.MIN_LOT_CHOICES.map { it.toPlainString() },
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankSymbolIsRejected() {
        SymbolVolumeRules().withMinLot(" ", d("0.01"))
    }
}
