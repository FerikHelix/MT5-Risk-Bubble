package com.ferik.mt5risk.pass

import com.ferik.mt5risk.core.Refusal
import com.ferik.mt5risk.core.VolumeClamp
import com.ferik.mt5risk.core.VolumeRule
import java.math.BigDecimal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PassRunnerTest {

    private fun d(text: String) = BigDecimal(text)

    private val forex = VolumeRule.forMinLot(d("0.01"))
    private val index = VolumeRule.forMinLot(d("0.1"))
    private val one = d("1")

    private fun runner(
        mt5: FakeMt5,
        balance: String? = "5000",
        rules: Map<String, VolumeRule> = mapOf("GBPJPY" to forex),
        timeoutMillis: Long = 20_000L,
    ) = PassRunner(
        driver = mt5,
        balance = { balance?.let(::d) },
        ruleFor = { rules[it] },
        timeoutMillis = timeoutMillis,
    )

    private suspend fun PassRunner.pass(percent: BigDecimal = one): PassResult = runIfIdle(percent)!!

    // ============================================================================================
    // Direct mode: the fields behind the collapsed strip are read and written, the sheet never moves
    // ============================================================================================

    @Test
    fun directModeSizesTheLotWithoutEverMovingTheSheet() = runTest {
        val mt5 = FakeMt5() // 0.01 lots lose 1.49 -> 1 % of 5000 = 0.33 lots
        val result = runner(mt5).pass()

        val outcome = result.outcome as PassOutcome.Success
        assertEquals("0.33", outcome.volumeText)
        assertEquals(0, d("49.17").compareTo(outcome.observedLoss))
        assertEquals(1, outcome.writes)
        assertEquals("USD", outcome.currency)
        assertTrue(outcome.observedLoss <= d("50"))
        assertEquals("only a write, no expand and no collapse", listOf("write:0.33"), mt5.events)
        assertFalse(mt5.expanded)
        assertFalse(result.collapseFailed)
    }

    @Test
    fun aTicketTheUserAlreadyExpandedIsReadInPlaceAndLeftExpanded() = runTest {
        val mt5 = FakeMt5(expanded = true)
        val result = runner(mt5).pass()

        assertTrue(result.outcome is PassOutcome.Success)
        assertEquals(listOf("write:0.33"), mt5.events)
        assertTrue(mt5.expanded)
    }

    @Test
    fun priceDriftOverBudgetIsCorrectedFromTheLatestReading() = runTest {
        // 0.33 lots read 52.27 after the write, so it steps down to 0.31
        val mt5 = FakeMt5(lossPerLot = listOf("149", "158.4", "158"))
        val result = runner(mt5).pass()

        val outcome = result.outcome as PassOutcome.Success
        assertEquals("0.31", outcome.volumeText)
        assertEquals(2, outcome.writes)
        assertTrue(outcome.observedLoss <= d("50"))
        assertEquals(listOf("write:0.33", "write:0.31"), mt5.events)
    }

    @Test
    fun stopsAfterThreeWritesAndReportsTheLastObservedLoss() = runTest {
        val mt5 = FakeMt5(lossPerLot = listOf("149", "170", "175", "180"))
        val result = runner(mt5).pass()

        val outcome = result.outcome as PassOutcome.Exhausted
        assertEquals(0, d("0.28").compareTo(outcome.volume))
        assertEquals(0, d("50.40").compareTo(outcome.observedLoss))
        assertEquals(3, mt5.writes.size)
        assertFalse(mt5.expanded)
    }

    @Test
    fun indexSymbolWritesWholeTenths() = runTest {
        // 0.1 lots lose 14.8 -> 50 * 0.1 / 14.8 = 0.3378 -> 0.3
        val mt5 = FakeMt5(symbol = "SP500", volume = d("0.1"), lossPerLot = listOf("148"), realStep = d("0.1"))
        val result = runner(mt5, rules = mapOf("SP500" to index)).pass()

        val outcome = result.outcome as PassOutcome.Success
        assertEquals("0.3", outcome.volumeText)
        assertEquals(listOf("write:0.3"), mt5.events)
    }

    @Test
    fun minimumLotAboveTheRiskIsWrittenWithAWarning() = runTest {
        val mt5 = FakeMt5(lossPerLot = listOf("10000")) // even 0.01 lots lose 100 > 50
        val result = runner(mt5).pass()

        val outcome = result.outcome as PassOutcome.Success
        assertEquals("0.01", outcome.volumeText)
        assertTrue(outcome.minLotExceedsRisk)
        assertEquals(1, outcome.writes)
    }

    @Test
    fun maximumVolumeCapIsReported() = runTest {
        val mt5 = FakeMt5(lossPerLot = listOf("0.5"))
        val result = runner(mt5, balance = "100000").pass(d("10"))

        val outcome = result.outcome as PassOutcome.Success
        assertEquals(VolumeClamp.CAPPED_AT_MAX, outcome.clamp)
        assertEquals("100.00", outcome.volumeText)
    }

    // --- refusals in direct mode: nothing is written and nothing moves ----------------------

    private suspend fun assertRefusedUntouched(mt5: FakeMt5, expected: Refusal, percent: BigDecimal = one, balance: String? = "5000") {
        val result = runner(mt5, balance = balance).pass(percent)
        assertEquals(PassOutcome.Refused(expected), result.outcome)
        assertTrue("nothing at all should be done but was ${mt5.events}", mt5.events.isEmpty())
        assertFalse(mt5.expanded)
    }

    @Test
    fun unknownSymbolIsRefusedBeforeAnythingIsTouched() = runTest {
        val mt5 = FakeMt5(symbol = "SP500")
        assertRefusedUntouched(mt5, Refusal.NoVolumeRule("SP500"))
    }

    @Test
    fun invalidRiskIsRefusedBeforeAnythingIsTouched() = runTest {
        val result = runner(FakeMt5()).pass(d("0"))
        assertTrue((result.outcome as PassOutcome.Refused).refusal is Refusal.InvalidRiskPercent)
    }

    @Test
    fun ticketNotOnScreenAsksTheUserToOpenIt() = runTest {
        assertRefusedUntouched(FakeMt5(present = false), Refusal.TicketNotOpen)
    }

    @Test
    fun noStopLossIsRefusedWithoutWriting() = runTest {
        assertRefusedUntouched(FakeMt5(slPrice = BigDecimal.ZERO), Refusal.NoStopLoss)
    }

    @Test
    fun wrongSideStopLossWritesNothing() = runTest {
        assertRefusedUntouched(FakeMt5(lossSign = 1), Refusal.WrongSideStopLoss)
    }

    @Test
    fun missingBalanceWritesNothing() = runTest {
        assertRefusedUntouched(FakeMt5(), Refusal.NoBalance, balance = null)
    }

    @Test
    fun staleBalanceBelowFreeMarginWritesNothing() = runTest {
        assertRefusedUntouched(FakeMt5(), Refusal.StaleBalance(d("4000"), d("5000.00")), balance = "4000")
    }

    @Test
    fun nonLotsUnitWritesNothing() = runTest {
        assertRefusedUntouched(FakeMt5(volumeUnit = "Amount"), Refusal.VolumeUnitNotLots("Amount"))
    }

    // --- failures in direct mode -------------------------------------------------------------

    @Test
    fun wrongRuleIsCaughtByTheReadBack() = runTest {
        // saved rule says 0.01 but the broker really steps by 0.1, so MT5 shows 0.3 after 0.33 is written
        val mt5 = FakeMt5(realStep = d("0.1"))
        val result = runner(mt5).pass()

        val outcome = result.outcome as PassOutcome.RuleMismatch
        assertEquals("GBPJPY", outcome.symbol)
        assertEquals(0, d("0.33").compareTo(outcome.wrote))
        assertEquals(0, d("0.3").compareTo(outcome.shown))
        assertFalse(mt5.expanded)
    }

    @Test
    fun aVolumeWriteMt5RejectsIsAFailureNotASuccess() = runTest {
        val mt5 = FakeMt5(setVolumeWorks = false)
        val result = runner(mt5).pass()
        assertEquals(PassOutcome.Failed("Couldn't set the volume"), result.outcome)
    }

    @Test
    fun aHangingWriteTimesOutWithoutTouchingTheSheet() = runTest {
        val mt5 = FakeMt5().apply { onSetVolume = { delay(Long.MAX_VALUE) } }
        val result = runner(mt5, timeoutMillis = 1_000).pass()

        assertTrue((result.outcome as PassOutcome.Failed).reason.startsWith("Timed out"))
        assertEquals(listOf("write:0.33"), mt5.events)
        assertFalse(result.collapseFailed)
    }

    // ============================================================================================
    // Fallback: the hidden fields cannot be read, so the sheet is expanded, used, and put back
    // ============================================================================================

    private fun noHiddenFields(
        lossPerLot: List<String> = listOf("149"),
        slPrice: BigDecimal = BigDecimal("209.932"),
    ) = FakeMt5(hiddenReadable = false, lossPerLot = lossPerLot, slPrice = slPrice)

    @Test
    fun fallbackExpandsWritesAndCollapses() = runTest {
        val mt5 = noHiddenFields()
        val result = runner(mt5).pass()

        val outcome = result.outcome as PassOutcome.Success
        assertEquals("0.33", outcome.volumeText)
        assertEquals(listOf("expand", "write:0.33", "collapse"), mt5.events)
        assertFalse(mt5.expanded)
        assertFalse(result.collapseFailed)
    }

    @Test
    fun fallbackCorrectsDriftAndStillCollapses() = runTest {
        val mt5 = noHiddenFields(lossPerLot = listOf("149", "158.4", "158"))
        val result = runner(mt5).pass()

        assertEquals("0.31", (result.outcome as PassOutcome.Success).volumeText)
        assertEquals(listOf("expand", "write:0.33", "write:0.31", "collapse"), mt5.events)
    }

    @Test
    fun fallbackRefusalAfterExpandingStillCollapses() = runTest {
        val mt5 = noHiddenFields(slPrice = BigDecimal.ZERO)
        val result = runner(mt5).pass()

        assertEquals(PassOutcome.Refused(Refusal.NoStopLoss), result.outcome)
        assertEquals(listOf("expand", "collapse"), mt5.events)
        assertFalse(mt5.expanded)
    }

    @Test
    fun fallbackExhaustionStillCollapses() = runTest {
        val mt5 = noHiddenFields(lossPerLot = listOf("149", "170", "175", "180"))
        val result = runner(mt5).pass()

        assertTrue(result.outcome is PassOutcome.Exhausted)
        assertEquals("collapse", mt5.events.last())
        assertFalse(mt5.expanded)
    }

    @Test
    fun fallbackSheetThatWillNotOpenIsReportedAndRestoreIsAttempted() = runTest {
        val mt5 = noHiddenFields().apply { expandWorks = false }
        val result = runner(mt5).pass()

        assertEquals(PassOutcome.Failed("Couldn't open the order ticket"), result.outcome)
        assertTrue(mt5.writes.isEmpty())
        assertTrue("collapse is still attempted", "collapse" in mt5.events)
    }

    @Test
    fun fallbackSheetThatWillNotCollapseIsFlagged() = runTest {
        val mt5 = noHiddenFields().apply { collapseWorks = false }
        val result = runner(mt5).pass()

        assertTrue(result.outcome is PassOutcome.Success)
        assertTrue(result.collapseFailed)
    }

    @Test
    fun fallbackHangingWriteTimesOutAndStillCollapses() = runTest {
        val mt5 = noHiddenFields().apply { onSetVolume = { delay(Long.MAX_VALUE) } }
        val result = runner(mt5, timeoutMillis = 1_000).pass()

        assertTrue((result.outcome as PassOutcome.Failed).reason.startsWith("Timed out"))
        assertEquals("collapse", mt5.events.last())
        assertFalse(mt5.expanded)
    }

    @Test
    fun fallbackCancelledMidWayStillCollapsesTheSheet() = runTest {
        val mt5 = noHiddenFields().apply { onSetVolume = { delay(Long.MAX_VALUE) } }
        val job = launch { runner(mt5).runIfIdle(one) }
        runCurrent()
        assertTrue(mt5.expanded)

        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertEquals("collapse", mt5.events.last())
        assertFalse(mt5.expanded)
    }

    @Test
    fun directModeCancelledMidWayHasNothingToRestore() = runTest {
        val mt5 = FakeMt5().apply { onSetVolume = { delay(Long.MAX_VALUE) } }
        val job = launch { runner(mt5).runIfIdle(one) }
        runCurrent()

        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertEquals(listOf("write:0.33"), mt5.events)
    }

    // ============================================================================================
    // One pass at a time
    // ============================================================================================

    @Test
    fun aSecondRequestWhileOneIsRunningIsIgnored() = runTest {
        val gate = CompletableDeferred<Unit>()
        val mt5 = FakeMt5().apply { onSetVolume = { gate.await() } }
        val runner = runner(mt5)

        val first = async { runner.runIfIdle(one) }
        runCurrent()
        assertTrue(runner.isRunning)
        assertNull("second tap does nothing", runner.runIfIdle(one))

        gate.complete(Unit)
        val firstResult = first.await()
        assertNotNull(firstResult)
        assertTrue(firstResult!!.outcome is PassOutcome.Success)
        assertFalse(runner.isRunning)
        assertEquals(1, mt5.events.count { it.startsWith("write:") })
    }

    @Test
    fun aNewPassCanRunOnceTheLastOneFinished() = runTest {
        val mt5 = FakeMt5()
        val runner = runner(mt5)
        assertNotNull(runner.runIfIdle(one))
        assertNotNull(runner.runIfIdle(one))
        assertEquals(2, mt5.events.count { it.startsWith("write:") })
    }

    // ============================================================================================
    // The guarantee that matters most
    // ============================================================================================

    @Test
    fun noPassEverDoesAnythingExceptExpandWriteAndCollapse() = runTest {
        val scenarios = listOf(
            FakeMt5(),
            FakeMt5(lossPerLot = listOf("149", "170", "175", "180")),
            FakeMt5(slPrice = BigDecimal.ZERO),
            FakeMt5(realStep = d("0.1")),
            FakeMt5(lossPerLot = listOf("10000")),
            FakeMt5(volumeUnit = "Amount"),
            noHiddenFields(),
            noHiddenFields(slPrice = BigDecimal.ZERO),
            FakeMt5(hiddenReadable = false, expandWorks = false),
        )
        for (mt5 in scenarios) {
            runner(mt5).runIfIdle(one)
            assertTrue(
                "unexpected action in ${mt5.events}",
                mt5.events.all { it == "expand" || it == "collapse" || it.startsWith("write:") },
            )
        }
    }

    @Test
    fun theNormalPathNeverExpandsOrCollapses() = runTest {
        val scenarios = listOf(
            FakeMt5(),
            FakeMt5(lossPerLot = listOf("149", "158.4", "158")),
            FakeMt5(lossPerLot = listOf("149", "170", "175", "180")),
            FakeMt5(slPrice = BigDecimal.ZERO),
            FakeMt5(realStep = d("0.1")),
        )
        for (mt5 in scenarios) {
            runner(mt5).runIfIdle(one)
            assertTrue("expand/collapse in ${mt5.events}", mt5.events.none { it == "expand" || it == "collapse" })
        }
    }
}
