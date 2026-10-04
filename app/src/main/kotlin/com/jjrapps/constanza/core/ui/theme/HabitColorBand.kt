package com.jjrapps.constanza.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The darkest contrast [habitBandColor] will produce against [ConstanzaColors.Background].
 *
 * **Why 3.6 and not WCAG's bare 3.0.** A habit's colour is now only an 8dp identity dot — a
 * non-text graphic, so WCAG 2.1 SC 1.4.11's **3:1** is the floor that applies (graphite redesign T2).
 * But the dot is not drawn only on [ConstanzaColors.Background]: rows and sheets sit on
 * [ConstanzaColors.SurfaceRaised] (`#232327`), the lightest surface in the theme. A colour measuring
 * exactly 3.0:1 on the background measures only 2.55:1 there. Solving for 3:1 on SurfaceRaised gives
 * 3.53:1 on the background; 3.6 (3.06:1 there) adds 8-bit rounding headroom. So this one number,
 * measured against the live background as every other theme measurement is, guarantees 3:1 on every
 * surface a dot is drawn on — `HabitColorBandTest` sweeps it against SurfaceRaised to prove it.
 */
internal const val HABIT_BAND_FLOOR = 3.6

/**
 * The lightest contrast [habitBandColor] will produce against [ConstanzaColors.Background].
 *
 * **Why a ceiling at all, for a non-text dot.** Legibility needs no ceiling; calm does. The graphite
 * palette is deliberately muted — its twelve presets measure 6.20:1 to 8.37:1 on the background — and
 * an unbounded custom pick could still be a near-white or a fluorescent yellow (pure white is
 * 18.4:1) that out-shouts every named habit. 9.0 sits just above the lightest preset ([HabitColor.LIME],
 * 8.37:1), so a custom colour can be as light as any preset and no lighter.
 */
internal const val HABIT_BAND_CEILING = 9.0

private const val MIN_POSITION = 0f
private const val MAX_POSITION = 1f

/**
 * The custom colour picker's third slider axis, in place of raw HSV `value`: [bandPosition] moves the
 * target contrast ratio against [ConstanzaColors.Background] linearly across
 * `[`[HABIT_BAND_FLOOR]`, `[HABIT_BAND_CEILING]`]`, and the colour at [hue]/[saturation] whose
 * contrast equals that target is solved for ([solveForContrast]).
 *
 * **Why the slider moves contrast rather than `value`.** An earlier picker ran the raw HSV mix into a
 * clamp that *rebuilt* every out-of-band colour at exactly the floor or ceiling, so most of the
 * brightness slider was dead (saturated red produced 12 distinct colours over 101 positions).
 * Driving the target contrast itself means every position asks for a different point on the same
 * monotonic contrast curve, and the result is in-band by construction — no downstream clamp exists.
 *
 * **Saturation is not capped** (graphite redesign T2 decision). Muting customs by capping saturation
 * would change what the saturation slider's existing positions mean and how an existing colour seeds
 * them — not a trivial change — while the ceiling already removes the loudest picks. A fully
 * saturated custom is therefore still possible, but only between 3.6:1 and 9:1.
 *
 * @param bandPosition `0f` (darkest, [HABIT_BAND_FLOOR]) to `1f` (lightest, [HABIT_BAND_CEILING]),
 *   coerced into range.
 */
fun habitBandColor(hue: Float, saturation: Float, bandPosition: Float): Int {
    val clamped = bandPosition.coerceIn(MIN_POSITION, MAX_POSITION)
    val target = HABIT_BAND_FLOOR + clamped * (HABIT_BAND_CEILING - HABIT_BAND_FLOOR)
    return solveForContrast(hue, saturation, target, ConstanzaColors.Background)
}

/**
 * The inverse of [habitBandColor]'s mapping: given a colour, the slider position that would
 * reproduce it. Used to seed `CustomColorDialog`'s third slider when it opens on an existing colour.
 * Coerced so a colour outside the band (an older custom colour clamped to the previous palette's
 * band, or pure black/white) still yields a valid slider position.
 */
