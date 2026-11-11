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
        assertLayout(GameSystem.NES, ScalingMode.SHARP, 480, 640, 256, 240)
        assertLayout(GameSystem.GENESIS, ScalingMode.SHARP, 480, 640, 320, 224)
    }

    @Test
    fun `gb and gbc sharp use whole pixels in both orientations and odd sized windows`() {
        for (system in listOf(GameSystem.GB, GameSystem.GBC)) {
            assertLayout(system, ScalingMode.SHARP, 640, 480, 480, 432)
            assertLayout(system, ScalingMode.SHARP, 479, 640, 320, 288)
            assertLayout(system, ScalingMode.SHARP, 481, 641, 480, 432)
            assertLayout(system, ScalingMode.SHARP, 160, 144, 160, 144)
            val layout = Scaling.compute(system, ScalingMode.SHARP, 479, 640)
            assertEquals(1f, layout.scaleX, 0f)
            assertEquals(1f, layout.scaleY, 0f)
            assertLayout(system, ScalingMode.SHARP, 80, 72, 80, 72)
        }
    }

    @Test
    fun `sharp keeps equal whole pixel blocks for every system across window sizes`() {
        for (system in GameSystem.entries) {
            for ((width, height) in listOf(480 to 640, 640 to 480, 479 to 639, 1080 to 1920)) {
                val layout = Scaling.compute(system, ScalingMode.SHARP, width, height)
                val factor = minOf(width / system.fbWidth, height / system.fbHeight)
                assertLayout(layout, system.fbWidth * factor, system.fbHeight * factor,
                    system.fbWidth * factor, system.fbHeight * factor)
                assertEquals(1f, layout.scaleX, 0f)
                assertEquals(1f, layout.scaleY, 0f)
            }
        }
    }

    @Test
    fun `gba sharp stays at two times in landscape instead of stretching to 640 pixels`() {
        assertLayout(GameSystem.GBA, ScalingMode.SHARP, 640, 480, 480, 320)
        assertLayout(GameSystem.NES, ScalingMode.SHARP, 640, 480, 512, 480)
        assertLayout(GameSystem.GENESIS, ScalingMode.SHARP, 640, 480, 640, 448)
    }

    @Test
    fun `sharp follows actual frame sizes including Genesis narrow and interlaced modes`() {
        for ((sourceWidth, sourceHeight) in listOf(256 to 224, 320 to 240, 320 to 448, 256 to 480)) {
            val layout = Scaling.compute(GameSystem.GENESIS, ScalingMode.SHARP, 640, 480,
                sourceWidth, sourceHeight)
            val factor = minOf(640 / sourceWidth, 480 / sourceHeight)
            assertLayout(layout, sourceWidth * factor, sourceHeight * factor,
                sourceWidth * factor, sourceHeight * factor)
            assertEquals(1f, layout.scaleX, 0f)
            assertEquals(1f, layout.scaleY, 0f)
        }
        assertLayout(Scaling.compute(GameSystem.NES, ScalingMode.SHARP, 480, 640, 256, 224),
            displayedWidth = 256, displayedHeight = 224)
    }

    @Test
    fun `tiny windows fit instead of crop and missing frame dimensions use system defaults`() {
        for (system in GameSystem.entries) {
            val layout = Scaling.compute(system, ScalingMode.SHARP, 80, 60)
            org.junit.Assert.assertTrue(layout.displayedWidth in 1..80)
            org.junit.Assert.assertTrue(layout.displayedHeight in 1..60)
            assertEquals(1f, layout.scaleX, 0f)
            assertEquals(1f, layout.scaleY, 0f)
            assertEquals(Scaling.compute(system, ScalingMode.SHARP, 480, 640),
                Scaling.compute(system, ScalingMode.SHARP, 480, 640, 0, 0))
        }
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
