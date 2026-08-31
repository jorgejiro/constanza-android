package com.jjrapps.constanza.domain

import com.jjrapps.constanza.domain.model.Due
import com.jjrapps.constanza.domain.model.PeriodProgress
import com.jjrapps.constanza.domain.model.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.Test

/**
 * habit-scheduling: N_TIMES_PER_WEEK Reminder Semantics, Week Boundary. RED for tasks 2a.5/2a.6.
 * **Provisional — OA-3, unconfirmed**: reminder suppression-on-quota-met is this implementation's
 * assumption about a semantics the user has not yet confirmed.
 */
class DueOnWeekQuotaTest {

    @Test
    fun `quota met mid-week yields zero quotaRemaining, silencing remaining reminders`() {
        val schedule = Schedule.NTimesPerWeek(times = 3)
        val progress = PeriodProgress(completedInWeek = 3, completedInMonth = 0)
        val candidate = assertIs<Due.Candidate>(dueOn(schedule, LocalDate.of(2026, 3, 4), progress))
        assertEquals(0, candidate.quotaRemaining)
    }

    @Test
    fun `quotaRemaining never goes negative once quota is exceeded`() {
        val schedule = Schedule.NTimesPerWeek(times = 2)
        val progress = PeriodProgress(completedInWeek = 5, completedInMonth = 0)
        val candidate = assertIs<Due.Candidate>(dueOn(schedule, LocalDate.of(2026, 3, 4), progress))
        assertEquals(0, candidate.quotaRemaining)
    }

    @Test
    fun `a new ISO week resets the quota because completedInWeek starts at zero again`() {
        val schedule = Schedule.NTimesPerWeek(times = 3)
        val progress = PeriodProgress(completedInWeek = 0, completedInMonth = 0)
        val candidate = assertIs<Due.Candidate>(dueOn(schedule, LocalDate.of(2026, 3, 9), progress))
        assertEquals(3, candidate.quotaRemaining)
    }

    @Test
    fun `startOfWeek computes the ISO Monday start regardless of the date's own day of week`() {
        // 2026-03-04 is a Wednesday; its Monday-start week begins 2026-03-02.
        assertEquals(LocalDate.of(2026, 3, 2), startOfWeek(LocalDate.of(2026, 3, 4), DayOfWeek.MONDAY))
        // Injecting a different weekStart parameter (never a Locale) shifts the boundary.
        assertEquals(LocalDate.of(2026, 3, 1), startOfWeek(LocalDate.of(2026, 3, 4), DayOfWeek.SUNDAY))
    }

    @Test
    fun `startOfWeek is idempotent on the boundary date itself`() {
        assertEquals(LocalDate.of(2026, 3, 2), startOfWeek(LocalDate.of(2026, 3, 2), DayOfWeek.MONDAY))
    }
}
