package com.ferik.mt5risk.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.ferik.mt5risk.mt5.AccessibilityUiNode
import com.ferik.mt5risk.mt5.CollapseGesturePlanner
import com.ferik.mt5risk.mt5.GesturePath
import com.ferik.mt5risk.mt5.Mt5Ids
import com.ferik.mt5risk.mt5.OrderControlGuard
import com.ferik.mt5risk.mt5.TicketDriver
import com.ferik.mt5risk.mt5.TicketReader
import com.ferik.mt5risk.mt5.TicketReading
import com.ferik.mt5risk.mt5.findFirst
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives the MetaTrader 5 ticket through the accessibility service. The only controls it ever acts
 * on are the expand arrow and the Volume field, plus one downward swipe; every target is checked
 * against [OrderControlGuard] first.
 */
class AccessibilityTicketDriver(
    private val service: AccessibilityService,
    private val pollMillis: Long = 100,
    private val waitMillis: Long = 2_500,
) : TicketDriver {

    /** Root of the MT5 window, whether or not it is the active one, or null when MT5 is not on screen. */
    fun mt5Root(): AccessibilityUiNode? {
        val active = service.rootInActiveWindow
        if (active?.packageName == Mt5Ids.PACKAGE) return AccessibilityUiNode(active)
        for (window in service.windows) {
            val root = window.root ?: continue
            if (root.packageName == Mt5Ids.PACKAGE) return AccessibilityUiNode(root)
        }
        return null
    }

    override suspend fun read(): TicketReading = mt5Root()?.let(TicketReader::read) ?: TicketReading.NotTicket

    override suspend fun expand(): Boolean {
        if (read() is TicketReading.Expanded) return true
        val root = mt5Root() ?: return false
        val arrow = root.findFirst(Mt5Ids.BTN_SUBMIT) as? AccessibilityUiNode ?: return false
        if (OrderControlGuard.isForbiddenTarget(root, arrow)) return false
        if (!arrow.info.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return false
        return waitUntil { read() is TicketReading.Expanded }
    }

    override suspend fun collapse(): Boolean {
        val current = read()
        if (current is TicketReading.Collapsed) return true
        if (current !is TicketReading.Expanded) return false
        val root = mt5Root() ?: return false
        val path = CollapseGesturePlanner.plan(root) ?: return false
        if (!swipe(path)) return false
        return waitUntil { read() is TicketReading.Collapsed }
    }

    override suspend fun setVolume(text: String): Boolean = setField(Mt5Ids.VOLUME_INPUT, text)

    private suspend fun setField(id: String, text: String): Boolean {
        val root = mt5Root() ?: return false
        val field = root.findFirst(id) as? AccessibilityUiNode ?: return false
        if (OrderControlGuard.isForbiddenTarget(root, field)) return false
        val accepted = setText(field.info, text)
        delay(SETTLE_MILLIS) // let MT5 recompute margin, loss and profit before the next read
        return accepted
    }

    private fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private suspend fun swipe(path: GesturePath): Boolean = suspendCancellableCoroutine { continuation ->
        val stroke = Path().apply {
            moveTo(path.start.x.toFloat(), path.start.y.toFloat())
            lineTo(path.end.x.toFloat(), path.end.y.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(stroke, 0, path.durationMillis))
            .build()
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (continuation.isActive) continuation.resume(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (continuation.isActive) continuation.resume(false)
            }
        }
        if (!service.dispatchGesture(gesture, callback, null) && continuation.isActive) continuation.resume(false)
    }

    private suspend fun waitUntil(condition: suspend () -> Boolean): Boolean =
        withTimeoutOrNull(waitMillis) {
            while (!condition()) delay(pollMillis)
            true
        } ?: false

    private companion object {
        const val SETTLE_MILLIS = 400L
    }
}
