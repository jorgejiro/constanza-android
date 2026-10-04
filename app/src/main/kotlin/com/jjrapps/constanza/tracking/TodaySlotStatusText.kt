package com.jjrapps.constanza.tracking

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.rememberTimeOfDayFormat
import com.jjrapps.constanza.domain.model.DayStatus
import com.jjrapps.constanza.domain.model.EntryStatus
import java.time.Instant
import java.time.ZoneId

/**
 * The two enum-to-copy maps a Today row's status reads from. Neither is drawn as visible text any
 * more: [slotStatusLabel] labels an answered slot's glyph and its change action for TalkBack, and
 * [dayStatusLabel] leads a multi-slot habit's spoken summary (graphite redesign: the visible day
 * rollup word gave way to per-reminder segments).
 */

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

/** The accessible label an answered row's click action carries: the habit name plus the slot's
 *  answered word, [time] first on a multi-slot habit's own line (`null` on a single-slot row). */
@Composable
internal fun answeredRowDescription(habitName: String, slot: TodaySlot, time: String?): String {
    val statusWord = stringResource(slotStatusLabel(slot.status))
    val spoken = if (time != null) "$time $statusWord" else statusWord
    return stringResource(R.string.today_slot_change_a11y, habitName, spoken)
}

@Composable
internal fun snoozeSentence(snoozedUntilEpochMs: Long, zone: ZoneId): String {
    val timeFormat = rememberTimeOfDayFormat()
    return stringResource(
        R.string.today_slot_pending_snoozed_until,
        timeFormat.format(Instant.ofEpochMilli(snoozedUntilEpochMs).atZone(zone).toLocalTime()),
    )
}
