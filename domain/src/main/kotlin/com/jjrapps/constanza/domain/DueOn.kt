package com.jjrapps.constanza.domain

import com.jjrapps.constanza.domain.model.Due
import com.jjrapps.constanza.domain.model.PeriodProgress
import com.jjrapps.constanza.domain.model.Schedule
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Mandated function 1 (design.md §10): the single authority for "is this due?", used by both
 * the reminder-arming path and any scoring/rollup reader (habit-scheduling: Occurrence-Due
 * Predicate). Pure [LocalDate] arithmetic only — no [java.time.ZonedDateTime]/[java.time.Instant]
 * (DST rationale lands with tasks 2a.9/2a.10 in a follow-up slice).
 */
fun dueOn(schedule: Schedule, date: LocalDate, progress: PeriodProgress): Due =
    when (schedule) {
        is Schedule.Daily, is Schedule.TimesPerDay -> Due.Required

        is Schedule.DaysOfWeek ->
            if (date.dayOfWeek in schedule.days) Due.Required else Due.NotDue

        is Schedule.Monthly -> {
            val effectiveDay = minOf(schedule.dayOfMonth, date.lengthOfMonth())
            if (date.dayOfMonth == effectiveDay) Due.Required else Due.NotDue
        }

        is Schedule.EveryNDays ->
            if (date.isBefore(schedule.anchor)) {
                Due.NotDue
            } else if (ChronoUnit.DAYS.between(schedule.anchor, date) % schedule.n == 0L) {
                Due.Required
            } else {
                Due.NotDue
            }

        // Provisional (OA-3, unconfirmed): N_TIMES_PER_WEEK carries no per-date determinate
        // obligation (design.md D8) — every day of the week returns Candidate, and the caller
        // (reminder-arming at fire time, or a scorer) reads quotaRemaining to decide whether to
        // suppress the reminder or count the day toward weekly compliance.
        is Schedule.NTimesPerWeek ->
            Due.Candidate(quotaRemaining = (schedule.times - progress.completedInWeek).coerceAtLeast(0))
    }
