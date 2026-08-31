package com.jjrapps.constanza.scheduling

import com.jjrapps.constanza.core.data.dao.HabitDao
import com.jjrapps.constanza.core.data.dao.ReminderOccurrenceDao
import com.jjrapps.constanza.core.data.dao.ReminderSlotDao
import com.jjrapps.constanza.core.data.dao.ScheduleDao
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.data.entity.ReminderSlotEntity
import com.jjrapps.constanza.core.data.entity.ScheduleEntity
import com.jjrapps.constanza.core.time.TimeProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test

private const val HABIT_ID = 1L
private const val SLOT_ID = 10L
private const val RESOLVE_DEADLINE_HOURS = 24L
private val TODAY = LocalDate.of(2026, 1, 1) // Thursday

/** Task 4a.6: planner arithmetic, isolated from Room/AlarmManager via mocked DAOs and a mocked
 *  [AlarmScheduler] — [OccurrencePlanner] takes plain DAO interfaces, so no Android runtime or
 *  Robolectric is needed to exercise the horizon/beyond-horizon/stale-cancellation logic. */
class OccurrencePlannerTest {

    private val habitDao = mockk<HabitDao>()
    private val scheduleDao = mockk<ScheduleDao>()
    private val reminderSlotDao = mockk<ReminderSlotDao>()
    private val reminderOccurrenceDao = mockk<ReminderOccurrenceDao>()
    private val alarmScheduler = mockk<AlarmScheduler>()
    private val timeProvider = mockk<TimeProvider>()
    private val planner = OccurrencePlanner(
        SchedulingDaos(habitDao, scheduleDao, reminderSlotDao, reminderOccurrenceDao),
        alarmScheduler, timeProvider,
        resolveDeadlineHours = RESOLVE_DEADLINE_HOURS,
    )

    private fun stubTimeAndDefaults() {
        every { timeProvider.today() } returns TODAY
        every { timeProvider.zone() } returns ZoneOffset.UTC
        coEvery { reminderOccurrenceDao.findByHabitSlotDate(any(), any(), any()) } returns null
        coEvery { reminderOccurrenceDao.upsert(any()) } returns 100L
        coEvery { reminderOccurrenceDao.updateExact(any(), any()) } returns Unit
        every { alarmScheduler.schedule(any(), any()) } returns true
    }

    private fun habit(archived: Boolean = false) =
        HabitEntity(id = HABIT_ID, name = "H", question = null, colorArgb = 0, notes = null, archived = archived, archivedAt = null, createdAt = "2026-01-01T00:00:00Z")

    private fun daily() = ScheduleEntity(
        habitId = HABIT_ID, kind = "DAILY", timesPerWeek = null, dayOfWeek = null,
        dayOfMonth = null, intervalDays = null, anchorDate = null, weekStart = 1,
    )

    private fun weekly(dayOfWeek: Int) = ScheduleEntity(
        habitId = HABIT_ID, kind = "WEEKLY", timesPerWeek = null, dayOfWeek = dayOfWeek,
        dayOfMonth = null, intervalDays = null, anchorDate = null, weekStart = 1,
    )

    private fun slot(id: Long = SLOT_ID, enabled: Boolean = true) =
        ReminderSlotEntity(id = id, habitId = HABIT_ID, minuteOfDay = 480, enabled = enabled)

    private fun occurrence(id: Long, date: String, state: String) = ReminderOccurrenceEntity(
        id = id, habitId = HABIT_ID, slotId = SLOT_ID, scheduledDate = date, scheduledAtEpochMs = 0,
        state = state, snoozeUntilEpochMs = null, snoozeCount = 0, notifiedAtEpochMs = null, resolveDeadlineMs = 0,
    )

    @Test
    fun `daily habit plans the full 48h horizon plus one occurrence beyond it`() = runBlocking {
        stubTimeAndDefaults()
        coEvery { habitDao.findAllSnapshot() } returns listOf(habit())
        coEvery { scheduleDao.findByHabitId(HABIT_ID) } returns daily()
        coEvery { reminderSlotDao.findByHabitId(HABIT_ID) } returns listOf(slot())
        coEvery { reminderOccurrenceDao.findByHabitId(HABIT_ID) } returns emptyList()

        planner.replanAll()

        // today, +1, +2 (horizon) plus one beyond it — Daily is due every date, so exactly 4.
        coVerify(exactly = 4) { reminderOccurrenceDao.upsert(any()) }
        coVerify(exactly = 4) { reminderOccurrenceDao.updateExact(100L, true) }
    }

    @Test
    fun `archived habit cancels every occurrence and is never planned`() = runBlocking {
        stubTimeAndDefaults()
        coEvery { habitDao.findAllSnapshot() } returns listOf(habit(archived = true))
        coEvery { reminderOccurrenceDao.findByHabitId(HABIT_ID) } returns
            listOf(occurrence(1, "2026-01-01", "ARMED"), occurrence(2, "2026-01-02", "ARMED"))
        every { alarmScheduler.cancel(any()) } returns Unit
        coEvery { reminderOccurrenceDao.deleteByHabitId(HABIT_ID) } returns Unit

        planner.replanAll()

        coVerify(exactly = 0) { scheduleDao.findByHabitId(any()) }
        coVerify(exactly = 0) { reminderOccurrenceDao.upsert(any()) }
        verify { alarmScheduler.cancel(1) }
        verify { alarmScheduler.cancel(2) }
        coVerify { reminderOccurrenceDao.deleteByHabitId(HABIT_ID) }
    }

