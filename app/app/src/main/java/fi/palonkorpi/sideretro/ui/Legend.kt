package fi.palonkorpi.sideretro.ui

import android.view.KeyEvent
import fi.palonkorpi.sideretro.emu.GameSystem
import fi.palonkorpi.sideretro.input.Keymap
import fi.palonkorpi.sideretro.input.Keytile
import fi.palonkorpi.sideretro.input.LogicalButton

/**
 * SPEC.md §5.1 — the legend is a requirement, not polish.
 *
 * The Mini Controller's diamond is Xbox-style (A at the bottom, B at the right) while every system
 * SideRetro emulates is Nintendo-style (A at the right), so the button printed A is not reliably the
 * button a game calls A — and no amount of looking at the tile tells you which is which. The other
 * four tiles are worse: a numpad has no visual relationship to game buttons at all.
 *
 * ### It draws the tile, because that removes the translation step
 *
 * A list of "J → B" still asks the player to find J. A picture of their own tile with the button lit
 * does not. So the legend is a miniature of the attached Keytile, and it lights up under the thumb.
 *
 * ### Nothing here is written per tile
 *
 * [TileArtwork] keys its buttons by **Android keycode name**, and `KeyEvent.keyCodeToString` /
 * `keyCodeFromString` round-trip exactly — so a label is produced by asking [Keymap] what a keycode
 * does right now. A legend that is *derived* from the map cannot drift from it, which matters
 * because the map varies by system, by orientation and by the §3.3 option. It has already earned
 * that: it caught the keymap advertising "L" and "R" on a Game Boy, which has no shoulder buttons.
 *
 * Directions are deliberately unlabelled. The §3.2 transform exists precisely so the key pointing a
 * given way moves the character that way, in both orientations — so they never need explaining, and
 * leaving them bare is what makes room for the labels that do.
 */
object Legend {

    fun artFor(tile: Keytile): TileArt? = when (tile) {
        Keytile.MINI_CONTROLLER -> TileArtwork.MINI_CONTROLLER
        Keytile.COMPACT_QWERTY -> TileArtwork.COMPACT_QWERTY
        // T9 and Classic send the same numpad codes, so one honest shared miniature is all the
        // inference layer can select. Classic's additional d-pad/soft keys still work; they are
        // simply not pictured by the T9 drawing.
        Keytile.NUMPAD -> TileArtwork.NUMPAD
        Keytile.SUNDIAL -> TileArtwork.SUNDIAL
        else -> null
    }

    /**
     * Keycode name → what to write on that button.
     *
     * Absent means the key does nothing on this system, and the drawing shows it dim. **Present but
     * blank means it works and needs no words** — the directions. Conflating the two once drew every
     * d-pad as dead, which is the opposite of true.
     */
    fun labels(
        art: TileArt,
        system: GameSystem,
        tile: Keytile,
        landscape: Boolean,
        positionalFaceButtons: Boolean,
    ): Map<String, String> = buildMap {
        art.buttons.keys.forEach { name ->
            val keyCode = KeyEvent.keyCodeFromString(name)
            if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return@forEach
            val logical = Keymap.resolve(keyCode, system, tile, landscape, positionalFaceButtons)
                ?: return@forEach
            labelFor(logical)?.let { put(name, it) }
        }
    }

    private fun labelFor(button: LogicalButton): String? = when (button) {
        LogicalButton.FACE_A -> "A"
        LogicalButton.FACE_B -> "B"
        LogicalButton.FACE_C -> "C"
        LogicalButton.L -> "L"
        LogicalButton.R -> "R"
        LogicalButton.START -> "Start"
        LogicalButton.SELECT -> "Select"
        LogicalButton.MENU -> "Menu"
        // Bound, but deliberately unlabelled — directions carry themselves. See the class comment.
        else -> ""
    }

    /**
     * What the band shows before the first keypress. The tile cannot be known until something
     * arrives, so there is nothing honest to draw — §5.1 puts the SideRetro mark here, which is
     * still open as [SID-210](https://linear.app/sidephone/issue/SID-210).
     */
    const val UNKNOWN_TILE_HINT = "SideRetro — press any key"

    /**
     * A plain line of text for tiles whose drawing has not landed yet. Worse than the picture, and
     * deliberately kept anyway: a player on a numpad tile should not be left with no legend at all
     * while the artwork is being made.
     */
    fun fallbackLine(
        tile: Keytile,
        system: GameSystem,
        landscape: Boolean,
        positionalFaceButtons: Boolean,
    ): String? {
        val keys = undrawnTileKeys(tile) ?: return null
        return keys.mapNotNull { (keyCode, printed) ->
            val logical = Keymap.resolve(keyCode, system, tile, landscape, positionalFaceButtons)
                ?: return@mapNotNull null
            val label = labelFor(logical)?.ifEmpty { null } ?: return@mapNotNull null
            if (printed.equals(label, ignoreCase = true)) printed else "$printed → $label"
        }.joinToString("   ").ifEmpty { null }
    }

    private fun undrawnTileKeys(tile: Keytile): List<Pair<Int, String>>? = when (tile) {
        Keytile.COMPACT_QWERTY -> listOf(
            KeyEvent.KEYCODE_J to "J", KeyEvent.KEYCODE_L to "L",
            KeyEvent.KEYCODE_U to "U", KeyEvent.KEYCODE_O to "O",
            KeyEvent.KEYCODE_SPACE to "Space", KeyEvent.KEYCODE_SHIFT_LEFT to "Shift",
        )

        Keytile.NUMPAD -> listOf(
            KeyEvent.KEYCODE_7 to "7", KeyEvent.KEYCODE_9 to "9",
            KeyEvent.KEYCODE_1 to "1", KeyEvent.KEYCODE_3 to "3",
            KeyEvent.KEYCODE_POUND to "#", KeyEvent.KEYCODE_STAR to "*",
            KeyEvent.KEYCODE_ENTER to "OK",
        )

        Keytile.SUNDIAL -> listOf(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "Centre", KeyEvent.KEYCODE_ENTER to "Lower right",
            KeyEvent.KEYCODE_TAB to "Lower left", KeyEvent.KEYCODE_DPAD_LEFT to "Upper left",
            KeyEvent.KEYCODE_DPAD_RIGHT to "Upper right",
        )

        // Drawn, or not yet identified.
        Keytile.MINI_CONTROLLER, Keytile.UNKNOWN -> null
    }
}
