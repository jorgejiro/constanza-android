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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            // Task 2.4 (design.md decision 3, hard blocker C4): without this, Room throws
            // `IllegalStateException: A migration from 1 to 2 was required but not found` at
            // first open on every existing install.
            .addMigrations(AppMigrations.MIGRATION_1_2)
            .build()

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
