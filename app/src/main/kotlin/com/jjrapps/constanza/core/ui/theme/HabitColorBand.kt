package com.jjrapps.constanza.core.ui.theme

import androidx.compose.ui.graphics.Color

/** What [clampToHabitBand] aims for when a colour measures below the legible band. */
internal const val HABIT_BAND_FLOOR = 7.0

/** What [clampToHabitBand] aims for when a colour measures above the legible band. */
internal const val HABIT_BAND_CEILING = 11.0

/**
 * 8-bit channel quantisation slack. [Hsv.toArgb] rounds every channel to a byte, so the bisections
 * below search a *step* function, not a smooth one — [HABIT_BAND_FLOOR] and [HABIT_BAND_CEILING] are
 * the targets a continuous search aims at, not values the discrete 24-bit colour space is guaranteed
 * to actually contain. [bisectValue]/[bisectSaturation] already pick whichever of their two final
 * bracketing candidates lands closer to the target once rounded (see their own KDoc), but "closer"
 * can still be a step away rather than exact — the palette itself is proof: `HabitColor`'s 21 presets
 * were produced by the same reasoning this clamp uses and measure `6.98:1` to `11.05:1`, not exactly
 * `[7.0, 11.0]`. So every passthrough check and every test asserting band membership compares against
 * `[HABIT_BAND_FLOOR - HABIT_BAND_TOLERANCE, HABIT_BAND_CEILING + HABIT_BAND_TOLERANCE]`, never
 * against the bare targets — a preset (or an already-legible custom colour) must never fail its own
 * re-check.
 */
internal const val HABIT_BAND_TOLERANCE = 0.1

private const val MIN_VALUE = 0f
private const val MAX_VALUE = 1f
private const val MIN_SATURATION = 0f

/** Bisection steps for both searches below. 40 halves the initial `0f..1f` span to well under a
 *  single `sRGB` byte's width many times over — far more than the ±1-channel tolerance this
 *  function's contract allows for — so it is cheap precision rather than a tuned constant. */
private const val BISECTION_STEPS = 40

