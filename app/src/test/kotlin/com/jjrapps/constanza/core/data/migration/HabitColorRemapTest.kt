package com.jjrapps.constanza.core.data.migration

import com.jjrapps.constanza.core.ui.theme.HabitPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 2.9 (design.md decision 3). Runs via `./gradlew :app:testDebugUnitTest`. Guards the
 * migration's own sign trap and the "frozen, not palette-referencing" invariant [HabitColorRemap]'s
 * KDoc states: if a future re-tone ever changes a [HabitPalette] member, [everyMappedValueIsACurrentPaletteMember]
 * is the test that fails first, before a silently-wrong migration ships.
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
    fun everyMappedValueIsACurrentPaletteMember() {
        val currentPalette = HabitPalette.ARGB.toSet()

        HabitColorRemap.LEGACY_TO_CURRENT.values.forEach { mapped ->
            assertTrue(mapped in currentPalette, "mapped value $mapped is not a current HabitPalette member")
        }
    }
}
