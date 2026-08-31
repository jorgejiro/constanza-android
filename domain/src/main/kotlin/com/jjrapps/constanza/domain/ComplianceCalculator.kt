package com.jjrapps.constanza.domain

import com.jjrapps.constanza.domain.model.Due
import com.jjrapps.constanza.domain.model.Entry
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.PeriodProgress
import com.jjrapps.constanza.domain.model.Schedule
import java.time.LocalDate

/**
 * Mandated function (design.md §10, D6): `completed / (completed + missed)`, computed on read.
 * `SKIPPED` and `UNKNOWN` are excluded from BOTH sides (habit-progress: Compliance Calculation).
 * `windowDays` is a caller parameter — never hard-coded here; the MVP UI passes 30, unconfirmed
 * (OA-4). Only [Due.Required]/[Due.Candidate] dates are considered, using the same [dueOn]
 * authority as [StreakCalculator] and never a second due-check.
 */
object ComplianceCalculator {
    fun ratio(schedule: Schedule, entries: List<Entry>, today: LocalDate, windowDays: Int): Double {
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
}
