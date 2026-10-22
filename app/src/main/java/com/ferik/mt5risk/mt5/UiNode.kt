package com.ferik.mt5risk.mt5

/** Screen rectangle in pixels, as MetaTrader 5 and uiautomator report it. */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    fun intersects(other: Bounds): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom

    /** True when [other] lies entirely inside this rectangle (edges included). */
    fun containsRect(other: Bounds): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

    fun inflate(by: Int): Bounds = Bounds(left - by, top - by, right + by, bottom + by)
}

data class Point(val x: Int, val y: Int)

/**
 * Read-only view of one node in a window's accessibility tree. The runtime adapter wraps
 * `AccessibilityNodeInfo`; tests wrap trees recorded from the device, so all MT5 reading logic
 * can be checked on the JVM.
 */
interface UiNode {
    val packageName: String?

    /** Full resource id such as `net.metaquotes.metatrader5:id/volume_lots_input`, if any. */
    val viewId: String?
    val text: String?
    val className: String?
    val isClickable: Boolean
    val isEditable: Boolean
    val bounds: Bounds
    val children: List<UiNode>

    /**
     * Whether the user can see this node. MetaTrader 5 keeps the whole expanded ticket in the
     * accessibility tree while the sheet is collapsed, off screen, so presence alone proves nothing.
     */
    val isVisible: Boolean get() = true
}

/** The node and everything below it, parents before children. */
fun UiNode.walk(): Sequence<UiNode> = sequence {
    yield(this@walk)
    for (child in children) yieldAll(child.walk())
}

/** Resource id without the package prefix, for example `volume_lots_input`. */
val UiNode.shortId: String? get() = viewId?.substringAfter(":id/", missingDelimiterValue = "")?.ifEmpty { null }

fun UiNode.findAll(shortId: String): List<UiNode> = walk().filter { it.shortId == shortId }.toList()

fun UiNode.findFirst(shortId: String): UiNode? = walk().firstOrNull { it.shortId == shortId }
