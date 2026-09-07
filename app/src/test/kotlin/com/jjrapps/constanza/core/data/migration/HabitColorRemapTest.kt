package com.jjrapps.constanza.core.data.migration

import com.jjrapps.constanza.core.ui.theme.HabitPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The exact six values version 2 of the schema meant, as literals. Version 3 replaced them with
 *  Material's families, so these can no longer be spelled `HabitColor.X.argb` — which is precisely
 *  what [HabitColorRemap]'s KDoc says both sides of that map must never be. */
private val SCHEMA_V2_PALETTE = setOf(
    0xFFFF9FA8.toInt(), // red
    0xFFFFA8DC.toInt(), // pink
    0xFFCBB2FF.toInt(), // violet
    0xFF8FC5FF.toInt(), // blue
    0xFF5DD6C7.toInt(), // teal
    0xFF8BDB95.toInt(), // green
)

/**
 * Task 2.9 (design.md decision 3). Runs via `./gradlew :app:testDebugUnitTest`. Guards the
 * migration's own sign trap and the "frozen, not palette-referencing" invariant [HabitColorRemap]'s
 * KDoc states.
 *
 * [everyMappedValueIsASchemaV2PaletteMember] used to read "…IsACurrentPaletteMember", and the
 * rename is the whole story of this file's update. That assertion existed to catch a re-tone that
 * would leave a migrated habit holding a colour the picker no longer offered. The picker now offers
 * a free custom colour, so an off-palette value is a first-class state rather than an orphan, and
 * the twenty-two-family standard palette that replaced the six pastels shares none of their values.
 * Keeping the old assertion would have meant either failing on a correct migration or dragging the
 * frozen v1→v2 map forward onto whatever the palette happens to be today — the exact drift
 * [HabitColorRemap]'s KDoc forbids. Pinning the v2 values as literals keeps the guard's real job:
 * this map must still say what version 2 meant, and nothing else.
 */
class HabitColorRemapTest {

    @Test
    fun `the map is a bijection - six distinct keys, six distinct values`() {
        val map = HabitColorRemap.LEGACY_TO_CURRENT

        assertEquals(6, map.keys.size, "expected exactly six legacy colours mapped")
        assertEquals(6, map.values.toSet().size, "two legacy colours must never collapse onto one current colour")
    }

    @Test
    fun `legacy orange maps to pink specifically`() {
        val legacyOrange = 0xFFFB8C00.toInt()
        val currentPink = 0xFFFFA8DC.toInt()

        assertEquals(currentPink, HabitColorRemap.normalize(legacyOrange))
    }

    @Test
    fun `an unmapped int passes through unchanged`() {
        val unmapped = 0x00123456

        assertEquals(unmapped, HabitColorRemap.normalize(unmapped))
    }

    @Test
    fun everyMappedValueIsASchemaV2PaletteMember() {
        HabitColorRemap.LEGACY_TO_CURRENT.values.forEach { mapped ->
            assertTrue(
                mapped in SCHEMA_V2_PALETTE,
                "mapped value $mapped is not one of the six colours schema version 2 meant",
            )
        }
    }

    /** The migration writes what version 2 meant, and version 3's palette is a different set. This
     *  asserts the two really are disjoint, so the rename above cannot be quietly undone by a
     *  future palette that happens to re-adopt a pastel. */
    @Test
    fun `the frozen version 2 palette is not the palette offered today`() {
        SCHEMA_V2_PALETTE.forEach { legacy ->
            assertFalse(
                HabitPalette.contains(legacy),
                "0x%08X is both a frozen schema-v2 colour and a currently offered preset".format(legacy),
            )
        }
    }

    /** The point of the custom swatch, stated as a migration fact: a habit that migration 1→2 left
     *  holding a pastel is still a habit the editor can open and show correctly, because the picker
     *  no longer requires a colour to be a preset. */
    @Test
    fun `a migrated version 2 colour survives as a custom colour rather than being orphaned`() {
        HabitColorRemap.LEGACY_TO_CURRENT.values.forEach { migrated ->
            assertEquals(
                migrated,
                HabitColorRemap.normalize(migrated),
                "an already-migrated colour must pass through untouched, not be re-mapped again",
            )
            assertFalse(HabitPalette.contains(migrated), "expected $migrated to be a custom, off-preset colour")
        }
    }
}
