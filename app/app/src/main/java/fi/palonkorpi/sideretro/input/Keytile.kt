package fi.palonkorpi.sideretro.input

import android.view.KeyEvent

/**
 * Which Keytile we believe is attached.
 *
 * There is no API for this. All five tiles enumerate as one anonymous `gxa535_keyboard` with one
 * capability bitmap that does not change when you swap, so identity can only ever be *inferred*
 * from keycodes we have actually seen arrive.
 *
 * This drives two things and nothing else:
 *  - the legend (§5.1), which needs to draw the right tile;
 *  - the three genuinely ambiguous keycodes in the union keymap (see [Keymap]).
 *
 * [NUMPAD] covers T9 and Classic together. They are keycode-identical on the numpad and therefore
 * mutually undecidable — which SPEC.md §5.1 notes is nearly free, since the mapping is the same.
 */
enum class Keytile {
    UNKNOWN,
    MINI_CONTROLLER,
    COMPACT_QWERTY,
    SUNDIAL,
    NUMPAD,
}

/**
 * Passive inference (§5.1). Never a setting, never a detection API — just a note of what has
 * arrived. Three tiles pin from a single keypress; the numpad tiles pin as a pair.
 *
 * Deliberately one-way: once a tile is pinned it is not un-pinned by a later ambiguous key. Swapping
 * a tile mid-session is rare, and the keys that would re-pin are tile-exclusive anyway, so a genuine
 * swap corrects itself on the first press of a distinctive key.
 */
class TileInference {

    var tile: Keytile = Keytile.UNKNOWN
        private set

    /** @return true if this observation changed our belief, so the legend can redraw. */
    fun observe(keyCode: Int): Boolean {
        val inferred = pin(keyCode) ?: return false
        if (inferred == tile) return false
        tile = inferred
        return true
    }

    private fun pin(keyCode: Int): Keytile? = when (keyCode) {
        // Gamepad codes exist on the Mini Controller alone.
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
        -> Keytile.MINI_CONTROLLER

        // Media codes and TAB exist on the Sundial alone. Its ring's horizontal axis is
        // MEDIA_PREVIOUS/MEDIA_NEXT, which is also what makes it pin on the very first left or
        // right press — before the ambiguous corner keys can be misread.
        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_TAB,
        -> Keytile.SUNDIAL

        // Digits and the two symbol keys exist on T9 and Classic only.
        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
        KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7,
        KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_POUND,
        -> Keytile.NUMPAD

        // Letters, SPACE, SHIFT and BACK exist on the Compact QWERTY alone. (DEL is shared with
        // Classic's right soft key, so it is deliberately not a signal.)
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_BACK,
        -> Keytile.COMPACT_QWERTY

        else -> if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) Keytile.COMPACT_QWERTY
        else null
    }
}
