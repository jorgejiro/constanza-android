package com.jjrapps.constanza.domain

import com.jjrapps.constanza.domain.model.Entry
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Test

/**
 * habit-progress: Streak Calculation; habit-entry-tracking: Streak interaction. RED for tasks
 * 2b.1/2b.2 — `StreakCalculator` does not exist yet.
 */
class StreakCalculatorTest {

    private val daily = Schedule.Daily()

    private fun entry(date: LocalDate, status: EntryStatus) =
        Entry(habitId = 1L, date = date, slotId = null, status = status)

    @Test
    fun `a skipped day enclosed inside a run does not break the streak and is counted`() {
        val start = LocalDate.of(2026, 3, 1)
        val entries = (0..4).map { entry(start.plusDays(it.toLong()), EntryStatus.COMPLETED) } +
            entry(start.plusDays(5), EntryStatus.SKIPPED) +
            (6..8).map { entry(start.plusDays(it.toLong()), EntryStatus.COMPLETED) }
        val today = start.plusDays(8)

        assertEquals(9, StreakCalculator.current(daily, entries, today))
    }

    @Test
    fun `an unknown day pending a snooze answer does not extend today's streak`() {
        val start = LocalDate.of(2026, 3, 1)
        val entries = (0..4).map { entry(start.plusDays(it.toLong()), EntryStatus.COMPLETED) }
        // day 5 (today) has no Entry at all — the pending, unanswered, still-snoozed occurrence.
        val today = start.plusDays(5)

        assertEquals(5, StreakCalculator.current(daily, entries, today))
    }

    @Test
    fun `only missed breaks the streak, skipped and unknown pass through unaffected`() {
        val start = LocalDate.of(2026, 4, 1)
        val entries = listOf(
            entry(start, EntryStatus.COMPLETED),
            entry(start.plusDays(1), EntryStatus.SKIPPED),
            entry(start.plusDays(2), EntryStatus.COMPLETED),
            entry(start.plusDays(3), EntryStatus.MISSED),
            entry(start.plusDays(4), EntryStatus.COMPLETED),
        )
        val today = start.plusDays(4)

        // The MISSED day resets the run; only the single COMPLETED day after it counts.
        assertEquals(1, StreakCalculator.current(daily, entries, today))
    }

    @Test
    fun `a day that is not due at all neither breaks nor extends the streak`() {
        val schedule = Schedule.Weekly(dayOfWeek = DayOfWeek.WEDNESDAY)
        // Four consecutive Wednesdays, all completed; no entries on the days in between.
        val wednesdays = listOf(
            LocalDate.of(2026, 3, 4),
            LocalDate.of(2026, 3, 11),
            LocalDate.of(2026, 3, 18),
            LocalDate.of(2026, 3, 25),
        )
        val entries = wednesdays.map { entry(it, EntryStatus.COMPLETED) }

        assertEquals(4, StreakCalculator.current(schedule, entries, wednesdays.last()))
    }

    @Test
    fun `an empty entry list yields a zero streak`() {
        val today = LocalDate.of(2026, 5, 1)

        assertEquals(0, StreakCalculator.current(daily, emptyList(), today))
        assertEquals(0, StreakCalculator.best(daily, emptyList(), today))
    }

    @Test
    fun `best captures a longer historical run even when the current streak is shorter`() {
        val start = LocalDate.of(2026, 6, 1)
        val entries = (0..4).map { entry(start.plusDays(it.toLong()), EntryStatus.COMPLETED) } +
            entry(start.plusDays(5), EntryStatus.MISSED) +
            (6..7).map { entry(start.plusDays(it.toLong()), EntryStatus.COMPLETED) }
        val today = start.plusDays(7)

        assertEquals(2, StreakCalculator.current(daily, entries, today))
        assertEquals(5, StreakCalculator.best(daily, entries, today))
    }

    @Test
    fun `streak recomputed after a late correction shows no break`() {
        val start = LocalDate.of(2026, 7, 1)
        val today = start.plusDays(5)
        val beforeCorrection = (0..4).map { entry(start.plusDays(it.toLong()), EntryStatus.COMPLETED) } +
            entry(today, EntryStatus.MISSED)
        val afterCorrection = (0..4).map { entry(start.plusDays(it.toLong()), EntryStatus.COMPLETED) } +
            entry(today, EntryStatus.COMPLETED)

        assertEquals(0, StreakCalculator.current(daily, beforeCorrection, today))
        assertEquals(6, StreakCalculator.current(daily, afterCorrection, today))
    }

    @Test
    fun `N_TIMES_PER_WEEK streak counts weeks, met regardless of which days were used`() {
        val schedule = Schedule.NTimesPerWeek(times = 3, weekStart = DayOfWeek.MONDAY)
        // Week 1 (Mon 03-02 .. Sun 03-08): quota met on Mon/Wed/Fri.
        // Week 2 (Mon 03-09 .. Sun 03-15): quota met on Tue/Thu/Sat — different days, still met.
        val entries = listOf(
            entry(LocalDate.of(2026, 3, 2), EntryStatus.COMPLETED),
            entry(LocalDate.of(2026, 3, 4), EntryStatus.COMPLETED),
            entry(LocalDate.of(2026, 3, 6), EntryStatus.COMPLETED),
            entry(LocalDate.of(2026, 3, 10), EntryStatus.COMPLETED),
            entry(LocalDate.of(2026, 3, 12), EntryStatus.COMPLETED),
            entry(LocalDate.of(2026, 3, 14), EntryStatus.COMPLETED),
        )
        val today = LocalDate.of(2026, 3, 15)

        assertEquals(2, StreakCalculator.current(schedule, entries, today))
        assertEquals(2, StreakCalculator.best(schedule, entries, today))
    }

    @Test
    fun `a week that falls short breaks the N_TIMES_PER_WEEK streak once, not per day`() {
        val schedule = Schedule.NTimesPerWeek(times = 3, weekStart = DayOfWeek.MONDAY)
        // Week 1 and 2 meet quota; week 3 falls short (only 1 completed); week 4 meets quota again.
        val entries = listOf(
            entry(LocalDate.of(2026, 3, 2), EntryStatus.COMPLETED),
            entry(LocalDate.of(2026, 3, 4), EntryStatus.COMPLETED),
            entry(LocalDate.of(2026, 3, 6), EntryStatus.COMPLETED),
            entry(LocalDate.of(2026, 3, 9), EntryStatus.COMPLETED),
            entry(LocalDate.of(2026, 3, 11), EntryStatus.COMPLETED),
            entry(LocalDate.of(2026, 3, 13), EntryStatus.COMPLETED),
            entry(LocalDate.of(2026, 3, 16), EntryStatus.COMPLETED),
            entry(LocalDate.of(2026, 3, 23), EntryStatus.COMPLETED),
            entry(LocalDate.of(2026, 3, 25), EntryStatus.COMPLETED),
            entry(LocalDate.of(2026, 3, 27), EntryStatus.COMPLETED),
        )
        val today = LocalDate.of(2026, 3, 29)

        assertEquals(1, StreakCalculator.current(schedule, entries, today))
        assertEquals(2, StreakCalculator.best(schedule, entries, today))
    }
}
