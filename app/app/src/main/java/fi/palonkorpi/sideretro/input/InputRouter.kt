package fi.palonkorpi.sideretro.input

import android.view.KeyEvent
import android.view.Surface
import com.swordfish.libretrodroid.GLRetroView
import fi.palonkorpi.sideretro.emu.GameSystem

/**
 * Walks a key event through all three layers of SPEC.md §2.2 and hands the result to LibretroDroid.
 *
 * Owns the tile inference, so the legend and the keymap always agree about which tile is attached.
 */
class InputRouter(
    private val system: GameSystem,
    private val inference: TileInference,
) {
    /** §8: Labels fixed (default) or positions fixed. Only has an effect in landscape. */
    var positionalFaceButtons: Boolean = false

    /** One of [Surface.ROTATION_0] … [Surface.ROTATION_270], read from the display each time it changes. */
    var displayRotation: Int = Surface.ROTATION_0

    /** Called when a press resolves to [LogicalButton.MENU], or when the tile belief changes. */
    var onMenu: (() -> Unit)? = null
    var onTileChanged: (() -> Unit)? = null

    val tile: Keytile get() = inference.tile

    private val landscape: Boolean
        get() = displayRotation == Surface.ROTATION_90 || displayRotation == Surface.ROTATION_270

    /**
     * Walks a key event down to the logical button it means *right now* — including the rotation
     * transform — without sending anything.
     *
     * SideRetro's own screens navigate with this rather than with raw keycodes. They have to: in
     * landscape the key the player reads as "up" is physically the tile's *left*, so a menu that
     * matched on `KEYCODE_DPAD_UP` would sit there doing nothing while the player pressed the arrow
     * that visibly points up. Reusing this one path is also what keeps a numpad `2` and the Sundial's
     * ring working everywhere, instead of each screen keeping its own half-copy of the keymap.
     */
    fun resolve(event: KeyEvent): LogicalButton? {
        if (event.keyCode in Keymap.neverArrives) return null

        val isFirstDown = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        if (isFirstDown && inference.observe(event.keyCode)) onTileChanged?.invoke()

        val logical = Keymap.resolve(
            keyCode = event.keyCode,
            system = system,
            tile = inference.tile,
            landscape = landscape,
            positionalFaceButtons = positionalFaceButtons,
        ) ?: return null

        return when {
            !landscape -> logical
            // ROTATION_90 is the display rotated to match a device turned counter-clockwise, which
            // is the orientation SPEC.md §3.2 describes: tile on the right, d-pad at the bottom.
            // Reading the real rotation rather than the requested constant keeps Auto mode honest.
            displayRotation == Surface.ROTATION_90 -> Keymap.rotateDirectionCcw(logical)
            else -> Keymap.rotateDirectionCw(logical)
        }
    }

    /**
     * Resolves a press for SideRetro UI, where the tile's printed controls deliberately win over
     * gameplay transforms. This does still observe the key so the legend becomes available while
     * a menu is open.
     */
    fun resolveForMenu(event: KeyEvent): LogicalButton? {
        if (event.keyCode in Keymap.neverArrives) return null

        val isFirstDown = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        if (isFirstDown && inference.observe(event.keyCode)) onTileChanged?.invoke()

        return MenuKeymap.resolve(event.keyCode, inference.tile)
    }

    /**
     * @return true if the event was ours and must not travel further.
     */
    fun dispatch(event: KeyEvent, retroView: GLRetroView?): Boolean {
        val logical = resolve(event) ?: return false

        if (logical == LogicalButton.MENU) {
            // Fire on release so a held key cannot repeat the menu open.
            if (event.action == KeyEvent.ACTION_UP) onMenu?.invoke()
            return true
        }

        val pad = logical.toRetroPad(system) ?: return true
        // port is always 0: `Input::onKeyEvent` indexes a GamePadState[4] unchecked, and this is a
        // single-player device with one tile.
        retroView?.sendKeyEvent(event.action, pad.androidKeyCode, 0)
        return true
    }
}
