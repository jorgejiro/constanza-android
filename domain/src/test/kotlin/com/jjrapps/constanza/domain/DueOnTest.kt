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
 * habit-scheduling: Occurrence-Due Predicate; MONTHLY on a day the month lacks; EVERY_N_DAYS is
 * unaffected by month length. RED for tasks 2a.3/2a.4 — `dueOn` does not exist yet.
 */
class DueOnTest {

    private val noProgress = PeriodProgress(completedInWeek = 0, completedInMonth = 0)

    @Test
    fun `DAILY is Required on every date`() {
        val schedule = Schedule.Daily()
        assertIs<Due.Required>(dueOn(schedule, LocalDate.of(2026, 3, 1), noProgress))
        assertIs<Due.Required>(dueOn(schedule, LocalDate.of(2026, 3, 2), noProgress))
    }

    @Test
    fun `TIMES_PER_DAY is Required every day, slots carry the times`() {
        val schedule = Schedule.TimesPerDay()
        assertIs<Due.Required>(dueOn(schedule, LocalDate.of(2026, 3, 1), noProgress))
    }

    @Test
    fun `DAYS_OF_WEEK single-day set behaves like the former WEEKLY kind`() {
        val schedule = Schedule.DaysOfWeek(days = setOf(DayOfWeek.WEDNESDAY))
        // 2026-03-04 is a Wednesday.
        assertIs<Due.Required>(dueOn(schedule, LocalDate.of(2026, 3, 4), noProgress))
        assertIs<Due.NotDue>(dueOn(schedule, LocalDate.of(2026, 3, 5), noProgress))
    }

    @Test
    fun `DAYS_OF_WEEK Monday-to-Friday set is due only on weekdays`() {
        val schedule = Schedule.DaysOfWeek(
            days = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
        )
        // 2026-03-02 is a Monday; the week runs through 2026-03-08 (Sunday).
        val week = (0..6).map { LocalDate.of(2026, 3, 2).plusDays(it.toLong()) }
        val dueDays = week.filter { dueOn(schedule, it, noProgress) is Due.Required }
        assertEquals(week.take(5), dueDays)
    }

    @Test
    fun `DAYS_OF_WEEK is NotDue on a day outside the set, never treated as missed`() {
        val schedule = Schedule.DaysOfWeek(
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        )
        // 2026-03-07 is a Saturday.
        assertIs<Due.NotDue>(dueOn(schedule, LocalDate.of(2026, 3, 7), noProgress))
    }

    @Test
    fun `DAYS_OF_WEEK non-contiguous set is due only on its member days`() {
        val schedule = Schedule.DaysOfWeek(
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )
        val week = (0..6).map { LocalDate.of(2026, 3, 2).plusDays(it.toLong()) }
        val dueDays = week.filter { dueOn(schedule, it, noProgress) is Due.Required }
        assertEquals(
            listOf(
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 4),
                LocalDate.of(2026, 3, 6),
            ),
            dueDays,
        )
    }

    @Test
    fun `MONTHLY on day 31 clamps to February's actual last day`() {
        val schedule = Schedule.Monthly(dayOfMonth = 31)
        assertIs<Due.Required>(dueOn(schedule, LocalDate.of(2026, 2, 28), noProgress))
        assertIs<Due.NotDue>(dueOn(schedule, LocalDate.of(2026, 2, 27), noProgress))
    }

    @Test
    fun `MONTHLY on day 31 clamps to April's 30th`() {
        val schedule = Schedule.Monthly(dayOfMonth = 31)
        assertIs<Due.Required>(dueOn(schedule, LocalDate.of(2026, 4, 30), noProgress))
    }

    @Test
    fun `MONTHLY on day 31 fires on day 31 in a month that has it`() {
        val schedule = Schedule.Monthly(dayOfMonth = 31)
        assertIs<Due.Required>(dueOn(schedule, LocalDate.of(2026, 1, 31), noProgress))
    }

    @Test
    fun `MONTHLY on day 31 clamps to a leap February 29th`() {
        // 2028 is a leap year.
        val schedule = Schedule.Monthly(dayOfMonth = 31)
        assertIs<Due.Required>(dueOn(schedule, LocalDate.of(2028, 2, 29), noProgress))
        assertIs<Due.NotDue>(dueOn(schedule, LocalDate.of(2028, 2, 28), noProgress))
    }

    @Test
    fun `EVERY_N_DAYS(30) stays exactly 30 days apart across a 28-day February`() {
        val schedule = Schedule.EveryNDays(n = 30, anchor = LocalDate.of(2026, 1, 1))
        assertIs<Due.Required>(dueOn(schedule, LocalDate.of(2026, 1, 1), noProgress))
        assertIs<Due.Required>(dueOn(schedule, LocalDate.of(2026, 1, 31), noProgress))
        assertIs<Due.NotDue>(dueOn(schedule, LocalDate.of(2026, 2, 15), noProgress))
        assertIs<Due.Required>(dueOn(schedule, LocalDate.of(2026, 3, 2), noProgress))
    }

    @Test
    fun `EVERY_N_DAYS is NotDue before its anchor`() {
        val schedule = Schedule.EveryNDays(n = 5, anchor = LocalDate.of(2026, 6, 1))
        assertIs<Due.NotDue>(dueOn(schedule, LocalDate.of(2026, 5, 31), noProgress))
    }

    @Test
    fun `N_TIMES_PER_WEEK returns Candidate with quota remaining, never NotDue or Required`() {
        val schedule = Schedule.NTimesPerWeek(times = 3)
        val result = dueOn(schedule, LocalDate.of(2026, 3, 4), PeriodProgress(1, 0))
        val candidate = assertIs<Due.Candidate>(result)
        assertEquals(2, candidate.quotaRemaining)
    }
}
