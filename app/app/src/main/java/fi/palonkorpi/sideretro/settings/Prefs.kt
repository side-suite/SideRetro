package fi.palonkorpi.sideretro.settings

import android.content.Context
import android.content.pm.ActivityInfo

/**
 * SPEC.md §8 — the complete settings list. Four entries, all global.
 *
 * Deliberately short: everything else is a decision we made so the user doesn't have to. Per-game
 * memory was considered and rejected for predictability.
 */
enum class Orientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
    AUTO("Auto");

    /**
     * ⚠️ `SCREEN_ORIENTATION_LANDSCAPE` is the counter-clockwise turn on a phone whose natural
     * orientation is portrait — the tile ends up on the right, which is what §3.2 specifies.
     * The direction transform does not depend on this being right: `InputRouter` reads the display's
     * actual rotation instead.
     */
    val activityInfoValue: Int
        get() = when (this) {
            PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            // "sensor", not "user": Auto is SideRetro's own setting, and the device ships with the
            // OS auto-rotate switched off, so following the OS would make the feature invisible.
            AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
}

/** SPEC.md §4.3. Naming follows the spec; see `Scaling` for the arithmetic. */
enum class ScalingMode(val label: String, val blurb: String) {
    SHARP("Sharp", "The pixels as the console drew them"),
    LARGE("Large", "The picture as the TV showed it"),
    FILL("Fill", "Stretched to the whole screen"),
}

/**
 * The only two display treatments that survived device review. CRT is the intended default;
 * Off is there for a completely unprocessed picture.
 */
enum class ScreenFilter(val label: String) {
    OFF("Off"),
    CRT("CRT"),

    ;

    companion object {
        /**
         * v1 previously persisted CRISP, SHARP and LCD. Keep old installs working while removing
         * the unreviewed choices: they all migrate to the approved default rather than failing to
         * parse or selecting the broken LCD shader.
         */
        fun fromStoredName(name: String?): ScreenFilter = when (name) {
            OFF.name -> OFF
            CRT.name, "CRISP", "SHARP", "LCD", null -> CRT
            else -> CRT
        }
    }
}

/**
 * SPEC.md §5.3 — what fills the space around the picture.
 *
 * On any other phone this space is where the touch controls go. We rejected those (§3), so it is
 * genuinely free here, and empty it reads as letterboxing. Two dressings, deliberately not more:
 * [CONSOLE] makes the phone look like the machine, [LIT] makes the picture look emitted rather than
 * placed. See `ConsoleSkin` for why neither reproduces any trademark.
 */
enum class Faceplate(val label: String) {
    OFF("Off"),
    CONSOLE("Console"),
    LIT("Lit"),
}

class Prefs(context: Context) {

    private val store = context.getSharedPreferences("sideretro", Context.MODE_PRIVATE)

    var orientation: Orientation
        get() = read(KEY_ORIENTATION, Orientation.PORTRAIT, Orientation.entries)
        set(value) = store.edit().putString(KEY_ORIENTATION, value.name).apply()

    var scaling: ScalingMode
        get() = read(KEY_SCALING, ScalingMode.SHARP, ScalingMode.entries)
        set(value) = store.edit().putString(KEY_SCALING, value.name).apply()

    var screenFilter: ScreenFilter
        get() {
            val stored = store.getString(KEY_FILTER, null)
            val filter = ScreenFilter.fromStoredName(stored)
            if (stored != null && stored != filter.name) {
                store.edit().putString(KEY_FILTER, filter.name).apply()
            }
            return filter
        }
        set(value) = store.edit().putString(KEY_FILTER, value.name).apply()

    var faceplate: Faceplate
        get() = read(KEY_FACEPLATE, Faceplate.LIT, Faceplate.entries)
        set(value) = store.edit().putString(KEY_FACEPLATE, value.name).apply()

    /** §3.3. False = labels fixed, the default. */
    var positionalFaceButtons: Boolean
        get() = store.getBoolean(KEY_POSITIONAL, false)
        set(value) = store.edit().putBoolean(KEY_POSITIONAL, value).apply()

    /** §5.1. The legend band under the game in portrait. */
    var legendEnabled: Boolean
        get() = store.getBoolean(KEY_LEGEND, true)
        set(value) = store.edit().putBoolean(KEY_LEGEND, value).apply()

    /**
     * Not a setting — the persisted Downloads folder grant (§7.2). Kept here because it is the one
     * other thing that must survive a restart, and a second store for one string is not worth it.
     */
    var downloadsTree: String?
        get() = store.getString(KEY_DOWNLOADS_TREE, null)
        set(value) = store.edit().putString(KEY_DOWNLOADS_TREE, value).apply()

    private fun <T : Enum<T>> read(key: String, fallback: T, values: List<T>): T {
        val name = store.getString(key, null) ?: return fallback
        return values.firstOrNull { it.name == name } ?: fallback
    }

    private companion object {
        const val KEY_ORIENTATION = "orientation"
        const val KEY_SCALING = "scaling"
        const val KEY_POSITIONAL = "positional_face_buttons"
        const val KEY_LEGEND = "legend"
        const val KEY_DOWNLOADS_TREE = "downloads_tree"
        const val KEY_FILTER = "screen_filter"
        const val KEY_FACEPLATE = "faceplate"
    }
}
