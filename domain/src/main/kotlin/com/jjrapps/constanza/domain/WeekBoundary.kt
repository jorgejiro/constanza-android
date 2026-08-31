package com.jjrapps.constanza.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * ISO-8601 week boundary, threaded as an explicit [weekStart] parameter — never derived from
 * device locale (design.md D7; habit-scheduling: Week Boundary for N_TIMES_PER_WEEK). Callers
 * use this to bucket entries into the current week before computing
 * [com.jjrapps.constanza.domain.model.PeriodProgress.completedInWeek] for [dueOn].
 */
private const val DAYS_IN_WEEK = 7

fun startOfWeek(date: LocalDate, weekStart: DayOfWeek): LocalDate {
    val daysSinceStart = (date.dayOfWeek.value - weekStart.value + DAYS_IN_WEEK) % DAYS_IN_WEEK
    return date.minusDays(daysSinceStart.toLong())
}
