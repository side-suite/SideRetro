package fi.palonkorpi.sideretro.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileInferenceTest {

    @Test
    fun `ambiguous observations leave the tile unknown`() {
        val inference = TileInference()

        assertFalse(inference.observe(KeyEvent.KEYCODE_DPAD_LEFT))
        assertFalse(inference.observe(KeyEvent.KEYCODE_ENTER))
        assertFalse(inference.observe(KeyEvent.KEYCODE_DEL))
        assertEquals(Keytile.UNKNOWN, inference.tile)
    }

    @Test
    fun `each distinctive key family pins its matching tile`() {
        assertPins(KeyEvent.KEYCODE_BUTTON_A, Keytile.MINI_CONTROLLER)
        assertPins(KeyEvent.KEYCODE_MEDIA_NEXT, Keytile.SUNDIAL)
        assertPins(KeyEvent.KEYCODE_TAB, Keytile.SUNDIAL)
        assertPins(KeyEvent.KEYCODE_7, Keytile.NUMPAD)
        assertPins(KeyEvent.KEYCODE_POUND, Keytile.NUMPAD)
        assertPins(KeyEvent.KEYCODE_E, Keytile.COMPACT_QWERTY)
        assertPins(KeyEvent.KEYCODE_SPACE, Keytile.COMPACT_QWERTY)
        assertPins(KeyEvent.KEYCODE_BACK, Keytile.COMPACT_QWERTY)
    }

    @Test
    fun `repeated observations do not redraw but a distinctive swapped tile corrects belief`() {
        val inference = TileInference()

        assertTrue(inference.observe(KeyEvent.KEYCODE_BUTTON_A))
        assertFalse(inference.observe(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(Keytile.MINI_CONTROLLER, inference.tile)

        assertTrue(inference.observe(KeyEvent.KEYCODE_2))
        assertEquals(Keytile.NUMPAD, inference.tile)
    }

    private fun assertPins(keyCode: Int, expected: Keytile) {
        val inference = TileInference()
        assertTrue(inference.observe(keyCode))
        assertEquals(expected, inference.tile)
    }
}
