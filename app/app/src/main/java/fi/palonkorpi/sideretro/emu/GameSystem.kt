package fi.palonkorpi.sideretro.emu

/**
 * The five systems of SPEC.md §1. Three cores serve them: mGBA covers GB, GBC *and* GBA, which is
 * the outcome of the SameBoy reversal (§1, Reversal 1) — SameBoy ran Game Boy at ~74% speed on this
 * SoC at 110% CPU, against mGBA's 59–60 fps at 38%.
 *
 * @param coreName    the `lib<name>_libretro_android.so` shipped in jniLibs/arm64-v8a.
 * @param extensions  lowercase, no dot. Determines both the badge in the library and the core.
 * @param fbWidth     framebuffer width in pixels, before any aspect correction.
 * @param fbHeight    framebuffer height in pixels.
 * @param displayAspect the shape the system was *meant* to be seen at. For GB and GBA this equals
 *                    the framebuffer shape — they had square pixels and real LCDs, so there is
 *                    nothing to correct. For NES and Genesis it is 4:3, the CRT they were designed
 *                    for, which is also exactly the shape of this panel rotated (§4.3).
 */
enum class GameSystem(
    val label: String,
    val badge: String,
    val coreName: String,
    val extensions: List<String>,
    val fbWidth: Int,
    val fbHeight: Int,
    val displayAspect: Float,
) {
    GB("Game Boy", "GB", "mgba", listOf("gb"), 160, 144, 160f / 144f),
    GBC("Game Boy Color", "GBC", "mgba", listOf("gbc"), 160, 144, 160f / 144f),
    GBA("Game Boy Advance", "GBA", "mgba", listOf("gba"), 240, 160, 240f / 160f),
    NES("NES", "NES", "fceumm", listOf("nes", "fds", "unf", "unif"), 256, 240, 4f / 3f),
    GENESIS("Mega Drive", "MD", "clownmdemu", listOf("md", "gen", "smd", "bin"), 320, 224, 4f / 3f);

    val isGenesis: Boolean get() = this == GENESIS

    /** Only the GBA has shoulder buttons. Drives both the keymap and the legend. */
    val hasShoulders: Boolean get() = this == GBA

    /** The Genesis pad has no Select. Neither does it have a fourth face button in 3-button mode. */
    val hasSelect: Boolean get() = !isGenesis

    /** Square-pixel aspect — what "Sharp" means (§4.3). */
    val pixelAspect: Float get() = fbWidth.toFloat() / fbHeight.toFloat()

    /**
     * The aspect ratio the *core* reports to LibretroDroid, which is what the native renderer
     * letterboxes to inside whatever box we give the view. See `Scaling` for why this matters and
     * how it is verified on device.
     *
     * All three cores report their framebuffer shape (libretro treats `aspect_ratio == 0` as
     * "use base_width / base_height"), so this is the square-pixel aspect for every system.
     * ⚠️ If a core is ever swapped, re-verify this against a device screenshot before trusting it.
     */
    val coreReportedAspect: Float get() = pixelAspect

    companion object {
        fun forExtension(extension: String): GameSystem? {
            val ext = extension.lowercase()
            return entries.firstOrNull { ext in it.extensions }
        }

        val allExtensions: Set<String> = entries.flatMap { it.extensions }.toSet()
    }
}
