package com.ferik.mt5risk.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.ferik.mt5risk.AppGraph
import com.ferik.mt5risk.mt5.Mt5Ids
import com.ferik.mt5risk.mt5.TicketDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reads and drives the MetaTrader 5 order ticket. Scoped to the MT5 package by
 * accessibility_service_config.xml, so no other app's content is ever delivered here.
 */
class RiskAccessibilityService : AccessibilityService(), AppGraph.AccessibilityHandle {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticketDriver: AccessibilityTicketDriver? = null
    private var lastBalanceCheck = 0L

    override val driver: TicketDriver
        get() = checkNotNull(ticketDriver) { "accessibility service is not connected" }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val connected = AccessibilityTicketDriver(this)
        ticketDriver = connected
        AppGraph.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != Mt5Ids.PACKAGE) return
        captureBalance()
    }

    /** Records the Trade tab's Balance row whenever it is on screen; cheap and throttled. */
    private fun captureBalance() {
        val now = SystemClock.uptimeMillis()
        if (now - lastBalanceCheck < BALANCE_CHECK_INTERVAL_MS) return
        lastBalanceCheck = now

        val root = rootInActiveWindow ?: return
        val row = root.findAccessibilityNodeInfosByViewId("${Mt5Ids.PACKAGE}:id/${Mt5Ids.BALANCE}")
            .firstOrNull { it.isVisibleToUser } ?: return
        val text = row.text?.toString()
        scope.launch { AppGraph.balanceWatcher.onBalanceText(text, AppGraph.lastCurrency) }
    }

    /** True while the topmost app window belongs to MetaTrader 5, even if our overlay has focus. */
    override fun isMt5Foreground(): Boolean {
        val topApp = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION } ?: return false
        return topApp.root?.packageName == Mt5Ids.PACKAGE
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AppGraph.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AppGraph.detach(this)
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val BALANCE_CHECK_INTERVAL_MS = 700L
    }
}
