package fi.palonkorpi.sideretro.input

import android.view.KeyEvent
import fi.palonkorpi.sideretro.emu.GameSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeymapTest {

    @Test
    fun `face buttons send the RetroPad button named after the requested action`() {
        assertEquals(RetroPad.A, LogicalButton.FACE_A.toRetroPad(GameSystem.GB))
        assertEquals(RetroPad.B, LogicalButton.FACE_B.toRetroPad(GameSystem.GB))
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, LogicalButton.FACE_A.toRetroPad(GameSystem.GB)?.androidKeyCode)
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, LogicalButton.FACE_B.toRetroPad(GameSystem.GB)?.androidKeyCode)

        assertEquals(RetroPad.Y, LogicalButton.FACE_A.toRetroPad(GameSystem.GENESIS))
        assertEquals(RetroPad.B, LogicalButton.FACE_B.toRetroPad(GameSystem.GENESIS))
        assertEquals(RetroPad.A, LogicalButton.FACE_C.toRetroPad(GameSystem.GENESIS))
    }

    @Test
    fun `mini controller maps every supported system without mirroring A and B`() {
        for (system in GameSystem.entries.filterNot { it.isGenesis }) {
            assertEquals(LogicalButton.FACE_A, resolve(KeyEvent.KEYCODE_BUTTON_A, system, Keytile.MINI_CONTROLLER))
            assertEquals(LogicalButton.FACE_B, resolve(KeyEvent.KEYCODE_BUTTON_B, system, Keytile.MINI_CONTROLLER))
            assertEquals(LogicalButton.START, resolve(KeyEvent.KEYCODE_BUTTON_START, system, Keytile.MINI_CONTROLLER))
        }

        assertEquals(LogicalButton.FACE_A, resolve(KeyEvent.KEYCODE_BUTTON_X, GameSystem.GENESIS, Keytile.MINI_CONTROLLER))
        assertEquals(LogicalButton.FACE_B, resolve(KeyEvent.KEYCODE_BUTTON_A, GameSystem.GENESIS, Keytile.MINI_CONTROLLER))
        assertEquals(LogicalButton.FACE_C, resolve(KeyEvent.KEYCODE_BUTTON_B, GameSystem.GENESIS, Keytile.MINI_CONTROLLER))
        assertNull(resolve(KeyEvent.KEYCODE_BUTTON_Y, GameSystem.GENESIS, Keytile.MINI_CONTROLLER))

        assertEquals(LogicalButton.L, resolve(KeyEvent.KEYCODE_BUTTON_X, GameSystem.GBA, Keytile.MINI_CONTROLLER))
        assertEquals(LogicalButton.R, resolve(KeyEvent.KEYCODE_BUTTON_Y, GameSystem.GBA, Keytile.MINI_CONTROLLER))
        assertNull(resolve(KeyEvent.KEYCODE_BUTTON_X, GameSystem.NES, Keytile.MINI_CONTROLLER))
    }

    @Test
    fun `compact qwerty and numpad preserve their physical action roles`() {
        assertEquals(LogicalButton.FACE_B, resolve(KeyEvent.KEYCODE_J, GameSystem.GB, Keytile.COMPACT_QWERTY))
        assertEquals(LogicalButton.FACE_A, resolve(KeyEvent.KEYCODE_L, GameSystem.GB, Keytile.COMPACT_QWERTY))
        assertEquals(LogicalButton.L, resolve(KeyEvent.KEYCODE_U, GameSystem.GBA, Keytile.COMPACT_QWERTY))
        assertEquals(LogicalButton.R, resolve(KeyEvent.KEYCODE_O, GameSystem.GBA, Keytile.COMPACT_QWERTY))
        assertEquals(LogicalButton.MENU, resolve(KeyEvent.KEYCODE_BACK, GameSystem.GB, Keytile.COMPACT_QWERTY))

        assertEquals(LogicalButton.FACE_A, resolve(KeyEvent.KEYCODE_J, GameSystem.GENESIS, Keytile.COMPACT_QWERTY))
        assertEquals(LogicalButton.FACE_B, resolve(KeyEvent.KEYCODE_L, GameSystem.GENESIS, Keytile.COMPACT_QWERTY))
        assertEquals(LogicalButton.FACE_C, resolve(KeyEvent.KEYCODE_O, GameSystem.GENESIS, Keytile.COMPACT_QWERTY))

        assertEquals(LogicalButton.FACE_B, resolve(KeyEvent.KEYCODE_7, GameSystem.GB, Keytile.NUMPAD))
        assertEquals(LogicalButton.FACE_A, resolve(KeyEvent.KEYCODE_9, GameSystem.GB, Keytile.NUMPAD))
        assertEquals(LogicalButton.FACE_A, resolve(KeyEvent.KEYCODE_7, GameSystem.GENESIS, Keytile.NUMPAD))
        assertEquals(LogicalButton.FACE_C, resolve(KeyEvent.KEYCODE_3, GameSystem.GENESIS, Keytile.NUMPAD))
        assertEquals(LogicalButton.MENU, resolve(KeyEvent.KEYCODE_0, GameSystem.GB, Keytile.NUMPAD))
        assertEquals(LogicalButton.MENU, resolve(KeyEvent.KEYCODE_DEL, GameSystem.GB, Keytile.NUMPAD))
    }

    @Test
    fun `sundial takes over ambiguous corners only once inferred`() {
        assertEquals(LogicalButton.LEFT, resolve(KeyEvent.KEYCODE_DPAD_LEFT, GameSystem.GBA, Keytile.UNKNOWN))
        assertEquals(LogicalButton.RIGHT, resolve(KeyEvent.KEYCODE_DPAD_RIGHT, GameSystem.GBA, Keytile.UNKNOWN))
        assertEquals(LogicalButton.FACE_A, resolve(KeyEvent.KEYCODE_ENTER, GameSystem.GBA, Keytile.UNKNOWN))

        assertEquals(LogicalButton.LEFT, resolve(KeyEvent.KEYCODE_MEDIA_PREVIOUS, GameSystem.GB, Keytile.SUNDIAL))
        assertEquals(LogicalButton.RIGHT, resolve(KeyEvent.KEYCODE_MEDIA_NEXT, GameSystem.GB, Keytile.SUNDIAL))
        assertEquals(LogicalButton.FACE_A, resolve(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, GameSystem.GB, Keytile.SUNDIAL))
        assertEquals(LogicalButton.FACE_B, resolve(KeyEvent.KEYCODE_ENTER, GameSystem.GB, Keytile.SUNDIAL))
        assertEquals(LogicalButton.START, resolve(KeyEvent.KEYCODE_TAB, GameSystem.GB, Keytile.SUNDIAL))
        val expectedLeftCorner = mapOf(
            GameSystem.GB to LogicalButton.SELECT,
            GameSystem.GBC to LogicalButton.SELECT,
            GameSystem.GBA to LogicalButton.L,
            GameSystem.NES to LogicalButton.SELECT,
            GameSystem.GENESIS to null,
        )
        val expectedRightCorner = mapOf(
            GameSystem.GB to null,
            GameSystem.GBC to null,
            GameSystem.GBA to LogicalButton.R,
            GameSystem.NES to null,
            GameSystem.GENESIS to LogicalButton.FACE_C,
        )
        for (system in GameSystem.entries) {
            assertEquals(expectedLeftCorner.getValue(system), resolve(KeyEvent.KEYCODE_DPAD_LEFT, system, Keytile.SUNDIAL))
            assertEquals(expectedRightCorner.getValue(system), resolve(KeyEvent.KEYCODE_DPAD_RIGHT, system, Keytile.SUNDIAL))
            assertEquals(LogicalButton.UP, resolve(KeyEvent.KEYCODE_DPAD_UP, system, Keytile.SUNDIAL))
            assertEquals(LogicalButton.DOWN, resolve(KeyEvent.KEYCODE_DPAD_DOWN, system, Keytile.SUNDIAL))
        }
    }

    @Test
    fun `direction rotation is a quarter turn and leaves actions alone`() {
        assertEquals(LogicalButton.LEFT, Keymap.rotateDirectionCcw(LogicalButton.UP))
        assertEquals(LogicalButton.DOWN, Keymap.rotateDirectionCcw(LogicalButton.LEFT))
        assertEquals(LogicalButton.RIGHT, Keymap.rotateDirectionCcw(LogicalButton.DOWN))
        assertEquals(LogicalButton.UP, Keymap.rotateDirectionCcw(LogicalButton.RIGHT))
        assertEquals(LogicalButton.RIGHT, Keymap.rotateDirectionCw(LogicalButton.UP))
        assertEquals(LogicalButton.LEFT, Keymap.rotateDirectionCw(LogicalButton.DOWN))
        assertEquals(LogicalButton.FACE_A, Keymap.rotateDirectionCcw(LogicalButton.FACE_A))
    }

    @Test
    fun `positions fixed rotates only two dimensional face clusters in landscape`() {
        assertEquals(
            LogicalButton.FACE_A,
            resolve(KeyEvent.KEYCODE_BUTTON_X, GameSystem.GBA, Keytile.MINI_CONTROLLER, landscape = true, positional = true),
        )
        assertEquals(
            LogicalButton.FACE_B,
            resolve(KeyEvent.KEYCODE_BUTTON_A, GameSystem.GBA, Keytile.MINI_CONTROLLER, landscape = true, positional = true),
        )
        assertEquals(
            LogicalButton.R,
            resolve(KeyEvent.KEYCODE_BUTTON_B, GameSystem.GBA, Keytile.MINI_CONTROLLER, landscape = true, positional = true),
        )
        assertEquals(
            LogicalButton.L,
            resolve(KeyEvent.KEYCODE_BUTTON_Y, GameSystem.GBA, Keytile.MINI_CONTROLLER, landscape = true, positional = true),
        )
        assertEquals(
            LogicalButton.FACE_A,
            resolve(KeyEvent.KEYCODE_BUTTON_A, GameSystem.GBA, Keytile.MINI_CONTROLLER, landscape = false, positional = true),
        )
        assertEquals(
            LogicalButton.FACE_B,
            resolve(KeyEvent.KEYCODE_7, GameSystem.GB, Keytile.NUMPAD, landscape = true, positional = true),
        )
    }

    @Test
    fun `positions fixed rotates the sundial corners geometrically`() {
        assertEquals(LogicalButton.START, resolve(KeyEvent.KEYCODE_DPAD_LEFT, GameSystem.GBA, Keytile.SUNDIAL, true, true))
        assertEquals(LogicalButton.FACE_B, resolve(KeyEvent.KEYCODE_TAB, GameSystem.GBA, Keytile.SUNDIAL, true, true))
        assertEquals(LogicalButton.R, resolve(KeyEvent.KEYCODE_ENTER, GameSystem.GBA, Keytile.SUNDIAL, true, true))
        assertEquals(LogicalButton.L, resolve(KeyEvent.KEYCODE_DPAD_RIGHT, GameSystem.GBA, Keytile.SUNDIAL, true, true))
    }

    private fun resolve(
        keyCode: Int,
        system: GameSystem,
        tile: Keytile,
        landscape: Boolean = false,
        positional: Boolean = false,
    ): LogicalButton? = Keymap.resolve(keyCode, system, tile, landscape, positional)
}
