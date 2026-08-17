package fi.palonkorpi.sideretro.ui

import fi.palonkorpi.sideretro.input.Keytile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TileArtworkTest {

    @Test
    fun `mini controller tight visual bounds contain every physical cap`() {
        val art = requireNotNull(Legend.artFor(Keytile.MINI_CONTROLLER))
        val bounds = requireNotNull(art.contentBounds)

        assertTrue(bounds.width < art.width)
        assertTrue(bounds.height < art.height)
        art.buttons.values.forEach { shape ->
            // All Mini controls are circles or cardinal/45-degree pills. Sampling their support
            // along the four screen axes makes this test protect the real rendered bounds.
            val left = shape.centreX - shape.extentTowards(-1f, 0f)
            val top = shape.centreY - shape.extentTowards(0f, -1f)
            val right = shape.centreX + shape.extentTowards(1f, 0f)
            val bottom = shape.centreY + shape.extentTowards(0f, 1f)
            assertTrue(bounds.left <= left)
            assertTrue(bounds.top <= top)
            assertTrue(bounds.right >= right)
            assertTrue(bounds.bottom >= bottom)
        }
    }

    @Test
    fun `compact qwerty artwork names every reachable control exactly once`() {
        assertNotNull(Legend.artFor(Keytile.COMPACT_QWERTY))
        val art = requireNotNull(Legend.artFor(Keytile.COMPACT_QWERTY))
        val expected = setOf(
            "KEYCODE_E", "KEYCODE_C", "KEYCODE_A", "KEYCODE_G",
            "KEYCODE_J", "KEYCODE_L", "KEYCODE_U", "KEYCODE_O",
            "KEYCODE_SPACE", "KEYCODE_SHIFT_LEFT", "KEYCODE_BACK",
        )

        assertEquals(expected, art.buttons.keys)
        // Backspace is printed on the tile, but it is deliberately not a SideRetro binding.
        assertFalse(art.buttons.containsKey("KEYCODE_DEL"))
    }

    @Test
    fun `t9 artwork uses the shared numpad mapping and leaves five static`() {
        assertNotNull(Legend.artFor(Keytile.NUMPAD))
        val art = requireNotNull(Legend.artFor(Keytile.NUMPAD))
        val expected = setOf(
            "KEYCODE_0", "KEYCODE_1", "KEYCODE_2", "KEYCODE_3", "KEYCODE_4",
            "KEYCODE_6", "KEYCODE_7", "KEYCODE_8", "KEYCODE_9", "KEYCODE_STAR",
            "KEYCODE_POUND",
        )

        assertEquals(expected, art.buttons.keys)
        assertFalse(art.buttons.containsKey("KEYCODE_5"))
    }

    @Test
    fun `sundial artwork names its ring and all four contextual corners`() {
        assertNotNull(Legend.artFor(Keytile.SUNDIAL))
        val art = requireNotNull(Legend.artFor(Keytile.SUNDIAL))
        val expected = setOf(
            "KEYCODE_DPAD_UP", "KEYCODE_DPAD_DOWN",
            "KEYCODE_MEDIA_PREVIOUS", "KEYCODE_MEDIA_NEXT", "KEYCODE_MEDIA_PLAY_PAUSE",
            "KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_RIGHT", "KEYCODE_TAB", "KEYCODE_ENTER",
        )

        assertEquals(expected, art.buttons.keys)
        // The body ring is deliberately authored in the SVG rather than inferred from the live
        // button overlays.  Keeping it guards against the converter treating an outline as a
        // redundant cap and making the Sundial lose its visual centre.
        assertEquals(1, art.body.size)
        assertEquals(Role.OUTLINE, art.body.single().role)
        val ring = art.body.single().shape
        assertTrue(ring is Shape.Circle)
        assertEquals(734.2f, (ring as Shape.Circle).r, 0.01f)
    }

}
