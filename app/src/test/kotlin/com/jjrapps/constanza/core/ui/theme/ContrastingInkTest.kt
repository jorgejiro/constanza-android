package com.jjrapps.constanza.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The selection tick's legibility rule, asserted rather than eyeballed.
 *
 * This is the same class of check `ColorContrastTest` runs on the palette, but it has to be a
 * different shape: the palette is a closed list that can simply be walked, while the tick has to
 * stay readable on *any* colour a user can mix in the custom picker. So the guard here is a bound
 * over the whole sRGB cube, not a table.
 *
 * It is directly the carried-forward item `fab-and-selected-chip-fills-below-the-non-text-floor`'s
 * failure mode — a control fill whose marker sits under the non-text floor — which is why the tick
 * is computed from the fill's luminance instead of being pinned to the accent, as the old selection
 * ring was.
 */
class ContrastingInkTest {

    @Test
    fun `a light swatch takes a dark tick`() {
        assertEquals(Color.Black, contrastingInk(0xFFFFEB3B.toInt()), "Yellow 500 is light; the tick must be black")
        assertEquals(Color.Black, contrastingInk(0xFFFFFFFF.toInt()), "white must take a black tick")
    }

    @Test
    fun `a dark swatch takes a light tick`() {
        assertEquals(Color.White, contrastingInk(0xFF1A237E.toInt()), "Indigo 900 is dark; the tick must be white")
        assertEquals(Color.White, contrastingInk(0xFF000000.toInt()), "black must take a white tick")
    }

    /**
     * Every offered preset takes the black branch, and that is a consequence rather than a
     * coincidence: clearing the ratified 4.5:1 floor against `ConstanzaColors.SurfaceSelected`
     * requires relative luminance >= 0.233, while black stops being the better tick below 0.179. So
     * on this app's ramp, *any* colour legible enough to be offered is also light enough for a black
     * tick — which is why the white branch above is exercised with custom colours instead.
     */
    @Test
    fun `every offered preset is light enough to take a black tick`() {
        HabitPalette.ARGB.forEach { argb ->
            assertEquals(Color.Black, contrastingInk(argb), "0x%08X unexpectedly needs a white tick".format(argb))
        }
    }

    @Test
    fun `every offered preset clears the text floor for its tick`() {
        HabitPalette.ORDERED.forEach { habitColor ->
            val ratio = contrastingInkRatio(habitColor.argb)
            assertTrue(
                ratio >= TEXT_FLOOR,
                "${habitColor.name}'s tick measured %.2f:1, below the required %.2f:1".format(ratio, TEXT_FLOOR),
            )
        }
    }

    /**
     * The bound that makes the custom picker safe at all: no colour whatsoever can push the tick
     * below 4.58:1. Swept rather than re-derived — the algebra is in `contrastingInk`'s KDoc, and a
     * sweep catches an implementation that stops matching it.
     *
     * A 16-step sweep per channel is 4,096 colours, which runs in milliseconds and lands within
     * 1/15th of the crossover luminance in every direction; the worst case it finds is reported in
     * the failure message so a regression says how far it fell, not just that it did.
     */
    @Test
    fun `no colour in the sRGB cube can push the tick below the guaranteed floor`() {
        var worstRatio = Double.MAX_VALUE
        var worstArgb = 0
        for (r in 0..CHANNEL_MAX step CHANNEL_STEP) {
            for (g in 0..CHANNEL_MAX step CHANNEL_STEP) {
                for (b in 0..CHANNEL_MAX step CHANNEL_STEP) {
                    val argb = OPAQUE or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
                    val ratio = contrastingInkRatio(argb)
                    if (ratio < worstRatio) {
                        worstRatio = ratio
                        worstArgb = argb
                    }
                }
            }
        }
        assertTrue(
            worstRatio >= GUARANTEED_FLOOR,
            "the worst colour swept, 0x%08X, measured %.3f:1 against its own tick, below the %.2f:1 this rule guarantees"
                .format(worstArgb, worstRatio, GUARANTEED_FLOOR),
        )
    }

    /** The tick also has to clear the non-text floor, which is the floor the carried-forward item is
     *  written against. It does so on every colour, by the bound above. */
    @Test
    fun `the guaranteed floor clears WCAG's non-text floor`() {
        assertTrue(GUARANTEED_FLOOR >= NON_TEXT_FLOOR, "the guaranteed tick floor must clear SC 1.4.11's 3:1")
    }

    private companion object {
        /** WCAG 2.1 SC 1.4.3, the same floor `ColorContrastTest` holds the palette to. */
        const val TEXT_FLOOR = 4.5

        /** WCAG 2.1 SC 1.4.11 Non-text Contrast. */
        const val NON_TEXT_FLOOR = 3.0

        /** `sqrt(1.05 * 0.05) / 0.05`, the worst ratio "black or white, whichever is better" can
         *  produce on any colour at all. See `contrastingInk`'s KDoc for the derivation. */
        const val GUARANTEED_FLOOR = 4.58

        const val CHANNEL_MAX = 255
        const val CHANNEL_STEP = 17
        const val OPAQUE = 0xFF shl 24
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
    }
}
