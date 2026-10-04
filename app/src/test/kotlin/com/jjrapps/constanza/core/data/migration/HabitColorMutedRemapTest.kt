package com.jjrapps.constanza.core.data.migration

import com.jjrapps.constanza.core.ui.theme.HabitPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [HabitColorMutedRemap] (Room v7 -> v8, backup schema 5): the 21 retired legible-band presets onto
 * the 12 muted graphite ones. The retired side is pinned as literals, because those enum members no
 * longer exist and a frozen map must keep meaning what it meant.
 */
class HabitColorMutedRemapTest {

    @Test
    fun `every retired preset has exactly one entry`() {
        assertEquals(RETIRED_LEGIBLE_BAND_PRESETS.toSet(), HabitColorMutedRemap.LEGACY_TO_CURRENT.keys)
    }

    @Test
    fun `every retired preset maps to a current preset`() {
        HabitColorMutedRemap.LEGACY_TO_CURRENT.forEach { (legacy, current) ->
            assertTrue(
                HabitPalette.contains(current),
                "0x%08X maps to 0x%08X, which is not one of the twelve muted presets".format(legacy, current),
            )
        }
    }

    @Test
    fun `no retired preset is still offered`() {
        RETIRED_LEGIBLE_BAND_PRESETS.forEach { legacy ->
            assertFalse(HabitPalette.contains(legacy), "0x%08X was retired and must read as custom".format(legacy))
        }
    }

    @Test
    fun `current presets pass through unchanged`() {
        HabitPalette.ARGB.forEach { argb ->
            assertEquals(argb, HabitColorMutedRemap.normalize(argb), "0x%08X is already current".format(argb))
        }
    }

    /** Unlike the v5/v6 maps there is no clamp fallthrough: a custom colour is the user's own pick
     *  and stays byte-identical, however dark or light it is. */
    @Test
    fun `custom colours are left untouched`() {
        listOf(0xFF3D2B1F, 0xFF000000, 0xFFFFFFFF, 0xFF8791FF, 0xFFC2C2C2).map { it.toInt() }.forEach { custom ->
            assertEquals(custom, HabitColorMutedRemap.normalize(custom), "0x%08X is custom".format(custom))
        }
    }

    @Test
    fun `normalising twice is the same as normalising once`() {
        (RETIRED_LEGIBLE_BAND_PRESETS + HabitPalette.ARGB + CUSTOM_SAMPLE).forEach { argb ->
            val once = HabitColorMutedRemap.normalize(argb)
            assertEquals(once, HabitColorMutedRemap.normalize(once), "0x%08X is not idempotent".format(argb))
        }
    }

    /** The hue-family overrides the table's KDoc documents, pinned so a recomputation that silently
     *  falls back to raw nearest-ΔE fails here. */
    @Test
    fun `hue-family overrides hold`() {
        mapOf(
            0xFF4CAF50 to 0xFF7FA889, // GREEN -> SAGE
            0xFFF5B907 to 0xFFC4A77A, // AMBER -> SAND
            0xFF55D7B8 to 0xFF6FA6A6, // MINT -> TEAL
            0xFF469DFF to 0xFF7D9CC4, // BLUE -> BLUE
            0xFFB992FF to 0xFFA98BB8, // LILAC -> LAVENDER
        ).forEach { (legacy, muted) ->
            assertEquals(muted.toInt(), HabitColorMutedRemap.normalize(legacy.toInt()))
        }
    }

    private companion object {
        /** The 21-preset legible-band palette, in its old grid order. */
        val RETIRED_LEGIBLE_BAND_PRESETS = listOf(
            0xFF4CAF50, 0xFFE860FF, 0xFFFF6D48, 0xFF03A9F4, 0xFFF5B907, 0xFFF48FB1, 0xFF55D7B8,
            0xFF8896E3, 0xFFDEC233, 0xFFFC6799, 0xFFB1CA33, 0xFF00AE9D, 0xFFD477E4, 0xFF7CB342,
            0xFF00ABBD, 0xFFFF9800, 0xFFB0958B, 0xFF469DFF, 0xFFA19F25, 0xFFB992FF, 0xFFE9BA75,
        ).map { it.toInt() }

        val CUSTOM_SAMPLE = listOf(0xFF3D2B1F, 0xFF123456).map { it.toInt() }
    }
}
