package com.ferik.mt5risk.mt5

/**
 * The only place MetaTrader 5's view ids live (build 500.6260, recorded on the target device).
 * If an MT5 update renames one, this file is the single point of change.
 */
object Mt5Ids {
    const val PACKAGE = "net.metaquotes.metatrader5"

    // Collapsed strip and shared header
    const val SYMBOL_NAME = "symbol_name"
    const val BTN_SUBMIT = "btn_submit" // the arrow that expands the sheet
    const val SYMBOL_SELECTOR = "symbol_selector" // header row of the sheet, just under its drag handle

    // Expanded ticket
    const val VOLUME_INPUT = "volume_lots_input"
    const val VOLUME_MODE_TEXT = "volume_mode_text"
    const val SL_INPUT = "sl_input"
    const val TP_INPUT = "tp_input"
    const val SL_CALC_LOSS = "sl_calc_loss"
    const val TP_CALC_PROFIT = "tp_calc_profit"
    const val MARGIN_VALUE = "margin_value"
    const val FREE_MARGIN_VALUE = "free_margin_value"
    const val BID_PRICE = "bid_price"
    const val ASK_PRICE = "ask_price"
    const val ADD_STOPS_BTN = "add_stops_btn" // present only while no stop levels are set

    // Trade tab
    const val BALANCE = "balance"
    const val EQUITY = "equity"
    const val MARGIN_FREE = "margin_free"

    // Order-submission and one-click controls. The app must never act on or gesture across these.
    const val SELL_BTN = "sell_btn"
    const val BUY_BTN = "buy_btn"
    const val PLACE_BTN = "place_btn"
    const val SELL_BUTTON = "sell_button"
    const val BUY_BUTTON = "buy_button"
    const val BTN_CHART_QUICK_TRADE = "btn_chart_quick_trade"
    const val MENU_CHART_ONE_CLICK_ORDER = "menu_chart_one_click_order"
    const val TOOLBAR_ONE_CLICK_TRADE = "toolbar_one_click_trade"

    val FORBIDDEN_CONTROLS: Set<String> = setOf(
        SELL_BTN,
        BUY_BTN,
        PLACE_BTN,
        SELL_BUTTON,
        BUY_BUTTON,
        BTN_CHART_QUICK_TRADE,
        MENU_CHART_ONE_CLICK_ORDER,
        TOOLBAR_ONE_CLICK_TRADE,
    )
}
