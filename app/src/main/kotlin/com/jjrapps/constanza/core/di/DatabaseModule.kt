package com.jjrapps.constanza.core.di

import android.content.Context
import androidx.room.Room
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.dao.HabitDao
import com.jjrapps.constanza.core.data.dao.ReminderOccurrenceDao
import com.jjrapps.constanza.core.data.dao.ReminderSlotDao
import com.jjrapps.constanza.core.data.dao.ScheduleDao
import com.jjrapps.constanza.core.data.migration.AppMigrations
import com.jjrapps.constanza.core.data.migration.PreMigrationSnapshotWriter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Wires [AppDatabase] and its DAOs into the Hilt graph (design.md D5). */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        // Task 2.4/3.7 (design.md decision 3, hard blocker C4): without every migration in this
        // chain, Room throws `IllegalStateException: A migration from N to N+1 was required but
        // not found` at first open on any install stuck at that version.
        //
        // Task 3.3: `AppMigrations` stays an `object` (see its KDoc), so `filesDir` cannot be a
        // constructor parameter on it — the writer is built here, at the one call site that has a
        // `Context`, and handed into both factory functions. One shared instance: both migrations
        // write to the same `files/pre-migration/` directory, and each names its snapshot from
        // `db.version` (design.md D3), so they never collide.
        val writer = PreMigrationSnapshotWriter(context.filesDir)
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(AppMigrations.migration1To2(writer), AppMigrations.migration2To3(writer))
            .build()
    }

    @Provides
    fun provideHabitDao(database: AppDatabase): HabitDao = database.habitDao()

    @Provides
    fun provideScheduleDao(database: AppDatabase): ScheduleDao = database.scheduleDao()

    @Provides
    fun provideReminderSlotDao(database: AppDatabase): ReminderSlotDao = database.reminderSlotDao()

    @Provides
    fun provideEntryDao(database: AppDatabase): EntryDao = database.entryDao()

    @Provides
    fun provideReminderOccurrenceDao(database: AppDatabase): ReminderOccurrenceDao =
        database.reminderOccurrenceDao()
}
