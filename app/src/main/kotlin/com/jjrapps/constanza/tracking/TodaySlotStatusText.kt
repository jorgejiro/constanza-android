package com.jjrapps.constanza.tracking

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.rememberTimeOfDayFormat
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.domain.model.DayStatus
import com.jjrapps.constanza.domain.model.EntryStatus
import java.time.Instant
import java.time.ZoneId

/**
 * The sentence a Today row puts beside its answer controls, and the two enum-to-copy maps behind it.
 * Split out of `TodayScreen.kt` the same way `TodayDateBar`, `TodayBanners` and `TodayAddHabitAction`
 * already are: that file lays a screen out, this one decides what its rows say.
 */

/**
 * The typographic gap joining a status sentence's two halves — see [slotStatusText]. It replaces the
 * em dash that used to join them (`08:00 — Hecho`), which spent a glyph's worth of emphasis on the
 * join and made the two halves read as equal.
 *
 * Public, and for the same reason [com.jjrapps.constanza.habit.REMINDER_TIME_MODE_TOGGLE_TEST_TAG]
 * is: `TodaySlotRowComposeTest` asserts the row's WHOLE sentence rather than a substring — that is
 * deliberate and must stay that way — and a test that re-typed three invisible spaces by hand would
 * be a silent trap the first time this value changes. Not a `contentDescription` and nothing but
 * whitespace on screen, so it adds no announcement of its own; [demotedSuffix]'s callers collapse it
 * back to a single space for anything that is read aloud rather than laid out.
 */
const val TODAY_SLOT_STATUS_GAP = "   "

/** The demoted half of a status sentence. The row's own text is `bodyMedium` (14sp) and the habit
 *  name above it `bodyLarge` (16sp); 11sp is a third step down, which is what makes the trailing
 *  half read as metadata hanging off the leading word rather than as a second thing competing with
 *  it. A `Typography` role is deliberately not used here: this is one span inside a sentence, not a
 *  text style a component picks up. */
private val DEMOTED_TEXT_SIZE = 11.sp

/**
 * [zone] comes from [TodayUiState.zone] ([com.jjrapps.constanza.core.time.TimeProvider.zone]), never
 * `ZoneId.systemDefault()` directly — the same clock-access ban design.md §4 enforces everywhere else
 * (config/detekt/detekt.yml `ForbiddenMethodCall`).
 *
 * Both times here — the slot's own time and the snoozed-until time — go through the one
 * [com.jjrapps.constanza.core.ui.TimeOfDayFormat]. They used to be formatted two different ways in
 * this one function: a `DateTimeFormatter.ofPattern("HH:mm")` file constant for the snooze and a bare
 * `"%02d:%02d"` literal for the slot, which is two copies of a decision that has to agree.
 *
 * The format is remembered per row rather than hoisted into `TodayContent` and threaded down. That is
 * deliberate: threading it would add a parameter to both `HabitRollupRow` and `SlotRow` to save one
 * small immutable object per visible row, and the rows are already carrying every argument they can
 * justify.
 *
 * [bypassSnooze] is today-answered-slot-collapse, design.md decision 2: `SlotRow`'s answered branch
 * passes `true` so the snooze sentence below is never even reached for a slot already carrying a
 * resolved `Entry` — `&&` short-circuits before `snoozedUntilEpochMs` is read at all, not merely
 * before it renders. Without this an answered slot whose occurrence had not yet been resolved would
 * read "Pendiente, aplazado hasta 09:00" over a `COMPLETED` entry, a literal failure of the spec's
 * "text naming its specific answer".
 *
 * ## today-row-alignment, decision 4: the two halves are NOT ordered the same way on every row, and
 * ## making them agree would be a regression rather than a tidy-up.
 *
 * The reported complaint was that the reminder time "aporta poco". It does, on a single-slot habit:
 * the maintainer's own list has "Cenar antes de las 10 de la noche" at 21:00 and "Relax a las 23h" at
 * 23:00, so `21:00 — Pendiente` spent half a line restating a name that already said the time.
 *
 * Deleting the time was rejected, because on a MULTI-SLOT habit it is the only thing that tells one
 * slot from another — it is that slot's whole name, and `TodayAdaptiveComposeTest` finds those rows
 * by it. So the rule is ordering, not deletion: **whichever half carries the row's identity leads at
 * the row's own text style, and the other trails demoted.** A single-slot habit used to read
 * `Pendiente  21:00`, because its name is its identity and the time is metadata. A multi-slot slot
 * ([timeIsIdentity], set by `HabitRollupRow`'s expanded branch) reads `15:00  Pendiente`, because
 * there the time IS the identity and the status is what varies.
 *
 * A future reader will see two rows disagreeing and want to make them consistent. Making the
 * single-slot row lead with its time restores the defect this change fixed; making the multi-slot
 * slot lead with its status demotes the only label those three sibling rows have to tell them apart,
 * and shrinks it to 11sp while it is at it. Neither is a tidy-up.
 *
 * ## today-status-icons supersedes the single-slot illustration above
 *
 * That `Pendiente  21:00` example is now wrong on purpose: a single-slot habit's own name and (once
 * answered) its own status glyph already say everything the row has to say, so
 * [TodaySlot.minuteOfDay] renders nothing at all there any more, not even demoted.
 *
 * [timeIsIdentity] doubles as that gate rather than taking a separate parameter (`LongParameterList`
 * is already at this function's configured threshold): the true source of truth is
 * `TodayHabitRow.slots.size > 1`, but `HabitRollupRow` already derives [timeIsIdentity] from exactly
 * that condition at both its call sites — `false` only for a habit's single, un-expandable slot,
 * `true` only inside a multi-slot habit's expanded rows — so there is no longer a call shape where a
 * time renders trailing-and-demoted (the case the `else` branch below used to cover). Reusing it
 * here means the "single-slot" and "leads the sentence" questions are answered by one boolean that
 * is provably always the right value for both, not two that happen to agree by coincidence; `SlotRow`
 * itself recomputes `row.slots.size > 1` independently for [com.jjrapps.constanza.tracking.AnsweredStatusRow],
 * which does not go through this function at all.
 */
