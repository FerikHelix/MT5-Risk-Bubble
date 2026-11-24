package com.ferik.mt5risk.ui

import java.math.BigDecimal

/**
 * Decides when a stop-loss move is worth a pass. Pure: the caller feeds it the value it just read
 * and the clock, so the debounce can be tested without sleeping.
 *
 * It seeds a baseline on the first read and whenever the symbol changes, so merely opening the
 * ticket never fires. A changed value is remembered, and the move only fires once the value has
 * stayed put for [debounceMillis], which collapses a drag into a single pass.
 */
class SlChangeDetector(
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val epsilon: BigDecimal = DEFAULT_EPSILON,
) {

    private var symbol: String? = null
    private var slPrice: BigDecimal? = null
    private var lastChangeAt: Long? = null
    private var pending = false

    /** Record the value now shown, without ever firing for it. */
    fun seed(symbol: String?, slPrice: BigDecimal?) {
        this.symbol = symbol
        this.slPrice = slPrice
        lastChangeAt = null
        pending = false
    }

    /** Feed one read. Returns true exactly once per settled move. */
    fun observe(symbol: String?, slPrice: BigDecimal?, nowMillis: Long): Boolean {
        if (symbol == null || slPrice == null || slPrice.signum() == 0) {
            seed(symbol, slPrice)
            return false
        }
        if (this.symbol != symbol) {
            seed(symbol, slPrice)
            return false
        }
        val baseline = this.slPrice
        if (baseline == null) {
            seed(symbol, slPrice)
            return false
        }

        if (slPrice.subtract(baseline).abs() > epsilon) {
            this.slPrice = slPrice
            lastChangeAt = nowMillis
            pending = true
            return false
        }

        val changedAt = lastChangeAt
        if (pending && changedAt != null && nowMillis - changedAt >= debounceMillis) {
            pending = false
            lastChangeAt = null
            return true
        }
        return false
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 700L
        private val DEFAULT_EPSILON = BigDecimal("0.0000001")
    }
}