fun habitBandPositionOf(argb: Int): Float {
    val ratio = contrastRatio(Color(argb), ConstanzaColors.Background)
    val position = (ratio - HABIT_BAND_FLOOR) / (HABIT_BAND_CEILING - HABIT_BAND_FLOOR)
    return position.toFloat().coerceIn(MIN_POSITION, MAX_POSITION)
}

private const val MIN_VALUE = 0f
private const val MAX_VALUE = 1f
private const val MIN_SATURATION = 0f

/** Bisection steps for both searches below. 40 halves the initial `0f..1f` span to well under a
 *  single `sRGB` byte's width many times over, so it is cheap precision rather than a tuned
 *  constant. */
private const val BISECTION_STEPS = 40

/**
 * Solves for the argb at fixed [hue]/[saturation] whose contrast against [ground] equals [target].
 * Shared by the live band ([habitBandColor]) and the frozen legacy one (`clampToLegacyHabitBand`),
 * which pass their own ground and targets; the solver itself is pure arithmetic and has never
 * changed, which is what lets the legacy migrations keep producing byte-identical output.
 *
 * **Value first.** Value scales every RGB channel by the same factor at fixed hue/saturation, so
 * luminance — and therefore contrast against a fixed dark ground — is monotonic in value and a plain
 * bisection converges on it. **Saturation only when value alone cannot reach [target]** even at
 * `value = 1f` (a saturated blue or red carries little luminance): value is then fixed at `1f` and
 * saturation is bisected downward toward white, keeping as much of the hue as the target allows.
 * [Hsv.toArgb] always returns an opaque colour, so every result is opaque.
 */
internal fun solveForContrast(hue: Float, saturation: Float, target: Double, ground: Color): Int =
    if (ratioAt(hue, saturation, MAX_VALUE, ground) < target) {
        Hsv(hue, bisectSaturation(hue, saturation, target, ground), MAX_VALUE).toArgb()
    } else {
        Hsv(hue, saturation, bisectValue(hue, saturation, target, ground)).toArgb()
    }

private fun ratioAt(hue: Float, saturation: Float, value: Float, ground: Color): Double =
    contrastRatio(Color(Hsv(hue, saturation, value).toArgb()), ground)

/** Contrast is monotonically increasing in `value` at fixed hue/saturation, so this is a plain
 *  bisection for the `value` whose contrast equals [target]. [Hsv.toArgb] rounds each channel to a
 *  byte, so the last step picks whichever of the two final bracketing values lands closer to
 *  [target] once rounded. */
private fun bisectValue(hue: Float, saturation: Float, target: Double, ground: Color): Float {
    var low = MIN_VALUE
    var high = MAX_VALUE
    repeat(BISECTION_STEPS) {
        val mid = (low + high) / 2f
        if (ratioAt(hue, saturation, mid, ground) < target) low = mid else high = mid
    }
    return closerToTarget(low, high, target) { ratioAt(hue, saturation, it, ground) }
}

/** Contrast is monotonically decreasing in `saturation` at `value = 1f` (more saturated is further
 *  from white, hence darker), so this bisects downward from [originalSaturation] toward `0f` for the
 *  `saturation` whose contrast equals [target]. Same rounding-boundary refinement as [bisectValue]. */
private fun bisectSaturation(hue: Float, originalSaturation: Float, target: Double, ground: Color): Float {
    var low = MIN_SATURATION
    var high = originalSaturation
    repeat(BISECTION_STEPS) {
        val mid = (low + high) / 2f
        if (ratioAt(hue, mid, MAX_VALUE, ground) < target) high = mid else low = mid
    }
    return closerToTarget(low, high, target) { ratioAt(hue, it, MAX_VALUE, ground) }
}

/** Whichever of the bisection's two final bracketing parameters yields a ratio closer to [target],
 *  measured after the same byte-rounding [Hsv.toArgb] applies for real. */
private inline fun closerToTarget(low: Float, high: Float, target: Double, ratioOf: (Float) -> Double): Float {
    val lowError = kotlin.math.abs(ratioOf(low) - target)
    val highError = kotlin.math.abs(ratioOf(high) - target)
    return if (lowError <= highError) low else high
}
