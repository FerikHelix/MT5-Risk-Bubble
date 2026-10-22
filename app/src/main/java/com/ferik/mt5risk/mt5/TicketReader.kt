package com.ferik.mt5risk.mt5

import com.ferik.mt5risk.core.Amount
import com.ferik.mt5risk.core.AmountParser
import java.math.BigDecimal

/** Values read from MetaTrader 5's order ticket, whether its sheet is expanded or collapsed. */
data class TicketSnapshot(
    val symbol: String,
    val volume: BigDecimal,
    /** Text of the volume unit selector, for example `Lots`. */
    val volumeUnit: String?,
    /** Zero when no stop-loss is set. */
    val slPrice: BigDecimal,
    /** The red loss label as MT5 prints it, negative for a real loss; null when the label is absent. */
    val slLoss: Amount?,
    val freeMargin: Amount,
    val margin: Amount?,
    val bid: BigDecimal?,
    val ask: BigDecimal?,
    /** True for Buy/Sell Limit and Stop tickets, which end in a single Place button. */
    val isPending: Boolean,
) {
    /** Account currency shown next to the money values, when MT5 prints one. */
    val currency: String? get() = slLoss?.currency ?: freeMargin.currency ?: margin?.currency
}

sealed interface TicketReading {
    /** Neither the collapsed strip nor the expanded ticket is on screen. */
    data object NotTicket : TicketReading

    /**
     * Only the collapsed strip is visible. MetaTrader 5 still keeps the whole ticket in the tree,
     * off screen and live, so [hidden] holds its values when they can be read; otherwise
     * [hiddenProblem] names the field that blocked it and the pass must expand the sheet instead.
     */
    data class Collapsed(
        val symbol: String?,
        val hidden: TicketSnapshot? = null,
        val hiddenProblem: String? = null,
    ) : TicketReading

    data class Expanded(val ticket: TicketSnapshot) : TicketReading

    /** The ticket is on screen but a required field was missing or could not be parsed. */
    data class Unreadable(val field: String) : TicketReading
}

/** The ticket values, whichever way they were read: expanded in place or hidden behind the strip. */
fun TicketReading.snapshotOrNull(): TicketSnapshot? = when (this) {
    is TicketReading.Expanded -> ticket
    is TicketReading.Collapsed -> hidden
    else -> null
}

/** Turns an MT5 accessibility tree into a [TicketReading]. Pure: it reads, never acts. */
object TicketReader {

    fun read(root: UiNode): TicketReading {
        val all = root.walk().filter { it.packageName == null || it.packageName == Mt5Ids.PACKAGE }.toList()
        // The ticket state comes from what the user can see: the collapsed sheet's fields are still
        // in the tree, off screen, so their presence alone proves nothing.
        val visible = all.filter { it.isVisible }

        if (visible.any { it.shortId == Mt5Ids.VOLUME_INPUT }) {
            return when (val parsed = parse(visible)) {
                is Parsed.Snapshot -> TicketReading.Expanded(parsed.ticket)
                is Parsed.Missing -> TicketReading.Unreadable(parsed.field)
            }
        }

        if (visible.any { it.shortId == Mt5Ids.BTN_SUBMIT }) {
            val symbol = visible.firstOrNull { it.shortId == Mt5Ids.SYMBOL_NAME }?.text?.trim()?.ifEmpty { null }
            return when (val parsed = parse(all)) {
                is Parsed.Snapshot -> TicketReading.Collapsed(symbol, hidden = parsed.ticket)
                is Parsed.Missing -> TicketReading.Collapsed(symbol, hiddenProblem = parsed.field)
            }
        }

        return TicketReading.NotTicket
    }

    private sealed interface Parsed {
        data class Snapshot(val ticket: TicketSnapshot) : Parsed
        data class Missing(val field: String) : Parsed
    }

    private fun parse(nodes: List<UiNode>): Parsed {
        fun first(id: String) = nodes.firstOrNull { it.shortId == id }

        val volumeNode = first(Mt5Ids.VOLUME_INPUT) ?: return Parsed.Missing(Mt5Ids.VOLUME_INPUT)

        val symbol = first(Mt5Ids.SYMBOL_NAME)?.text?.trim()?.ifEmpty { null }
            ?: return Parsed.Missing(Mt5Ids.SYMBOL_NAME)

        val volume = AmountParser.parse(volumeNode.text)?.value ?: return Parsed.Missing(Mt5Ids.VOLUME_INPUT)

        val freeMargin = AmountParser.parse(first(Mt5Ids.FREE_MARGIN_VALUE)?.text)
            ?: return Parsed.Missing(Mt5Ids.FREE_MARGIN_VALUE)

        val slPrice = readStopLossPrice(::first) ?: return Parsed.Missing(Mt5Ids.SL_INPUT)

        val lossNode = first(Mt5Ids.SL_CALC_LOSS)
        val slLoss = lossNode?.let {
            AmountParser.parse(it.text) ?: return Parsed.Missing(Mt5Ids.SL_CALC_LOSS)
        }

        return Parsed.Snapshot(
            TicketSnapshot(
                symbol = symbol,
                volume = volume,
                volumeUnit = first(Mt5Ids.VOLUME_MODE_TEXT)?.text?.trim()?.ifEmpty { null },
                slPrice = slPrice,
                slLoss = slLoss,
                freeMargin = freeMargin,
                margin = AmountParser.parse(first(Mt5Ids.MARGIN_VALUE)?.text),
                bid = AmountParser.parse(first(Mt5Ids.BID_PRICE)?.text)?.value,
                ask = AmountParser.parse(first(Mt5Ids.ASK_PRICE)?.text)?.value,
                isPending = first(Mt5Ids.PLACE_BTN) != null,
            ),
        )
    }

    /** `sl_input` text, or zero while MT5 only offers the "Add Stop Levels" button. */
    private fun readStopLossPrice(first: (String) -> UiNode?): BigDecimal? {
        val input = first(Mt5Ids.SL_INPUT)
        if (input != null) return AmountParser.parse(input.text)?.value
        return if (first(Mt5Ids.ADD_STOPS_BTN) != null) BigDecimal.ZERO else null
    }
}
