package com.ferik.mt5risk.mt5

/** A straight downward stroke to dispatch as a swipe. */
data class GesturePath(val start: Point, val end: Point, val durationMillis: Long = 300)

/**
 * Plans the swipe that drags the expanded order sheet back down to its collapsed strip. Geometry
 * comes from node bounds, so it adapts to the screen, and the path is vetoed (null) whenever it
 * would touch an order-submission control.
 */
object CollapseGesturePlanner {

    /** The sheet's drag handle sits about this far above the symbol row. */
    const val HANDLE_OFFSET_PX = 60

    /** Distance to travel when nothing is in the way. */
    const val SWIPE_LENGTH_PX = 1100

    /** A shorter stroke is too small to count as a drag. */
    const val MIN_SWIPE_PX = 400

    /** Stay this far above the first order control below the start. */
    const val CLEARANCE_PX = 60

    fun plan(root: UiNode): GesturePath? {
        val nodes = root.walk().toList()
        val header = nodes.firstOrNull { it.shortId == Mt5Ids.SYMBOL_SELECTOR } ?: return null
        val screenWidth = nodes.maxOfOrNull { it.bounds.right } ?: return null
        if (screenWidth <= 0) return null

        val startY = header.bounds.top - HANDLE_OFFSET_PX
        if (startY <= 0) return null
        val startX = screenWidth / 2

        val firstControlBelow = OrderControlGuard.forbiddenBounds(root)
            .map { it.top }
            .filter { it > startY }
            .minOrNull()

        var endY = startY + SWIPE_LENGTH_PX
        if (firstControlBelow != null) endY = minOf(endY, firstControlBelow - CLEARANCE_PX)
        if (endY - startY < MIN_SWIPE_PX) return null

        val path = GesturePath(Point(startX, startY), Point(startX, endY))
        return if (OrderControlGuard.pathCrossesForbidden(root, path.start, path.end)) null else path
    }
}
