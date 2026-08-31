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

    /** Non-`Flow` snapshot for [com.jjrapps.constanza.scheduling.OccurrencePlanner], which runs
     *  once per reschedule trigger rather than observing continuously. */
    @Query("SELECT * FROM habits")
    suspend fun findAllSnapshot(): List<HabitEntity>

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

/**
 * design.md D4/§8.2: `id` doubles as the notification id, the `PendingIntent` request code, and
 * the `WorkManager` unique-work suffix — every write below keys off that same id.
 */
@Dao
interface ReminderOccurrenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(occurrence: ReminderOccurrenceEntity): Long

    @Query("SELECT * FROM reminder_occurrences WHERE id = :id")
    suspend fun findById(id: Long): ReminderOccurrenceEntity?

    @Query(
        "SELECT * FROM reminder_occurrences " +
            "WHERE habitId = :habitId AND slotId = :slotId AND scheduledDate = :scheduledDate",
    )
    suspend fun findByHabitSlotDate(habitId: Long, slotId: Long, scheduledDate: String): ReminderOccurrenceEntity?

    @Query("SELECT * FROM reminder_occurrences WHERE habitId = :habitId")
    suspend fun findByHabitId(habitId: Long): List<ReminderOccurrenceEntity>

    /** [AlarmScheduler.schedule]'s exact/inexact result lands here (reminder-delivery: Exact-Alarm
     *  Permission States) — a single-column update is cheaper than a full-row [upsert]. */
    @Query("UPDATE reminder_occurrences SET exact = :exact WHERE id = :id")
    suspend fun updateExact(id: Long, exact: Boolean)

    @Query("DELETE FROM reminder_occurrences WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reminder_occurrences WHERE habitId = :habitId")
    suspend fun deleteByHabitId(habitId: Long)
}
