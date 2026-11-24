package com.ferik.mt5risk.pass

import com.ferik.mt5risk.core.Amount
import com.ferik.mt5risk.mt5.Mt5Ids
import com.ferik.mt5risk.mt5.TicketDriver
import com.ferik.mt5risk.mt5.TicketReading
import com.ferik.mt5risk.mt5.TicketSnapshot
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A stand-in for the MetaTrader 5 ticket that behaves like the real one observed on the device:
 * the stop-loss loss is `lossPerLot * volume` and drifts with the price on every read, a written
 * volume may be normalised to the symbol's real step, and the sheet is either collapsed or expanded.
 *
 * While collapsed, MT5 keeps the ticket's fields live behind the strip, so a read returns them and a
 * write works without opening the sheet ([hiddenReadable] = true, direct mode). Set it to false to
 * model a build that stops doing that, which forces the expand-and-collapse fallback.
 *
 * [events] records everything the pass did, so tests can prove what was and was not touched.
 */
class FakeMt5(
    private val symbol: String = "GBPJPY",
    var expanded: Boolean = false,
    var volume: BigDecimal = BigDecimal("0.01"),
    var slPrice: BigDecimal = BigDecimal("209.932"),
    /** The prices MT5 shows for the ticket. */
    var bid: BigDecimal? = BigDecimal("210.000"),
    var ask: BigDecimal? = BigDecimal("210.050"),
    /** Loss for 1.00 lot at each successive ticket read; the last value repeats. */
    private val lossPerLot: List<String> = listOf("149"),
    private val currency: String? = "USD",
    private val freeMargin: BigDecimal = BigDecimal("5000.00"),
    var volumeUnit: String? = "Lots",
    /** When set, MT5 rounds a written volume down to this step, as if the saved rule were wrong. */
    private val realStep: BigDecimal? = null,
    /** True: the fields behind the collapsed strip are live and writable (direct mode). */
    var hiddenReadable: Boolean = true,
    var expandWorks: Boolean = true,
    var collapseWorks: Boolean = true,
    var setVolumeWorks: Boolean = true,
    var present: Boolean = true,
    var isPending: Boolean = false,
    /** Sign of the loss label: -1 for a normal loss, +1 for a stop-loss on the wrong side. */
    var lossSign: Int = -1,
) : TicketDriver {

    val events = mutableListOf<String>()

    var onExpand: suspend () -> Unit = {}
    var onSetVolume: suspend () -> Unit = {}

    private var reads = 0

    private fun snapshot(): TicketSnapshot {
        val perLot = BigDecimal(lossPerLot[minOf(reads, lossPerLot.lastIndex)])
        reads++
        val hasStop = slPrice.signum() != 0
        val loss = perLot.multiply(volume).setScale(2, RoundingMode.HALF_UP)
        return TicketSnapshot(
            symbol = symbol,
            volume = volume,
            volumeUnit = volumeUnit,
            slPrice = slPrice,
            slLoss = if (hasStop) Amount(loss.multiply(BigDecimal(lossSign)), currency) else null,
            freeMargin = Amount(freeMargin, currency),
            margin = null,
            bid = bid,
            ask = ask,
            isPending = isPending,
        )
    }

    override suspend fun read(): TicketReading {
        if (!present) return TicketReading.NotTicket
        if (expanded) return TicketReading.Expanded(snapshot())
        return if (hiddenReadable) {
            TicketReading.Collapsed(symbol, hidden = snapshot())
        } else {
            TicketReading.Collapsed(symbol, hiddenProblem = Mt5Ids.VOLUME_INPUT)
        }
    }

    override suspend fun expand(): Boolean {
        events += "expand"
        onExpand()
        if (expandWorks) expanded = true
        return expandWorks
    }

    override suspend fun collapse(): Boolean {
        events += "collapse"
        if (collapseWorks) expanded = false
        return collapseWorks
    }

    override suspend fun setVolume(text: String): Boolean {
        events += "write:$text"
        onSetVolume()
        val reachable = expanded || hiddenReadable
        if (!reachable || !setVolumeWorks) return false
        val requested = BigDecimal(text)
        volume = realStep?.let { step ->
            requested.divideToIntegralValue(step).multiply(step)
        } ?: requested
        return true
    }

    val writes: List<String> get() = events.filter { it.startsWith("write:") }
}
