package com.jjrapps.constanza.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Asserts the accessibility contract as JVM unit tests instead of trusting a table in a PR
 * (Engram #47, point 10a; ported from the sibling app's `sleep-noise-android` `ColorContrastTest`).
 *
 * Spec `Habit Colour And Accent Contrast Floor` / `Contrast Floors Asserted By Automated Test`: every
 * offered habit colour and the accent MUST clear **4.5:1** against both [ConstanzaColors.Background]
 * and [ConstanzaColors.SurfaceSelected] — one floor, not a lighter graphics-only tier, because a habit
 * colour also has to carry the habit name as adjacent text. The ratified palette measures well above
 * this floor (≥10:1 vs `Background`, ≥8.4:1 vs `SurfaceSelected`), but the floor asserted here is the
 * spec's 4.5:1, not the current headroom — a future re-tone should fail this test before it ships
 * rather than after.
 */
class ColorContrastTest {

    @Test
    fun `every habit colour clears the floor against the background`() {
        HabitColor.entries.forEach { habitColor ->
            assertRatioAtLeast(
                foreground = habitColor.composeColor,
                background = ConstanzaColors.Background,
                minimum = CONTRAST_FLOOR,
                label = "${habitColor.name} on Background",
            )
        }
    }

    @Test
    fun `every habit colour clears the floor against the selected surface`() {
        HabitColor.entries.forEach { habitColor ->
            assertRatioAtLeast(
                foreground = habitColor.composeColor,
                background = ConstanzaColors.SurfaceSelected,
                minimum = CONTRAST_FLOOR,
                label = "${habitColor.name} on SurfaceSelected",
            )
        }
    }

    @Test
    fun `the accent clears the floor against the background`() {
        assertRatioAtLeast(ConstanzaColors.Accent, ConstanzaColors.Background, CONTRAST_FLOOR, "Accent on Background")
    }

    @Test
    fun `the accent clears the floor against the selected surface`() {
        assertRatioAtLeast(
            ConstanzaColors.Accent,
            ConstanzaColors.SurfaceSelected,
            CONTRAST_FLOOR,
            "Accent on SurfaceSelected",
        )
    }

    @Test
    fun `primary text clears the AA floor on the background with room to spare`() {
        assertRatioAtLeast(ConstanzaColors.OnBackground, ConstanzaColors.Background, PRIMARY_TEXT_FLOOR, "OnBackground")
    }

    @Test
    fun `secondary text clears the AA floor on the background`() {
        assertRatioAtLeast(
            ConstanzaColors.OnBackgroundVariant,
            ConstanzaColors.Background,
            SECONDARY_TEXT_FLOOR,
            "OnBackgroundVariant",
        )
    }

    @Test
    fun `muted labels clear the AA body floor`() {
        assertRatioAtLeast(ConstanzaColors.OnBackgroundMuted, ConstanzaColors.Background, CONTRAST_FLOOR, "OnBackgroundMuted")
    }

    @Test
    fun `text on the accent is legible`() {
        assertRatioAtLeast(ConstanzaColors.OnAccent, ConstanzaColors.Accent, CONTRAST_FLOOR, "OnAccent on Accent")
    }

    private fun assertRatioAtLeast(foreground: Color, background: Color, minimum: Double, label: String) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            actual = ratio >= minimum,
            message = "$label measured %.2f:1, below the required %.2f:1".format(ratio, minimum),
        )
    }

    /** WCAG 2.1 relative luminance and contrast ratio. */
    private fun contrastRatio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + LUMINANCE_OFFSET) / (darker + LUMINANCE_OFFSET)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= GAMMA_THRESHOLD) v / GAMMA_LINEAR_DIVISOR else Math.pow((v + GAMMA_OFFSET) / GAMMA_DIVISOR, GAMMA_EXPONENT)
        }
        return RED_WEIGHT * channel(color.red) + GREEN_WEIGHT * channel(color.green) + BLUE_WEIGHT * channel(color.blue)
    }

    private companion object {
        /** Spec `Habit Colour And Accent Contrast Floor`: one 4.5:1 floor for both surfaces. */
        const val CONTRAST_FLOOR = 4.5
        const val PRIMARY_TEXT_FLOOR = 12.0
        const val SECONDARY_TEXT_FLOOR = 7.0

        // WCAG 2.1 relative-luminance constants (sRGB gamma correction + contrast formula offset).
        const val LUMINANCE_OFFSET = 0.05
        const val GAMMA_THRESHOLD = 0.04045
        const val GAMMA_LINEAR_DIVISOR = 12.92
        const val GAMMA_OFFSET = 0.055
        const val GAMMA_DIVISOR = 1.055
        const val GAMMA_EXPONENT = 2.4
        const val RED_WEIGHT = 0.2126
        const val GREEN_WEIGHT = 0.7152
        const val BLUE_WEIGHT = 0.0722
    }
}
