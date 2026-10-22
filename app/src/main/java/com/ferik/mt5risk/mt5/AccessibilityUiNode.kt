package com.ferik.mt5risk.mt5

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** [UiNode] over a live `AccessibilityNodeInfo`. Keeps the info so the driver can act on it. */
class AccessibilityUiNode(val info: AccessibilityNodeInfo) : UiNode {

    override val packageName: String? get() = info.packageName?.toString()
    override val viewId: String? get() = info.viewIdResourceName
    override val text: String? get() = info.text?.toString()
    override val className: String? get() = info.className?.toString()
    override val isClickable: Boolean get() = info.isClickable
    override val isEditable: Boolean get() = info.isEditable
    override val isVisible: Boolean get() = info.isVisibleToUser

    override val bounds: Bounds
        get() {
            val rect = Rect()
            info.getBoundsInScreen(rect)
            return Bounds(rect.left, rect.top, rect.right, rect.bottom)
        }

    override val children: List<UiNode> by lazy {
        (0 until info.childCount).mapNotNull { index -> info.getChild(index)?.let(::AccessibilityUiNode) }
    }
}
