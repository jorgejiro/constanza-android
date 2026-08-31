package com.jjrapps.constanza.domain

import com.jjrapps.constanza.domain.model.Due
import com.jjrapps.constanza.domain.model.Entry
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.PeriodProgress
import com.jjrapps.constanza.domain.model.Schedule
import java.time.LocalDate

private const val DAYS_PER_WEEK = 7L

/**
 * Mandated function (design.md §10, D6): current/best streak, computed on read from raw [Entry]
 * history, no I/O (habit-progress: Streak Calculation; habit-entry-tracking: Streak interaction).
 * `SKIPPED` and `UNKNOWN` pass through without breaking a streak; only `MISSED` breaks. For
 * [Schedule.NTimesPerWeek] the unit of obligation is the WEEK, not the day (design D8).
 */
object StreakCalculator {
    fun current(schedule: Schedule, entries: List<Entry>, today: LocalDate): Int =
        streaks(schedule, entries, today).current

    fun best(schedule: Schedule, entries: List<Entry>, today: LocalDate): Int =
        streaks(schedule, entries, today).best
}

private data class Streaks(val current: Int, val best: Int)

private fun streaks(schedule: Schedule, entries: List<Entry>, today: LocalDate): Streaks =
    if (schedule is Schedule.NTimesPerWeek) {
        weeklyStreaks(schedule, entries, today)
    } else {
        dailyStreaks(schedule, entries, today)
    }

/**
 * Walks calendar days, skipping any [Due.NotDue] date entirely — it neither breaks nor extends
 * (design.md §10: "walks occurrences, not calendar days"). Only a `COMPLETED` day lengthens the
 * run; only a `MISSED` day breaks it. `SKIPPED` and `UNKNOWN` do neither, so a pass-through day
 * bridges the run without lengthening it — the treatment is identical whether that day sits
 * enclosed inside the run or trailing at `today`.
 */
/**
 * Mutable per-day accumulator for [dailyStreaks], extracted to keep the `while`/`if`/`when` shape
 * below the detekt `NestedBlockDepth` threshold, not to reconcile any tentative-vs-confirmed
 * ambiguity — the rule itself no longer needs one.
 */
private class DailyRun {
    var run = 0
    var best = 0

    fun advance(status: EntryStatus) {
        when (status) {
            EntryStatus.MISSED -> run = 0
            EntryStatus.COMPLETED -> run++
            EntryStatus.SKIPPED, EntryStatus.UNKNOWN -> Unit
        }
        best = maxOf(best, run)
    }
}

private fun dailyStreaks(schedule: Schedule, entries: List<Entry>, today: LocalDate): Streaks {
    val alwaysZeroProgress = PeriodProgress(completedInWeek = 0, completedInMonth = 0)
    val boundary = entries.minOfOrNull { it.date } ?: today
    val run = DailyRun()
    var date = boundary
    while (!date.isAfter(today)) {
        if (dueOn(schedule, date, alwaysZeroProgress) != Due.NotDue) {
            run.advance(resolvedStatus(date, entries))
        }
        date = date.plusDays(1)
    }
    return Streaks(current = run.run, best = run.best)
}

/**
 * The unit of obligation is the WEEK (design D8): a week meeting [Schedule.NTimesPerWeek.times]
 * extends the streak by one regardless of which days were used; a week falling short breaks it
 * exactly once, never per missed day. The in-progress week containing `today` is credited as soon
 * as its quota is met, and never breaks the run before it is over (OA-3: quota re-evaluated at
 * fire time, so a week is never prematurely judged to have fallen short).
 */
private fun weeklyStreaks(schedule: Schedule.NTimesPerWeek, entries: List<Entry>, today: LocalDate): Streaks {
    val boundaryWeek = startOfWeek(entries.minOfOrNull { it.date } ?: today, schedule.weekStart)
    val todayWeek = startOfWeek(today, schedule.weekStart)
    var run = 0
    var best = 0
    var week = boundaryWeek
    while (!week.isAfter(todayWeek)) {
        val weekEnd = week.plusDays(DAYS_PER_WEEK)
        val completedInWeek = entries.count {
            it.status == EntryStatus.COMPLETED && !it.date.isBefore(week) && it.date.isBefore(weekEnd)
        }
        val metQuota = completedInWeek >= schedule.times
        val isCurrentWeek = week == todayWeek
        when {
            metQuota -> run++
            !isCurrentWeek -> run = 0
            // else: the current week is still in progress and hasn't met quota yet — neither
            // breaks nor extends, exactly like an undecided day.
        }
        best = maxOf(best, run)
        week = week.plusDays(DAYS_PER_WEEK)
    }
    return Streaks(current = run, best = best)
}
