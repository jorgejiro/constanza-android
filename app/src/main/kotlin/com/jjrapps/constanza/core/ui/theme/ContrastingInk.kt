package com.jjrapps.constanza.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// WCAG 2.1 relative-luminance constants (sRGB gamma correction) and the contrast-ratio offset.
private const val GAMMA_THRESHOLD = 0.04045
private const val GAMMA_LINEAR_DIVISOR = 12.92
private const val GAMMA_OFFSET = 0.055
private const val GAMMA_DIVISOR = 1.055
private const val GAMMA_EXPONENT = 2.4
private const val RED_WEIGHT = 0.2126
private const val GREEN_WEIGHT = 0.7152
private const val BLUE_WEIGHT = 0.0722
private const val LUMINANCE_OFFSET = 0.05

/** WCAG 2.1 relative luminance of [color], in `0.0..1.0`. */
internal fun relativeLuminance(color: Color): Double {
    fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= GAMMA_THRESHOLD) {
            v / GAMMA_LINEAR_DIVISOR
        } else {
            ((v + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA_EXPONENT)
        }
    }
    return RED_WEIGHT * channel(color.red) + GREEN_WEIGHT * channel(color.green) + BLUE_WEIGHT * channel(color.blue)
}

/** WCAG 2.1 contrast ratio between two colours, in `1.0..21.0`. Symmetric in its arguments. */
internal fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (max(la, lb) + LUMINANCE_OFFSET) / (min(la, lb) + LUMINANCE_OFFSET)
}

/**
 * Black or white — whichever the WCAG 2.1 contrast ratio says is more legible **on this exact
 * colour**. Computed, never tabulated: the colour picker offers a free custom colour, so the set of
 * fills this has to stay readable on is the whole sRGB cube and no table could enumerate it.
 *
 * This is the selection tick's tint in the habit colour picker, and it replaces the accent-coloured
 * ring that used to mark the selected swatch. The ring had two problems: it looked bad against the
 * pastel fills it circled, and — the reason it matters here — a single fixed accent cannot be
 * legible on every fill in an unbounded palette, which is the class of defect the carried-forward
 * item `fab-and-selected-chip-fills-below-the-non-text-floor` is about.
 *
 * **The floor this guarantees, for any colour whatsoever.** Picking the better of black and white
 * is worst at the luminance where the two ratios are equal:
 * `(L + 0.05) / 0.05 = 1.05 / (L + 0.05)`, i.e. `L = sqrt(0.0525) - 0.05 ≈ 0.1791`, giving
 * `≈ 4.58:1`. So no fill — preset or custom, chosen by anyone — can push the tick below 4.58:1.
 * That clears WCAG 2.1 SC 1.4.11's 3:1 non-text floor with room to spare, and clears SC 1.4.3's
 * 4.5:1 text floor as well. `ContrastingInkTest` asserts the bound by sweeping the colour cube
 * rather than by re-deriving the algebra.
 *
 * Within the offered presets the measured worst case is much better than that bound — Red 500
 * (`#F44336`) at 5.70:1 — and every preset takes the black branch. That is forced rather than lucky:
 * clearing the palette's ratified 4.5:1 floor against `ConstanzaColors.SurfaceSelected` requires
 * relative luminance >= 0.233, and black stops being the better tick only below 0.179, so a colour
 * cannot be legible enough to offer and dark enough to need a white tick at the same time. The white
 * branch exists for custom colours, which answer to no floor.
 */
fun contrastingInk(argb: Int): Color {
    val fill = Color(argb)
    return if (contrastRatio(fill, Color.Black) >= contrastRatio(fill, Color.White)) Color.Black else Color.White
}

/** The ratio [contrastingInk] actually achieves on [argb]. Exposed for tests and for reporting. */
internal fun contrastingInkRatio(argb: Int): Double {
    val fill = Color(argb)
    return max(contrastRatio(fill, Color.Black), contrastRatio(fill, Color.White))
}
