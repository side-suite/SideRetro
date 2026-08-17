package fi.palonkorpi.sideretro.input

import android.view.KeyEvent

/**
 * Physical navigation for SideRetro's own UI.
 *
 * Gameplay is allowed to rotate directions and reassign a face cluster when the player chooses
 * Positions. A menu is not gameplay: it must follow what is printed on the tile. In particular,
 * an arrow named DPAD_LEFT always moves a menu selection left, and a Mini Controller's printed A
 * always confirms while its printed B always goes back.
 */
object MenuKeymap {

    fun resolve(keyCode: Int, tile: Keytile): LogicalButton? {
        // These names are an Android-level, physical contract. Check them before the Sundial's
        // gameplay-only ambiguous-corner interpretation.
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> return LogicalButton.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> return LogicalButton.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> return LogicalButton.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> return LogicalButton.RIGHT
            KeyEvent.KEYCODE_BACK -> return LogicalButton.FACE_B
        }

        return when (tile) {
            Keytile.MINI_CONTROLLER -> when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_START -> LogicalButton.FACE_A
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_SELECT -> LogicalButton.FACE_B
                else -> null
            }

            Keytile.COMPACT_QWERTY -> when (keyCode) {
                // The cross physically surrounding D on the tile.
                KeyEvent.KEYCODE_E -> LogicalButton.UP
                KeyEvent.KEYCODE_C -> LogicalButton.DOWN
                KeyEvent.KEYCODE_A -> LogicalButton.LEFT
                KeyEvent.KEYCODE_G -> LogicalButton.RIGHT
                KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_SPACE -> LogicalButton.FACE_A
                KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_DEL -> LogicalButton.FACE_B
                else -> null
            }

            Keytile.NUMPAD -> when (keyCode) {
                KeyEvent.KEYCODE_2 -> LogicalButton.UP
                KeyEvent.KEYCODE_8 -> LogicalButton.DOWN
                KeyEvent.KEYCODE_4 -> LogicalButton.LEFT
                KeyEvent.KEYCODE_6 -> LogicalButton.RIGHT
                // The printed action pair is B on 7 and A on 9, irrespective of console.
                KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_POUND -> LogicalButton.FACE_A
                KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_STAR,
                KeyEvent.KEYCODE_0 -> LogicalButton.FACE_B
                else -> null
            }

            Keytile.SUNDIAL -> when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> LogicalButton.LEFT
                KeyEvent.KEYCODE_MEDIA_NEXT -> LogicalButton.RIGHT
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_TAB -> LogicalButton.FACE_A
                KeyEvent.KEYCODE_ENTER -> LogicalButton.FACE_B
                else -> null
            }

            // Before tile inference has enough evidence, retain the familiar Android fallback.
            Keytile.UNKNOWN -> when (keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> LogicalButton.FACE_A
                KeyEvent.KEYCODE_DEL -> LogicalButton.FACE_B
                else -> null
            }
        }
    }
}