@Composable
internal fun slotStatusText(
    slot: TodaySlot,
    zone: ZoneId,
    timeIsIdentity: Boolean = false,
    bypassSnooze: Boolean = false,
    muted: Boolean = false,
): AnnotatedString {
    val timeFormat = rememberTimeOfDayFormat()
    val time = slot.minuteOfDay?.takeIf { timeIsIdentity }?.let(timeFormat::format)
    val snoozed = !bypassSnooze && slot.snoozedUntilEpochMs != null
    val status = if (snoozed) {
        // Still ahead of the status itself: a snoozed slot is pending WITH a time attached, and that
        // time is the more useful half of the sentence.
        stringResource(
            R.string.today_slot_pending_snoozed_until,
            timeFormat.format(Instant.ofEpochMilli(slot.snoozedUntilEpochMs).atZone(zone).toLocalTime()),
        )
    } else {
        stringResource(slotStatusLabel(slot.status))
    }
    return if (timeIsIdentity && time != null) {
        // The slot's own time still leads here even when snoozed, and the two times do not collide:
        // `15:00  Pendiente, aplazado hasta 21:30` names the slot and then what happened to it.
        demotedSuffix(lead = time, suffix = status, muted = muted)
    } else {
        // A snoozed sentence ALREADY names a time — the one the reminder was pushed to — so trailing
        // the slot's original reminder time after it puts two unrelated times on one line in the
        // order `21:30  21:00`, which is the wrong way round and reads as a typo. This is measured
        // rather than reasoned, at 360dp with a snoozed slot actually seeded and rendered: 4 wrapped
        // lines on `main` (`21:00 —` / `Pendiente,` / `aplazado hasta` / `21:30`, a 96dp node), 3
        // with the trailing time kept, 1 as it now stands — the shortening of
        // `today_slot_pending_snoozed_until` to `Aplazado hasta %1$s` is what closed the last two,
        // and dropping the trailing time here is what keeps the single time on that one line. Keep
        // both halves of that: put "Pendiente," back and it wraps again, or let the trailing time
        // through and `Aplazado hasta 21:30  21:00` is what lands.
        demotedSuffix(lead = status, suffix = time.takeUnless { snoozed }, muted = muted)
    }
}

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
