package com.jjrapps.constanza.tracking

import com.jjrapps.constanza.domain.model.EntryStatus

/** Graphite Today's header progress: [answered] habits out of [total] shown for the day. */
data class TodayProgress(val answered: Int, val total: Int) {
    /** The progress bar's fill, `0f` when nothing is shown rather than a division by zero. */
    val fraction: Float get() = if (total == 0) 0f else answered.toFloat() / total
}

/**
 * The header's "N of M" (graphite redesign): M is every habit shown for the day, N those whose
 * slots are ALL answered — completed, missed or skipped alike, since the line measures how much of
 * the day is accounted for, not how much went well. A multi-slot habit counts once, and only when
 * its last slot is answered: the same per-habit rule [groupTodayRows] uses to place a row in
 * [TodaySectionKind.DONE], so the count and the "Answered" section can never disagree.
 */
fun todayProgress(rows: List<TodayHabitRow>): TodayProgress =
    TodayProgress(
        answered = rows.count { row -> row.slots.all { it.status != EntryStatus.UNKNOWN } },
        total = rows.size,
    )

/** A multi-reminder habit's own line: [answered] of [total] slots, and the earliest still
 *  unanswered reminder time, `null` once none is left (or the unanswered ones have no time). */
data class ReminderProgress(val answered: Int, val total: Int, val nextMinuteOfDay: Int?)

/** Drives the "x of N · next HH:MM" line and the per-reminder segments under a multi-slot name. */
fun reminderProgress(row: TodayHabitRow): ReminderProgress {
    val unanswered = row.slots.filter { it.status == EntryStatus.UNKNOWN }
    return ReminderProgress(
        answered = row.slots.size - unanswered.size,
        total = row.slots.size,
        nextMinuteOfDay = unanswered.mapNotNull { it.minuteOfDay }.minOrNull(),
    )
}
