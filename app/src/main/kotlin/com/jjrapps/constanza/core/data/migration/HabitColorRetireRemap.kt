package com.jjrapps.constanza.core.data.migration

import com.jjrapps.constanza.core.ui.theme.HabitColor
import com.jjrapps.constanza.core.ui.theme.clampToHabitBand

/**
 * The frozen "22-preset legible-band palette" -> "21-preset legible-band palette" habit-colour map,
 * in [HabitColorRetoneRemap]'s exact shape: a literal-int map plus a total `normalize` function, for
 * the same reason that file gives — this describes what the palette meant just before `BLUE_GREY`
 * was retired and what it means now, and both sides must stay literal ints so a future retirement
 * cannot silently change what this map has always said.
 *
 * **Why `BLUE_GREY` needs an explicit entry, unlike the retired `SILVER` before it
 * ([HabitColorRetoneRemap]'s own KDoc).** [clampToHabitBand] would not catch this one: `#849FAC`
 * measures 7.01:1, inside the `[7:1, 11:1]` band, so an unmapped `#849FAC` would clamp straight back
 * to itself — silently becoming a *custom* grey, exactly the near-neutral identity this retirement
 * removes. `SILVER`'s `#E0E0E0` measured 14.81:1, outside the band, so its retirement needed no
 * entry; `BLUE_GREY`'s legibility was never the problem, so it needs one.
 *
 * **Why `#00ABBD` ([HabitColor.CYAN]), not the nearer-by-ΔE [HabitColor.BROWN].** `BROWN`
 * (`#B0958B`) measures ΔE 24.0 against `#849FAC`, against `CYAN`'s 25.4 — a practical tie by ΔE
 * alone. But ΔE here is mostly matching *desaturation* rather than family: `BROWN` is a warm beige,
 * where `BLUE_GREY` was a cool grey-blue. `CYAN` keeps the cool family a person choosing "blue grey"
 * was reaching for.
 */
internal object HabitColorRetireRemap {

    /**
     * Retired preset argb -> its chosen replacement. One entry so far: `BLUE_GREY` (`#849FAC`) ->
     * [HabitColor.CYAN] (`#00ABBD`).
     */
    val LEGACY_TO_CURRENT: Map<Int, Int> = mapOf(
        0xFF849FAC.toInt() to 0xFF00ABBD.toInt(), // BLUE_GREY -> CYAN
    )

    /**
     * A total function: an explicitly remapped retired preset takes its mapped current value; every
     * current preset, any earlier-retired preset (`SILVER`), and any genuine custom colour all fall
     * through to [clampToHabitBand] instead of being coerced onto some arbitrary palette member.
     */
    fun normalize(argb: Int): Int = LEGACY_TO_CURRENT[argb] ?: clampToHabitBand(argb)
}