/**
 * Pushes [argb] into the legible band `[7:1, 11:1]` against [ConstanzaColors.Background], preserving
 * hue wherever that is possible. Presets never need this — [HabitPalette]'s 22 members are already
 * inside the band by construction — so this exists entirely for the free custom colour wheel
 * (`CustomColorDialog.kt`), which offers the whole sRGB cube.
 *
 * **Why a floor (and a ceiling) is new here.** `ContrastingInk.kt` records, correctly at the time it
 * was written, that a custom colour "answers to no floor" — that was tolerable when the colour was a
 * 12dp dot: a navy habit was merely an ugly dark dot, still legible as a dot because the *tick* on it
 * had its own guaranteed floor. Once the colour is painted on the habit's own *name*, "ugly dark dot"
 * becomes "text you cannot read": navy `#1A237E` measures 1.48:1 as body text on this ground, forest
 * `#1B5E20` measures 2.48:1, and pure black is 1.07:1 — all three fail even WCAG's 3:1 *non-text*
 * floor, let alone a text one. A ceiling exists for the same reason in the other direction: pure
 * white measures 19.55:1, which is louder than every preset in the band this palette was retoned to,
 * so an unclamped custom colour could still out-shout every named habit even after the preset retone.
 *
 * **Why the band is preserved by hue rather than replaced with a fixed tone.** A uniform tone was
 * tried for the preset palette itself and rejected (see [HabitColor]'s KDoc) because it collapses
 * distinct hues into the same colour. The same argument applies here: rebuilding a clamped custom
 * colour from black or white instead of from its own hue would turn every out-of-band pick into the
 * same grey, discarding the one thing a "custom" colour is for.
 *
 * ## The algorithm
 *
 * 1. Measure the contrast ratio already achieved. Inside
 *    `[HABIT_BAND_FLOOR - HABIT_BAND_TOLERANCE, HABIT_BAND_CEILING + HABIT_BAND_TOLERANCE]`, [argb]
 *    is returned unchanged — this function only ever moves a colour that is actually out of band, and
 *    the tolerance is there so a colour this very function already produced (every preset, and any
 *    already-clamped custom colour) cannot fail its own re-check merely because `7.0`/`11.0` are not
 *    themselves reachable 8-bit values (see [HABIT_BAND_TOLERANCE]'s KDoc).
 * 2. Otherwise the target is [HABIT_BAND_FLOOR] (below the floor) or [HABIT_BAND_CEILING] (above the
 *    ceiling). Decompose to
 *    [Hsv] and bisect **value** in `0f..1f`, holding hue and saturation fixed, for the argb whose
 *    contrast against [ConstanzaColors.Background] equals the target. Value scales every RGB channel
 *    by the same factor at fixed hue/saturation ([Hsv.toArgb]'s `chroma`/`match` are both linear in
 *    `value`), so luminance — and therefore contrast against a fixed dark ground — is monotonic in
 *    value and a plain bisection converges on it.
 * 3. Value alone cannot always reach the floor: a fully saturated hue whose luminance weight is low
 *    (blue's channel carries only 0.0722 of relative luminance, red's 0.2126) stays under 7:1 even at
 *    `value = 1f` — brightening a saturated blue or red only makes a *more* saturated version of the
 *    same too-dark colour, it does not make it lighter. When that happens, value is fixed at `1f` and
 *    **saturation** is bisected downward from the original saturation toward `0f` instead: desaturating
 *    toward white is the only remaining way up, and it is bisected rather than dropped to `0f`
 *    outright so as much of the original hue survives as the floor allows.
 * 4. [Hsv.toArgb] always returns a fully opaque colour, so every clamped result is opaque too.
 *
 * Measured cases (`HabitColorBandTest`): navy `#1A237E` (1.48:1) clamps to `#8791FF` (7.00:1); deep
 * purple `#311B92` (1.58:1) to `#9F8AFF` (7.03:1); maroon `#7B1F1F` (1.92:1) to `#FF6A6A` (7.00:1);
 * chocolate `#5D4037` (2.10:1) to `#CB8C78` (7.04:1); forest `#1B5E20` (2.48:1) to `#33B23C` (7.05:1);
 * black `#000000` (1.07:1) to `#9B9B9B` (7.03:1) — a grey, via the value branch, since black has no
 * hue to preserve; white `#FFFFFF` (19.55:1) to `#C2C2C2` (10.97:1); and pure red `#FF0000` (4.89:1)
 * — the value this palette's own [HabitColor.RED] KDoc explains cannot reach 7:1 as a true red
 * either — to `#FF6A6A` (7.00:1) via the saturation branch, landing on the same colour maroon does.
 */
fun clampToHabitBand(argb: Int): Int {
    val ratio = contrastRatio(Color(argb), ConstanzaColors.Background)
    val passthroughRange = (HABIT_BAND_FLOOR - HABIT_BAND_TOLERANCE)..(HABIT_BAND_CEILING + HABIT_BAND_TOLERANCE)
    if (ratio in passthroughRange) return argb

    val target = if (ratio < HABIT_BAND_FLOOR) HABIT_BAND_FLOOR else HABIT_BAND_CEILING
    val hsv = hsvOf(argb)
    return solveForTarget(hsv.hue, hsv.saturation, target)
}

