package com.jjrapps.constanza.core.data.migration

import com.jjrapps.constanza.core.ui.theme.HabitColor
import com.jjrapps.constanza.core.ui.theme.HabitPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The retired `SILVER` preset (Grey 300), literal rather than `HabitColor.SILVER` because that
 *  member no longer exists — this is exactly the class of literal [HabitColorRetoneRemap]'s own
 *  KDoc requires for a value one side of a frozen map used to mean. */
private const val RETIRED_SILVER = 0xFFE0E0E0.toInt()

/** [clampToHabitBand]'s own measured result for [RETIRED_SILVER] and for pure white alike — both are
 *  fully desaturated and above the ceiling, so both converge on the same grey. */
private const val CLAMPED_RETIRED_SILVER = 0xFFC2C2C2.toInt()

/**
 * [HabitColorRetoneRemap] is the "23-preset warm-dark palette" -> "22-preset legible-band palette"
 * analogue of [HabitColorRemap]'s v1->v2 bijection, so this test follows that file's shape: pin the
 * map's size and bijectivity, pin one representative entry, and pin the fall-through behaviour for
 * both the deliberately-omitted retired preset and a genuine custom colour.
 */
class HabitColorRetoneRemapTest {

    @Test
    fun `the map has one entry per preset that actually moved`() {
        val map = HabitColorRetoneRemap.LEGACY_TO_CURRENT

        assertEquals(EXPECTED_ENTRY_COUNT, map.keys.size, "expected one entry per preset whose value changed")
        assertEquals(EXPECTED_ENTRY_COUNT, map.values.toSet().size, "two legacy colours must never collapse onto one current colour")
    }

    @Test
    fun `legacy red maps to the new red specifically`() {
        val legacyRed = 0xFFF44336.toInt()
        val currentRed = 0xFFFF6D48.toInt()

        assertEquals(currentRed, HabitColorRetoneRemap.normalize(legacyRed))
    }

    @Test
    fun `every mapped value is a current preset`() {
        HabitColorRetoneRemap.LEGACY_TO_CURRENT.values.forEach { mapped ->
            assertTrue(
                HabitPalette.contains(mapped),
                "0x%08X is not one of the 22 current presets".format(mapped),
            )
        }
    }

    @Test
    fun `the five presets whose value did not change carry no entry`() {
        listOf(
            HabitColor.GREEN,
            HabitColor.LIGHT_BLUE,
            HabitColor.LIGHT_GREEN,
            HabitColor.PINK,
            HabitColor.ORANGE,
        ).forEach { unchanged ->
            assertEquals(
                unchanged.argb,
                HabitColorRetoneRemap.normalize(unchanged.argb),
                "${unchanged.name} did not change value and must pass through unchanged",
            )
        }
    }

    /**
     * The retired `SILVER` preset gets no explicit entry on purpose (`HabitColorRetoneRemap`'s KDoc):
     * it falls through to `clampToHabitBand`, which is asserted here rather than merely assumed, so a
     * future change to the map cannot silently start treating `SILVER` as an explicit remap target.
     */
    @Test
    fun `the retired silver preset falls through to the contrast clamp`() {
        assertEquals(CLAMPED_RETIRED_SILVER, HabitColorRetoneRemap.normalize(RETIRED_SILVER))
    }

    /** A colour that was never any version of this palette also falls through to the clamp — if it
     *  is already legible it survives untouched, exactly as [clampToHabitBand] documents. */
    @Test
    fun `a custom colour already inside the band passes through unchanged`() {
        val alreadyLegible = HabitColor.TEAL.argb

        assertEquals(alreadyLegible, HabitColorRetoneRemap.normalize(alreadyLegible))
    }

    private companion object {
        /** 17 of the 22 current presets moved value; the other five ([HabitColorRetoneRemap]'s own
         *  KDoc names them) did not and carry no entry. */
        const val EXPECTED_ENTRY_COUNT = 17
    }
}
