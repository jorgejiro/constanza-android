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
    }
}
