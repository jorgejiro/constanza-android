package com.jjrapps.constanza.habit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.domain.model.Schedule
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val ENTRY_COUNT = 3

/**
 * habit-management: Habit Deletion, against real Room (design.md D1). Every scenario in the Spec
 * Conformance table this class proves is named in its own test's KDoc; the mechanism throughout is
 * [HabitRepository.delete]'s four `ForeignKey.CASCADE` declarations plus post-commit alarm
 * cancellation — never a hand-written per-table delete.
 */
@RunWith(AndroidJUnit4::class)
class HabitRepositoryDeleteTest {

    private lateinit var fixture: HabitRepositoryTestFixture
    private val database: AppDatabase get() = fixture.database
    private val repository: HabitRepository get() = fixture.habitRepository

    @Before
    fun setUp() {
        fixture = HabitRepositoryTestFixture(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() = fixture.close()

    /** Proves: "Deleting a habit with history removes it and all its records" (task 4.1). */
    @Test
    fun deletingAHabitWithHistoryRemovesItAndAllFourChildTables() = runBlocking {
        val habitId = repository.create(newHabit(name = "Read"), Schedule.Daily())
        val slotId = fixture.insertEnabledSlot(habitId)
        fixture.occurrencePlanner.replanAll()
        repeat(ENTRY_COUNT) { index ->
            database.entryDao().insert(entryFor(habitId, slotId, "2026-09-0${index + 1}"))
        }
        assertTrue(
            "arrange failed: the habit must have a schedule before deletion proves anything",
            database.scheduleDao().findByHabitId(habitId) != null,
        )
        assertTrue(
            "arrange failed: the habit must have armed occurrences before deletion proves anything",
            fixture.armedOccurrenceDates(habitId).isNotEmpty(),
        )

        repository.delete(habitId)

        assertNull(database.habitDao().findById(habitId))
        assertNull(database.scheduleDao().findByHabitId(habitId))
        assertTrue(database.reminderSlotDao().findByHabitId(habitId).isEmpty())
        assertTrue(database.entryDao().findByHabitId(habitId).isEmpty())
        assertTrue(database.reminderOccurrenceDao().findByHabitId(habitId).isEmpty())
    }

    /**
     * Proves the FIRST line of defence only — that cancellation actually ran — not that a later
     * broadcast is ignored; `CoreFlowE2ETest`'s
     * `deletingAHabitThroughTheUiRemovesItAndItsHistoryAndSilencesItsReminder` (task 4.9) proves the
     * second (task 4.2).
     */
    @Test
    fun deletingAHabitCancelsEveryArmedOccurrenceItHad() = runBlocking {
        val habitId = repository.create(newHabit(name = "Read"), Schedule.Daily())
        fixture.insertEnabledSlot(habitId)
        fixture.occurrencePlanner.replanAll()
        val armedIds = database.reminderOccurrenceDao().findByHabitId(habitId).map { it.id }
        assertTrue("arrange failed: the habit must have armed occurrences to cancel", armedIds.isNotEmpty())

        repository.delete(habitId)

        armedIds.forEach { id -> verify(exactly = 1) { fixture.alarmScheduler.cancel(id) } }
    }

    /** Proves: "Deletion does not affect archiving" (task 4.3). */
    @Test
    fun deletingOneHabitLeavesAnArchivedHabitCompletelyUnchanged() = runBlocking {
        val archivedId = repository.create(newHabit(name = "Meditate"), Schedule.Daily())
        val archivedSlotId = fixture.insertEnabledSlot(archivedId)
        database.entryDao().insert(entryFor(archivedId, archivedSlotId, "2026-09-01"))
        repository.setArchived(archivedId, true)
        val archivedBefore = requireNotNull(database.habitDao().findById(archivedId))
        val entriesBefore = database.entryDao().findByHabitId(archivedId)

        val activeId = repository.create(newHabit(name = "Read"), Schedule.Daily())

        repository.delete(activeId)

        val archivedAfter = requireNotNull(database.habitDao().findById(archivedId))
        assertEquals(
            "deleting an unrelated habit must not flip the archived flag",
            archivedBefore.archived,
            archivedAfter.archived,
        )
        assertEquals(
            "deleting an unrelated habit must not touch the archive stamp",
            archivedBefore.archivedAt,
            archivedAfter.archivedAt,
        )
        assertEquals(
            "deleting an unrelated habit must not touch its entry history",
            entriesBefore,
            database.entryDao().findByHabitId(archivedId),
        )
        assertNull("the deleted habit itself must still be gone", database.habitDao().findById(activeId))
    }

    /** Proves: "Deleting a habit with no history behaves the same as one with history" (task 4.4) —
     *  same assertions as [deletingAHabitWithHistoryRemovesItAndAllFourChildTables] minus entries,
     *  since there are none to begin with. */
    @Test
    fun deletingAZeroEntryHabitBehavesTheSameMinusEntries() = runBlocking {
        val habitId = repository.create(newHabit(name = "Read"), Schedule.Daily())
        fixture.insertEnabledSlot(habitId)
        fixture.occurrencePlanner.replanAll()
        assertTrue(database.entryDao().findByHabitId(habitId).isEmpty())

        repository.delete(habitId)

        assertNull(database.habitDao().findById(habitId))
        assertNull(database.scheduleDao().findByHabitId(habitId))
        assertTrue(database.reminderSlotDao().findByHabitId(habitId).isEmpty())
        assertTrue(database.reminderOccurrenceDao().findByHabitId(habitId).isEmpty())
    }

    private fun entryFor(habitId: Long, slotId: Long, date: String) = EntryEntity(
        habitId = habitId,
        date = date,
        slotId = slotId,
        status = "COMPLETED",
        value = null,
        answeredAt = "${date}T08:00:00Z",
        source = "IN_APP",
    )
}
