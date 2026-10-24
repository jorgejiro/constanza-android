package com.jjrapps.constanza.recap

import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.tracking.TodayHabitRow
import java.time.LocalDate
import kotlin.math.roundToInt

private const val PERCENT = 100

/** One day's completion for the morning recap: [done] of the [total] habits that had something to
 *  count on [date]. [percent] rounds the same way [com.jjrapps.constanza.domain.WeeklyRecap] does. */
data class DaySummary(val date: LocalDate, val done: Int, val total: Int) {
    val percent: Int get() = if (total == 0) 0 else (done * PERCENT.toDouble() / total).roundToInt()
}

/**
 * day-review-and-morning-recap (S7): counts per habit from Today's own rows for [date], so the
 * notification agrees with the list its tap opens. Skipped slots are ignored and a habit whose every
 * slot was skipped is left out; a habit is done when every remaining slot is completed, and anything
 * else — missed, or still unanswered — is not done.
 */
fun summarizeDay(date: LocalDate, rows: List<TodayHabitRow>): DaySummary {
    val counted = rows
        .map { row -> row.slots.map { it.status }.filter { it != EntryStatus.SKIPPED } }
        .filter { it.isNotEmpty() }
    return DaySummary(
        date = date,
        done = counted.count { statuses -> statuses.all { it == EntryStatus.COMPLETED } },
        total = counted.size,
    )
}
