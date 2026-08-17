package fi.palonkorpi.sideretro.input

import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_0
import android.view.KeyEvent.KEYCODE_1
import android.view.KeyEvent.KEYCODE_2
import android.view.KeyEvent.KEYCODE_3
import android.view.KeyEvent.KEYCODE_4
import android.view.KeyEvent.KEYCODE_6
import android.view.KeyEvent.KEYCODE_7
import android.view.KeyEvent.KEYCODE_8
import android.view.KeyEvent.KEYCODE_9
import android.view.KeyEvent.KEYCODE_A
import android.view.KeyEvent.KEYCODE_BACK
import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_B
import android.view.KeyEvent.KEYCODE_BUTTON_SELECT
import android.view.KeyEvent.KEYCODE_BUTTON_START
import android.view.KeyEvent.KEYCODE_BUTTON_X
import android.view.KeyEvent.KEYCODE_BUTTON_Y
import android.view.KeyEvent.KEYCODE_C
import android.view.KeyEvent.KEYCODE_DEL
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.KeyEvent.KEYCODE_DPAD_UP
import android.view.KeyEvent.KEYCODE_E
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.KeyEvent.KEYCODE_G
import android.view.KeyEvent.KEYCODE_J
import android.view.KeyEvent.KEYCODE_L
import android.view.KeyEvent.KEYCODE_MEDIA_NEXT
import android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
import android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
import android.view.KeyEvent.KEYCODE_O
import android.view.KeyEvent.KEYCODE_POUND
import android.view.KeyEvent.KEYCODE_SHIFT_LEFT
import android.view.KeyEvent.KEYCODE_SPACE
import android.view.KeyEvent.KEYCODE_STAR
import android.view.KeyEvent.KEYCODE_TAB
import android.view.KeyEvent.KEYCODE_U
import fi.palonkorpi.sideretro.emu.GameSystem

/**
 * SPEC.md §2.4 — the universal keymap, bound as the union of all five tiles at once.
 *
 * There is no detection and no switching. A key the attached tile lacks simply never arrives, so
 * the same table serves every tile, and swapping mid-game keeps working because nothing ever knew
 * which tile was attached.
 *
 * ### The one place the union genuinely collides
 *
 * Three keycodes mean different things on the Sundial than everywhere else, and no static table can
 * hold both readings at once:
 *
 * | Keycode | Mini Controller / Classic | Sundial |
 * |---|---|---|
 * | `DPAD_LEFT`  | direction: left  | top-left corner button |
 * | `DPAD_RIGHT` | direction: right | top-right corner button |
 * | `ENTER`      | Classic d-pad centre → A | bottom-right corner → B |
 *
 * This is SPEC.md §2.6 invariant 5, the one it warns catches people. It is resolved by passive
 * inference: the *directions* reading is the default, and the Sundial reading only takes over once
 * [TileInference] has pinned a Sundial. That is safe in practice because the Sundial's own left and
 * right come from the ring, which emits `MEDIA_PREVIOUS`/`MEDIA_NEXT` — Sundial-exclusive codes —
 * so any attempt to steer pins the tile before a corner press can be misread.
 */
object Keymap {

    /**
     * @param positionalFaceButtons the §3.3 "positions fixed" option. Only meaningful in landscape,
     *   and only on tiles whose action cluster is two-dimensional; see [rotateFaceCluster].
     */
    fun resolve(
        keyCode: Int,
        system: GameSystem,
        tile: Keytile,
        landscape: Boolean,
        positionalFaceButtons: Boolean,
    ): LogicalButton? {
        val effective = if (landscape && positionalFaceButtons) {
            rotateFaceCluster(keyCode, tile)
        } else {
            keyCode
        }

        // Directions are deliberately *not* rotated here. The caller applies
        // [rotateDirectionCcw] / [rotateDirectionCw] from the display's real rotation, so Auto mode
        // gets the correct transform without this table needing to know about the screen at all.
        return if (tile == Keytile.SUNDIAL) {
            val sundialButton = sundial(effective, system)
            // A corner can deliberately have no role on a system (the Genesis's top-left, and
            // the non-GBA/non-Genesis top-right). It is still a Sundial-owned key, not a shared
            // d-pad direction, so it must stay inert rather than fall through to [shared].
            if (sundialButton != null || effective in sundialButtons) sundialButton
            else shared(effective, system)
        } else {
            shared(effective, system)
        }
    }

