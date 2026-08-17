package fi.palonkorpi.sideretro.ui

/**
 * Optical portrait placement for the dense Keytile legends (Compact QWERTY and T9).
 *
 * Those legends live in the lower-right corner, while the SideRetro mark moves to lower-left.
 * The game therefore keeps its selected size and can use more of the upper glass than the normal
 * faceplate composition.  It must not, however, look pinned to the SP-01's rounded bezel.  Start
 * from the ordinary faceplate's optical placement, retain enough lower panel for a readable map
 * when possible, then enforce a visual top safe zone.  If a tall picture cannot satisfy both
 * requirements, the legend (not the game) is allowed to adapt down.
 */
internal object PortraitGamePlacement {
    fun denseLegendTop(
        rootHeightPx: Int,
        pictureHeightPx: Float,
        density: Float,
        opticalTopFraction: Float,
        readableLegendDp: Int,
        legendEdgeDp: Int,
        legendGapDp: Int,
        visualTopFloorDp: Int,
    ): Float {
        val leftover = (rootHeightPx - pictureHeightPx).coerceAtLeast(0f)
        if (leftover == 0f) return 0f

        val normalOpticalTop = leftover * opticalTopFraction
        val readableLowerPanel = (readableLegendDp + legendEdgeDp + legendGapDp) * density
        // This is a cap rather than a target: visual balance wins when there is more lower space
        // than the legend needs, while a wide/tall picture may borrow just enough headroom.
        val topThatKeepsReadableLegend = leftover - readableLowerPanel
        val visualFloor = (visualTopFloorDp * density).coerceAtMost(leftover)

        return minOf(normalOpticalTop, topThatKeepsReadableLegend)
            .coerceAtLeast(visualFloor)
            .coerceAtMost(leftover)
    }
}
