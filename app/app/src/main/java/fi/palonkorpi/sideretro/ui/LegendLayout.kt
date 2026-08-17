package fi.palonkorpi.sideretro.ui

/**
 * Places the controls reference in the part of the faceplate which is genuinely unoccupied.
 *
 * This is intentionally independent of Views.  A transformed GL surface is still a rectangular
 * picture on the window, and treating that rectangle as an exclusion zone keeps the reference
 * honest for every core and scaling mode.  In particular, the bottom-right corner is a preference,
 * never permission to cover a game.
 */
internal object LegendLayout {

    data class Rect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top

        fun intersects(other: Rect): Boolean =
            left < other.right && right > other.left && top < other.bottom && bottom > other.top

        fun expanded(by: Int, maxWidth: Int, maxHeight: Int): Rect = Rect(
            (left - by).coerceAtLeast(0),
            (top - by).coerceAtLeast(0),
            (right + by).coerceAtMost(maxWidth),
            (bottom + by).coerceAtMost(maxHeight),
        )
    }

    data class Placement(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val scale: Float,
    )

    /**
     * [minScale] is the comfortable-reading floor, not a visibility cliff.  We first honour it,
     * then continue down to the smallest possible reference before conceding that an edge-to-edge
     * picture has left no faceplate at all.  A tiny reference is still more useful than silently
     * losing the control map on a large portrait game.
     *
     * The safe insets describe parts of the window which may be covered by transient system UI.
     * They constrain the legend's *outer* edge without changing the picture exclusion zone.
     *
     * @return a placement which clears [picture] by [gap], or null only when there is genuinely
     *         nowhere outside the picture to put even a one-pixel reference.
     */
    fun place(
        rootWidth: Int,
        rootHeight: Int,
        picture: Rect,
        desiredWidth: Int,
        desiredHeight: Int,
        edge: Int,
        gap: Int,
        minScale: Float,
        safeLeft: Int = 0,
        safeTop: Int = 0,
        safeRight: Int = 0,
        safeBottom: Int = 0,
    ): Placement? {
        if (rootWidth <= 0 || rootHeight <= 0 || desiredWidth <= 0 || desiredHeight <= 0) return null
        val exclusion = picture.expanded(gap, rootWidth, rootHeight)
        val bounds = Bounds(
            left = maxOf(edge, safeLeft),
            top = maxOf(edge, safeTop),
            right = minOf(rootWidth - edge, rootWidth - safeRight),
            bottom = minOf(rootHeight - edge, rootHeight - safeBottom),
        )
        if (bounds.left >= bounds.right || bounds.top >= bounds.bottom) return null

        // Work one physical pixel at a time.  It happens only on layout changes, and avoids the
        // old 4dp quantisation where a 99px lower band hid a 100px legend rather than showing a
        // 99px one.  The first pass is the readable floor supplied by the caller.
        val readableWidth = kotlin.math.ceil(desiredWidth * minScale.coerceIn(0f, 1f)).toInt()
            .coerceIn(1, desiredWidth)
        placeAtEveryScale(
            fromWidth = desiredWidth,
            toWidth = readableWidth,
            rootWidth = rootWidth,
            rootHeight = rootHeight,
            desiredWidth = desiredWidth,
            desiredHeight = desiredHeight,
            bounds = bounds,
            exclusion = exclusion,
        )?.let { return it }

        // A faceplate band smaller than the readable floor still gets a map.  This matters most
        // for portrait NES/Genesis Large: the game is wide and leaves only a shallow lower band.
        placeAtEveryScale(
            fromWidth = readableWidth - 1,
            toWidth = 1,
            rootWidth = rootWidth,
            rootHeight = rootHeight,
            desiredWidth = desiredWidth,
            desiredHeight = desiredHeight,
            bounds = bounds,
            exclusion = exclusion,
        )?.let { return it }

        // A sliver thinner than the desired 6dp separation cannot satisfy the preferred gap.
        // Keep it external rather than hiding it, but only as this last-resort treatment.  A Fill
        // picture has no such external pixel and therefore still returns null.
        if (!covers(rootWidth, rootHeight, picture)) {
            val barePicture = picture.expanded(0, rootWidth, rootHeight)
            placeAtEveryScale(
                fromWidth = desiredWidth,
                toWidth = 1,
                rootWidth = rootWidth,
                rootHeight = rootHeight,
                desiredWidth = desiredWidth,
                desiredHeight = desiredHeight,
                bounds = bounds,
                exclusion = barePicture,
            )?.let { return it }
        }
        return null
    }

    /**
     * Places the unrecognised-tile prompt beneath the game rather than pretending that it is a
     * miniature tile.  Its centre follows the actual transformed picture, which is particularly
     * important for handheld games whose picture is narrower than the phone.
     *
     * The same adaptive rule as [place] applies: honour [gap] and the readable scale first, then
     * make the prompt smaller before giving up.  The no-gap pass is only for a one- to five-pixel
     * lower sliver.  If there is no lower band at all, fall back to the general external placement
     * so a non-Fill picture never loses its only instruction.
     */
    fun placeBelowPicture(
        rootWidth: Int,
        rootHeight: Int,
        picture: Rect,
        desiredWidth: Int,
        desiredHeight: Int,
        edge: Int,
        gap: Int,
        minScale: Float,
        safeLeft: Int = 0,
        safeTop: Int = 0,
        safeRight: Int = 0,
        safeBottom: Int = 0,
    ): Placement? {
        if (rootWidth <= 0 || rootHeight <= 0 || desiredWidth <= 0 || desiredHeight <= 0) return null
        val bounds = Bounds(
            left = maxOf(edge, safeLeft),
            top = maxOf(edge, safeTop),
            right = minOf(rootWidth - edge, rootWidth - safeRight),
            bottom = minOf(rootHeight - edge, rootHeight - safeBottom),
        )
        if (bounds.left >= bounds.right || bounds.top >= bounds.bottom) return null
        val readableWidth = kotlin.math.ceil(desiredWidth * minScale.coerceIn(0f, 1f)).toInt()
            .coerceIn(1, desiredWidth)

        fun tryLowerBand(clearance: Int): Placement? {
            val bottom = (picture.bottom + clearance).coerceAtMost(rootHeight)
            return placeBelowAtEveryScale(
                fromWidth = desiredWidth,
                toWidth = readableWidth,
                desiredWidth = desiredWidth,
                desiredHeight = desiredHeight,
                pictureCentreX = (picture.left + picture.right) / 2f,
                minTop = bottom,
                bounds = bounds,
            ) ?: placeBelowAtEveryScale(
                fromWidth = readableWidth - 1,
                toWidth = 1,
                desiredWidth = desiredWidth,
                desiredHeight = desiredHeight,
                pictureCentreX = (picture.left + picture.right) / 2f,
                minTop = bottom,
                bounds = bounds,
            )
        }

        tryLowerBand(gap)?.let { return it }
        if (!covers(rootWidth, rootHeight, picture)) {
            tryLowerBand(0)?.let { return it }
        }

        // No lower band: retain the no-overlap guarantee and the general fallback behaviour.
        return place(
            rootWidth,
            rootHeight,
            picture,
            desiredWidth,
            desiredHeight,
            edge,
            gap,
            minScale,
            safeLeft,
            safeTop,
            safeRight,
            safeBottom,
        )
    }

    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun covers(rootWidth: Int, rootHeight: Int, picture: Rect): Boolean =
        picture.left <= 0 && picture.top <= 0 && picture.right >= rootWidth && picture.bottom >= rootHeight

    private fun placeAtEveryScale(
        fromWidth: Int,
        toWidth: Int,
        rootWidth: Int,
        rootHeight: Int,
        desiredWidth: Int,
        desiredHeight: Int,
        bounds: Bounds,
        exclusion: Rect,
    ): Placement? {
        if (fromWidth < toWidth) return null
        for (width in fromWidth downTo toWidth) {
            val scale = width.toFloat() / desiredWidth
            val height = (desiredHeight * scale).toInt().coerceAtLeast(1)
            placementAtScale(scale, rootWidth, rootHeight, width, height, bounds, exclusion)
                ?.let { return it }
        }
        return null
    }

    private fun placeBelowAtEveryScale(
        fromWidth: Int,
        toWidth: Int,
        desiredWidth: Int,
        desiredHeight: Int,
        pictureCentreX: Float,
        minTop: Int,
        bounds: Bounds,
    ): Placement? {
        if (fromWidth < toWidth) return null
        for (width in fromWidth downTo toWidth) {
            val scale = width.toFloat() / desiredWidth
            val height = (desiredHeight * scale).toInt().coerceAtLeast(1)
            val left = (pictureCentreX - width / 2f).toInt()
            val top = bounds.bottom - height
            if (left >= bounds.left && left + width <= bounds.right && top >= minTop) {
                return Placement(left, top, width, height, scale)
            }
        }
        return null
    }

    private fun placementAtScale(
        scale: Float,
        rootWidth: Int,
        rootHeight: Int,
        width: Int,
        height: Int,
        bounds: Bounds,
        exclusion: Rect,
    ): Placement? {
        if (width > bounds.right - bounds.left || height > bounds.bottom - bounds.top) return null
        val candidates = listOf(
            // Preserve the familiar reference position whenever it is possible.
            Rect(bounds.right - width, bounds.bottom - height, bounds.right, bounds.bottom),
            Rect(bounds.left, bounds.bottom - height, bounds.left + width, bounds.bottom),
            Rect(bounds.right - width, bounds.top, bounds.right, bounds.top + height),
            Rect(bounds.left, bounds.top, bounds.left + width, bounds.top + height),
        )
        val candidate = candidates.firstOrNull { !it.intersects(exclusion) } ?: return null
        return Placement(candidate.left, candidate.top, width, height, scale)
    }
}
