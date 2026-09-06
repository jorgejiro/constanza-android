package com.jjrapps.constanza.scheduling

import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.dao.HabitDao
import com.jjrapps.constanza.core.data.dao.ReminderOccurrenceDao
import com.jjrapps.constanza.core.data.dao.ReminderSlotDao
import com.jjrapps.constanza.core.data.dao.ScheduleDao
import com.jjrapps.constanza.core.data.entity.EntryEntity
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
    private val entryDao = mockk<EntryDao>()
    private val alarmScheduler = mockk<AlarmScheduler>()
    private val timeProvider = mockk<TimeProvider>()
    private val planner = OccurrencePlanner(
        SchedulingDaos(habitDao, scheduleDao, reminderSlotDao, reminderOccurrenceDao),
        entryDao, alarmScheduler, timeProvider,
        resolveDeadlineHours = RESOLVE_DEADLINE_HOURS,
    )

    private fun stubTimeAndDefaults() {
        every { timeProvider.today() } returns TODAY
        every { timeProvider.zone() } returns ZoneOffset.UTC
        coEvery { reminderOccurrenceDao.findByHabitSlotDate(any(), any(), any()) } returns null
        coEvery { reminderOccurrenceDao.upsert(any()) } returns 100L
        coEvery { reminderOccurrenceDao.updateExact(any(), any()) } returns Unit
        coEvery { entryDao.findByHabitAndDate(any(), any()) } returns emptyList()
        every { alarmScheduler.schedule(any(), any()) } returns true
    }

    private fun habit(archived: Boolean = false) = HabitEntity(
        id = HABIT_ID,
        name = "H",
        colorArgb = 0,
        notes = null,
        archived = archived,
        archivedAt = null,
        createdAt = "2026-01-01T00:00:00Z",
    )

    private fun daily() = ScheduleEntity(
        habitId = HABIT_ID, kind = "DAILY", timesPerWeek = null, dayOfWeek = null,
        dayOfMonth = null, intervalDays = null, anchorDate = null, weekStart = 1,
    )

    private fun daysOfWeek(mask: Int) = ScheduleEntity(
        habitId = HABIT_ID, kind = "DAYS_OF_WEEK", timesPerWeek = null, dayOfWeek = null,
        dayOfMonth = null, intervalDays = null, anchorDate = null, weekStart = 1, daysOfWeekMask = mask,
    )

    private fun slot(id: Long = SLOT_ID, enabled: Boolean = true) =
        ReminderSlotEntity(id = id, habitId = HABIT_ID, minuteOfDay = 480, enabled = enabled)

    private fun entry(status: String, date: String = TODAY.toString(), slotId: Long = SLOT_ID) = EntryEntity(
        habitId = HABIT_ID, date = date, slotId = slotId, status = status, value = null,
        answeredAt = "${date}T08:00:00Z", source = "IN_APP",
    )

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
        // Monday (2026-01-05) must be found by the beyond-horizon search. Mask bit 0 == Monday.
        coEvery { scheduleDao.findByHabitId(HABIT_ID) } returns daysOfWeek(mask = 0b0000001)
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

    /**
     * The defect this guard exists for. Import wipes `reminder_occurrences` by cascade and
     * replans, so `findByHabitSlotDate` returns null for a date the user already completed, and
     * every "leave it alone" gate the planner had was keyed on that very row. A reminder time
     * earlier than the current moment then armed an alarm in the past, which
     * `setExactAndAllowWhileIdle` fires immediately.
     */
    @Test
    fun `a completed entry for today arms nothing and schedules no alarm for that date`() = runBlocking {
        stubTimeAndDefaults()
        coEvery { habitDao.findAllSnapshot() } returns listOf(habit())
        coEvery { scheduleDao.findByHabitId(HABIT_ID) } returns daily()
        coEvery { reminderSlotDao.findByHabitId(HABIT_ID) } returns listOf(slot())
        coEvery { reminderOccurrenceDao.findByHabitId(HABIT_ID) } returns emptyList()
        coEvery { entryDao.findByHabitAndDate(HABIT_ID, TODAY.toString()) } returns listOf(entry("COMPLETED"))

        planner.replanAll()

        coVerify(exactly = 0) { reminderOccurrenceDao.upsert(match { it.scheduledDate == TODAY.toString() }) }
        // The other three dates are untouched by the answer and must still be planned.
        coVerify(exactly = 3) { reminderOccurrenceDao.upsert(any()) }
    }

    /** `SKIPPED` is settable ONLY through a deliberate in-app action (habit-entry-tracking: Entry
     *  States), so re-asking would override a decision the user made on purpose. */
    @Test
    fun `a skipped entry for today also arms nothing for that date`() = runBlocking {
        stubTimeAndDefaults()
        coEvery { habitDao.findAllSnapshot() } returns listOf(habit())
        coEvery { scheduleDao.findByHabitId(HABIT_ID) } returns daily()
        coEvery { reminderSlotDao.findByHabitId(HABIT_ID) } returns listOf(slot())
        coEvery { reminderOccurrenceDao.findByHabitId(HABIT_ID) } returns emptyList()
        coEvery { entryDao.findByHabitAndDate(HABIT_ID, TODAY.toString()) } returns listOf(entry("SKIPPED"))

        planner.replanAll()

        coVerify(exactly = 0) { reminderOccurrenceDao.upsert(match { it.scheduledDate == TODAY.toString() }) }
    }

    /**
     * **Pins the deliberate exception, which is a ratified constraint rather than a preference.**
     * habit-entry-tracking's Provisional-Missed Correction names an import as one of the three
     * paths that MUST be able to correct a `MISSED` into a `COMPLETED`. Suppressing on `MISSED`
     * would close a route the specification requires to stay open, so a `MISSED` row must still
     * arm exactly as an absent one does.
     */
    @Test
    fun `a missed entry for today still arms, keeping the correction route open`() = runBlocking {
        stubTimeAndDefaults()
        coEvery { habitDao.findAllSnapshot() } returns listOf(habit())
        coEvery { scheduleDao.findByHabitId(HABIT_ID) } returns daily()
        coEvery { reminderSlotDao.findByHabitId(HABIT_ID) } returns listOf(slot())
        coEvery { reminderOccurrenceDao.findByHabitId(HABIT_ID) } returns emptyList()
        coEvery { entryDao.findByHabitAndDate(HABIT_ID, TODAY.toString()) } returns listOf(entry("MISSED"))

        planner.replanAll()

        coVerify(exactly = 4) { reminderOccurrenceDao.upsert(any()) }
    }

    /** An unknown/absent entry is the ordinary case and must behave exactly as before the guard. */
    @Test
    fun `an entry for another slot on the same date does not suppress this slot`() = runBlocking {
        stubTimeAndDefaults()
        coEvery { habitDao.findAllSnapshot() } returns listOf(habit())
        coEvery { scheduleDao.findByHabitId(HABIT_ID) } returns daily()
        coEvery { reminderSlotDao.findByHabitId(HABIT_ID) } returns listOf(slot())
        coEvery { reminderOccurrenceDao.findByHabitId(HABIT_ID) } returns emptyList()
        coEvery { entryDao.findByHabitAndDate(HABIT_ID, TODAY.toString()) } returns
            listOf(entry("COMPLETED", slotId = SLOT_ID + 1))

        planner.replanAll()

        coVerify(exactly = 4) { reminderOccurrenceDao.upsert(any()) }
    }

    /**
     * The third door, and the one the concrete slot id alone would miss. A habit saved with no
     * reminder time is answered from Today under `entries.slotId = 0` (design.md D11's sentinel);
     * adding a reminder time afterwards mints a FRESH slot id, so the answer and the slot never
     * share an id and the day would be re-asked immediately.
     */
    @Test
    fun `an in-app answer stored under the slot sentinel suppresses a newly added reminder slot`() = runBlocking {
        stubTimeAndDefaults()
        coEvery { habitDao.findAllSnapshot() } returns listOf(habit())
        coEvery { scheduleDao.findByHabitId(HABIT_ID) } returns daily()
        coEvery { reminderSlotDao.findByHabitId(HABIT_ID) } returns listOf(slot())
        coEvery { reminderOccurrenceDao.findByHabitId(HABIT_ID) } returns emptyList()
        coEvery { entryDao.findByHabitAndDate(HABIT_ID, TODAY.toString()) } returns
            listOf(entry("COMPLETED", slotId = 0L))

        planner.replanAll()

        coVerify(exactly = 0) { reminderOccurrenceDao.upsert(match { it.scheduledDate == TODAY.toString() }) }
    }

    /**
     * A live `ARMED` row keeps its identity: design.md §8.2 makes `occurrence.id` the
     * `PendingIntent` request code, so the guard is applied ONLY when no row exists. The fire-time
     * net in [ReminderFireHandler] is what covers this case instead.
     */
    @Test
    fun `an existing armed occurrence is still updated in place even when the entry is answered`() = runBlocking {
        stubTimeAndDefaults()
        coEvery { habitDao.findAllSnapshot() } returns listOf(habit())
        coEvery { scheduleDao.findByHabitId(HABIT_ID) } returns daily()
        coEvery { reminderSlotDao.findByHabitId(HABIT_ID) } returns listOf(slot())
        coEvery { reminderOccurrenceDao.findByHabitId(HABIT_ID) } returns emptyList()
        coEvery { entryDao.findByHabitAndDate(HABIT_ID, TODAY.toString()) } returns listOf(entry("COMPLETED"))
        val existing = occurrence(id = 77, date = TODAY.toString(), state = "ARMED")
        coEvery { reminderOccurrenceDao.findByHabitSlotDate(HABIT_ID, SLOT_ID, TODAY.toString()) } returns existing
        coEvery { reminderOccurrenceDao.upsert(match { it.id == 77L }) } returns 77L

        planner.replanAll()

        coVerify { reminderOccurrenceDao.upsert(match { it.id == 77L }) }
    }

    /**
     * `replanAll` runs on every app resume, so the guard must not cost one query per
     * (habit x slot x date). Two slots walking the same four due dates must still produce exactly
     * four entry reads, not eight.
     */
    @Test
    fun `entries are read once per habit and date, not once per slot`() = runBlocking {
        stubTimeAndDefaults()
        coEvery { habitDao.findAllSnapshot() } returns listOf(habit())
        coEvery { scheduleDao.findByHabitId(HABIT_ID) } returns daily()
        coEvery { reminderSlotDao.findByHabitId(HABIT_ID) } returns listOf(slot(), slot(id = SLOT_ID + 1))
        coEvery { reminderOccurrenceDao.findByHabitId(HABIT_ID) } returns emptyList()

        planner.replanAll()

        coVerify(exactly = 4) { entryDao.findByHabitAndDate(HABIT_ID, any()) }
        coVerify(exactly = 8) { reminderOccurrenceDao.upsert(any()) }
    }

    /** The beyond-horizon search walks up to 400 dates but only touches a DAO on a DUE one, so a
     *  sparse schedule must not turn the guard into hundreds of reads. */
    @Test
    fun `a sparse schedule reads entries only for the dates it actually plans`() = runBlocking {
        stubTimeAndDefaults()
        coEvery { habitDao.findAllSnapshot() } returns listOf(habit())
        coEvery { scheduleDao.findByHabitId(HABIT_ID) } returns daysOfWeek(mask = 0b0000001)
        coEvery { reminderSlotDao.findByHabitId(HABIT_ID) } returns listOf(slot())
        coEvery { reminderOccurrenceDao.findByHabitId(HABIT_ID) } returns emptyList()

        planner.replanAll()

        // Only Monday 2026-01-05 is due within the walked range.
        coVerify(exactly = 1) { entryDao.findByHabitAndDate(HABIT_ID, "2026-01-05") }
        coVerify(exactly = 1) { entryDao.findByHabitAndDate(HABIT_ID, any()) }
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
