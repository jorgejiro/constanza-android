package com.jjrapps.constanza.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The live custom-colour band (graphite redesign T2): [habitBandColor] must only ever produce a
 * colour inside `[HABIT_BAND_FLOOR, HABIT_BAND_CEILING]` against the live
 * [ConstanzaColors.Background], and — the reason the floor is 3.6 rather than 3.0 — every such
 * colour must clear WCAG 1.4.11's 3:1 non-text floor on [ConstanzaColors.SurfaceRaised], the
 * lightest surface an identity dot is drawn on.
 */
class HabitColorBandTest {

    @Test
    fun `the band is measured against the live graphite background`() {
        assertEquals(Color(0xFF141416), ConstanzaColors.Background)
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
     * The non-text floor, asserted where it binds. A sweep over hue (every 15°), saturation (five
     * steps) and the darkest slider positions: every result must clear 3:1 on Background, Surface
     * and SurfaceRaised, and no result may exceed the ceiling on Background.
     */
    @Test
    fun `every custom colour clears 3 to 1 on every surface and stays under the ceiling`() {
        val surfaces = listOf(ConstanzaColors.Background, ConstanzaColors.Surface, ConstanzaColors.SurfaceRaised)
        val samples = (0 until HUE_STEPS).flatMap { hueStep ->
            (0..SAT_STEPS).flatMap { satStep ->
                listOf(0f, 0.1f, 0.5f, 1f).map { position ->
                    Triple(hueStep * FULL_TURN / HUE_STEPS, satStep.toFloat() / SAT_STEPS, position)
                }
            }
        }
        samples.forEach { (hue, saturation, position) ->
            val color = Color(habitBandColor(hue, saturation, position))
            surfaces.forEach { surface ->
                val ratio = contrastRatio(color, surface)
                assertTrue(
                    ratio >= NON_TEXT_FLOOR,
                    "hue=$hue sat=$saturation pos=$position measured %.3f:1 on $surface".format(ratio),
                )
            }
            val onBackground = contrastRatio(color, ConstanzaColors.Background)
            assertTrue(
                onBackground <= HABIT_BAND_CEILING + RATIO_TOLERANCE,
                "hue=$hue sat=$saturation pos=$position measured %.3f:1, above the ceiling"
                    .format(onBackground),
            )
        }
    }

    /** Every muted preset sits inside the band, so editing one in the custom dialog seeds a slider
     *  position that reproduces it rather than snapping it to an end. */
    @Test
    fun `every preset sits inside the custom band`() {
        HabitPalette.ORDERED.forEach { habitColor ->
            val ratio = contrastRatio(Color(habitColor.argb), ConstanzaColors.Background)
            assertTrue(
                ratio in HABIT_BAND_FLOOR..HABIT_BAND_CEILING,
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

            val originalRatio = contrastRatio(Color(habitColor.argb), ConstanzaColors.Background)
            val reconstructedRatio = contrastRatio(Color(reconstructed), ConstanzaColors.Background)

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
        val floorRatio = contrastRatio(Color(habitBandColor(hue, saturation, 0f)), ConstanzaColors.Background)
        val ceilingRatio = contrastRatio(Color(habitBandColor(hue, saturation, 1f)), ConstanzaColors.Background)

        assertTrue(
            kotlin.math.abs(floorRatio - HABIT_BAND_FLOOR) <= RATIO_TOLERANCE,
            "hue=$hue sat=$saturation at position 0f measured %.3f:1, expected close to $HABIT_BAND_FLOOR:1"
                .format(floorRatio),
        )
        assertTrue(
            kotlin.math.abs(ceilingRatio - HABIT_BAND_CEILING) <= RATIO_TOLERANCE,
            "hue=$hue sat=$saturation at position 1f measured %.3f:1, expected close to $HABIT_BAND_CEILING:1"
                .format(ceilingRatio),
        )
    }

    private fun assertDistinctSweep(hue: Float, saturation: Float) {
        val results = (0..SWEEP_STEPS).map { step -> habitBandColor(hue, saturation, step.toFloat() / SWEEP_STEPS) }
        val distinctCount = results.toSet().size
        assertTrue(
            distinctCount >= MIN_DISTINCT_SWEEP_COUNT,
            "hue=$hue sat=$saturation produced only $distinctCount distinct colours " +
                "across ${SWEEP_STEPS + 1} positions",
        )
    }

    private companion object {
        const val OPAQUE = 0xFF shl 24

        /** WCAG 2.1 SC 1.4.11, non-text contrast. */
        const val NON_TEXT_FLOOR = 3.0

        /** 8-bit rounding slack around a solved target ratio. */
        const val RATIO_TOLERANCE = 0.1

        const val HUE_STEPS = 24
        const val SAT_STEPS = 4
        const val FULL_TURN = 360f

        /** 101 positions (`0f` to `1f` in steps of `0.01`), matching the slider's own resolution. */
        const val SWEEP_STEPS = 100

        /** Comfortably above the dead slider's 12/13 while leaving room for 8-bit rounding to merge
         *  a few adjacent positions. */
        const val MIN_DISTINCT_SWEEP_COUNT = 60

        /** Generous enough to absorb 8-bit rounding on both the seed measurement and the rebuild. */
        const val ROUND_TRIP_RATIO_TOLERANCE = 0.2
    }
}
