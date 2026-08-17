package fi.palonkorpi.sideretro.ui

import fi.palonkorpi.sideretro.emu.GameSystem
import fi.palonkorpi.sideretro.emu.Scaling
import fi.palonkorpi.sideretro.settings.ScalingMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegendLayoutTest {

    @Test
    fun `portrait legends clear every system and scaling picture or hide`() {
        GameSystem.entries.forEach { system ->
            ScalingMode.entries.forEach { mode ->
                val layout = Scaling.compute(system, mode, 480, 640)
                val shownHeight = layout.displayedHeight
                val top = if (mode == ScalingMode.FILL) 0 else ((640 - shownHeight) * 0.43f).toInt()
                assertNeverOverlaps(
                    rootWidth = 480,
                    rootHeight = 640,
                    picture = LegendLayout.Rect(0, top, layout.displayedWidth, top + shownHeight),
                )
            }
        }
    }

    @Test
    fun `landscape legends clear every system and scaling picture or hide`() {
        GameSystem.entries.forEach { system ->
            ScalingMode.entries.forEach { mode ->
                val layout = Scaling.compute(system, mode, 640, 480)
                val left = (640 - layout.displayedWidth) / 2
                val top = (480 - layout.displayedHeight) / 2
                assertNeverOverlaps(
                    rootWidth = 640,
                    rootHeight = 480,
                    picture = LegendLayout.Rect(left, top, left + layout.displayedWidth, top + layout.displayedHeight),
                )
            }
        }
    }

    @Test
    fun `fill hides a legend when the picture owns all of the glass`() {
        val placement = LegendLayout.place(
            rootWidth = 480,
            rootHeight = 640,
            picture = LegendLayout.Rect(0, 0, 480, 640),
            desiredWidth = 124,
            desiredHeight = 124,
            edge = 3,
            gap = 6,
            minScale = READABLE_MIN_SCALE,
        )
        assertNull(placement)
    }

    @Test
    fun `full width portrait NES sized picture keeps a scaled legend in its lower band`() {
        // This deliberately leaves far less than the full-size legend under a full-width console
        // picture. It used to return null at the old 80% floor and make the reference vanish; the
        // new rule keeps the bottom-right composition and scales it to the real band.
        val rootWidth = 480
        val rootHeight = 640
        val picture = LegendLayout.Rect(0, 42, 480, 570)
        val placement = LegendLayout.place(
            rootWidth = rootWidth,
            rootHeight = rootHeight,
            picture = picture,
            desiredWidth = 124,
            desiredHeight = 124,
            edge = 3,
            gap = 6,
            minScale = READABLE_MIN_SCALE,
        )

        assertNotNull(placement)
        placement ?: return
        assertTrue("the shallow band should trigger an adaptive scale", placement.scale < 1f)
        assertDoesNotOverlap(rootWidth, rootHeight, picture, placement)
    }

    @Test
    fun `wide unknown tile prompt is centered below the game and scales rather than disappearing`() {
        val rootWidth = 480
        val rootHeight = 640
        val picture = LegendLayout.Rect(0, 0, 480, 612)
        val placement = LegendLayout.placeBelowPicture(
            rootWidth = rootWidth,
            rootHeight = rootHeight,
            picture = picture,
            desiredWidth = 164,
            desiredHeight = 36,
            edge = 3,
            gap = 6,
            minScale = 1f,
        )

        assertNotNull(placement)
        placement ?: return
        assertTrue("the shallow prompt should adapt to the available lower band", placement.scale < 1f)
        assertTrue(
            "the prompt must be horizontally centred on the game picture",
            kotlin.math.abs((placement.left + placement.width / 2f) - (picture.left + picture.right) / 2f) <= 0.5f,
        )
        assertDoesNotOverlap(rootWidth, rootHeight, picture, placement)
    }

    @Test
    fun `compact and T9 portrait composition keeps full game and a readable inset legend`() {
        // Compact QWERTY and T9 use a lower-left identity and lower-right legend. Their selected
        // game remains 480×450 on portrait Sharp NES. At unit density its dense layout uses the
        // normal 43% optical position, capped only enough to retain the readable lower map.
        val rootWidth = 480
        val rootHeight = 640
        val normal = Scaling.compute(GameSystem.NES, ScalingMode.SHARP, rootWidth, rootHeight)
        val shownHeight = normal.displayedHeight
        val top = PortraitGamePlacement.denseLegendTop(
            rootHeightPx = rootHeight,
            pictureHeightPx = shownHeight.toFloat(),
            density = 1f,
            opticalTopFraction = 0.43f,
            readableLegendDp = 96,
            legendEdgeDp = 12,
            legendGapDp = 6,
            visualTopFloorDp = 32,
        ).toInt()
        val picture = LegendLayout.Rect(0, top, rootWidth, top + shownHeight)
        val placement = LegendLayout.place(
            rootWidth = rootWidth,
            rootHeight = rootHeight,
            picture = picture,
            desiredWidth = 124,
            desiredHeight = 124,
            edge = 12,
            gap = 6,
            minScale = 96f / 124f,
        )

        assertNotNull(placement)
        placement ?: return
        assertTrue("the game width must remain normal", normal.displayedWidth == rootWidth)
        assertTrue("the game height must remain normal", shownHeight == 450)
        assertTrue("the game is below the visual bezel zone while retaining a full lower map", top == 76)
        assertTrue("dense key labels must retain their readable floor", placement.scale >= 96f / 124f)
        assertTrue("the corner needs a deliberate rather than hairline margin", placement.left + placement.width <= 468)
        assertDoesNotOverlap(rootWidth, rootHeight, picture, placement)
    }

    private fun assertNeverOverlaps(rootWidth: Int, rootHeight: Int, picture: LegendLayout.Rect) {
        val placement = LegendLayout.place(
            rootWidth = rootWidth,
            rootHeight = rootHeight,
            picture = picture,
            desiredWidth = 124,
            desiredHeight = 124,
            edge = 3,
            gap = 6,
            minScale = READABLE_MIN_SCALE,
        )

        val pictureOwnsEveryPixel = picture.left <= 0 && picture.top <= 0 &&
            picture.right >= rootWidth && picture.bottom >= rootHeight
        if (pictureOwnsEveryPixel) {
            assertNull(placement)
            return
        }

        assertNotNull("a non-edge-to-edge picture must keep a legend", placement)
        placement ?: return

        assertDoesNotOverlap(rootWidth, rootHeight, picture, placement)
    }

    private fun assertDoesNotOverlap(
        rootWidth: Int,
        rootHeight: Int,
        picture: LegendLayout.Rect,
        placement: LegendLayout.Placement,
    ) {
        val bounds = LegendLayout.Rect(
            placement.left,
            placement.top,
            placement.left + placement.width,
            placement.top + placement.height,
        )
        assertFalse(bounds.intersects(picture.expanded(6, rootWidth, rootHeight)))
    }

    private companion object {
        const val READABLE_MIN_SCALE = 44f / 124f
    }
}
