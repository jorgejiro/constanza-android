package com.jjrapps.constanza.habit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.data.entity.ReminderSlotEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 3.5: [HabitRepository.deleteSlot] pays D11's FK-drop cost — it must remove the slot's
 * entries and the slot row atomically, since there is no cascading foreign key to do it for us.
 */
@RunWith(AndroidJUnit4::class)
class HabitRepositoryDeleteSlotTest {

    private lateinit var fixture: HabitRepositoryTestFixture
    private val database: AppDatabase get() = fixture.database
    private val repository: HabitRepository get() = fixture.habitRepository

    @Before
    fun setUp() {
        fixture = HabitRepositoryTestFixture(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() = fixture.close()

    @Test
    fun deletingASlotRemovesItsEntriesInTheSameTransaction() = runBlocking {
        val habitId = database.habitDao().insert(
            HabitEntity(
                name = "Read",
                colorArgb = 0,
                notes = null,
                archivedAt = null,
                createdAt = "2026-01-01T00:00:00Z",
            ),
        )
        val slotId = database.reminderSlotDao().insert(
            ReminderSlotEntity(habitId = habitId, minuteOfDay = 480, enabled = true),
        )
        database.entryDao().insert(
            EntryEntity(
                habitId = habitId,
                date = "2026-09-01",
                slotId = slotId,
                status = "COMPLETED",
                value = null,
                answeredAt = "2026-09-01T08:00:00Z",
                source = "IN_APP",
            ),
        )

        repository.deleteSlot(habitId, slotId)

        assertTrue(database.reminderSlotDao().findByHabitId(habitId).isEmpty())
        assertTrue(database.entryDao().findByHabitId(habitId).isEmpty())
    }

    @Test
    fun deletingASlotLeavesOtherSlotsEntriesUntouched() = runBlocking {
        val habitId = database.habitDao().insert(
            HabitEntity(
                name = "Read",
                colorArgb = 0,
                notes = null,
                archivedAt = null,
                createdAt = "2026-01-01T00:00:00Z",
            ),
        )
        val deletedSlotId = database.reminderSlotDao().insert(
            ReminderSlotEntity(habitId = habitId, minuteOfDay = 480, enabled = true),
        )
        val keptSlotId = database.reminderSlotDao().insert(
            ReminderSlotEntity(habitId = habitId, minuteOfDay = 1200, enabled = true),
        )
        database.entryDao().insert(
            EntryEntity(
                habitId = habitId,
                date = "2026-09-01",
                slotId = deletedSlotId,
                status = "COMPLETED",
                value = null,
                answeredAt = "2026-09-01T08:00:00Z",
                source = "IN_APP",
            ),
        )
        database.entryDao().insert(
            EntryEntity(
                habitId = habitId,
                date = "2026-09-01",
                slotId = keptSlotId,
                status = "MISSED",
                value = null,
                answeredAt = "2026-09-01T20:05:00Z",
                source = "SWEEP",
            ),
        )

        repository.deleteSlot(habitId, deletedSlotId)

        val remainingEntries = database.entryDao().findByHabitId(habitId)
        assertTrue(remainingEntries.size == 1)
        assertTrue(remainingEntries.single().slotId == keptSlotId)
    }
}
