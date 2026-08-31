package com.jjrapps.constanza.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.data.entity.ReminderSlotEntity
import com.jjrapps.constanza.core.data.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun findById(id: Long): HabitEntity?

    @Query("SELECT * FROM habits ORDER BY sortOrder")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: ScheduleEntity)

    @Query("SELECT * FROM schedules WHERE habitId = :habitId")
    suspend fun findByHabitId(habitId: Long): ScheduleEntity?
}

@Dao
interface ReminderSlotDao {
    @Insert
    suspend fun insert(slot: ReminderSlotEntity): Long

    @Query("SELECT * FROM reminder_slots WHERE habitId = :habitId")
    suspend fun findByHabitId(habitId: Long): List<ReminderSlotEntity>

    @Query("DELETE FROM reminder_slots WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/**
 * [upsert] uses `OnConflictStrategy.REPLACE` against `UNIQUE(habitId, date, slotId)` (design.md
 * §8.2): on a conflict, Room deletes the existing row and inserts the new one, which is the
 * idempotent write every reminder-response path needs. [insert] keeps the default
 * `OnConflictStrategy.ABORT` specifically so a duplicate write can be **observed failing** — task
 * 3.6 requires proving the constraint rejects a duplicate, not merely trusting it.
 */
@Dao
interface EntryDao {
    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: EntryEntity): Long

    @Query("SELECT * FROM entries WHERE habitId = :habitId AND date = :date")
    suspend fun findByHabitAndDate(habitId: Long, date: String): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE habitId = :habitId")
    suspend fun findByHabitId(habitId: Long): List<EntryEntity>

    @Query("DELETE FROM entries WHERE habitId = :habitId AND slotId = :slotId")
    suspend fun deleteBySlot(habitId: Long, slotId: Long)
}

@Dao
interface ReminderOccurrenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(occurrence: ReminderOccurrenceEntity): Long

    @Query("SELECT * FROM reminder_occurrences WHERE id = :id")
    suspend fun findById(id: Long): ReminderOccurrenceEntity?

    @Query("DELETE FROM reminder_occurrences WHERE id = :id")
    suspend fun deleteById(id: Long)
}
