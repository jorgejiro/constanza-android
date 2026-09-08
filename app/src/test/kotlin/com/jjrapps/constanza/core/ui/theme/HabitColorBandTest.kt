package com.jjrapps.constanza.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [clampToHabitBand]'s own contract: every custom colour a user can mix must land inside
 * `[7.0, 11.0]` against [ConstanzaColors.Background], and every already-legible preset must be
 * returned untouched.
 *
 * Measured cases below are copied verbatim from [clampToHabitBand]'s own KDoc. Channel comparisons
 * allow ±1 per RGB channel — the bisection's own contract permits landing a hair off the byte a hand
 * calculation would round to, and this test asserts that contract rather than chasing an exact byte.
 */
class HabitColorBandTest {

    @Test
    fun `navy clamps up to the floor`() = assertClamp(0x1A237E, 0x8791FF)

    @Test
    fun `deep purple clamps up to the floor`() = assertClamp(0x311B92, 0x9F8AFF)

    @Test
    fun `maroon clamps up to the floor`() = assertClamp(0x7B1F1F, 0xFF6A6A)

    @Test
    fun `chocolate clamps up to the floor`() = assertClamp(0x5D4037, 0xCB8C78)

    @Test
    fun `forest clamps up to the floor`() = assertClamp(0x1B5E20, 0x33B23C)

    @Test
    fun `pure black clamps up to the floor as a grey`() = assertClamp(0x000000, 0x9B9B9B)

    @Test
    fun `pure white clamps down to the ceiling`() = assertClamp(0xFFFFFF, 0xC2C2C2)

    @Test
    fun `pure red clamps up to the floor via the saturation branch`() = assertClamp(0xFF0000, 0xFF6A6A)

    @Test
    fun `every preset is returned unchanged`() {
        HabitPalette.ORDERED.forEach { habitColor ->
            assertEquals(
                habitColor.argb,
                clampToHabitBand(habitColor.argb),
                "${habitColor.name} is already inside the band and must not be touched",
            )
        }
    }

    /**
     * A 17-step sweep per channel is 4,096 colours (matching `ContrastingInkTest`'s own sweep
     * density), cheap enough to run in milliseconds while still exercising every hue/saturation/value
     * combination a user's drag could actually land the sliders on.
     */
    @Test
    fun `every colour in the sRGB cube clamps inside the band, with rounding slack`() {
        var worstLow = Double.MAX_VALUE
        var worstHigh = 0.0
        var worstLowArgb = 0
        var worstHighArgb = 0
        for (r in 0..CHANNEL_MAX step CHANNEL_STEP) {
            for (g in 0..CHANNEL_MAX step CHANNEL_STEP) {
                for (b in 0..CHANNEL_MAX step CHANNEL_STEP) {
                    val argb = OPAQUE or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
                    val clamped = clampToHabitBand(argb)
                    val ratio = contrastRatio(Color(clamped), ConstanzaColors.Background)
                    if (ratio < worstLow) {
                        worstLow = ratio
                        worstLowArgb = argb
                    }
                    if (ratio > worstHigh) {
                        worstHigh = ratio
                        worstHighArgb = argb
                    }
                }
            }
        }
        assertTrue(
            worstLow >= HABIT_BAND_FLOOR - HABIT_BAND_TOLERANCE,
            "0x%06X clamped to a ratio of %.3f:1, below the %.2f:1 sweep floor"
                .format(worstLowArgb and 0xFFFFFF, worstLow, HABIT_BAND_FLOOR - HABIT_BAND_TOLERANCE),
        )
        assertTrue(
            worstHigh <= HABIT_BAND_CEILING + HABIT_BAND_TOLERANCE,
            "0x%06X clamped to a ratio of %.3f:1, above the %.2f:1 sweep ceiling"
                .format(worstHighArgb and 0xFFFFFF, worstHigh, HABIT_BAND_CEILING + HABIT_BAND_TOLERANCE),
        )
    }

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

    /**
     * The regression test that would have caught the dead brightness slider: before this change, the
     * slider drove raw HSV `value` straight into [clampToHabitBand], which rebuilds any out-of-band
     * colour from scratch at exactly the floor or ceiling — discarding `value` entirely once a colour
     * was out of band. Sweeping the old slider's 101 positions (`0f` to `1f` in steps of `0.01`)
     * produced only 12 distinct outputs for saturated red and only 13 for saturated blue; the other 89
     * (red) and 88 (blue) positions were indistinguishable from one another. [habitBandColor] fixes
     * this by driving the target contrast ratio itself instead of `value`, so every position asks for a
     * genuinely different point on the band and must produce a genuinely different colour.
     */
    @Test
    fun `habitBandColor sweep has no dead zone, unlike the old value-driven slider`() {
        assertDistinctSweep(hue = 12f, saturation = 1f, previousDistinctCount = 12)
        assertDistinctSweep(hue = 212f, saturation = 1f, previousDistinctCount = 13)
    }

