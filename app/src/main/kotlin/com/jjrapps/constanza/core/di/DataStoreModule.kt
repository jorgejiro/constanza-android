package com.jjrapps.constanza.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val REMINDER_SETTINGS_DATASTORE_NAME = "reminder_settings"

private val Context.reminderSettingsDataStore by preferencesDataStore(name = REMINDER_SETTINGS_DATASTORE_NAME)

/** Wires the single DataStore Preferences file backing task 5.6's snooze duration and task 5.2's
 *  "requested notification permission before" flag ([ReminderSettingsStore]) into the Hilt graph
 *  (design.md D5, §14). One file, not two — both are single scalar reads/writes with no shared
 *  schema risk between them. */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideReminderSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.reminderSettingsDataStore
}