    /**
     * §3.3 "positions fixed": the key that now sits at the *viewer's* position P should do what the
     * key at portrait's position P did. That is a pure geometric rotation of a two-dimensional
     * action cluster, so it is expressed here as a keycode-level permutation applied *before* the
     * tile table — which means it works unchanged for the Genesis three-button mapping too.
     *
     * Only two tiles have a cluster this can apply to. The Compact QWERTY's action keys (`J`/`L`)
     * and the numpad's (`7`/`9`) are horizontal *pairs*: rotated they become a vertical pair, and
     * there is no portrait vertical pair for them to take the behaviour of. Those tiles are
     * therefore identity in both modes, and their legend is the same in both.
     */
    private fun rotateFaceCluster(keyCode: Int, tile: Keytile): Int = when (tile) {
        // Xbox-style diamond: A bottom, B right, X left, Y top. Rotated counter-clockwise the key
        // at the diamond's left appears at the viewer's bottom, so printed X takes printed A's job.
        // This reproduces SPEC.md §3.3 exactly: X→A, A→B, B→R(=Y's job), Y→L(=X's job).
        Keytile.MINI_CONTROLLER -> when (keyCode) {
            KEYCODE_BUTTON_X -> KEYCODE_BUTTON_A
            KEYCODE_BUTTON_A -> KEYCODE_BUTTON_B
            KEYCODE_BUTTON_B -> KEYCODE_BUTTON_Y
            KEYCODE_BUTTON_Y -> KEYCODE_BUTTON_X
            else -> keyCode
        }

        // The Sundial's four corners are the same rotation, one step counter-clockwise:
        // top-left takes bottom-left's job, and so on round the dome. Derived from the same rule as
        // the diamond above rather than stated in the spec, which only worked the Mini Controller.
        Keytile.SUNDIAL -> when (keyCode) {
            KEYCODE_DPAD_LEFT -> KEYCODE_TAB           // top-left  takes bottom-left's job
            KEYCODE_TAB -> KEYCODE_ENTER               // bottom-left takes bottom-right's job
            KEYCODE_ENTER -> KEYCODE_DPAD_RIGHT        // bottom-right takes top-right's job
            KEYCODE_DPAD_RIGHT -> KEYCODE_DPAD_LEFT    // top-right takes top-left's job
            else -> keyCode
        }

        else -> keyCode
    }

    /**
     * Everything except the Sundial's three ambiguous keys. Safe to consult for every tile: each
     * entry is either tile-exclusive or means the same thing on every tile that has it.
     */
    private fun shared(keyCode: Int, system: GameSystem): LogicalButton? = when (keyCode) {
        // ---- Directions ---------------------------------------------------------------------
        // Real d-pads: Mini Controller and Classic.
        KEYCODE_DPAD_UP -> LogicalButton.UP
        KEYCODE_DPAD_DOWN -> LogicalButton.DOWN
        KEYCODE_DPAD_LEFT -> LogicalButton.LEFT
        KEYCODE_DPAD_RIGHT -> LogicalButton.RIGHT

        // T9 / Classic numpad. No dedicated diagonals — they come from 2-key rollover (`2`+`4`),
        // which is all the tiles are rated for anyway (§2.6 invariant 6).
        KEYCODE_2 -> LogicalButton.UP
        KEYCODE_8 -> LogicalButton.DOWN
        KEYCODE_4 -> LogicalButton.LEFT
        KEYCODE_6 -> LogicalButton.RIGHT

        // Compact QWERTY: the cross *around* D. D is the centre of that cross, not its bottom —
        // the key below E is C. (The 2026-08-09 spike bound E/D/A/G and was geometrically wrong.)
        KEYCODE_E -> LogicalButton.UP
        KEYCODE_C -> LogicalButton.DOWN
        KEYCODE_A -> LogicalButton.LEFT
        KEYCODE_G -> LogicalButton.RIGHT

        // ---- Mini Controller face buttons ---------------------------------------------------
        // Genesis reads the diamond left-to-right as A-B-C, so printed X becomes Genesis A.
        // Only the GBA has shoulders, so on GB, GBC and NES these keys stay unbound rather than
        // being wired to a button the console does not have — otherwise the legend advertises an
        // "L" that does nothing, which is worse than showing nothing at all.
        KEYCODE_BUTTON_X -> when {
            system.isGenesis -> LogicalButton.FACE_A
            system.hasShoulders -> LogicalButton.L
            else -> null
        }
        KEYCODE_BUTTON_A -> if (system.isGenesis) LogicalButton.FACE_B else LogicalButton.FACE_A
        KEYCODE_BUTTON_B -> if (system.isGenesis) LogicalButton.FACE_C else LogicalButton.FACE_B
        KEYCODE_BUTTON_Y -> if (system.hasShoulders) LogicalButton.R else null
        KEYCODE_BUTTON_START -> LogicalButton.START
        KEYCODE_BUTTON_SELECT -> if (system.hasSelect) LogicalButton.SELECT else null

        // ---- Compact QWERTY action buttons --------------------------------------------------
        // J and L side by side with B on the left: the real GBA arrangement.
        KEYCODE_J -> if (system.isGenesis) LogicalButton.FACE_A else LogicalButton.FACE_B
        KEYCODE_L -> if (system.isGenesis) LogicalButton.FACE_B else LogicalButton.FACE_A
        KEYCODE_U -> if (system.hasShoulders) LogicalButton.L else null
        KEYCODE_O -> when {
            system.isGenesis -> LogicalButton.FACE_C
            system.hasShoulders -> LogicalButton.R
            else -> null
        }
        KEYCODE_SPACE -> LogicalButton.START
        KEYCODE_SHIFT_LEFT -> if (system.hasSelect) LogicalButton.SELECT else null

        // ---- T9 / Classic action buttons ----------------------------------------------------
        // Fixed physical roles, the same on every system, so a player never relearns the tile.
        KEYCODE_7 -> if (system.isGenesis) LogicalButton.FACE_A else LogicalButton.FACE_B
        KEYCODE_9 -> if (system.isGenesis) LogicalButton.FACE_B else LogicalButton.FACE_A
        KEYCODE_1 -> if (system.hasShoulders) LogicalButton.L else null
        KEYCODE_3 -> when {
            system.isGenesis -> LogicalButton.FACE_C
            system.hasShoulders -> LogicalButton.R
            else -> null
        }
        KEYCODE_STAR -> if (system.hasSelect) LogicalButton.SELECT else null
        KEYCODE_POUND -> LogicalButton.START
        KEYCODE_0 -> LogicalButton.MENU

        // Classic's d-pad centre, and its right soft key. Classic has no key that emits BACK and
        // its left soft key reports HOME, which never reaches us — so DEL is its only escape.
        KEYCODE_ENTER -> LogicalButton.FACE_A
        KEYCODE_DEL -> LogicalButton.MENU

        // Compact QWERTY's hardware Back. The only tile that emits it (§2.6 invariant 2).
        KEYCODE_BACK -> LogicalButton.MENU

        else -> null
    }

