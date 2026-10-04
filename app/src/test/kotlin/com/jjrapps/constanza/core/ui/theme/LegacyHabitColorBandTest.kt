package com.jjrapps.constanza.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [clampToLegacyHabitBand]'s frozen contract: the v5/v6 migrations and old-backup normalisation
 * replay it, so its outputs must never move. Every custom colour lands inside `[7.0, 11.0]` against
 * the frozen [HabitBandGround] (`#110B06`), and every retired 21-preset legible-band colour is
 * returned untouched.
 *
 * Measured cases are copied verbatim from the clamp's KDoc. Channel comparisons allow ±1 per RGB
 * channel — the bisection's own contract permits landing a hair off the byte a hand calculation would
 * round to.
 */
class LegacyHabitColorBandTest {

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

    /** The retired 21 presets, as literals: they were inside this band by construction, and the v6
     *  migration's output depends on the clamp still agreeing. */
    @Test
    fun `every retired legible-band preset is returned unchanged`() {
        RETIRED_LEGIBLE_BAND_PRESETS.forEach { argb ->
            assertEquals(argb, clampToLegacyHabitBand(argb), "0x%08X is inside the band".format(argb))
        }
    }

    @Test
    fun `the frozen ground is the retired warm-dark background`() {
        assertEquals(Color(0xFF110B06), HabitBandGround)
    }

    /** 4,096 colours (a 17-step sweep per channel), cheap and dense enough to exercise every region
     *  of the cube a stored custom colour could come from. */
    @Test
    fun `every colour in the sRGB cube clamps inside the band, with rounding slack`() {
        val low = LEGACY_HABIT_BAND_FLOOR - LEGACY_HABIT_BAND_TOLERANCE
        val high = LEGACY_HABIT_BAND_CEILING + LEGACY_HABIT_BAND_TOLERANCE
        for (r in 0..CHANNEL_MAX step CHANNEL_STEP) {
            for (g in 0..CHANNEL_MAX step CHANNEL_STEP) {
                for (b in 0..CHANNEL_MAX step CHANNEL_STEP) {
                    val argb = OPAQUE or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
                    val ratio = contrastRatio(Color(clampToLegacyHabitBand(argb)), HabitBandGround)
                    assertTrue(ratio in low..high, "0x%06X clamped to %.3f:1".format(argb and 0xFFFFFF, ratio))
                }
            }
        }
    }

    private fun assertClamp(inputRgb: Int, expectedRgb: Int) {
        val input = OPAQUE or inputRgb
        val expected = OPAQUE or expectedRgb
        val actual = clampToLegacyHabitBand(input)

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

        /** The 21-preset legible-band palette retired by Room v8 / backup schema 5. */
        val RETIRED_LEGIBLE_BAND_PRESETS = listOf(
            0xFF4CAF50, 0xFFE860FF, 0xFFFF6D48, 0xFF03A9F4, 0xFFF5B907, 0xFFF48FB1, 0xFF55D7B8,
            0xFF8896E3, 0xFFDEC233, 0xFFFC6799, 0xFFB1CA33, 0xFF00AE9D, 0xFFD477E4, 0xFF7CB342,
            0xFF00ABBD, 0xFFFF9800, 0xFFB0958B, 0xFF469DFF, 0xFFA19F25, 0xFFB992FF, 0xFFE9BA75,
        ).map { it.toInt() }
    }
}
