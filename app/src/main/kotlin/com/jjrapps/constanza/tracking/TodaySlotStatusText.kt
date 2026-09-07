package com.jjrapps.constanza.tracking

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.domain.model.DayStatus
import com.jjrapps.constanza.domain.model.EntryStatus

/**
 * The two enum-to-copy maps a Today row's remaining status text reads from, plus [demotedSuffix],
 * the one-`AnnotatedString` join those maps still feed. Split out of `TodayScreen.kt` the same way
 * `TodayDateBar`, `TodayBanners` and `TodayAddHabitAction` already are: that file lays a screen out,
 * this one decides what its rows say.
 *
 * today-one-line-row: `slotStatusText`, the sentence this file used to build for a Today slot's
 * pending/answered status word, is gone with it — a pending slot shows two answer pills instead of
 * the word "Pendiente", and an answered slot shows [TodayAnsweredStatusGlyph]'s glyph, which this
 * file never fed at all. [demotedSuffix] survives because [dayStatusLabel]'s day rollup still needs
 * it, for a collapsed multi-slot habit's header line (`TodayScreen.kt`'s `HabitRollupHeader`).
 */

/**
 * The typographic gap joining [demotedSuffix]'s two halves — see that function. It replaces the em
 * dash that used to join them (`08:00 — Hecho`), which spent a glyph's worth of emphasis on the
 * join and made the two halves read as equal.
 *
 * Public, and for the same reason [com.jjrapps.constanza.habit.REMINDER_TIME_MODE_TOGGLE_TEST_TAG]
 * is: a test asserting a day-rollup sentence by exact match, rather than a substring, needs this
 * rather than three invisible spaces re-typed by hand — a silent trap the first time this value
 * changes. Not a `contentDescription` and nothing but whitespace on screen, so it adds no
 * announcement of its own; [demotedSuffix]'s callers collapse it back to a single space for
 * anything that is read aloud rather than laid out.
 */
const val TODAY_SLOT_STATUS_GAP = "   "

/** The demoted half of a status sentence. The row's own text is `bodyMedium` (14sp) and the habit
 *  name above it `bodyLarge` (16sp); 11sp is a third step down, which is what makes the trailing
 *  half read as metadata hanging off the leading word rather than as a second thing competing with
 *  it. A `Typography` role is deliberately not used here: this is one span inside a sentence, not a
 *  text style a component picks up. */
private val DEMOTED_TEXT_SIZE = 11.sp

/**
 * [lead] at the caller's own text style, [suffix] after [TODAY_SLOT_STATUS_GAP] at
 * [DEMOTED_TEXT_SIZE] in `onSurfaceVariant`. A null [suffix] renders [lead] alone, which is the
 * habit-with-no-reminder-time case and the single-slot-header case.
 *
 * One [AnnotatedString] rather than two `Text`s, and that is a layout decision as much as a
 * typographic one. `SlotRow`'s `weight(1f)` only means anything applied to a single child — see the
 * comment there for what happened the last time the status text and the button group disagreed about
 * width. One node also makes ONE wrapping decision instead of two that can disagree, and TalkBack
 * reads one sentence instead of stopping twice inside it.
 *
 * [muted] (today-grouped-sections, design.md) switches the suffix's own colour to
 * [ConstanzaColors.OnBackgroundMuted] instead of the brighter `onSurfaceVariant` — the design's
 * explicit rule that in a muted row the demoted suffix must dim WITH the lead text, never stay
 * behind at its normal, brighter tone.
 *
 * [leadColor] (the colour overhaul) is the one caller-supplied exception to "[lead] carries no
 * colour of its own here": [HabitRollupHeader] passes the habit's own [TodayHabitRow.colorArgb]
 * through it so the habit's identity colour paints the name span while the suffix span keeps its
 * own state-only tone — the two are deliberately independent, and [muted] never reaches [leadColor].
 * Every other caller of this function leaves it `null`, so [lead] there still inherits whatever
 * colour the caller's own `Text` was given, exactly as before.
 */
@Composable
internal fun demotedSuffix(
    lead: String,
    suffix: String?,
    muted: Boolean = false,
    leadColor: Color? = null,
): AnnotatedString {
    val quiet = if (muted) ConstanzaColors.OnBackgroundMuted else MaterialTheme.colorScheme.onSurfaceVariant
    return remember(lead, suffix, quiet, leadColor) {
        buildAnnotatedString {
            if (leadColor != null) {
                withStyle(SpanStyle(color = leadColor)) { append(lead) }
            } else {
                append(lead)
            }
            if (suffix != null) {
                withStyle(SpanStyle(color = quiet, fontSize = DEMOTED_TEXT_SIZE)) {
                    append(TODAY_SLOT_STATUS_GAP + suffix)
                }
            }
        }
    }
}

/** today-row-answering-is-cramped-and-always-on, defect 2. The fallback here used to be
 *  `slot.status.name`, which put the Kotlin constant `COMPLETED` on screen; this mirrors
 *  [dayStatusLabel]'s existing shape instead, which is what it should have done from the start.
 *
 *  Exhaustive over [EntryStatus] with no `else`, deliberately: adding a member to that enum must
 *  break this compile rather than silently reach a default. [EntryStatus.UNKNOWN] is the pending
 *  case and keeps the string it already had. */
internal fun slotStatusLabel(status: EntryStatus) = when (status) {
    EntryStatus.COMPLETED -> R.string.today_slot_completed
    EntryStatus.MISSED -> R.string.today_slot_missed
    EntryStatus.SKIPPED -> R.string.today_slot_skipped
    EntryStatus.UNKNOWN -> R.string.today_slot_pending
}

internal fun dayStatusLabel(status: DayStatus) = when (status) {
    DayStatus.ALL_COMPLETED -> R.string.today_status_all_completed
    DayStatus.PARTIAL -> R.string.today_status_partial
    DayStatus.ANY_MISSED -> R.string.today_status_any_missed
    DayStatus.ALL_SKIPPED -> R.string.today_status_all_skipped
    DayStatus.PENDING, DayStatus.NOT_DUE -> R.string.today_status_pending
}
