package com.jjrapps.constanza.core.data.migration

/**
 * The frozen "21-preset legible-band palette" -> "12-preset muted graphite palette" habit-colour map
 * (graphite redesign T2), in [HabitColorRetoneRemap]'s and [HabitColorRetireRemap]'s shape: a
 * literal-int map plus a total `normalize` function. Both sides are literal ints, never
 * `HabitColor.X.argb`, so a future repaint cannot silently change what this map has always said.
 * Consumed by `AppMigrations.migration7To8` (Room v7 -> v8) and by backup-import normalisation for
 * files below backup schema version 5.
 *
 * ## How the table was computed (offline, once)
 *
 * For every retired preset, the twelve muted presets were ranked by CIE76 ΔE (CIE Lab over D65, the
 * same formula `HabitPaletteTest` uses). The nearest one was taken, except in five cases where the
 * nearest crossed into a different hue family while the runner-up — close behind — kept the family
 * the person had chosen; those take the runner-up and say so on their line. ΔE to the chosen target
 * is given per line; `(nearest X dE n)` marks an override.
 *
 * ## What it does not touch
 *
 * Only the 21 retired preset ints are keys. Every other int — a current preset, or a genuine custom
 * colour — passes through [normalize] unchanged: unlike [HabitColorRetoneRemap] and
 * [HabitColorRetireRemap] there is **no clamp fallthrough**. A non-text dot only needs 3:1, and the
 * previous palette's band already guaranteed far more than that for every stored custom colour, so
 * there is nothing to repair and the user's own pick is left exactly as they made it.
 *
 * Keys and values are disjoint (no retired preset equals a muted one), so applying [normalize] twice
 * is the same as applying it once — `HabitColorMutedRemapTest` asserts it.
 */
internal object HabitColorMutedRemap {

    val LEGACY_TO_CURRENT: Map<Int, Int> = mapOf(
        0xFF4CAF50.toInt() to 0xFF7FA889.toInt(), // GREEN -> SAGE, dE 39.9 (nearest LIME dE 31.1, a yellow-green)
        0xFFE860FF.toInt() to 0xFFA98BB8.toInt(), // VIOLET -> LAVENDER, dE 64.6
        0xFFFF6D48.toInt() to 0xFFC48A7D.toInt(), // RED -> CLAY, dE 46.0
        0xFF03A9F4.toInt() to 0xFF7D9CC4.toInt(), // LIGHT_BLUE -> BLUE, dE 25.0
        0xFFF5B907.toInt() to 0xFFC4A77A.toInt(), // AMBER -> SAND, dE 53.8 (nearest OLIVE dE 51.2, a yellow-green)
        0xFFF48FB1.toInt() to 0xFFC2879F.toInt(), // PINK -> ROSE, dE 18.2
        0xFF55D7B8.toInt() to 0xFF6FA6A6.toInt(), // MINT -> TEAL, dE 30.6 (nearest SAGE dE 27.2, a green)
        0xFF8896E3.toInt() to 0xFF8F94C9.toInt(), // INDIGO -> INDIGO, dE 13.4
        0xFFDEC233.toInt() to 0xFFB5B072.toInt(), // YELLOW -> OLIVE, dE 38.6
        0xFFFC6799.toInt() to 0xFFC2879F.toInt(), // MAGENTA -> ROSE, dE 35.5
        0xFFB1CA33.toInt() to 0xFF9CB87A.toInt(), // LIME -> LIME, dE 40.0
        0xFF00AE9D.toInt() to 0xFF6FA6A6.toInt(), // TEAL -> TEAL, dE 23.5
        0xFFD477E4.toInt() to 0xFFA98BB8.toInt(), // PURPLE -> LAVENDER, dE 39.2
        0xFF7CB342.toInt() to 0xFF9CB87A.toInt(), // LIGHT_GREEN -> LIME, dE 27.6
        0xFF00ABBD.toInt() to 0xFF6FA6A6.toInt(), // CYAN -> TEAL, dE 18.3
        0xFFFF9800.toInt() to 0xFFC4A77A.toInt(), // ORANGE -> SAND, dE 56.5
        0xFFB0958B.toInt() to 0xFFB89A80.toInt(), // BROWN -> TAN, dE 9.1
        0xFF469DFF.toInt() to 0xFF7D9CC4.toInt(), // BLUE -> BLUE, dE 32.8 (nearest INDIGO dE 28.7, a periwinkle)
        0xFFA19F25.toInt() to 0xFFB5B072.toInt(), // OLIVE -> OLIVE, dE 28.2
        0xFFB992FF.toInt() to 0xFFA98BB8.toInt(), // LILAC -> LAVENDER, dE 34.8 (nearest INDIGO dE 34.3, a blue)
        0xFFE9BA75.toInt() to 0xFFC4A77A.toInt(), // PEACH -> SAND, dE 16.8
    )

    /** A total function: a retired preset takes its mapped muted value; every other int, current
     *  preset or custom colour alike, passes through unchanged. */
    fun normalize(argb: Int): Int = LEGACY_TO_CURRENT[argb] ?: argb
}
