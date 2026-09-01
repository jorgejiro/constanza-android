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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The "archived for 5 days" of habit-management's un-archive scenario. */
private const val ARCHIVED_DAYS = 5L

/**
 * Task 6a.4 (habit-management: Habit Archiving) against real Room, including the
 * "Un-archiving does not back-fill missed slots" scenario, which
 * [com.jjrapps.constanza.scheduling.OccurrencePlanner] satisfies only because it plans from today
 * forward — a property nothing else in the suite pins.
 */
@RunWith(AndroidJUnit4::class)
class HabitRepositoryArchiveTest {

    private lateinit var fixture: HabitRepositoryTestFixture
    private val repository: HabitRepository get() = fixture.habitRepository

    @Before
    fun setUp() {
        fixture = HabitRepositoryTestFixture(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() = fixture.close()

    @Test
    fun archivingRoundTripsThroughTheStoredFlagAndTheObservedList() = runBlocking {
        val today = fixture.timeProvider.today()
        val id = repository.create(newHabit(name = "Read"), Schedule.Daily())
        assertEquals(listOf(false), repository.observeAll().first().map { it.archived })

        repository.setArchived(id, true)

        val archived = requireNotNull(repository.findById(id))
        assertTrue(archived.archived)
        assertEquals(
            "archiving must stamp the archive date, since ProgressViewModel's effectiveToday " +
                "clamp and entry filter both key off it",
            today,
            archived.archivedAt,
        )
        assertEquals(listOf(true), repository.observeAll().first().map { it.archived })
        assertTrue(
            "the habit list's active filter reads exactly this flag off observeAll()",
            repository.observeAll().first().none { !it.archived },
        )

        repository.setArchived(id, false)

        val restored = requireNotNull(repository.findById(id))
        assertFalse(restored.archived)
        assertNull("un-archiving must clear the archive date, not leave a stale one", restored.archivedAt)
        assertEquals(listOf(false), repository.observeAll().first().map { it.archived })
    }

    /** habit-management: Archiving stops reminders. */
    @Test
    fun archivingCancelsEveryArmedOccurrence() = runBlocking {
        val id = repository.create(newHabit(name = "Read"), Schedule.Daily())
        fixture.insertEnabledSlot(id)
        fixture.occurrencePlanner.replanAll()
        assertTrue(
            "arrange failed: the habit must have armed occurrences to cancel",
            fixture.armedOccurrenceDates(id).isNotEmpty(),
        )

        repository.setArchived(id, true)

        assertTrue(
            "an archived habit must fire no reminder at all",
            fixture.database.reminderOccurrenceDao().findByHabitId(id).isEmpty(),
        )
    }

    /**
     * habit-management: Un-archiving does not back-fill missed slots — GIVEN a habit archived for 5
     * days, WHEN the user un-archives it, THEN reminders resume from now onward and no `Entry` is
     * retroactively created for the archived window.
     */
    @Test
    fun unArchivingResumesFromTodayAndBackFillsNothing() = runBlocking {
        val today = fixture.timeProvider.today()
        val id = repository.create(newHabit(name = "Read"), Schedule.Daily())
        fixture.insertEnabledSlot(id)
        repository.setArchived(id, true)
        backdateArchive(id, today.minusDays(ARCHIVED_DAYS))
        assertTrue(fixture.database.reminderOccurrenceDao().findByHabitId(id).isEmpty())

        repository.setArchived(id, false)

        val planned = fixture.database.reminderOccurrenceDao().findByHabitId(id)
        assertTrue("un-archiving must resume reminder scheduling", planned.isNotEmpty())
        val backFilled = planned.map { LocalDate.parse(it.scheduledDate) }.filter { it.isBefore(today) }
        assertEquals("no reminder may be planned inside the archived window", emptyList<LocalDate>(), backFilled)
        assertTrue(
            "no Entry may be retroactively created for the archived window",
            fixture.database.entryDao().findByHabitId(id).isEmpty(),
        )
    }

    /** Moves the archive stamp into the past so the habit has genuinely been archived for a window,
     *  which a fixed test clock cannot produce by waiting. */
    private suspend fun backdateArchive(habitId: Long, archivedAt: LocalDate) {
        val stored = requireNotNull(fixture.database.habitDao().findById(habitId))
        fixture.database.habitDao().update(stored.copy(archivedAt = archivedAt.toString()))
    }
}
