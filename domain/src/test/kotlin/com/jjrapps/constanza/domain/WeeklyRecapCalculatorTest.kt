package com.jjrapps.constanza.domain

import com.jjrapps.constanza.domain.model.Entry
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Test

/** weekly-recap S1/S6: one Monday–Sunday week, counted the way [ComplianceCalculator] counts. */
class WeeklyRecapCalculatorTest {

    /** A Monday. */
    private val weekStart = LocalDate.of(2026, 10, 12)

    private fun entry(habitId: Long, day: Int, status: EntryStatus, slotId: Long? = null) =
        Entry(habitId = habitId, date = weekStart.plusDays(day.toLong()), slotId = slotId, status = status)

    private fun week(habitId: Long, pattern: String): List<Entry> = pattern.mapIndexedNotNull { day, c ->
        when (c) {
            '1' -> entry(habitId, day, EntryStatus.COMPLETED)
            '0' -> entry(habitId, day, EntryStatus.MISSED)
            's' -> entry(habitId, day, EntryStatus.SKIPPED)
            else -> null
        }
    }

    @Test
    fun `daily habit marks each day and counts done over done plus missed`() {
        val recap = WeeklyRecapCalculator.habitWeek(Schedule.Daily(), week(1L, "1011010"), weekStart)

        assertEquals("✓✕✓✓✕✓✕", recap.marks.joinToString("") { if (it == RecapMark.DONE) "✓" else "✕" })
        assertEquals(4, recap.done)
        assertEquals(7, recap.total)
    }

    @Test
    fun `skipped and unanswered days are neither done nor missed`() {
        val recap = WeeklyRecapCalculator.habitWeek(Schedule.Daily(), week(1L, "1s-1001"), weekStart)

        assertEquals(RecapMark.NONE, recap.marks[1])
        assertEquals(RecapMark.NONE, recap.marks[2])
        assertEquals(3, recap.done)
        assertEquals(5, recap.total)
    }

    @Test
    fun `days a days-of-week habit is not due stay empty even with an entry`() {
        val schedule = Schedule.DaysOfWeek(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val recap = WeeklyRecapCalculator.habitWeek(schedule, week(1L, "1010000"), weekStart)

        assertEquals(RecapMark.DONE, recap.marks[0])
        assertEquals(RecapMark.NONE, recap.marks[1])
        assertEquals(RecapMark.DONE, recap.marks[2])
        assertEquals(2, recap.done)
        assertEquals(2, recap.total)
    }

    @Test
    fun `a day with any missed slot is missed`() {
        val entries = listOf(
            entry(1L, 0, EntryStatus.COMPLETED, slotId = 1L),
            entry(1L, 0, EntryStatus.MISSED, slotId = 2L),
        )
        val recap = WeeklyRecapCalculator.habitWeek(Schedule.TimesPerDay(), entries, weekStart)

        assertEquals(RecapMark.MISSED, recap.marks[0])
        assertEquals(0, recap.done)
        assertEquals(1, recap.total)
    }

    @Test
    fun `n-times-per-week habit counts completions against its quota, capped`() {
        val schedule = Schedule.NTimesPerWeek(times = 3)

        val short = WeeklyRecapCalculator.habitWeek(schedule, week(1L, "1-1----"), weekStart)
        val over = WeeklyRecapCalculator.habitWeek(schedule, week(1L, "11111--"), weekStart)

        assertEquals(RecapMark.DONE, short.marks[0])
        assertEquals(RecapMark.NONE, short.marks[1])
        assertEquals(2, short.done)
        assertEquals(3, short.total)
        assertEquals(3, over.done)
        assertEquals(3, over.total)
    }

    @Test
    fun `entries outside the week are ignored`() {
        val outside = listOf(
            Entry(1L, weekStart.minusDays(1), null, EntryStatus.MISSED),
            Entry(1L, weekStart.plusDays(7), null, EntryStatus.MISSED),
        )
        val recap = WeeklyRecapCalculator.habitWeek(Schedule.Daily(), week(1L, "1") + outside, weekStart)

        assertEquals(1, recap.done)
        assertEquals(1, recap.total)
    }

    @Test
    fun `recap sums every habit, rounds the percent and drops habits with nothing to count`() {
        val recap = WeeklyRecapCalculator.recap(
            weekStart,
            listOf(
                HabitWeekInput(1L, Schedule.Daily(), week(1L, "1011010")),
                HabitWeekInput(2L, Schedule.Daily(), week(2L, "1110111")),
                HabitWeekInput(3L, Schedule.Daily(), week(3L, "1101101")),
                HabitWeekInput(4L, Schedule.Daily(), week(4L, "0100101")),
                HabitWeekInput(5L, Schedule.Daily(), week(5L, "-------")),
            ),
        )

        assertEquals(listOf(1L, 2L, 3L, 4L), recap.habits.map { it.habitId })
        assertEquals(18, recap.done)
        assertEquals(28, recap.total)
        assertEquals(64, recap.percent)
    }

    @Test
    fun `an empty week has zero percent and nothing to count`() {
        val recap = WeeklyRecapCalculator.recap(weekStart, emptyList())

        assertEquals(0, recap.total)
        assertEquals(0, recap.percent)
        assertEquals(false, recap.hasAnythingToCount)
    }
}
