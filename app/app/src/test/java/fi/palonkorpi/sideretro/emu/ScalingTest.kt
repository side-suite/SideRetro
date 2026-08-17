package fi.palonkorpi.sideretro.emu

import fi.palonkorpi.sideretro.settings.ScalingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ScalingTest {

    @Test
    fun `sharp portrait reproduces each square pixel framebuffer aspect`() {
        assertLayout(GameSystem.GB, ScalingMode.SHARP, 480, 640, 480, 432)
        assertLayout(GameSystem.GBC, ScalingMode.SHARP, 480, 640, 480, 432)
        assertLayout(GameSystem.GBA, ScalingMode.SHARP, 480, 640, 480, 320)
        assertLayout(GameSystem.NES, ScalingMode.SHARP, 480, 640, 480, 450)
        assertLayout(GameSystem.GENESIS, ScalingMode.SHARP, 480, 640, 480, 336)
    }

    @Test
    fun `large landscape gives consoles a four by three displayed picture`() {
        val gb = Scaling.compute(GameSystem.GB, ScalingMode.LARGE, 640, 480)
        assertLayout(gb, 533, 480, 533, 480)

        val gba = Scaling.compute(GameSystem.GBA, ScalingMode.LARGE, 640, 480)
        assertLayout(gba, 640, 427, 640, 427)

        val nes = Scaling.compute(GameSystem.NES, ScalingMode.LARGE, 640, 480)
        assertLayout(nes, 512, 480, 640, 480)
        assertEquals(1.25f, nes.scaleX, 0.0001f)

        val genesis = Scaling.compute(GameSystem.GENESIS, ScalingMode.LARGE, 640, 480)
        assertLayout(genesis, 640, 448, 640, 480)
        assertEquals(480f / 448f, genesis.scaleY, 0.0001f)
    }

    @Test
    fun `fill always occupies the available glass after its transform`() {
        for (system in GameSystem.entries) {
            assertLayout(Scaling.compute(system, ScalingMode.FILL, 480, 640), displayedWidth = 480, displayedHeight = 640)
            assertLayout(Scaling.compute(system, ScalingMode.FILL, 640, 480), displayedWidth = 640, displayedHeight = 480)
        }
    }

    @Test
    fun `non positive available dimensions are passed through without a transform`() {
        assertLayout(Scaling.compute(GameSystem.GB, ScalingMode.SHARP, 0, 640), 0, 640, 0, 640)
        assertLayout(Scaling.compute(GameSystem.GB, ScalingMode.LARGE, -1, 640), -1, 640, -1, 640)
    }

    private fun assertLayout(
        system: GameSystem,
        mode: ScalingMode,
        width: Int,
        height: Int,
        displayedWidth: Int,
        displayedHeight: Int,
    ) = assertLayout(Scaling.compute(system, mode, width, height), displayedWidth = displayedWidth, displayedHeight = displayedHeight)

    private fun assertLayout(
        layout: Scaling.Layout,
        viewWidth: Int? = null,
        viewHeight: Int? = null,
        displayedWidth: Int,
        displayedHeight: Int,
    ) {
        viewWidth?.let { assertEquals(it, layout.viewWidth) }
        viewHeight?.let { assertEquals(it, layout.viewHeight) }
        assertEquals(displayedWidth, layout.displayedWidth)
        assertEquals(displayedHeight, layout.displayedHeight)
    }
}
