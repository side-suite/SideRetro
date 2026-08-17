package fi.palonkorpi.sideretro.ui

import fi.palonkorpi.sideretro.emu.GameSystem

/**
 * What the space around the picture is dressed as (SPEC.md §5.3).
 *
 * The SP-01 is the only phone where this space is free. Every other emulator fills it with glass
 * buttons — that is *why* the space exists in their layouts — and we rejected touch controls, so
 * there is a band above and below every picture with nothing in it. Empty it reads as letterboxing;
 * dressed it reads as a machine.
 *
 * ### Evocation, not reproduction
 *
 * These are colour palettes and proportions, and every word drawn on them is ours: the wordmark is
 * always "SideRetro" and the second word is a factual description of the machine ("COLOR", "16-BIT").
 * No console's name, logo, tagline or printed legend is reproduced — those are trademarks, and a
 * grey plate with two pinstripes is not. The recognition comes from colour and layout, which is
 * exactly the part that is ours to make.
 */
data class ConsoleSkin(
    /** Plate gradient, top to bottom. Plastic is never one flat colour under a light. */
    val plateTop: Int,
    val plateBottom: Int,
    /** The recess the picture sits in, and the line that separates it from the plate. */
    val well: Int,
    val wellEdge: Int,
    val wellRadiusDp: Float,
    /** Printed lines above the well. Empty means this machine had none. */
    val stripes: List<Int>,
    /** The power lamp, drawn only when there is no stripe band to draw instead. */
    val led: Int,
    /** Wordmark colour, and the small print beside it. */
    val ink: Int,
    val subInk: Int,
    /** Per-letter wordmark colours, for the one machine whose mark was polychrome. */
    val letterInks: List<Int>?,
    /** The second word. Factual, never a trademark. */
    val mode: String,
    /** What colour this machine's screen throws in [fi.palonkorpi.sideretro.settings.Faceplate.LIT]. */
    val glow: Int,
) {
    companion object {

        fun of(system: GameSystem): ConsoleSkin = when (system) {
            // Warm grey plastic, a slate well, and the two pinstripes that are the whole visual
            // signature of the original handheld. Glow is the mono LCD's yellow-green.
            GameSystem.GB -> ConsoleSkin(
                plateTop = 0xFFD5D7CD.toInt(),
                plateBottom = 0xFFBCBEB4.toInt(),
                well = 0xFF6D717B.toInt(),
                wellEdge = 0xFF54586180.toInt(),
                wellRadiusDp = 5f,
                stripes = listOf(0xFF8E3C63.toInt(), 0xFF3E4396.toInt()),
                led = 0xFFCF4536.toInt(),
                ink = 0xFF2A2C31.toInt(),
                subInk = 0xFF6E7179.toInt(),
                letterInks = null,
                mode = "MONO",
                glow = 0xFF9BBC0F.toInt(),
            )

            // Near-black plate, square well, polychrome wordmark: the one machine whose mark was
            // built out of colour, which is also the thing the machine was named for.
            GameSystem.GBC -> ConsoleSkin(
                plateTop = 0xFF2B2B34.toInt(),
                plateBottom = 0xFF1A1A21.toInt(),
                well = 0xFF0C0C10.toInt(),
                wellEdge = 0xFF3A3A45.toInt(),
                wellRadiusDp = 3f,
                stripes = emptyList(),
                led = 0xFFE04B4B.toInt(),
                ink = 0xFFE8E8EE.toInt(),
                subInk = 0xFF9A9AA6.toInt(),
                letterInks = listOf(
                    0xFF3D8FE0.toInt(), 0xFFE0C233.toInt(), 0xFF52B24A.toInt(), 0xFFD8453D.toInt(),
                    0xFF8B5FCF.toInt(), 0xFF3D8FE0.toInt(), 0xFFE0C233.toInt(), 0xFF52B24A.toInt(),
                    0xFFD8453D.toInt(),
                ),
                mode = "COLOR",
                glow = 0xFFEDEDE0.toInt(),
            )

            // Glossy black, and the only well with a large radius — the wide window of the machine
            // this evokes was a rounded rectangle, not a square.
            GameSystem.GBA -> ConsoleSkin(
                plateTop = 0xFF24262C.toInt(),
                plateBottom = 0xFF141519.toInt(),
                well = 0xFF07080A.toInt(),
                wellEdge = 0xFF3A3D45.toInt(),
                wellRadiusDp = 13f,
                stripes = emptyList(),
                led = 0xFF4CD08A.toInt(),
                ink = 0xFFC0C5CD.toInt(),
                subInk = 0xFF7E838C.toInt(),
                letterInks = null,
                mode = "32-BIT",
                glow = 0xFF9AD0FF.toInt(),
            )

            // Console-grey plastic with the dark red trim of a front-loading deck.
            GameSystem.NES -> ConsoleSkin(
                plateTop = 0xFFC6C2B7.toInt(),
                plateBottom = 0xFFA8A499.toInt(),
                well = 0xFF16161A.toInt(),
                wellEdge = 0xFF5D5A53.toInt(),
                wellRadiusDp = 4f,
                stripes = listOf(0xFF8E2A22.toInt(), 0xFF8E2A22.toInt()),
                led = 0xFFD03A2A.toInt(),
                ink = 0xFF7A2018.toInt(),
                subInk = 0xFF5E5B54.toInt(),
                letterInks = null,
                mode = "8-BIT",
                glow = 0xFFFFFFFF.toInt(),
            )

            // Black deck, one red stripe, silver mark — the 16-bit generation's whole look.
            GameSystem.GENESIS -> ConsoleSkin(
                plateTop = 0xFF1B1C21.toInt(),
                plateBottom = 0xFF0D0E11.toInt(),
                well = 0xFF08080A.toInt(),
                wellEdge = 0xFF3B3D44.toInt(),
                wellRadiusDp = 4f,
                stripes = listOf(0xFFB3392C.toInt()),
                led = 0xFFE23B2E.toInt(),
                ink = 0xFFD9DDE3.toInt(),
                subInk = 0xFF8A8F97.toInt(),
                letterInks = null,
                mode = "16-BIT",
                glow = 0xFFDCE6FF.toInt(),
            )
        }
    }
}
