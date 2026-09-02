package com.jjrapps.constanza.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
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

/**
 * first-run-onboarding design.md §8.1's seeding mechanism, corrected during `sdd-apply` (Unit B):
 * the design's own snippet declared this `@EntryPoint` inside `androidTest/CoreFlowTestFixture.kt`,
 * but Hilt's aggregating KSP step processes `main` and `androidTest` as separate compilations for a
 * plain (non-`HiltAndroidTest`) instrumented app — an entry point declared only in `androidTest`
 * never reaches the `main`-generated `SingletonComponent` implementation, and
 * `EntryPointAccessors.fromApplication` then throws `ClassCastException` at runtime because the
 * real component class does not implement it. Measured directly, not assumed: the exact matrix run
 * this fixture exists for failed every `CoreFlowE2ETest` method with
 * `ClassCastException: Cannot cast ...SingletonCImpl to ...ReminderSettingsDataStoreEntryPoint`
 * until this declaration moved here, into `main`.
 *
 * `internal`, not `private`, for the same reason [ReminderSettingsStore]'s companion object is
 * `internal`: `androidTest` already has compile-time visibility into `main`'s `internal`
 * declarations in this module's Gradle source-set setup, and a rename here should break the
 * fixture at compile time rather than silently seeding through a stale accessor.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ReminderSettingsDataStoreEntryPoint {
    fun reminderSettingsDataStore(): DataStore<Preferences>
}
