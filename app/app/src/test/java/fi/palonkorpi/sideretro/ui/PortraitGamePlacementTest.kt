package fi.palonkorpi.sideretro.ui

import fi.palonkorpi.sideretro.emu.GameSystem
import fi.palonkorpi.sideretro.emu.Scaling
import fi.palonkorpi.sideretro.settings.ScalingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitGamePlacementTest {

    @Test
    fun `dense portrait placement keeps every system full size at the SP-01 density`() {
        // The SP-01 reports 204dpi, i.e. 1.275 Android pixels per dp.  This makes a 32dp bezel
        // clearance 40.8px and the desired 96dp legend plus 12dp edge plus 6dp gap 145.35px.
        // Keep the values explicit: they describe the intended on-device compositions.
        val cases = listOf(
            Case(GameSystem.GB, ScalingMode.SHARP, 62.65f),
            Case(GameSystem.GBC, ScalingMode.SHARP, 62.65f),
            Case(GameSystem.GBA, ScalingMode.SHARP, 137.60f),
            Case(GameSystem.NES, ScalingMode.SHARP, 44.65f),
            Case(GameSystem.GENESIS, ScalingMode.SHARP, 130.72f),
            Case(GameSystem.GB, ScalingMode.LARGE, 62.65f),
            Case(GameSystem.GBC, ScalingMode.LARGE, 62.65f),
            Case(GameSystem.GBA, ScalingMode.LARGE, 137.60f),
            Case(GameSystem.NES, ScalingMode.LARGE, 120.40f),
            Case(GameSystem.GENESIS, ScalingMode.LARGE, 120.40f),
        )

        cases.forEach { case ->
            val layout = Scaling.compute(case.system, case.mode, ROOT_WIDTH, ROOT_HEIGHT)
            val top = denseTop(layout.displayedHeight.toFloat(), SP01_DENSITY)

            assertEquals("${case.system} ${case.mode}", case.expectedTop, top, 0.01f)
            assertTrue("${case.system} ${case.mode} preserves its selected game size", layout.displayedHeight > 0)
            assertTrue("${case.system} ${case.mode} clears the visual bezel zone", top >= VISUAL_FLOOR_SP01)
            assertTrue("${case.system} ${case.mode} stays on the glass", top + layout.displayedHeight <= ROOT_HEIGHT)
        }
    }

    @Test
    fun `density conversion changes dense safe margins rather than assuming one pixel per dp`() {
        val sharpNes = Scaling.compute(GameSystem.NES, ScalingMode.SHARP, ROOT_WIDTH, ROOT_HEIGHT)

        val unitDensityTop = denseTop(sharpNes.displayedHeight.toFloat(), 1f)
        val sp01Top = denseTop(sharpNes.displayedHeight.toFloat(), SP01_DENSITY)

        assertEquals(76f, unitDensityTop, 0.01f)
        assertEquals(44.65f, sp01Top, 0.01f)
        assertTrue("the real device's 32dp floor is about 41px", VISUAL_FLOOR_SP01 in 40f..42f)
    }

    private fun denseTop(pictureHeight: Float, density: Float): Float =
        PortraitGamePlacement.denseLegendTop(
            rootHeightPx = ROOT_HEIGHT,
            pictureHeightPx = pictureHeight,
            density = density,
            opticalTopFraction = 0.43f,
            readableLegendDp = 96,
            legendEdgeDp = 12,
            legendGapDp = 6,
            visualTopFloorDp = 32,
        )

    private data class Case(
        val system: GameSystem,
        val mode: ScalingMode,
        val expectedTop: Float,
    )

    private companion object {
        const val ROOT_WIDTH = 480
        const val ROOT_HEIGHT = 640
        const val SP01_DENSITY = 204f / 160f
        const val VISUAL_FLOOR_SP01 = 32f * SP01_DENSITY
    }
}
