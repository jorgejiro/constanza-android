package com.jjrapps.constanza.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.Test

/**
 * habit-scheduling: Six Frequency Kinds; habit-entry-tracking: Entry States.
 * RED for tasks 2a.1/2a.2 — these types do not exist yet.
 */
class ModelTest {

    @Test
    fun `Habit carries all fields including nullable notes and archivedAt`() {
        val habit = Habit(
            id = 1L,
            name = "Drink water",
            colorArgb = 0xFF00FF00.toInt(),
            notes = null,
            archived = false,
            archivedAt = null,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            sortOrder = 0,
        )
        assertEquals("Drink water", habit.name)
        assertNull(habit.notes)
        assertNull(habit.archivedAt)
    }

    @Test
    fun `Schedule has exactly the six ratified kinds and each carries weekStart`() {
        val schedules: List<Schedule> = listOf(
            Schedule.Daily(),
            Schedule.TimesPerDay(),
            Schedule.NTimesPerWeek(times = 3),
            Schedule.DaysOfWeek(days = setOf(DayOfWeek.MONDAY)),
            Schedule.Monthly(dayOfMonth = 31),
            Schedule.EveryNDays(n = 30, anchor = LocalDate.of(2026, 1, 1)),
        )
        assertEquals(6, schedules.size)
        schedules.forEach { assertEquals(DayOfWeek.MONDAY, it.weekStart) }
    }

    @Test
    fun `DaysOfWeek cannot be constructed with an empty day set`() {
        assertFailsWith<IllegalArgumentException> {
            Schedule.DaysOfWeek(days = emptySet())
        }
    }

    @Test
    fun `DaysOfWeek copy also enforces the non-empty invariant`() {
        val schedule = Schedule.DaysOfWeek(days = setOf(DayOfWeek.MONDAY))
        assertFailsWith<IllegalArgumentException> {
            schedule.copy(days = emptySet())
        }
    }

    @Test
    fun `ReminderSlot carries an independent minuteOfDay and enabled flag`() {
        val slot = ReminderSlot(id = 10L, habitId = 1L, minuteOfDay = 8 * 60, enabled = true)
        assertEquals(480, slot.minuteOfDay)
    }

    @Test
    fun `EntryStatus has exactly four values`() {
        assertEquals(
            setOf("COMPLETED", "MISSED", "SKIPPED", "UNKNOWN"),
            EntryStatus.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun `Entry defaults value and answeredAt to null and carries a nullable slotId`() {
        val entry = Entry(
            habitId = 1L,
            date = LocalDate.of(2026, 9, 1),
            slotId = null,
            status = EntryStatus.UNKNOWN,
        )
        assertNull(entry.value)
        assertNull(entry.answeredAt)
        assertNull(entry.slotId)
    }

    @Test
    fun `Due is sealed with NotDue, Required and Candidate carrying quotaRemaining`() {
        val results: List<Due> = listOf(Due.NotDue, Due.Required, Due.Candidate(quotaRemaining = 2))
        val candidate = results.last() as Due.Candidate
        assertEquals(2, candidate.quotaRemaining)
    }

    @Test
    fun `DayStatus has exactly the six rollup outcomes`() {
        assertEquals(
            setOf("ALL_COMPLETED", "PARTIAL", "ANY_MISSED", "ALL_SKIPPED", "NOT_DUE", "PENDING"),
            DayStatus.entries.map { it.name }.toSet(),
        )
    }
}
