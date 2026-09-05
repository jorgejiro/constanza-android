package com.jjrapps.constanza.core.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.HabitEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 3.6: proves — on a real device, not by inspection — that `UNIQUE(habitId, date, slotId)`
 * (design.md §8.1) actually rejects a second `slotId = 0` row for the same habit and date. A
 * constraint never observed rejecting anything is a constraint that has not been tested.
 */
@RunWith(AndroidJUnit4::class)
class EntryDaoUniqueConstraintTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun duplicateSlotZeroRowForSameHabitAndDateIsRejected() = runBlocking {
        val habitId = database.habitDao().insert(
            HabitEntity(
                name = "Drink water",
                colorArgb = 0,
                notes = null,
                archivedAt = null,
                createdAt = "2026-01-01T00:00:00Z",
            ),
        )
        val firstEntry = EntryEntity(
            habitId = habitId,
            date = "2026-09-01",
            slotId = 0,
            status = "COMPLETED",
            value = null,
            answeredAt = "2026-09-01T08:00:00Z",
            source = "IN_APP",
        )
        database.entryDao().insert(firstEntry)

        var rejected = false
        try {
            // Same (habitId, date, slotId = 0) as firstEntry — the plain `insert` keeps the
            // default OnConflictStrategy.ABORT so the constraint violation surfaces, not upsert.
            database.entryDao().insert(firstEntry.copy(status = "MISSED"))
        } catch (expected: SQLiteConstraintException) {
            rejected = true
        }

        assertTrue("Expected UNIQUE(habitId, date, slotId) to reject the duplicate row", rejected)
        assertTrue(database.entryDao().findByHabitAndDate(habitId, "2026-09-01").size == 1)
    }

    @Test
    fun upsertReplacesTheConflictingRowInsteadOfDuplicatingIt() = runBlocking {
        val habitId = database.habitDao().insert(
            HabitEntity(
                name = "Drink water",
                colorArgb = 0,
                notes = null,
                archivedAt = null,
                createdAt = "2026-01-01T00:00:00Z",
            ),
        )
        val entry = EntryEntity(
            habitId = habitId,
            date = "2026-09-01",
            slotId = 0,
            status = "MISSED",
            value = null,
            answeredAt = "2026-09-01T08:00:00Z",
            source = "SWEEP",
        )
        database.entryDao().insert(entry)

        database.entryDao().upsert(entry.copy(status = "COMPLETED", source = "IN_APP"))

        val rows = database.entryDao().findByHabitAndDate(habitId, "2026-09-01")
        assertTrue(rows.size == 1)
        assertTrue(rows.single().status == "COMPLETED")
    }
}
