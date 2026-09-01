package com.jjrapps.constanza.habit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
            stored.copy(name = "Read at night", question = "Did you read?", notes = "20 pages"),
            Schedule.Daily(),
        )

        val updated = requireNotNull(repository.findById(id))
        assertEquals("Read at night", updated.name)
        assertEquals("Did you read?", updated.question)
        assertEquals("20 pages", updated.notes)
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
        fixture.insertEnabledSlot(id)
        assertTrue(
            "no reschedule trigger has fired since the slot appeared, so nothing may be armed yet",
            fixture.armedOccurrenceDates(id).isEmpty(),
        )

        repository.update(stored, Schedule.Daily())

        assertEquals(Schedule.Daily(), repository.findScheduleFor(id))
        val armed = fixture.armedOccurrenceDates(id)
        val dailyHorizon = listOf(today, today.plusDays(1), today.plusDays(2)).map(LocalDate::toString)
        assertTrue(
            "update must run replanAll() inside its own transaction; armed dates were $armed",
            armed.containsAll(dailyHorizon),
        )
    }
}