    /**
     * The Sundial's nine buttons. Directions come from the *ring*, including its horizontal axis:
     * the `DPAD_LEFT`/`DPAD_RIGHT` corners are semantically named right and physically wrong, and
     * nobody can play a platformer with left and right at the top corners of a dome.
     *
     * Nine buttons, ten needed for GBA — so **GBA drops Select here** (§2.4). L and R stay because
     * they are live gameplay inputs in racing and action titles while Select is almost never pressed
     * mid-action; Select remains reachable from the touch menu.
     *
     * Returns null both for keys the Sundial does not own and for its intentionally unassigned
     * corners. The caller distinguishes those cases with [sundialButtons], so only the former can
     * fall through to [shared].
     */
    private fun sundial(keyCode: Int, system: GameSystem): LogicalButton? = when (keyCode) {
        KEYCODE_MEDIA_PREVIOUS -> LogicalButton.LEFT
        KEYCODE_MEDIA_NEXT -> LogicalButton.RIGHT
        KEYCODE_MEDIA_PLAY_PAUSE -> LogicalButton.FACE_A
        KEYCODE_ENTER -> LogicalButton.FACE_B
        KEYCODE_TAB -> LogicalButton.START

        KEYCODE_DPAD_LEFT -> when {
            system.hasShoulders -> LogicalButton.L      // GBA: this is where Select would have gone
            system.hasSelect -> LogicalButton.SELECT
            else -> null                                 // Genesis has neither
        }
        KEYCODE_DPAD_RIGHT -> when {
            system.hasShoulders -> LogicalButton.R
            system.isGenesis -> LogicalButton.FACE_C
            else -> null
        }

        else -> null
    }

    /** Keys emitted by the Sundial's buttons rather than its directional ring. */
    private val sundialButtons: Set<Int> = setOf(
        KEYCODE_MEDIA_PREVIOUS,
        KEYCODE_MEDIA_NEXT,
        KEYCODE_MEDIA_PLAY_PAUSE,
        KEYCODE_ENTER,
        KEYCODE_TAB,
        KEYCODE_DPAD_LEFT,
        KEYCODE_DPAD_RIGHT,
    )

    /**
     * SPEC.md §3.2. Counter-clockwise: the tile ends up on the right of the screen with the d-pad
     * at the bottom, so the key at the tile's physical top must move the character toward the
     * viewer's left.
     *
     * Applied at the logical layer, which is what keeps it one transform instead of five.
     */
    fun rotateDirectionCcw(button: LogicalButton): LogicalButton = when (button) {
        LogicalButton.UP -> LogicalButton.LEFT
        LogicalButton.LEFT -> LogicalButton.DOWN
        LogicalButton.DOWN -> LogicalButton.RIGHT
        LogicalButton.RIGHT -> LogicalButton.UP
        else -> button
    }

    /** The mirror image, for a device held rotated the other way in Auto mode. */
    fun rotateDirectionCw(button: LogicalButton): LogicalButton = when (button) {
        LogicalButton.UP -> LogicalButton.RIGHT
        LogicalButton.RIGHT -> LogicalButton.DOWN
        LogicalButton.DOWN -> LogicalButton.LEFT
        LogicalButton.LEFT -> LogicalButton.UP
        else -> button
    }

    /**
     * Keys the OS consumes before dispatch. Listed so nothing tries to bind them and quietly
     * produces dead code: `HOME` (3), `CALL` (5), `ENDCALL` (6), `APP_SWITCH` (187).
     */
    val neverArrives: Set<Int> = setOf(
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_CALL,
        KeyEvent.KEYCODE_ENDCALL,
        KeyEvent.KEYCODE_APP_SWITCH,
    )
}
