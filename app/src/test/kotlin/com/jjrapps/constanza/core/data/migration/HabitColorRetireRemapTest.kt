package com.jjrapps.constanza.core.data.migration

import com.jjrapps.constanza.core.ui.theme.HabitColor
import com.jjrapps.constanza.core.ui.theme.HabitPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The retired `BLUE_GREY` preset (Blue Grey 400), literal rather than `HabitColor.BLUE_GREY`
 *  because that member no longer exists — the same class of literal [HabitColorRetireRemap]'s own
 *  KDoc requires for a value one side of a frozen map used to mean. */
private const val RETIRED_BLUE_GREY = 0xFF849FAC.toInt()

/**
 * [HabitColorRetireRemap] is the "22-preset legible-band palette" -> "21-preset legible-band
 * palette" analogue of [HabitColorRetoneRemap]'s v4->v5 map, so this test follows that file's shape:
 * pin the map's one entry, pin the fall-through behaviour for a current preset and for a genuine
 * custom colour, and pin that the mapped value is itself a current preset.
 */
class HabitColorRetireRemapTest {

    @Test
    fun `the retired blue grey preset maps to cyan specifically`() {
        assertEquals(HabitColor.CYAN.argb, HabitColorRetireRemap.normalize(RETIRED_BLUE_GREY))
    }

    @Test
    fun `the map holds exactly one entry`() {
        assertEquals(1, HabitColorRetireRemap.LEGACY_TO_CURRENT.size, "one retirement so far: BLUE_GREY")
    }

    @Test
    fun `the mapped value is a current preset`() {
        HabitColorRetireRemap.LEGACY_TO_CURRENT.values.forEach { mapped ->
            assertTrue(
                HabitPalette.contains(mapped),
                "0x%08X is not one of the 21 current presets".format(mapped),
            )
        }
    }

    /** Every current preset carries no entry — it did not move value, so it must pass through
     *  [HabitColorRetireRemap.normalize] unchanged via the [clampToHabitBand] fallthrough. */
    @Test
    fun `a current preset passes through unchanged`() {
        val current = HabitColor.CYAN.argb

        assertEquals(current, HabitColorRetireRemap.normalize(current))
    }

    /** A colour that was never any version of this palette also falls through to the clamp — if it
     *  is already legible it survives untouched, exactly as [clampToHabitBand] documents. */
    @Test
    fun `a custom colour already inside the band passes through unchanged`() {
        val alreadyLegible = HabitColor.TEAL.argb

        assertEquals(alreadyLegible, HabitColorRetireRemap.normalize(alreadyLegible))
    }
}
