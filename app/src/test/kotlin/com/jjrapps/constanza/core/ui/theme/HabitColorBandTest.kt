package com.jjrapps.constanza.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The custom-colour band: [habitBandColor] must only ever produce a colour inside
 * `[HABIT_BAND_FLOOR, HABIT_BAND_CEILING]` against [HabitBandGround]. The frozen clamp the migrations
 * replay is pinned separately by `LegacyHabitColorBandTest`.
 */
class HabitColorBandTest {

    @Test
    fun `habitBandColor spans the whole band for saturated red`() =
        assertBandBoundary(hue = 12f, saturation = 1f)

    @Test
    fun `habitBandColor spans the whole band for saturated blue`() =
        assertBandBoundary(hue = 212f, saturation = 1f)

    @Test
    fun `habitBandColor spans the whole band for saturated green`() =
        assertBandBoundary(hue = 120f, saturation = 1f)

    @Test
    fun `habitBandColor spans the whole band for yellow`() =
        assertBandBoundary(hue = 50f, saturation = 1f)

    @Test
    fun `habitBandColor spans the whole band for pure grey`() =
        assertBandBoundary(hue = 0f, saturation = 0f)

    /** Every preset sits inside the band, so editing one in the custom dialog seeds a slider
     *  position that reproduces it rather than snapping it to an end. */
    @Test
    fun `every preset sits inside the custom band`() {
        val band = (HABIT_BAND_FLOOR - HABIT_BAND_TOLERANCE)..(HABIT_BAND_CEILING + HABIT_BAND_TOLERANCE)
        HabitPalette.ORDERED.forEach { habitColor ->
            val ratio = contrastRatio(Color(habitColor.argb), HabitBandGround)
            assertTrue(
                ratio in band,
                "${habitColor.name} measured %.2f:1, outside the custom band".format(ratio),
            )
        }
    }

    /** The regression test for the dead brightness slider of the value-driven picker (12 distinct
     *  outputs for saturated red, 13 for saturated blue over 101 positions). */
    @Test
    fun `habitBandColor sweep has no dead zone`() {
        assertDistinctSweep(hue = 12f, saturation = 1f)
        assertDistinctSweep(hue = 212f, saturation = 1f)
    }

    @Test
    fun `habitBandColor round-trips through habitBandPositionOf for every preset`() {
        HabitPalette.ORDERED.forEach { habitColor ->
            val hsv = hsvOf(habitColor.argb)
            val reconstructed = habitBandColor(hsv.hue, hsv.saturation, habitBandPositionOf(habitColor.argb))

            val originalRatio = contrastRatio(Color(habitColor.argb), HabitBandGround)
            val reconstructedRatio = contrastRatio(Color(reconstructed), HabitBandGround)

            assertTrue(
                kotlin.math.abs(originalRatio - reconstructedRatio) <= ROUND_TRIP_RATIO_TOLERANCE,
                "${habitColor.name}: reconstructed %.3f:1, the preset measures %.3f:1"
                    .format(reconstructedRatio, originalRatio),
            )
        }
    }

    @Test
    fun `habitBandPositionOf coerces pure black to 0 and pure white to 1`() {
        assertEquals(0f, habitBandPositionOf(OPAQUE or 0x000000))
        assertEquals(1f, habitBandPositionOf(OPAQUE or 0xFFFFFF))
    }

    private fun assertBandBoundary(hue: Float, saturation: Float) {
        val floorRatio = contrastRatio(Color(habitBandColor(hue, saturation, 0f)), HabitBandGround)
        val ceilingRatio = contrastRatio(Color(habitBandColor(hue, saturation, 1f)), HabitBandGround)

        assertTrue(
            kotlin.math.abs(floorRatio - HABIT_BAND_FLOOR) <= HABIT_BAND_TOLERANCE,
            "hue=$hue sat=$saturation at position 0f measured %.3f:1, expected close to $HABIT_BAND_FLOOR:1"
                .format(floorRatio),
        )
        assertTrue(
            kotlin.math.abs(ceilingRatio - HABIT_BAND_CEILING) <= HABIT_BAND_TOLERANCE,
            "hue=$hue sat=$saturation at position 1f measured %.3f:1, expected close to $HABIT_BAND_CEILING:1"
                .format(ceilingRatio),
        )
    }

    private fun assertDistinctSweep(hue: Float, saturation: Float) {
        val results = (0..SWEEP_STEPS).map { step -> habitBandColor(hue, saturation, step.toFloat() / SWEEP_STEPS) }
        val distinctCount = results.toSet().size
        assertTrue(
            distinctCount >= MIN_DISTINCT_SWEEP_COUNT,
            "hue=$hue sat=$saturation produced only $distinctCount distinct colours across ${SWEEP_STEPS + 1} positions",
        )
    }

    private companion object {
        const val OPAQUE = 0xFF shl 24

        /** 101 positions (`0f` to `1f` in steps of `0.01`), matching the slider's own resolution. */
        const val SWEEP_STEPS = 100

        /** Comfortably above the dead slider's 12/13 while leaving room for 8-bit rounding to merge
         *  a few adjacent positions. */
        const val MIN_DISTINCT_SWEEP_COUNT = 60

        /** Generous enough to absorb 8-bit rounding on both the seed measurement and the rebuild. */
        const val ROUND_TRIP_RATIO_TOLERANCE = 0.2
    }
}
