package com.jjrapps.constanza.tracking

import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

private val TODAY: LocalDate = LocalDate.parse("2026-09-01") // a Tuesday
private val FIXED_INSTANT: Instant = Instant.parse("2026-09-01T08:00:00Z")
private const val HABIT_COLOR_ARGB = 0xFF009688.toInt()
private const val MORNING_SLOT_ID = 1L
private const val EVENING_SLOT_ID = 2L

private fun habit(id: Long, name: String = "Habit $id") = Habit(
    id = id, name = name, colorArgb = HABIT_COLOR_ARGB, notes = null,
    archived = false, archivedAt = null, createdAt = FIXED_INSTANT, sortOrder = 0,
)

private fun slot(id: Long, habitId: Long, minuteOfDay: Int) =
    ReminderSlot(id = id, habitId = habitId, minuteOfDay = minuteOfDay, enabled = true)

private fun entryEntity(habitId: Long, slotId: Long?, status: EntryStatus) = EntryEntity(
    habitId = habitId, date = TODAY.toString(), slotId = slotId ?: 0L, status = status.name,
    value = null, answeredAt = FIXED_INSTANT.toString(), source = "IN_APP",
)

/**
 * day-review, slice A (day-review-data): [countHabitsWithUnansweredSlots] is a pure join over
 * [buildTodayHabitRow]'s own output, exercised the same way `TodayViewModelTest` exercises
 * [buildTodayHabitRow] itself — real [Habit]/[Schedule]/[ReminderSlot] fixtures through the real
 * function, never a hand-built [TodayHabitRow] standing in for what that join actually produces.
 */
class DayReviewUnansweredCountTest {

    private fun rowsFor(
        habits: List<Habit>,
        schedules: Map<Long, Schedule>,
        slotsByHabit: Map<Long, List<ReminderSlot>>,
        entries: List<EntryEntity>,
    ): List<TodayHabitRow> {
        val snapshot = TodaySnapshot(entriesToday = entries, unresolvedOccurrences = emptyList(), today = TODAY)
        return habits.mapNotNull { habit ->
            val schedule = schedules.getValue(habit.id)
            val slots = slotsByHabit[habit.id].orEmpty()
            buildTodayHabitRow(habit, schedule, slots, snapshot)
        }
    }

    @Test
    fun `nothing due counts zero`() {
        val notToday = TODAY.dayOfWeek.plus(1)
        val h = habit(1)
        val rows = rowsFor(
            habits = listOf(h),
            schedules = mapOf(h.id to Schedule.DaysOfWeek(days = setOf(notToday))),
            slotsByHabit = emptyMap(),
            entries = emptyList(),
        )

        assertEquals(0, countHabitsWithUnansweredSlots(rows))
    }

    @Test
    fun `all answered counts zero`() {
        val h = habit(1)
        val rows = rowsFor(
            habits = listOf(h),
            schedules = mapOf(h.id to Schedule.Daily()),
            slotsByHabit = emptyMap(),
            entries = listOf(entryEntity(h.id, slotId = null, status = EntryStatus.COMPLETED)),
        )

        assertEquals(0, countHabitsWithUnansweredSlots(rows))
    }

    @Test
    fun `some unanswered counts those habits`() {
        val answered = habit(1)
        val unanswered = habit(2)
        val rows = rowsFor(
            habits = listOf(answered, unanswered),
            schedules = mapOf(answered.id to Schedule.Daily(), unanswered.id to Schedule.Daily()),
            slotsByHabit = emptyMap(),
            entries = listOf(entryEntity(answered.id, slotId = null, status = EntryStatus.COMPLETED)),
        )

        assertEquals(1, countHabitsWithUnansweredSlots(rows))
    }

    /** A multi-slot habit with one answered slot and one unanswered slot counts ONCE — this answers
     *  "how many habits", not "how many slots". */
    @Test
    fun `a multi-slot habit half answered counts as one unanswered habit, not two`() {
        val h = habit(1)
        val rows = rowsFor(
            habits = listOf(h),
            schedules = mapOf(h.id to Schedule.TimesPerDay()),
            slotsByHabit = mapOf(h.id to listOf(slot(MORNING_SLOT_ID, h.id, 8 * 60), slot(EVENING_SLOT_ID, h.id, 20 * 60))),
            entries = listOf(entryEntity(h.id, slotId = MORNING_SLOT_ID, status = EntryStatus.COMPLETED)),
        )

        assertEquals(1, countHabitsWithUnansweredSlots(rows))
    }

    /** SKIPPED is answered, not unanswered — it must not count. */
    @Test
    fun `a skipped slot does not count as unanswered`() {
        val h = habit(1)
        val rows = rowsFor(
            habits = listOf(h),
            schedules = mapOf(h.id to Schedule.Daily()),
            slotsByHabit = emptyMap(),
            entries = listOf(entryEntity(h.id, slotId = null, status = EntryStatus.SKIPPED)),
        )

        assertEquals(0, countHabitsWithUnansweredSlots(rows))
    }

    /** A not-due habit produces no row at all ([buildTodayHabitRow] mirrors [DayStatus.NOT_DUE]
     *  exactly), so it cannot contribute to the count even though it has no entry either. */
    @Test
    fun `a not-due habit does not count even though it has nothing answered`() {
        val notToday = TODAY.dayOfWeek.plus(1)
        val due = habit(1)
        val notDue = habit(2)
        val rows = rowsFor(
            habits = listOf(due, notDue),
            schedules = mapOf(due.id to Schedule.Daily(), notDue.id to Schedule.DaysOfWeek(days = setOf(notToday))),
            slotsByHabit = emptyMap(),
            entries = emptyList(),
        )

        assertEquals(1, countHabitsWithUnansweredSlots(rows))
    }
}
