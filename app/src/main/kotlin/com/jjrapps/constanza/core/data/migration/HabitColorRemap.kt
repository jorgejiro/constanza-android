package com.jjrapps.constanza.core.data.migration

/**
 * Task 2.1 (design.md decision 3). The frozen v1 -> v2 habit-colour bijection: the six legacy
 * `HabitEditorViewModel.HabitColorPalette` swatches, each mapped to its warm-dark
 * `core.ui.theme.HabitPalette` counterpart. Consumed by `AppMigrations`'s `MIGRATION_1_2`
 * (already-persisted habits) and by `BackupImporter` (legacy backup files, gated on
 * `schemaVersion < 2`) — the same one-to-one mapping both places, per the `habit-management` spec's
 * "Persisted Habit Colour Stays On-Palette Across A Palette Change".
 *
 * **Both sides are literal ints, never `HabitColor.X.argb`.** This map is a frozen historical
 * artifact: it describes what v1 meant and what v2 meant at the moment this migration was
 * written. If a future re-tone changes `HabitColor.VIOLET`, a palette-referencing `Migration(1,2)`
 * would silently start writing a value that never existed at version 2, and
 * `AppDatabaseMigrationTest` would still pass while the two migrations disagreed about what v2
 * means. That has now actually happened — see below — which is why the literal-ints rule was worth
 * writing down before it was needed.
 *
 * **Version 3 does not remap, and the reason is a feature, not an omission.** The palette this map
 * writes into (the six warm-dark pastels) has been replaced by twenty-three standard colour families
 * plus a free custom colour. No third entry is added here and no `Migration(2,3)` rewrites colours,
 * because the premise of the whole exercise is gone: a habit holding a colour that is not one of
 * the offered presets is no longer orphaned, it simply opens on the picker's custom swatch showing
 * its own colour. The six pastels below are exactly such colours. `Habit.colorArgb` has always been
 * a plain `Int` end to end — Room column, domain model, backup field, `NotificationPoster.setColor`
 * — so nothing about storage changes either.
 *
 * `HabitColorRemapTest` therefore pins both sides of this map to literals rather than asserting the
 * right-hand values are current palette members, which is the assertion that used to guard drift
 * and would now be asserting something false.
 */
internal object HabitColorRemap {

    /**
     * legacy `HabitColorPalette.SWATCHES` int -> current `HabitPalette.ARGB` int. Disjoint domains
     * (checked: `00897B/1E88E5/E53935/FB8C00/8E24AA/43A047` vs
     * `FF9FA8/FFA8DC/CBB2FF/8FC5FF/5DD6C7/8BDB95`), so no entry can ever re-map a value another
     * entry already wrote.
     */
    val LEGACY_TO_CURRENT: Map<Int, Int> = mapOf(
        0xFF00897B.toInt() to 0xFF5DD6C7.toInt(), // teal -> teal
        0xFF1E88E5.toInt() to 0xFF8FC5FF.toInt(), // blue -> blue
        0xFFE53935.toInt() to 0xFFFF9FA8.toInt(), // red -> red
        0xFF8E24AA.toInt() to 0xFFCBB2FF.toInt(), // purple -> violet
        0xFF43A047.toInt() to 0xFF8BDB95.toInt(), // green -> green
        0xFFFB8C00.toInt() to 0xFFFFA8DC.toInt(), // orange -> pink (the one family change)
    )

    /** A total function: any int not in [LEGACY_TO_CURRENT] (already current, or unrecognized)
     *  passes through unchanged rather than being coerced onto some arbitrary palette member. */
    fun normalize(argb: Int): Int = LEGACY_TO_CURRENT[argb] ?: argb
}