/**
 * Solves for the argb at fixed [hue]/[saturation] whose contrast against [ConstanzaColors.Background]
 * equals [target], choosing the value branch or the saturation branch exactly as
 * [clampToHabitBand]'s KDoc describes (step 3): value first, and saturation only when value alone
 * cannot reach [target] even at `value = 1f`.
 *
 * This is also [habitBandColor]'s solver. The two callers pass different targets — `7.0` or `11.0`
 * from [clampToHabitBand]'s own out-of-band measurement, or any point in between from
 * [habitBandColor]'s slider position — but the branch selection and both bisections are identical
 * either way, so there is exactly one implementation of "find the colour at this hue/saturation whose
 * contrast is `target`" rather than two copies that could drift apart.
 *
 * **Why dropping the `target == HABIT_BAND_FLOOR` half of the original condition changes nothing.**
 * The original branch guard was `target == HABIT_BAND_FLOOR && ratioAtMaxValue < HABIT_BAND_FLOOR`.
 * For `target == HABIT_BAND_CEILING` that guard was already always `false`, because a colour is only
 * ever clamped *down* to the ceiling when its own ratio already exceeds `HABIT_BAND_CEILING +
 * HABIT_BAND_TOLERANCE`, and contrast is monotonically increasing in `value` (see [bisectValue]'s
 * KDoc) — so `ratioAt(hue, saturation, MAX_VALUE)` is at least that colour's own ratio, which is
 * already above the ceiling, and `ratioAtMaxValue < target` is therefore always `false` for a ceiling
 * target. The saturation branch was unreachable for `target == HABIT_BAND_CEILING` before this
 * refactor exactly as it is unreachable after it — this function just tests `ratioAtMaxValue < target`
 * directly instead of gating that test behind a redundant floor check. [HabitColorBandTest]'s measured
 * cases (navy, deep purple, maroon, chocolate, forest, black landing on the saturation-only "black has
 * no hue" grey, white on the value-only ceiling, red on the saturation branch) are unchanged by this
 * refactor and remain the proof.
 */
private fun solveForTarget(hue: Float, saturation: Float, target: Double): Int =
    if (ratioAt(hue, saturation, MAX_VALUE) < target) {
        Hsv(hue, bisectSaturation(hue, saturation, target), MAX_VALUE).toArgb()
    } else {
        Hsv(hue, saturation, bisectValue(hue, saturation, target)).toArgb()
    }

/**
 * The custom colour picker's third slider axis, in place of raw HSV `value`.
 *
 * **Why the slider moves contrast rather than `value`.** The picker used to run `Hsv(hue, saturation,
 * value).toArgb()` straight into [clampToHabitBand] for preview and commit. That clamp *rebuilds* any
 * out-of-band colour from scratch at exactly [HABIT_BAND_FLOOR] or [HABIT_BAND_CEILING] — it never
 * reads the caller's `value` once a colour is out of band — so every `value` position that clamped to
 * the same target produced the identical output colour. Measured over the slider's 101 positions
 * (`0f` to `1f` in 0.01 steps) before this change: saturated red (`h=12, s=1`) produced 12 distinct
 * outputs, with 50 of the 101 positions identical to the maximum; saturated blue (`h=212, s=1`)
 * produced 13 distinct outputs with 38 positions collapsed; saturated green (`h=120, s=1`) produced
 * 22 with 12 collapsed; yellow (`h=50, s=1`) produced 31 with 3 collapsed. Only `value == 0f` ever
 * looked different, because black has no hue to preserve and clamped to a plain grey instead of the
 * hue-preserving rebuild every other floor-bound value collapsed onto — which is exactly what was
 * reported: the slider only visibly did anything at its two extremes.
 *
 * Reparameterising the slider to move [bandPosition] — the target contrast ratio itself, linearly
 * across `[`[HABIT_BAND_FLOOR]`, `[HABIT_BAND_CEILING]`]` — and solving for the matching colour with
 * [solveForTarget] removes the dead zone rather than patching around it: every position now asks for a
 * *different* point on the same monotonic contrast curve the clamp already searches, so every position
 * produces a different colour (modelled: 95/101 distinct for blue, 101/101 for red, 84/101 for yellow,
 * 46/101 for green), there is no discontinuity at zero, and the result is in-band by construction — no
 * downstream clamp is needed or applied.
 *
 * @param bandPosition `0f` (darkest legible, [HABIT_BAND_FLOOR]) to `1f` (lightest legible,
 *   [HABIT_BAND_CEILING]), coerced into range.
 */
