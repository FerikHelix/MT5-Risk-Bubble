package com.ferik.mt5risk.mt5

/**
 * What a lot-sizing pass may do to the MetaTrader 5 ticket. The runtime implementation drives the
 * accessibility service; tests use a fake that behaves like MT5. By construction there is no way
 * to ask for Buy, Sell or Place: those controls are not part of this interface.
 */
interface TicketDriver {

    /** Read whatever MT5 currently shows. Never changes anything. */
    suspend fun read(): TicketReading

    /** Expand the collapsed sheet and wait (bounded) until the ticket fields appear. */
    suspend fun expand(): Boolean

    /** Collapse the expanded sheet and wait (bounded) until the strip is back. True if it ended collapsed. */
    suspend fun collapse(): Boolean

    /** Put [text] into the Volume field. True if the action was accepted; the value is checked by a later read. */
    suspend fun setVolume(text: String): Boolean
}
