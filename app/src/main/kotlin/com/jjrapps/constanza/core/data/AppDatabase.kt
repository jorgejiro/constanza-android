package com.jjrapps.constanza.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
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

/**
 * `version = 1`, `exportSchema = true` from the first commit (design.md §8): every version is
 * diffable and reversible. `fallbackToDestructiveMigration()` is never called anywhere in this
 * app. `room.schemaLocation` is set to `app/schemas/` in `app/build.gradle.kts` (work unit 1);
 * the generated `app/schemas/1.json` is committed alongside this class.
 */
@Database(
    entities = [
        HabitEntity::class,
        ScheduleEntity::class,
        ReminderSlotEntity::class,
        EntryEntity::class,
        ReminderOccurrenceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun reminderSlotDao(): ReminderSlotDao
    abstract fun entryDao(): EntryDao
    abstract fun reminderOccurrenceDao(): ReminderOccurrenceDao

    companion object {
        const val DATABASE_NAME = "constanza.db"
    }
}
