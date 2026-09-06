package com.jjrapps.constanza.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The app's spacing scale (design.md decision 2). A `.dp` literal in screen code becomes one of
 * these tokens only if its value changes or the code touching it is new during the tonal pass;
 * every unchanged literal is deliberately left alone so a screen's diff stays readable.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/**
 * Fixed component dimensions shared across screens. [HabitDot]/[HabitDotSlot] are the habit colour
 * dot (design.md decision 6, work unit 4); [Swatch] is the colour picker swatch, moved here from
 * `HabitEditorScreen.kt`'s private constants (work unit 5) so the editor and both list screens
 * agree on one number instead of each holding a private copy. [PagerDot] is
 * `first-run-onboarding`'s progress indicator (design.md §12, A7) — a size, not a gap, so it belongs
 * here as a `Dimens` token rather than being reused from `Spacing`, which is a scale of gaps and
 * padding, not sizes.
 *
 * **`SwatchBorder` is gone, and so is what it drew.** It was a 3dp ring in `primary` around the
 * selected swatch, and it read badly on every fill it circled. The selected swatch now carries a
 * tick drawn *inside* it, tinted black or white by the fill's own luminance (`contrastingInk`), so
 * the marker is legible on a palette that is no longer a closed set of six. [SwatchTick] is that
 * tick's size.
 *
 * [SwatchTouchTarget] is the swatch's *hit* area, not its paint: [Swatch] is 40dp, below the 48dp
 * minimum touch target, and a grid of twenty-four of them a finger-width apart is exactly where that
 * matters. The circle stays 40dp; the selectable box around it is 48dp.
 *
 * [FieldBorder] is Material 3's own unfocused outlined-text-field border width, named here so a
 * control that is *shaped* like a form field without *being* an `OutlinedTextField` can line up
 * with the real ones instead of guessing (`habit.ScheduleEditors`'s reminder-time row).
 *
 * [PickerTrack]/[PickerPreview] are the custom-colour dialog's gradient slider bar and its live
 * preview chip.
 */
object Dimens {
    val HabitDot = 12.dp
    val HabitDotSlot = 24.dp
    val Swatch = 40.dp
    val SwatchTouchTarget = 48.dp
    val SwatchTick = 22.dp
    val PagerDot = 8.dp
    val FieldBorder = 1.dp
    val PickerTrack = 12.dp
    val PickerPreview = 56.dp
}
