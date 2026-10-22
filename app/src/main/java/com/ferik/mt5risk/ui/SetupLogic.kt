package com.ferik.mt5risk.ui

/** How a row of the setup checklist stands. */
enum class SetupStatus { READY, MISSING, CHECK_MANUALLY }

/** One permission or setting the bubble depends on on the target phone. */
enum class SetupItem(val title: String, val why: String, val required: Boolean) {
    OVERLAY(
        "Draw over other apps",
        "Lets the bubble float above MetaTrader 5.",
        required = true,
    ),
    ACCESSIBILITY(
        "Accessibility service",
        "Lets the app read MetaTrader 5's order ticket and set its Volume field. On a sideloaded app, first open " +
            "App info, tap the menu and choose Allow restricted settings.",
        required = true,
    ),
    NOTIFICATIONS(
        "Notifications",
        "Android keeps the bubble running only with a visible notification, which also holds the Stop button.",
        required = false,
    ),
    POPUPS(
        "Pop-up windows while running in the background (Xiaomi)",
        "Xiaomi phones block overlays from the background unless this is allowed in the app's permissions.",
        required = false,
    ),
    AUTOSTART(
        "Autostart (Xiaomi)",
        "Stops the phone from killing the bubble when the screen is off.",
        required = false,
    ),
    BATTERY(
        "Battery: no restrictions",
        "Keeps the phone's battery saver from stopping the bubble.",
        required = false,
    ),
}

object SetupLogic {

    /** The bubble may only run passes once the overlay and the accessibility service are both on. */
    fun canStart(statuses: Map<SetupItem, SetupStatus>): Boolean =
        SetupItem.entries.filter { it.required }.all { statuses[it] == SetupStatus.READY }

    /** The required items that still need attention, in checklist order. */
    fun missingRequired(statuses: Map<SetupItem, SetupStatus>): List<SetupItem> =
        SetupItem.entries.filter { it.required && statuses[it] != SetupStatus.READY }

    /** What the bubble button should say, given what is missing and whether it already runs. */
    fun startButtonLabel(statuses: Map<SetupItem, SetupStatus>, running: Boolean): String = when {
        running -> "Stop the bubble"
        canStart(statuses) -> "Start the bubble"
        else -> "Finish the required steps first"
    }

    /** Statement shown before the accessibility shortcut, so the user knows exactly what they allow. */
    const val DISCLOSURE: String =
        "MT5 Risk Bubble reads only the MetaTrader 5 screen. It never places, changes or closes an order and " +
            "never presses Buy, Sell or Place. The only thing it ever changes is the Volume field of the ticket, " +
            "when you tap the bubble and choose a risk. It does not use the internet."
}