fun habitBandColor(hue: Float, saturation: Float, bandPosition: Float): Int {
    val clamped = bandPosition.coerceIn(MIN_VALUE, MAX_VALUE)
    val target = HABIT_BAND_FLOOR + clamped * (HABIT_BAND_CEILING - HABIT_BAND_FLOOR)
    return solveForTarget(hue, saturation, target)
}

/**
 * The inverse of [habitBandColor]'s mapping: given an already-in-band colour, the slider position that
 * would reproduce it. Used to seed [CustomColorDialog][com.jjrapps.constanza.habit.CustomColorDialog]'s
 * third slider when it opens on an existing colour, so editing a habit starts the slider where that
 * colour actually measures instead of resetting it to one end.
 *
 * Measures [argb]'s own contrast against [ConstanzaColors.Background] and maps it back onto `0f..1f`
 * across `[`[HABIT_BAND_FLOOR]`, `[HABIT_BAND_CEILING]`]`, the exact inverse of [habitBandColor]'s
 * linear map. Coerced so a colour outside the band (a pre-band-fix legacy value, or pure black/white)
 * still yields a valid, clamped slider position rather than a value the slider cannot represent.
 */
fun habitBandPositionOf(argb: Int): Float {
    val ratio = contrastRatio(Color(argb), ConstanzaColors.Background)
    val position = (ratio - HABIT_BAND_FLOOR) / (HABIT_BAND_CEILING - HABIT_BAND_FLOOR)
    return position.toFloat().coerceIn(MIN_VALUE, MAX_VALUE)
}

private fun ratioAt(hue: Float, saturation: Float, value: Float): Double =
    contrastRatio(Color(Hsv(hue, saturation, value).toArgb()), ConstanzaColors.Background)

/** Contrast is monotonically increasing in `value` at fixed hue/saturation, so this is a plain
 *  bisection for the `value` whose contrast equals [target]. [Hsv.toArgb] rounds each channel to a
 *  byte, which turns the searched function into a step rather than a smooth curve, so the last step
 *  picks whichever of the bisection's two final bracketing values lands closer to [target] once
 *  rounded, instead of trusting the raw midpoint to have landed on the near side of a rounding
 *  boundary. */
private fun bisectValue(hue: Float, saturation: Float, target: Double): Float {
    var low = MIN_VALUE
    var high = MAX_VALUE
    repeat(BISECTION_STEPS) {
        val mid = (low + high) / 2f
        if (ratioAt(hue, saturation, mid) < target) low = mid else high = mid
    }
    return closerToTarget(low, high, target) { ratioAt(hue, saturation, it) }
}

/** Contrast is monotonically decreasing in `saturation` at `value = 1f` (more saturated is further
 *  from white, hence darker), so this bisects downward from [originalSaturation] toward `0f` for the
 *  `saturation` whose contrast equals [target]. Same rounding-boundary refinement as [bisectValue]. */
private fun bisectSaturation(hue: Float, originalSaturation: Float, target: Double): Float {
    var low = MIN_SATURATION
    var high = originalSaturation
    repeat(BISECTION_STEPS) {
        val mid = (low + high) / 2f
        if (ratioAt(hue, mid, MAX_VALUE) < target) high = mid else low = mid
    }
    return closerToTarget(low, high, target) { ratioAt(hue, it, MAX_VALUE) }
}

/** Whichever of the bisection's two final bracketing parameters yields a ratio closer to [target],
 *  measured after the same byte-rounding [Hsv.toArgb] applies for real. */
private inline fun closerToTarget(low: Float, high: Float, target: Double, ratioOf: (Float) -> Double): Float {
    val lowError = kotlin.math.abs(ratioOf(low) - target)
    val highError = kotlin.math.abs(ratioOf(high) - target)
    return if (lowError <= highError) low else high
}
