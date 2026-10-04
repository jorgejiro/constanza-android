package com.jjrapps.constanza.core.ui.theme

import androidx.compose.ui.graphics.Color

/** What [clampToLegacyHabitBand] aims for when a colour measures below the frozen legacy band. */
internal const val LEGACY_HABIT_BAND_FLOOR = 7.0

/** What [clampToLegacyHabitBand] aims for when a colour measures above the frozen legacy band. */
internal const val LEGACY_HABIT_BAND_CEILING = 11.0

/**
 * 8-bit channel quantisation slack. [Hsv.toArgb] rounds every channel to a byte, so the bisections
 * behind [solveForContrast] search a *step* function: [LEGACY_HABIT_BAND_FLOOR] and
 * [LEGACY_HABIT_BAND_CEILING] are targets, not values the discrete 24-bit colour space is guaranteed
 * to contain (the retired 21-preset palette measured 6.98:1 to 11.05:1). The passthrough check below
 * compares against the band widened by this tolerance, so a colour the clamp itself produced never
 * fails its own re-check.
 */
internal const val LEGACY_HABIT_BAND_TOLERANCE = 0.1

private const val HABIT_BAND_GROUND_ARGB = 0xFF110B06.toInt()

/**
 * The ground every legacy band measurement is taken against: the retired warm-dark background
 * `#110B06`, frozen here on purpose rather than read from [ConstanzaColors.Background].
 *
 * [clampToLegacyHabitBand] is called by the v5/v6 database migrations (`HabitColorRetoneRemap`,
 * `HabitColorRetireRemap`) and by backup-import normalisation for schema versions below 4. Those
 * outputs are historical facts — a migration must produce the same bytes whenever it runs — so they
 * cannot follow a re-tone of the live theme. With the graphite `#141416` the retired GREEN preset
 * would have dropped out of its own band and been silently rewritten.
 */
internal val HabitBandGround = Color(HABIT_BAND_GROUND_ARGB)

/**
 * **Frozen.** The `[7:1, 11:1]` "legible name text" band of the retired 21-preset palette, kept only
 * because migrations and old-backup normalisation replay it. Nothing in the live UI may call this:
 * the graphite redesign shows a habit's colour as a non-text dot, whose band is [habitBandColor]'s.
 *
 * Inside `[LEGACY_HABIT_BAND_FLOOR - LEGACY_HABIT_BAND_TOLERANCE, LEGACY_HABIT_BAND_CEILING +
 * LEGACY_HABIT_BAND_TOLERANCE]` against [HabitBandGround], [argb] is returned unchanged; otherwise it
 * is rebuilt at its own hue/saturation at exactly the floor or the ceiling via [solveForContrast].
 *
 * Measured cases (`LegacyHabitColorBandTest`): navy `#1A237E` (1.48:1) clamps to `#8791FF` (7.00:1);
 * deep purple `#311B92` to `#9F8AFF`; maroon `#7B1F1F` to `#FF6A6A`; chocolate `#5D4037` to `#CB8C78`;
 * forest `#1B5E20` to `#33B23C`; black to `#9B9B9B` (a grey, since black has no hue); white
 * `#FFFFFF` (19.55:1) to `#C2C2C2` (10.97:1); and pure red `#FF0000` (4.89:1) to `#FF6A6A` via the
 * saturation branch.
 */
fun clampToLegacyHabitBand(argb: Int): Int {
    val ratio = contrastRatio(Color(argb), HabitBandGround)
    val low = LEGACY_HABIT_BAND_FLOOR - LEGACY_HABIT_BAND_TOLERANCE
    val high = LEGACY_HABIT_BAND_CEILING + LEGACY_HABIT_BAND_TOLERANCE
    if (ratio in low..high) return argb

    val target = if (ratio < LEGACY_HABIT_BAND_FLOOR) LEGACY_HABIT_BAND_FLOOR else LEGACY_HABIT_BAND_CEILING
    val hsv = hsvOf(argb)
    return solveForContrast(hsv.hue, hsv.saturation, target, HabitBandGround)
}
