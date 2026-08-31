package com.jjrapps.constanza.domain

import com.jjrapps.constanza.domain.model.Due
import com.jjrapps.constanza.domain.model.Entry
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.PeriodProgress
import com.jjrapps.constanza.domain.model.Schedule
import java.time.LocalDate

private const val DAYS_PER_WEEK = 7L

/**
 * Mandated function (design.md §10, D6): `completed / (completed + missed)`, computed on read.
 * `SKIPPED` and `UNKNOWN` are excluded from BOTH sides (habit-progress: Compliance Calculation).
 * `windowDays` is a caller parameter — never hard-coded here; the MVP UI passes 30, unconfirmed
 * (OA-4). For [Schedule.NTimesPerWeek] the unit of obligation is the WEEK, not the day (design
 * D8): the ratio is the sum over whole weeks in the window of `min(completedInWeek, times)`,
 * divided by the sum of `times` over those same weeks, with partial weeks at the window edge
 * excluded. Every other schedule kind uses [dueOn] as the single due-authority, never a second
 * due-check, exactly like [StreakCalculator].
 */
object ComplianceCalculator {
    fun ratio(schedule: Schedule, entries: List<Entry>, today: LocalDate, windowDays: Int): Double =
        if (schedule is Schedule.NTimesPerWeek) {
            weeklyRatio(schedule, entries, today, windowDays)
        } else {
            dailyRatio(schedule, entries, today, windowDays)
        }

    private fun dailyRatio(schedule: Schedule, entries: List<Entry>, today: LocalDate, windowDays: Int): Double {
        val alwaysZeroProgress = PeriodProgress(completedInWeek = 0, completedInMonth = 0)
        val windowStart = today.minusDays((windowDays - 1).toLong())
        var completed = 0
        var missed = 0
        var date = windowStart
        while (!date.isAfter(today)) {
            if (dueOn(schedule, date, alwaysZeroProgress) != Due.NotDue) {
                when (resolvedStatus(date, entries)) {
                    EntryStatus.COMPLETED -> completed++
                    EntryStatus.MISSED -> missed++
                    EntryStatus.SKIPPED, EntryStatus.UNKNOWN -> Unit
                }
            }
            date = date.plusDays(1)
        }
        val total = completed + missed
        return if (total == 0) 0.0 else completed.toDouble() / total
    }

    /**
     * Sums `min(completedInWeek, times)` and `times` across every whole week fully contained in
     * `[windowStart, today]`, then divides once at the end — summing before dividing, rather than
     * averaging per-week ratios, keeps every week equally weighted. A week that sticks out past
     * either edge of the window is skipped entirely, because a week that has not finished cannot
     * have fallen short yet.
     */
    private fun weeklyRatio(
        schedule: Schedule.NTimesPerWeek,
        entries: List<Entry>,
        today: LocalDate,
        windowDays: Int,
    ): Double {
        val windowStart = today.minusDays((windowDays - 1).toLong())
        var completedSum = 0
        var quotaSum = 0
        var week = startOfWeek(windowStart, schedule.weekStart)
        while (!week.isAfter(today)) {
            val weekEnd = week.plusDays(DAYS_PER_WEEK - 1)
            if (!week.isBefore(windowStart) && !weekEnd.isAfter(today)) {
                val completedInWeek = entries.count {
                    it.status == EntryStatus.COMPLETED && !it.date.isBefore(week) && !it.date.isAfter(weekEnd)
                }
                completedSum += minOf(completedInWeek, schedule.times)
                quotaSum += schedule.times
            }
            week = week.plusDays(DAYS_PER_WEEK)
        }
        return if (quotaSum == 0) 0.0 else completedSum.toDouble() / quotaSum
    }
}
