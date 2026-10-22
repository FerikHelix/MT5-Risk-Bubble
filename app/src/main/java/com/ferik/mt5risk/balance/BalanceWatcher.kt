package com.ferik.mt5risk.balance

import com.ferik.mt5risk.core.AmountParser
import com.ferik.mt5risk.mt5.Mt5Ids
import com.ferik.mt5risk.mt5.UiNode
import com.ferik.mt5risk.mt5.shortId
import com.ferik.mt5risk.mt5.walk
import java.math.BigDecimal

/** Reads the Balance row from the MetaTrader 5 Trade tab. Pure: it reads, never acts. */
object TradeTabReader {

    /** The balance shown, or null when the Trade tab is not on screen or the row cannot be read. */
    fun readBalance(root: UiNode): BigDecimal? {
        val node = root.walk()
            .firstOrNull { it.shortId == Mt5Ids.BALANCE && it.isVisible } ?: return null
        if (node.packageName != null && node.packageName != Mt5Ids.PACKAGE) return null
        return parseBalance(node.text)
    }

    /** Parses a Balance row's text such as `5 000.00`; null unless it is a positive amount. */
    fun parseBalance(text: String?): BigDecimal? =
        AmountParser.parse(text)?.value?.takeIf { it.signum() > 0 }
}

/** Captures the balance into the [BalanceStore] whenever the Trade tab is visible. */
class BalanceWatcher(private val store: BalanceStore) {

    /**
     * Called on MetaTrader 5 window changes. Records a readable balance; an unreadable or missing
     * row leaves the previously saved balance untouched.
     *
     * @param currencyHint account currency learned from the ticket, since the Trade tab row has none
     * @return true when a balance was recorded
     */
    suspend fun onWindowChanged(root: UiNode, currencyHint: String? = null): Boolean {
        val balance = TradeTabReader.readBalance(root) ?: return false
        store.recordFromTradeTab(balance, currencyHint)
        return true
    }

    /**
     * Records the text of a visible Trade tab Balance row, for callers that already located the
     * node and do not want to walk the whole tree on every MT5 event.
     */
    suspend fun onBalanceText(text: String?, currencyHint: String? = null): Boolean {
        val balance = TradeTabReader.parseBalance(text) ?: return false
        store.recordFromTradeTab(balance, currencyHint)
        return true
    }
}
