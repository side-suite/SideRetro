package fi.palonkorpi.sideretro.input

import android.view.KeyEvent
import fi.palonkorpi.sideretro.emu.GameSystem

/**
 * The middle layer of SPEC.md §2.2:
 *
 *     tile keycode  ──►  logical button  ──►  keycode sent to LibretroDroid
 *
 * Nothing may map a tile key straight to a keycode. Everything that varies — rotation (§3.2),
 * the labels/positions option (§3.3), which system is running — acts here, on one enum, instead
 * of multiplying into five per-tile special cases.
 *
 * [FACE_C] exists only for the Genesis three-button pad.
 */
enum class LogicalButton {
    UP, DOWN, LEFT, RIGHT,
    FACE_A, FACE_B, FACE_C,
    L, R,
    START, SELECT,

    /** Not an emulator input. Opens SideRetro's own menu (§5.2). Never reaches the core. */
    MENU;

    val isDirection: Boolean
        get() = this == UP || this == DOWN || this == LEFT || this == RIGHT
}

/**
 * The keycode actually handed to `GLRetroView.sendKeyEvent`.
 *
 * ### ⚠️ Do not "correct" this into a swap. It was wrong that way and shipped wrong.
 *
 * `GamepadsManager.getGamepadKeyEvent` swaps A↔B and X↔Y (96↔97, 99↔100, verified by decompiling
 * the 0.14.0 artifact). `sendKeyEvent` does not call it — only `onKeyDown` does. That much SPEC.md
 * §2.1 got right. But the swap exists for a reason that does not apply to us: it converts a
 * **physical Android gamepad**, which is Xbox-laid-out with A at the bottom, into RetroPad, which is
 * Nintendo-laid-out with B at the bottom. Native maps `AKEYCODE_BUTTON_A` to RetroPad A directly.
 *
 * So the right thing is the boring thing: **send the keycode whose name matches the RetroPad button
 * you want.** Pre-swapping applies the correction twice and mirrors every action button on every
 * tile — which is exactly what happened, and it survived a play-test because the homebrew test ROMs
 * never name a button. It took Pokémon saying "press the A button" to expose it.
 *
 * SPEC.md §2.1 constraint 1 still holds: LibretroDroid's `convertAndroidToLibretroKey` is a
 * 20-entry allow-list. Every value here is inside it; nothing passes through raw.
 */
enum class RetroPad(val androidKeyCode: Int) {
    A(KeyEvent.KEYCODE_BUTTON_A),
    B(KeyEvent.KEYCODE_BUTTON_B),
    X(KeyEvent.KEYCODE_BUTTON_X),
    Y(KeyEvent.KEYCODE_BUTTON_Y),
    L1(KeyEvent.KEYCODE_BUTTON_L1),
    R1(KeyEvent.KEYCODE_BUTTON_R1),
    START(KeyEvent.KEYCODE_BUTTON_START),
    SELECT(KeyEvent.KEYCODE_BUTTON_SELECT),
    UP(KeyEvent.KEYCODE_DPAD_UP),
    DOWN(KeyEvent.KEYCODE_DPAD_DOWN),
    LEFT(KeyEvent.KEYCODE_DPAD_LEFT),
    RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT),
}

/**
 * SPEC.md §2.3. The Genesis pad's A, B, C sit on RetroPad Y, B, A respectively — the libretro
 * convention, and the reason [LogicalButton.FACE_C] cannot simply be folded into the others.
 */
fun LogicalButton.toRetroPad(system: GameSystem): RetroPad? = when (this) {
    LogicalButton.UP -> RetroPad.UP
    LogicalButton.DOWN -> RetroPad.DOWN
    LogicalButton.LEFT -> RetroPad.LEFT
    LogicalButton.RIGHT -> RetroPad.RIGHT
    LogicalButton.L -> RetroPad.L1
    LogicalButton.R -> RetroPad.R1
    LogicalButton.START -> RetroPad.START
    LogicalButton.SELECT -> RetroPad.SELECT
    LogicalButton.MENU -> null

    LogicalButton.FACE_A -> if (system.isGenesis) RetroPad.Y else RetroPad.A
    LogicalButton.FACE_B -> RetroPad.B
    LogicalButton.FACE_C -> if (system.isGenesis) RetroPad.A else null
}
