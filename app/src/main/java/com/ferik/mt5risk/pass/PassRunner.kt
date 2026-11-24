package com.ferik.mt5risk.pass

import com.ferik.mt5risk.core.BasisSelection
import com.ferik.mt5risk.core.Preconditions
import com.ferik.mt5risk.core.Refusal
import com.ferik.mt5risk.core.RiskBasis
import com.ferik.mt5risk.core.RiskEngine
import com.ferik.mt5risk.core.RiskSetting
import com.ferik.mt5risk.core.SizingRequest
import com.ferik.mt5risk.core.VerifyDecision
import com.ferik.mt5risk.core.VerifyLoop
import com.ferik.mt5risk.core.VolumeRule
import com.ferik.mt5risk.mt5.Mt5Ids
import com.ferik.mt5risk.mt5.TicketDriver
import com.ferik.mt5risk.mt5.TicketReading
import com.ferik.mt5risk.mt5.TicketSnapshot
import com.ferik.mt5risk.mt5.snapshotOrNull
import java.math.BigDecimal
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Runs one lot-sizing pass: read the symbol from the strip, read the ticket fields where they are
 * (direct mode, the sheet never moves), check preconditions, compute, write, and verify against
 * MT5's own loss. Only when the hidden fields cannot be read does it expand the sheet, and then it
 * always puts it back as it was found. Only one pass runs at a time; a second request while one is
 * running is ignored.
 *
 * @param balance the balance in effect (captured or manual), null when none exists
 * @param ruleFor the saved volume rule for an exact symbol name, null when the symbol is unknown
 */
class PassRunner(
    private val driver: TicketDriver,
    private val balance: suspend () -> BigDecimal?,
    private val ruleFor: suspend (String) -> VolumeRule?,
    private val basis: suspend () -> BasisSelection = { BasisSelection() },
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    private val mutex = Mutex()

    val isRunning: Boolean get() = mutex.isLocked

    /** Runs a pass, or returns null without doing anything when one is already running. */
    suspend fun runIfIdle(risk: RiskSetting): PassResult? {
        if (!mutex.tryLock()) return null
        try {
            return execute(risk)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun execute(risk: RiskSetting): PassResult {
        var expandedBySystem = false
        val outcome = try {
            withTimeout(timeoutMillis) { runPass(risk) { expandedBySystem = true } }
        } catch (timeout: TimeoutCancellationException) {
            PassOutcome.Failed("Timed out waiting for the order ticket")
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            if (expandedBySystem) collapseQuietly()
            throw cancelled
        } catch (error: Exception) {
            PassOutcome.Failed("Unexpected error: ${error.message ?: error::class.simpleName}")
        }
        val collapseFailed = expandedBySystem && !collapseQuietly()
        return PassResult(outcome, collapseFailed)
    }

    private suspend fun collapseQuietly(): Boolean = withContext(NonCancellable) {
        runCatching { driver.collapse() }.getOrDefault(false)
    }

    private suspend fun runPass(risk: RiskSetting, onExpanding: () -> Unit): PassOutcome {
        val first = driver.read()
        val symbol = when (first) {
            TicketReading.NotTicket -> return PassOutcome.Refused(Refusal.TicketNotOpen)
            is TicketReading.Unreadable -> return PassOutcome.Refused(Refusal.Unreadable(first.field))
            is TicketReading.Collapsed -> first.symbol
            is TicketReading.Expanded -> first.ticket.symbol
        }

        // Symbol, rule and risk are known without opening the sheet, so refuse before touching it.
        val rule = symbol?.let { ruleFor(it) }
        Preconditions.beforeExpand(risk, symbol, rule)?.let { return PassOutcome.Refused(it) }
        val knownSymbol = symbol!!
        val knownRule = rule!!

        // Direct mode: the ticket is read where it is, either expanded by the user or live behind the
        // collapsed strip, and the sheet never moves. Only when the hidden fields cannot be read is
        // the sheet expanded, and then it is always put back (see execute).
        var ticket: TicketSnapshot = first.snapshotOrNull() ?: run {
            onExpanding()
            if (!driver.expand()) return PassOutcome.Failed("Couldn't open the order ticket")
            val reading = driver.read()
            reading.snapshotOrNull() ?: return when (reading) {
                is TicketReading.Unreadable -> PassOutcome.Refused(Refusal.Unreadable(reading.field))
                else -> PassOutcome.Failed("Couldn't open the order ticket")
            }
        }

        Preconditions.volumeUnit(ticket.volumeUnit)?.let { return PassOutcome.Refused(it) }
        val savedBalance = balance()
        Preconditions.afterRead(
            balance = savedBalance,
            freeMargin = ticket.freeMargin.value,
            currentVolume = ticket.volume,
            slPrice = ticket.slPrice,
            slLoss = ticket.slLoss?.value,
        )?.let { return PassOutcome.Refused(it) }

        val accountBalance = savedBalance!!
        // The balance above stays a trust check; the budget's base may be the drawdown buffer instead.
        val selection = basis()
        val base = RiskBasis.resolve(accountBalance, selection.kind, selection.propFirm)
        if (base.isExhausted) return PassOutcome.Refused(Refusal.DrawdownExhausted)

        val sizing = RiskEngine.size(
            SizingRequest(
                balance = accountBalance,
                riskBudget = risk.budget(base.value),
                currentVolume = ticket.volume,
                slLoss = ticket.slLoss!!.value.abs(),
                rule = knownRule,
            ),
        )
        val budget = sizing.riskBudget
        var volume = sizing.volume
        var writes = 0

        while (true) {
            if (!driver.setVolume(knownRule.format(volume))) {
                return PassOutcome.Failed("Couldn't set the volume")
            }
            writes++

            val reading = driver.read()
            ticket = reading.snapshotOrNull() ?: return when (reading) {
                is TicketReading.Unreadable -> PassOutcome.Refused(Refusal.Unreadable(reading.field))
                is TicketReading.Collapsed ->
                    PassOutcome.Refused(Refusal.Unreadable(reading.hiddenProblem ?: Mt5Ids.VOLUME_INPUT))
                else -> PassOutcome.Failed("The order ticket closed while the volume was being set")
            }

            if (ticket.volume.compareTo(volume) != 0) {
                return PassOutcome.RuleMismatch(knownSymbol, knownRule, wrote = volume, shown = ticket.volume)
            }
            val loss = ticket.slLoss?.value ?: return PassOutcome.Refused(Refusal.Unreadable(Mt5Ids.SL_CALC_LOSS))
            if (loss.signum() >= 0) return PassOutcome.Refused(Refusal.WrongSideStopLoss)

            fun success(minLotExceedsRisk: Boolean) = PassOutcome.Success(
                symbol = knownSymbol,
                volume = volume,
                volumeText = knownRule.format(volume),
                riskSetting = risk,
                base = base.value,
                baseKind = base.kind,
                observedLoss = loss.abs(),
                currency = ticket.currency,
                clamp = sizing.clamp,
                minLotExceedsRisk = minLotExceedsRisk,
                writes = writes,
            )

            when (val decision = VerifyLoop.decide(writes, volume, loss.abs(), budget, knownRule)) {
                is VerifyDecision.Accept -> return success(minLotExceedsRisk = false)
                is VerifyDecision.MinLotOverBudget -> return success(minLotExceedsRisk = true)
                is VerifyDecision.Exhausted -> return PassOutcome.Exhausted(
                    volume = decision.volume,
                    observedLoss = decision.observedLoss,
                    budget = budget,
                    currency = ticket.currency,
                )
                is VerifyDecision.Retry -> volume = decision.volume
            }
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 20_000L
    }
}