    @Test
    fun `habitBandColor round-trips through habitBandPositionOf for every preset`() {
        HabitPalette.ORDERED.forEach { habitColor ->
            val hsv = hsvOf(habitColor.argb)
            val seededPosition = habitBandPositionOf(habitColor.argb)
            val reconstructed = habitBandColor(hsv.hue, hsv.saturation, seededPosition)

            val originalRatio = contrastRatio(Color(habitColor.argb), ConstanzaColors.Background)
            val reconstructedRatio = contrastRatio(Color(reconstructed), ConstanzaColors.Background)

            assertTrue(
                kotlin.math.abs(originalRatio - reconstructedRatio) <= ROUND_TRIP_RATIO_TOLERANCE,
                "${habitColor.name}: seeded position $seededPosition reconstructed a ratio of " +
                    "%.3f:1, but the preset itself measures %.3f:1".format(reconstructedRatio, originalRatio),
            )
        }
    }

    @Test
    fun `habitBandPositionOf coerces pure black to 0 and pure white to 1`() {
        assertEquals(0f, habitBandPositionOf(OPAQUE or 0x000000))
        assertEquals(1f, habitBandPositionOf(OPAQUE or 0xFFFFFF))
    }

    private fun assertBandBoundary(hue: Float, saturation: Float) {
        val floorRatio = contrastRatio(Color(habitBandColor(hue, saturation, 0f)), ConstanzaColors.Background)
        val ceilingRatio = contrastRatio(Color(habitBandColor(hue, saturation, 1f)), ConstanzaColors.Background)

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

    private fun assertDistinctSweep(hue: Float, saturation: Float, previousDistinctCount: Int) {
        val results = (0..SWEEP_STEPS).map { step ->
            habitBandColor(hue, saturation, step.toFloat() / SWEEP_STEPS)
        }
        val bandRange = (HABIT_BAND_FLOOR - HABIT_BAND_TOLERANCE)..(HABIT_BAND_CEILING + HABIT_BAND_TOLERANCE)
        results.forEach { argb ->
            val ratio = contrastRatio(Color(argb), ConstanzaColors.Background)
            assertTrue(
                ratio in bandRange,
                "hue=$hue sat=$saturation swept to 0x%06X, measuring %.3f:1, outside $bandRange"
                    .format(argb and 0xFFFFFF, ratio),
            )
        }

        val distinctCount = results.toSet().size
        assertTrue(
            distinctCount >= MIN_DISTINCT_SWEEP_COUNT,
            "hue=$hue sat=$saturation produced only $distinctCount distinct colours across " +
                "${SWEEP_STEPS + 1} swept positions (was $previousDistinctCount before the band-position " +
                "reparameterisation) — expected at least $MIN_DISTINCT_SWEEP_COUNT",
        )
    }

    private fun assertClamp(inputRgb: Int, expectedRgb: Int) {
        val input = OPAQUE or inputRgb
        val expected = OPAQUE or expectedRgb
        val actual = clampToHabitBand(input)

        assertTrue(
            channelsWithinTolerance(expected, actual),
            "0x%06X clamped to 0x%08X, expected close to 0x%08X".format(inputRgb, actual, expected),
        )
    }

    private fun channelsWithinTolerance(expected: Int, actual: Int): Boolean =
        channel(expected, RED_SHIFT) closeTo channel(actual, RED_SHIFT) &&
            channel(expected, GREEN_SHIFT) closeTo channel(actual, GREEN_SHIFT) &&
            channel(expected, 0) closeTo channel(actual, 0)

    private fun channel(argb: Int, shift: Int): Int = (argb ushr shift) and 0xFF

    private infix fun Int.closeTo(other: Int): Boolean = kotlin.math.abs(this - other) <= CHANNEL_TOLERANCE

    private companion object {
        const val OPAQUE = 0xFF shl 24
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val CHANNEL_TOLERANCE = 1
        const val CHANNEL_MAX = 255
        const val CHANNEL_STEP = 17

        /** 101 positions (`0f` to `1f` in steps of `0.01`), matching the slider's own resolution. */
        const val SWEEP_STEPS = 100

        /**
         * The regression floor: comfortably above both pre-fix counts (12 for red, 13 for blue) while
         * leaving headroom for 8-bit channel quantisation to still collapse a handful of adjacent
         * positions onto the same rounded byte.
         */
        const val MIN_DISTINCT_SWEEP_COUNT = 60

        /** Contrast-ratio "tenths" tolerance for [habitBandPositionOf]'s round trip — generous enough
         *  to absorb 8-bit channel rounding on both the seed measurement and the reconstruction. */
        const val ROUND_TRIP_RATIO_TOLERANCE = 0.2
    }
}
