package com.jjrapps.constanza.tracking

import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.Schedule
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

private val TODAY: LocalDate = LocalDate.parse("2026-09-01") // a Tuesday
private val FIXED_INSTANT: Instant = Instant.parse("2026-09-01T08:00:00Z")
private const val HABIT_COLOR_ARGB = 0xFF009688.toInt()

private fun habit(id: Long, name: String = "Habit $id") = Habit(
    id = id, name = name, colorArgb = HABIT_COLOR_ARGB, notes = null,
    archived = false, archivedAt = null, createdAt = FIXED_INSTANT, sortOrder = 0,
)

private fun entryEntity(habitId: Long, status: EntryStatus) = EntryEntity(
    habitId = habitId, date = TODAY.toString(), slotId = 0L, status = status.name,
    value = null, answeredAt = FIXED_INSTANT.toString(), source = "IN_APP",
)

private fun rowsFor(habits: List<Habit>, schedules: Map<Long, Schedule>, entries: List<EntryEntity>): List<TodayHabitRow> {
    val snapshot = TodaySnapshot(entriesToday = entries, unresolvedOccurrences = emptyList(), today = TODAY)
    return habits.mapNotNull { habit ->
        buildTodayHabitRow(habit, schedules.getValue(habit.id), emptyList(), snapshot)
    }
}

/**
 * day-review, slice B (day-review-notification): [decideDayReview]'s five branches, exercised the
 * same way [DayReviewUnansweredCountTest] exercises [countHabitsWithUnansweredSlots] — real
 * [TodayHabitRow]s built through [buildTodayHabitRow], never a hand-rolled stand-in for what that
 * join produces. The late-fire guard is deliberately NOT covered here (this function has no wall
 * clock to guard) — it is [com.jjrapps.constanza.scheduling.DayReviewWorkerInstrumentedTest]'s job.
 */
class DayReviewDecisionTest {

    @Test
    fun `fires-every-night with outstanding habits posts the outstanding count`() {
        val answered = habit(1)
        val unanswered = habit(2)
        val rows = rowsFor(
            habits = listOf(answered, unanswered),
            schedules = mapOf(answered.id to Schedule.Daily(), unanswered.id to Schedule.Daily()),
            entries = listOf(entryEntity(answered.id, EntryStatus.COMPLETED)),
        )

        val decision = decideDayReview(rows, firesEveryNight = true)

        assertEquals(DayReviewDecision.Post(outstandingCount = 1), decision)
    }

    @Test
    fun `fires-every-night with nothing outstanding still posts, at zero`() {
        val h = habit(1)
        val rows = rowsFor(
            habits = listOf(h),
            schedules = mapOf(h.id to Schedule.Daily()),
            entries = listOf(entryEntity(h.id, EntryStatus.COMPLETED)),
        )

        val decision = decideDayReview(rows, firesEveryNight = true)

        assertEquals(DayReviewDecision.Post(outstandingCount = 0), decision)
    }

    @Test
    fun `only-when-pending with outstanding habits posts the outstanding count`() {
        val h = habit(1)
        val rows = rowsFor(habits = listOf(h), schedules = mapOf(h.id to Schedule.Daily()), entries = emptyList())

        val decision = decideDayReview(rows, firesEveryNight = false)

        assertEquals(DayReviewDecision.Post(outstandingCount = 1), decision)
    }

    @Test
    fun `only-when-pending with nothing outstanding posts nothing`() {
        val h = habit(1)
        val rows = rowsFor(
            habits = listOf(h),
            schedules = mapOf(h.id to Schedule.Daily()),
            entries = listOf(entryEntity(h.id, EntryStatus.COMPLETED)),
        )

        val decision = decideDayReview(rows, firesEveryNight = false)

        assertEquals(DayReviewDecision.Skip, decision)
    }

    /** Nothing due at all that day (every habit's schedule mapped it to NOT_DUE, so [rowsFor]
     *  produces zero rows): posts nothing in EITHER mode, including fires-every-night's own
     *  default — this is the case flagged in this slice's own report for the owner to confirm. */
    @Test
    fun `nothing due at all posts nothing even in fires-every-night mode`() {
        val notToday = TODAY.dayOfWeek.plus(1)
        val h = habit(1)
        val rows = rowsFor(habits = listOf(h), schedules = mapOf(h.id to Schedule.DaysOfWeek(days = setOf(notToday))), entries = emptyList())

        val decision = decideDayReview(rows, firesEveryNight = true)

        assertEquals(DayReviewDecision.Skip, decision)
    }

    @Test
    fun `nothing due at all posts nothing in only-when-pending mode too`() {
        val notToday = TODAY.dayOfWeek.plus(1)
        val h = habit(1)
        val rows = rowsFor(habits = listOf(h), schedules = mapOf(h.id to Schedule.DaysOfWeek(days = setOf(notToday))), entries = emptyList())

        val decision = decideDayReview(rows, firesEveryNight = false)

        assertEquals(DayReviewDecision.Skip, decision)
    }
}
