package fi.palonkorpi.sideretro.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuKeymapTest {

    @Test
    fun `mini controller printed A confirms and B goes back`() {
        assertEquals(LogicalButton.FACE_A, MenuKeymap.resolve(KeyEvent.KEYCODE_BUTTON_A, Keytile.MINI_CONTROLLER))
        assertEquals(LogicalButton.FACE_B, MenuKeymap.resolve(KeyEvent.KEYCODE_BUTTON_B, Keytile.MINI_CONTROLLER))
    }

    @Test
    fun `named dpad directions always navigate their named menu direction`() {
        for (tile in Keytile.entries) {
            assertEquals(LogicalButton.UP, MenuKeymap.resolve(KeyEvent.KEYCODE_DPAD_UP, tile))
            assertEquals(LogicalButton.DOWN, MenuKeymap.resolve(KeyEvent.KEYCODE_DPAD_DOWN, tile))
            assertEquals(LogicalButton.LEFT, MenuKeymap.resolve(KeyEvent.KEYCODE_DPAD_LEFT, tile))
            assertEquals(LogicalButton.RIGHT, MenuKeymap.resolve(KeyEvent.KEYCODE_DPAD_RIGHT, tile))
        }
    }

    @Test
    fun `non controller tiles retain their physical menu controls`() {
        assertEquals(LogicalButton.FACE_A, MenuKeymap.resolve(KeyEvent.KEYCODE_L, Keytile.COMPACT_QWERTY))
        assertEquals(LogicalButton.FACE_B, MenuKeymap.resolve(KeyEvent.KEYCODE_J, Keytile.COMPACT_QWERTY))
        assertEquals(LogicalButton.FACE_A, MenuKeymap.resolve(KeyEvent.KEYCODE_9, Keytile.NUMPAD))
        assertEquals(LogicalButton.FACE_B, MenuKeymap.resolve(KeyEvent.KEYCODE_7, Keytile.NUMPAD))
        assertEquals(LogicalButton.LEFT, MenuKeymap.resolve(KeyEvent.KEYCODE_MEDIA_PREVIOUS, Keytile.SUNDIAL))
        assertEquals(LogicalButton.RIGHT, MenuKeymap.resolve(KeyEvent.KEYCODE_MEDIA_NEXT, Keytile.SUNDIAL))
        assertEquals(LogicalButton.FACE_A, MenuKeymap.resolve(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, Keytile.SUNDIAL))
        assertEquals(LogicalButton.FACE_B, MenuKeymap.resolve(KeyEvent.KEYCODE_ENTER, Keytile.SUNDIAL))
    }
}
