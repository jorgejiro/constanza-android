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
 * Fixed component dimensions shared across screens. [Swatch] is the colour picker swatch, moved here from
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
 * minimum touch target, and a grid of twenty-two of them a finger-width apart is exactly where that
 * matters. The circle stays 40dp; the selectable box around it is 48dp.
 *
 * [FieldBorder] is Material 3's own unfocused outlined-text-field border width, named here so a
 * control that is *shaped* like a form field without *being* an `OutlinedTextField` can line up
 * with the real ones instead of guessing (`habit.ScheduleEditors`'s reminder-time row).
 *
 * [PickerTrack]/[PickerPreview] are the custom-colour dialog's gradient slider bar and its live
 * preview chip.
 *
 * [StatusGlyph] (today-status-icons) is the square an answered Today slot's status glyph draws in
 * — `Icon`'s own default size for a `material-icons-core` vector is 24dp, but that glyph now sits
 * inline beside `bodyMedium`/demoted-time text rather than standing alone, so it is sized down to
 * match an inline icon next to body copy rather than a standalone control. [StatusGlyphDashWidth]/
 * [StatusGlyphDashHeight] are the hand-drawn dash that stands in for [StatusGlyph] on a `SKIPPED`
 * slot (`material-icons-core` ships no minus/remove glyph) — sized to approximate the visual width
 * `Icons.Filled.Check`/`Close` actually draw inside that same square, since Material's vector
 * glyphs do not fill their full viewport.
 *
 * [AnswerPillWidth]/[AnswerPillHeight] (today-one-line-row) are the pending Today slot's Sí/No
 * pills, straight from the owner-approved `TodayOneLineRowPrototype`. The same paint/hit split
 * [Swatch]/[SwatchTouchTarget] already established applies here: 46x28dp is the pill's own PAINT,
 * below the 48dp minimum touch target on its short 28dp side, so [AnswerPillTouchTarget] is the
 * square hit box the pill is centred inside rather than a size the pill itself ever draws at.
 *
 * [MinTouchTarget] (today-one-line-row, vertical-rhythm correction) is the standard 48dp Android
 * accessible minimum, same value as [SwatchTouchTarget]/[AnswerPillTouchTarget] but named
 * generically because it is applied to a whole Today ROW now, not one small control inside it: an
 * answered row's line is itself the tap target that opens the change dialog, and text/glyph
 * content alone measures well under 48dp on a muted (Contestados) row. A pending row takes the
 * identical floor so both row types settle at the same minimum for the same reason, rather than
 * merely by coincidence of their own content heights.
 */
object Dimens {
    val Swatch = 40.dp
    val SwatchTouchTarget = 48.dp
    val SwatchTick = 22.dp
    val PagerDot = 8.dp
    val FieldBorder = 1.dp
    val PickerTrack = 12.dp
    val PickerPreview = 56.dp
    val StatusGlyph = 18.dp
    val StatusGlyphDashWidth = 12.dp
    val StatusGlyphDashHeight = 2.dp
    val AnswerPillWidth = 46.dp
    val AnswerPillHeight = 28.dp
    val AnswerPillTouchTarget = 48.dp
    val MinTouchTarget = 48.dp
}
