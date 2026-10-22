package com.ferik.mt5risk.mt5

/**
 * Deny-list that keeps the app away from MetaTrader 5's order-submission and one-click controls.
 * Every click or gesture the driver is about to perform is checked here first. The guard never
 * allows anything extra; it only vetoes.
 */
object OrderControlGuard {

    /** Extra pixels around each control, because Buy and Sell sit only a few pixels apart. */
    const val SAFETY_MARGIN_PX = 24

    /**
     * Bounds of every forbidden control that occupies screen space, without the safety margin.
     * Off-screen controls report empty or inverted rectangles and cannot be touched, so they are skipped.
     */
    fun forbiddenBounds(root: UiNode): List<Bounds> =
        root.walk()
            .filter { it.shortId in Mt5Ids.FORBIDDEN_CONTROLS }
            .map { it.bounds }
            .filter { it.right > it.left && it.bottom > it.top }
            .toList()

    /** True when [target] is a forbidden control or sits inside one, so it must not be clicked or set. */
    fun isForbiddenTarget(root: UiNode, target: UiNode): Boolean {
        if (target.shortId in Mt5Ids.FORBIDDEN_CONTROLS) return true
        return forbiddenBounds(root).any { it.containsRect(target.bounds) }
    }

    /** True when the straight stroke from [start] to [end] touches a forbidden control or its margin. */
    fun pathCrossesForbidden(root: UiNode, start: Point, end: Point): Boolean =
        forbiddenBounds(root).any { segmentIntersects(start, end, it.inflate(SAFETY_MARGIN_PX)) }

    /** Liang-Barsky clipping of a segment against a rectangle. */
    private fun segmentIntersects(start: Point, end: Point, rect: Bounds): Boolean {
        val dx = (end.x - start.x).toDouble()
        val dy = (end.y - start.y).toDouble()
        var t0 = 0.0
        var t1 = 1.0

        fun clip(p: Double, q: Double): Boolean {
            if (p == 0.0) return q >= 0.0
            val r = q / p
            if (p < 0.0) {
                if (r > t1) return false
                if (r > t0) t0 = r
            } else {
                if (r < t0) return false
                if (r < t1) t1 = r
            }
            return true
        }

        return clip(-dx, (start.x - rect.left).toDouble()) &&
            clip(dx, (rect.right - start.x).toDouble()) &&
            clip(-dy, (start.y - rect.top).toDouble()) &&
            clip(dy, (rect.bottom - start.y).toDouble())
    }
}
