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
 * Fixed component dimensions shared across screens. [PagerSegmentWidth]/
 * [PagerSegmentHeight] are `first-run-onboarding`'s progress indicator (graphite redesign: 24x4dp
 * segments, replacing the 8dp pager dots) — sizes, not gaps, so they belong here as `Dimens` tokens
 * rather than being reused from `Spacing`, which is a scale of gaps and padding, not sizes.
 * [PrimaryButtonHeight] is the graphite full-width light pill (editor Save, onboarding primary).
 *
 * [ColorDot] is the colour picker's swatch (graphite redesign): a 24dp dot hit through a
 * [ColorDotTouchTarget] box. Selection is a [ColorDotRing] ring in the text colour,
 * [ColorDotRingStroke] wide, around a [ColorDotSelected] dot — the ring is one achromatic tone that
 * reads on any fill, which is why it replaced the per-fill tick.
 *
 * [FieldBorder] is Material 3's own unfocused outlined-text-field border width, named here so a
 * control that is *shaped* like a form field without *being* one can line up with the real ones.
 *
 * [SettingsRow] is the minimum height of a graphite list row (editor switch and time rows,
 * settings rows); [Chip] the height of a settings pill chip; [Stepper] one round −/+ button.
 *
 * [PickerTrack]/[PickerPreview] are the custom-colour dialog's gradient slider bar and its live
 * preview dot.
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
 * [AnswerButton]/[AnswerButtonTouchTarget] (graphite redesign) are the pending Today slot's round
 * ✕/✓ controls: painted as a 44dp circle, hit as a 48dp square around it — the same paint/hit
 * split the colour picker also uses ([ColorDot]/[ColorDotTouchTarget]).
 *
 * [HabitDot] is the habit-colour dot leading a row, the only place a habit's colour is painted.
 * [CompactIconButton] is the graphite 44dp icon button (Today's header actions, day chevrons and
 * the multi-reminder expander); [ProgressBar] is the Today progress line's thickness, and
 * [ReminderSegmentWidth]/[ReminderSegmentHeight] one segment of a multi-reminder habit's
 * per-reminder bar.
 *
 * [MinTouchTarget] (today-one-line-row, vertical-rhythm correction) is the standard 48dp Android
 * accessible minimum, same value as [AnswerButtonTouchTarget] but named
 * generically because it is applied to a whole Today ROW now, not one small control inside it: an
 * answered row's line is itself the tap target that opens the change dialog, and text/glyph
 * content alone measures well under 48dp on a muted (Contestados) row. A pending row takes the
 * identical floor so both row types settle at the same minimum for the same reason, rather than
 * merely by coincidence of their own content heights.
 */
object Dimens {
    val ColorDot = 24.dp
    val ColorDotSelected = 20.dp
    val ColorDotRing = 30.dp
    val ColorDotRingStroke = 2.dp
    val ColorDotTouchTarget = 44.dp
    val SettingsRow = 56.dp
    val Chip = 40.dp
    val Stepper = 40.dp
    val PagerSegmentWidth = 24.dp
    val PagerSegmentHeight = 4.dp
    val PrimaryButtonHeight = 52.dp
    val FieldBorder = 1.dp
    val PickerTrack = 12.dp
    val PickerPreview = 56.dp
    val StatusGlyph = 18.dp
    val StatusGlyphDashWidth = 12.dp
    val StatusGlyphDashHeight = 2.dp
    val AnswerButton = 44.dp
    val AnswerButtonTouchTarget = 48.dp
    val HabitDot = 8.dp
    val CompactIconButton = 44.dp
    val ProgressBar = 4.dp
    val ReminderSegmentWidth = 22.dp
    val ReminderSegmentHeight = 4.dp
    val MinTouchTarget = 48.dp
}
