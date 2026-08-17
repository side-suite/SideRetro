package fi.palonkorpi.sideretro.emu

import fi.palonkorpi.sideretro.settings.ScalingMode
import kotlin.math.roundToInt

/**
 * SPEC.md §4.3 — how big the picture is and what shape it is.
 *
 * ### Why this is arithmetic and not configuration
 *
 * LibretroDroid's renderer (`videolayout.cpp`) always preserves the aspect ratio the *core* reports
 * and letterboxes it inside whatever box the view occupies. There is no API to stretch it: the
 * exposed `viewport` rectangle scales the box but cannot change its shape, and `setViewportAlignment`
 * only chooses where the letterbox space goes. So the scaling mode is implemented by **sizing the
 * `GLRetroView` itself**, not by configuring it.
 *
 * All three cores report their framebuffer shape, i.e. square pixels. That makes Sharp free — the
 * box already has the right shape, so nothing is letterboxed and nothing is resampled. The other two
 * modes need a shape the renderer will not produce, so they are reached with a view transform on top:
 * we lay the view out at the shape the renderer *will* fill, then scale it to the target rectangle.
 * For NES and Genesis in Large that transform is a 1.25× horizontal stretch, which is precisely what
 * a 4:3 correction of a 256- or 320-wide framebuffer is.
 *
 * ### The numbers this must reproduce (from §4.3, on a 480×640 panel)
 *
 * Portrait, Sharp:  GB 480×432 · NES 480×450 · Genesis 480×336 · GBA 480×320
 * Landscape, Large: NES 640×480 · Genesis 640×480 · GBA 640×427 · GB 533×480
 *
 * NES and Genesis fill the rotated panel exactly, with no crop and no distortion, because the panel
 * is 4:3 — the shape those games were made for.
 */
object Scaling {

    /**
     * @param viewWidth   width to lay the `GLRetroView` out at, in pixels.
     * @param viewHeight  height to lay it out at.
     * @param scaleX      view transform applied on top; 1.0 whenever the renderer can produce the
     *                    target shape by itself, which is every Sharp case and both handhelds.
     * @param scaleY      likewise.
     */
    data class Layout(
        val viewWidth: Int,
        val viewHeight: Int,
        val scaleX: Float,
        val scaleY: Float,
    ) {
        /** What the user actually sees, after the transform. Used for the legend band's budget. */
        val displayedWidth: Int get() = (viewWidth * scaleX).roundToInt()
        val displayedHeight: Int get() = (viewHeight * scaleY).roundToInt()
    }

    fun compute(system: GameSystem, mode: ScalingMode, availWidth: Int, availHeight: Int): Layout {
        if (availWidth <= 0 || availHeight <= 0) return Layout(availWidth, availHeight, 1f, 1f)

        // The rectangle we want on the glass.
        val (targetW, targetH) = when (mode) {
            ScalingMode.SHARP -> fit(system.pixelAspect, availWidth, availHeight)
            ScalingMode.LARGE -> fit(system.displayAspect, availWidth, availHeight)
            ScalingMode.FILL -> availWidth to availHeight
        }

        // The largest rectangle inside it that the renderer will fill edge to edge on its own.
        val (baseW, baseH) = fit(system.coreReportedAspect, targetW, targetH)

        return Layout(
            viewWidth = baseW,
            viewHeight = baseH,
            scaleX = if (baseW == 0) 1f else targetW.toFloat() / baseW,
            scaleY = if (baseH == 0) 1f else targetH.toFloat() / baseH,
        )
    }

    /** Largest rectangle of the given aspect that fits inside [width] × [height]. */
    private fun fit(aspect: Float, width: Int, height: Int): Pair<Int, Int> {
        val byWidth = (width / aspect).roundToInt()
        return if (byWidth <= height) {
            width to byWidth
        } else {
            (height * aspect).roundToInt() to height
        }
    }
}
