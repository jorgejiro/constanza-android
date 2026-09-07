package com.jjrapps.constanza.core.data.migration

import com.jjrapps.constanza.core.ui.theme.HabitColor
import com.jjrapps.constanza.core.ui.theme.HabitPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The retired `SILVER` preset (Grey 300), literal rather than `HabitColor.SILVER` because that
 *  member no longer exists — this is exactly the class of literal [HabitColorRetoneRemap]'s own
 *  KDoc requires for a value one side of a frozen map used to mean. */
private const val RETIRED_SILVER = 0xFFE0E0E0.toInt()

/** [clampToHabitBand]'s own measured result for [RETIRED_SILVER] and for pure white alike — both are
 *  fully desaturated and above the ceiling, so both converge on the same grey. */
private const val CLAMPED_RETIRED_SILVER = 0xFFC2C2C2.toInt()

/** The value schema version 5 meant by `BLUE_GREY`, literal for the same reason [RETIRED_SILVER] is
 *  — `HabitColor.BLUE_GREY` no longer exists, retired by schema version 6. */
private const val RETIRED_BLUE_GREY_IN_SCHEMA_V5 = 0xFF849FAC.toInt()

/**
 * The 22-preset legible-band palette exactly as schema version 5 meant it, as literals — the 17
 * values [HabitColorRetoneRemap.LEGACY_TO_CURRENT] maps to, plus the five presets that carried no
 * entry because they did not move value ([HabitColorRetoneRemap]'s own KDoc names them). Schema
 * version 6 retired `BLUE_GREY` (`#849FAC`) from this set, so it can no longer be pinned as
 * `HabitPalette.contains(...)` for every member — which is precisely what [HabitColorRetoneRemap]'s
 * KDoc says a frozen map's values must never drift with.
 */
private val SCHEMA_V5_PALETTE = setOf(
    // The 17 values HabitColorRetoneRemap.LEGACY_TO_CURRENT maps legacy presets to.
    0xFFFF6D48.toInt(), // RED
    0xFFF5B907.toInt(), // AMBER
    0xFFE860FF.toInt(), // VIOLET
    0xFFB992FF.toInt(), // LILAC
    0xFF00AE9D.toInt(), // TEAL
    0xFFFC6799.toInt(), // MAGENTA
    0xFF55D7B8.toInt(), // MINT
    0xFFA19F25.toInt(), // OLIVE
    0xFF849FAC.toInt(), // BLUE_GREY — retired by schema version 6
    0xFFDEC233.toInt(), // YELLOW
    0xFFD477E4.toInt(), // PURPLE
    0xFF00ABBD.toInt(), // CYAN
    0xFFE9BA75.toInt(), // PEACH
    0xFF8896E3.toInt(), // INDIGO
    0xFFB0958B.toInt(), // BROWN
    0xFFB1CA33.toInt(), // LIME
    0xFF469DFF.toInt(), // BLUE
    // The five presets that carried no entry because their value did not change.
    0xFF4CAF50.toInt(), // GREEN
    0xFF03A9F4.toInt(), // LIGHT_BLUE
    0xFF7CB342.toInt(), // LIGHT_GREEN
    0xFFF48FB1.toInt(), // PINK
    0xFFFF9800.toInt(), // ORANGE
)

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

    /**
     * Renamed from "…IsACurrentPreset" ([HabitColorRemapTest]'s own rename tells the same story):
     * that assertion existed to catch a re-tone producing a value the picker did not actually offer.
     * Schema version 6 later retired `BLUE_GREY`, one of these 17 mapped-to values, from the picker —
     * a correct, deliberate retirement, not a bug this map should have caught. Keeping the old
     * assertion would mean either failing on a correct migration or dragging this frozen v4->v5 map
     * forward onto whatever the palette happens to be today, the exact drift [HabitColorRetoneRemap]'s
     * KDoc forbids. Pinning against [SCHEMA_V5_PALETTE] keeps the guard's real job: this map must
     * still say what schema version 5 meant, and nothing else.
     */
    @Test
    fun `every mapped value was a schema v5 preset`() {
        HabitColorRetoneRemap.LEGACY_TO_CURRENT.values.forEach { mapped ->
            assertTrue(
                mapped in SCHEMA_V5_PALETTE,
                "0x%08X is not one of the 22 colours schema version 5 meant".format(mapped),
            )
        }
    }

    /** The point of [SCHEMA_V5_PALETTE]'s `BLUE_GREY` entry, stated as a fact rather than assumed:
     *  schema version 6 actually did retire it, so it must no longer be one of today's presets. This
     *  guards the rename above from quietly going stale if a future palette ever re-adopts it. */
    @Test
    fun `the frozen schema v5 blue grey value is not offered today`() {
        assertFalse(
            HabitPalette.contains(RETIRED_BLUE_GREY_IN_SCHEMA_V5),
            "BLUE_GREY was retired by schema version 6 and must not be a current preset",
        )
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
        /** 17 of the 22 presets schema version 5 offered moved value; the other five
         *  ([HabitColorRetoneRemap]'s own KDoc names them) did not and carry no entry. */
        const val EXPECTED_ENTRY_COUNT = 17
    }
}
