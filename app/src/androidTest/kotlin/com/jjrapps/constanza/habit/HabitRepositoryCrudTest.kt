package com.jjrapps.constanza.habit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Far enough out that nothing is due inside [com.jjrapps.constanza.scheduling.OccurrencePlanner]'s
 *  two-day horizon, which is what makes the post-edit horizon a visible change. */
private const val WEEKLY_DAY_OFFSET = 3L

/**
 * Tasks 6a.2/6a.3 (habit-management: Habit Creation, Habit Editing) against real Room. The
 * ViewModel unit tests mock [HabitRepository], so they prove what the ViewModels ask for and
 * nothing about what Room actually stores; these scenarios cover the persistence surface itself.
 */
@RunWith(AndroidJUnit4::class)
class HabitRepositoryCrudTest {

    private lateinit var fixture: HabitRepositoryTestFixture
    private val repository: HabitRepository get() = fixture.habitRepository

    @Before
    fun setUp() {
        fixture = HabitRepositoryTestFixture(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() = fixture.close()

    @Test
    fun creatingAHabitPersistsItAndItsScheduleUnderTheReturnedId() = runBlocking {
        val weeklyDay = fixture.timeProvider.today().plusDays(WEEKLY_DAY_OFFSET).dayOfWeek

        val id = repository.create(newHabit(name = "Read"), Schedule.Weekly(weeklyDay))

        assertTrue("create must return the real row id, not the 0 sentinel", id > 0)
        val stored = requireNotNull(repository.findById(id)) { "created habit must be findable by id" }
        assertEquals(id, stored.id)
        assertEquals("Read", stored.name)
        assertFalse(stored.archived)
        assertEquals(Schedule.Weekly(weeklyDay), repository.findScheduleFor(id))
        assertEquals(listOf(id), repository.observeAll().first().map { it.id })
    }

    @Test
    fun updatingAHabitChangesItsStoredFields() = runBlocking {
        val id = repository.create(newHabit(name = "Read"), Schedule.Daily())
        val stored = requireNotNull(repository.findById(id))

        repository.update(
            stored.copy(name = "Read at night", notes = "20 pages", colorArgb = UPDATED_COLOR_ARGB),
            Schedule.Daily(),
        )

        val updated = requireNotNull(repository.findById(id))
        assertEquals("Read at night", updated.name)
        assertEquals("20 pages", updated.notes)
        // Two TEXT columns (name, notes) cannot catch a column-order or type regression in a
        // rebuilt `habits` table (design.md D6) — colorArgb is the non-null INTEGER witness.
        assertEquals(UPDATED_COLOR_ARGB, updated.colorArgb)
    }

    /**
     * habit-management: Editing the schedule reschedules reminders. Asserts the replan itself, not
     * merely that the `schedules` row changed: a write that skipped
     * [com.jjrapps.constanza.scheduling.ScheduleEditor] would still change the row and would still
     * leave the new DAILY horizon unarmed.
     */
    @Test
    fun editingTheScheduleReplansAsPartOfTheSameWrite() = runBlocking {
        val today = fixture.timeProvider.today()
        val id = repository.create(
            newHabit(name = "Read"),
            Schedule.Weekly(today.plusDays(WEEKLY_DAY_OFFSET).dayOfWeek),
        )
        val stored = requireNotNull(repository.findById(id))
        val slotId = fixture.insertEnabledSlot(id)
        assertTrue(
            "no reschedule trigger has fired since the slot appeared, so nothing may be armed yet",
            fixture.armedOccurrenceDates(id).isEmpty(),
        )

        // Passed through update()'s own slots parameter (not left as an untouched external row):
        // syncSlots must run before the replan for this to see it (task 6a.8 ordering fix), the same
        // shape the real editor's save() call always uses.
        val slot = ReminderSlot(id = slotId, habitId = id, minuteOfDay = MORNING_MINUTE_OF_DAY, enabled = true)
        repository.update(stored, Schedule.Daily(), listOf(slot))

        assertEquals(Schedule.Daily(), repository.findScheduleFor(id))
        val armed = fixture.armedOccurrenceDates(id)
        val dailyHorizon = listOf(today, today.plusDays(1), today.plusDays(2)).map(LocalDate::toString)
        assertTrue(
            "update must run replanAll() inside its own transaction, seeing the just-synced slot; " +
                "armed dates were $armed",
            armed.containsAll(dailyHorizon),
        )
    }

    /** Task 6a.8 (habit-scheduling, ratified 2026-09-01): the single reminder time for a
     *  non-`TIMES_PER_DAY` kind is exactly one [ReminderSlot] in [HabitRepository.create]'s `slots`
     *  list — the same round trip `TIMES_PER_DAY`'s own slots already take, just capped at one. */
    @Test
    fun creatingADailyHabitWithAReminderTimeArmsAnOccurrence() = runBlocking {
        val slot = ReminderSlot(id = 0L, habitId = 0L, minuteOfDay = MORNING_MINUTE_OF_DAY, enabled = true)

        val id = repository.create(newHabit(name = "Stretch"), Schedule.Daily(), listOf(slot))

        assertTrue(
            "a habit saved with a reminder time must arm at least one occurrence",
            fixture.armedOccurrenceDates(id).isNotEmpty(),
        )
    }

    /** Pins the other half of the same ratified decision: a reminder-less habit MUST still be
     *  accepted, and MUST arm nothing — not a validation error, not a silently-armed default time.
     *  [com.jjrapps.constanza.scheduling.OccurrencePlanner.planHabit]'s own
     *  `enabledSlots.isEmpty()` early return is what makes the second assertion true; this proves it
     *  end to end through the same [HabitRepository.create] path the editor calls. */
    @Test
    fun creatingADailyHabitWithNoReminderTimeIsAcceptedAndArmsNothing() = runBlocking {
        val id = repository.create(newHabit(name = "Stretch"), Schedule.Daily())

        assertEquals("Stretch", requireNotNull(repository.findById(id)).name)
        assertTrue(
            "a habit saved with no reminder time (D7/OA-3) must arm nothing",
            fixture.armedOccurrenceDates(id).isEmpty(),
        )
    }
}

private const val MORNING_MINUTE_OF_DAY = 480
private const val UPDATED_COLOR_ARGB = 0xFFE53935.toInt()