    @Test
    fun `habit with no enabled slots plans nothing`() = runBlocking {
        stubTimeAndDefaults()
        coEvery { habitDao.findAllSnapshot() } returns listOf(habit())
        coEvery { scheduleDao.findByHabitId(HABIT_ID) } returns daily()
        coEvery { reminderSlotDao.findByHabitId(HABIT_ID) } returns listOf(slot(enabled = false))
        coEvery { reminderOccurrenceDao.findByHabitId(HABIT_ID) } returns emptyList()

        planner.replanAll()

        coVerify(exactly = 0) { reminderOccurrenceDao.upsert(any()) }
    }

    @Test
    fun `weekly habit plans only the matching weekday, found beyond the horizon here`() = runBlocking {
        stubTimeAndDefaults()
        coEvery { habitDao.findAllSnapshot() } returns listOf(habit())
        // TODAY is Thursday 2026-01-01; the horizon (Thu/Fri/Sat) contains no Monday, so the next
        // Monday (2026-01-05) must be found by the beyond-horizon search.
        coEvery { scheduleDao.findByHabitId(HABIT_ID) } returns weekly(dayOfWeek = 1)
        coEvery { reminderSlotDao.findByHabitId(HABIT_ID) } returns listOf(slot())
        coEvery { reminderOccurrenceDao.findByHabitId(HABIT_ID) } returns emptyList()

        planner.replanAll()

        coVerify(exactly = 1) { reminderOccurrenceDao.upsert(any()) }
    }

    @Test
    fun `an existing armed occurrence is updated in place, keeping the same id`() = runBlocking {
        stubTimeAndDefaults()
        coEvery { habitDao.findAllSnapshot() } returns listOf(habit())
        coEvery { scheduleDao.findByHabitId(HABIT_ID) } returns daily()
        coEvery { reminderSlotDao.findByHabitId(HABIT_ID) } returns listOf(slot())
        coEvery { reminderOccurrenceDao.findByHabitId(HABIT_ID) } returns emptyList()
        val existing = occurrence(id = 77, date = TODAY.toString(), state = "ARMED")
        coEvery { reminderOccurrenceDao.findByHabitSlotDate(HABIT_ID, SLOT_ID, TODAY.toString()) } returns existing
        coEvery { reminderOccurrenceDao.upsert(match { it.id == 77L }) } returns 77L

        planner.replanAll()

        coVerify { reminderOccurrenceDao.upsert(match { it.id == 77L }) }
    }

    @Test
    fun `an existing resolved occurrence for a due date is left untouched, never re-upserted`() = runBlocking {
        stubTimeAndDefaults()
        coEvery { habitDao.findAllSnapshot() } returns listOf(habit())
        coEvery { scheduleDao.findByHabitId(HABIT_ID) } returns daily()
        coEvery { reminderSlotDao.findByHabitId(HABIT_ID) } returns listOf(slot())
        coEvery { reminderOccurrenceDao.findByHabitId(HABIT_ID) } returns emptyList()
        val resolved = occurrence(id = 5, date = TODAY.toString(), state = "RESOLVED")
        coEvery { reminderOccurrenceDao.findByHabitSlotDate(HABIT_ID, SLOT_ID, TODAY.toString()) } returns resolved

        planner.replanAll()

        coVerify(exactly = 0) { reminderOccurrenceDao.upsert(match { it.scheduledDate == TODAY.toString() }) }
    }

    @Test
    fun `a disabled slot's armed occurrence is cancelled and deleted`() = runBlocking {
        stubTimeAndDefaults()
        coEvery { habitDao.findAllSnapshot() } returns listOf(habit())
        coEvery { scheduleDao.findByHabitId(HABIT_ID) } returns daily()
        coEvery { reminderSlotDao.findByHabitId(HABIT_ID) } returns emptyList() // the slot was deleted
        val stale = occurrence(id = 9, date = TODAY.toString(), state = "ARMED")
        coEvery { reminderOccurrenceDao.findByHabitId(HABIT_ID) } returns listOf(stale)
        every { alarmScheduler.cancel(9) } returns Unit
        coEvery { reminderOccurrenceDao.deleteById(9) } returns Unit

        planner.replanAll()

        verify { alarmScheduler.cancel(9) }
        coVerify { reminderOccurrenceDao.deleteById(9) }
        coVerify(exactly = 0) { reminderOccurrenceDao.upsert(any()) }
    }

    @Test
    fun `inexact scheduling result is persisted onto the occurrence row`() = runBlocking {
        stubTimeAndDefaults()
        every { alarmScheduler.schedule(any(), any()) } returns false
        coEvery { habitDao.findAllSnapshot() } returns listOf(habit())
        coEvery { scheduleDao.findByHabitId(HABIT_ID) } returns daily()
        coEvery { reminderSlotDao.findByHabitId(HABIT_ID) } returns listOf(slot())
        coEvery { reminderOccurrenceDao.findByHabitId(HABIT_ID) } returns emptyList()

        planner.replanAll()

        coVerify(atLeast = 1) { reminderOccurrenceDao.updateExact(100L, false) }
    }
}
