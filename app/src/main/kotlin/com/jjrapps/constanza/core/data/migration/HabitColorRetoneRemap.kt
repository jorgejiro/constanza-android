package com.jjrapps.constanza.core.data.migration

import com.jjrapps.constanza.core.ui.theme.clampToHabitBand

/**
 * The frozen "23-preset warm-dark palette" -> "22-preset legible-band palette" habit-colour
 * bijection, in [HabitColorRemap]'s exact shape: a literal-int map plus a total `normalize`
 * function, for the same reason that file gives — this describes what the *previous* palette meant
 * and what the *current* one means at the moment this re-tone was written, and both sides must stay
 * literal ints so a future re-tone cannot silently change what this map has always said.
 *
 * **Why every already-persisted habit needs this at all.** [HabitPalette.contains] answers `false`
 * for any int not among the 22 current presets, and every habit created before this re-tone holds
 * one of the *previous* palette's argb ints — none of which survive unchanged, because every preset
 * that changed value changed precisely because it needed to (into the `[7:1, 11:1]` band `HabitColor`'s
 * KDoc measures). Left alone, [HabitPalette.contains] would misread every existing habit as a custom
 * colour instead of the preset it was assigned from.
 *
 * **Remap, then clamp — not remap alone.** [LEGACY_TO_CURRENT] only ever held 17 entries: the five
 * presets whose value did not change ([HabitColorRemap]'s own six-pastel scope is unrelated and long
 * migrated) need no entry, and the retired `SILVER` (`#E0E0E0`) gets none either — it was never a
 * "this preset moved" case, it was retired outright. Both of those, plus any genuinely custom colour
 * a habit already held, fall through [normalize]'s `?:` to [clampToHabitBand], which is exactly what
 * that function is for: a stored colour that is not a recognised preset is either already inside the
 * legible band (and passes through unchanged) or gets pulled into it exactly as a fresh custom pick
 * would.
 */
internal object HabitColorRetoneRemap {

    /**
     * Previous-palette argb -> current-palette argb, for the 17 presets whose hue or tone actually
     * moved. `GREEN` (`#4CAF50`), `LIGHT_BLUE` (`#03A9F4`), `LIGHT_GREEN` (`#7CB342`), `PINK`
     * (`#F48FB1`) and `ORANGE` (`#FF9800`) are deliberately absent — their argb did not change, so
     * there is nothing to remap.
     */
    val LEGACY_TO_CURRENT: Map<Int, Int> = mapOf(
        0xFFF44336.toInt() to 0xFFFF6D48.toInt(), // RED
        0xFFFFC107.toInt() to 0xFFF5B907.toInt(), // AMBER
        0xFFE040FB.toInt() to 0xFFE860FF.toInt(), // VIOLET
        0xFF9575CD.toInt() to 0xFFB992FF.toInt(), // LILAC
        0xFF009688.toInt() to 0xFF00AE9D.toInt(), // TEAL
        0xFFF06292.toInt() to 0xFFFC6799.toInt(), // MAGENTA
        0xFF64FFDA.toInt() to 0xFF55D7B8.toInt(), // MINT
        0xFF9E9D24.toInt() to 0xFFA19F25.toInt(), // OLIVE
        0xFF78909C.toInt() to 0xFF849FAC.toInt(), // BLUE_GREY
        0xFFFFEB3B.toInt() to 0xFFDEC233.toInt(), // YELLOW
        0xFFBA68C8.toInt() to 0xFFD477E4.toInt(), // PURPLE
        0xFF0097A7.toInt() to 0xFF00ABBD.toInt(), // CYAN
        0xFFFFCC80.toInt() to 0xFFE9BA75.toInt(), // PEACH
        0xFF7986CB.toInt() to 0xFF8896E3.toInt(), // INDIGO
        0xFFA1887F.toInt() to 0xFFB0958B.toInt(), // BROWN
        0xFFC0CA33.toInt() to 0xFFB1CA33.toInt(), // LIME
        0xFF2196F3.toInt() to 0xFF469DFF.toInt(), // BLUE
    )

    /**
     * A total function: an explicitly remapped legacy preset takes its mapped current value; the
     * retired `SILVER` (`#E0E0E0`), any legacy preset that did not move, and any genuine custom colour
     * all fall through to [clampToHabitBand] instead of being coerced onto some arbitrary palette
     * member. `SILVER` in particular is asserted by [HabitColorRetoneRemapTest] rather than merely
     * assumed to fall through correctly: `#E0E0E0` measures `14.81:1` against
     * `ConstanzaColors.Background`, above the `11.0` ceiling, so it clamps to the same `#C2C2C2` pure
     * white clamps to — both are fully desaturated, and [clampToHabitBand]'s value bisection for a
     * fixed hue/saturation converges on the same grey regardless of where above the ceiling it
     * started.
     */
    fun normalize(argb: Int): Int = LEGACY_TO_CURRENT[argb] ?: clampToHabitBand(argb)
}
