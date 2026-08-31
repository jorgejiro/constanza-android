package com.jjrapps.constanza.reminding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * design.md §14 (`reminding/` snooze settings): task 5.6's snooze-duration preference, default
 * [SnoozeDuration.DEFAULT], and task 5.2's "already asked once" flag that lets
 * [NotificationPermission] approximate the system's permanently-blocked state. Both live in the
 * same [DataStore] instance ([com.jjrapps.constanza.core.di.DataStoreModule]) since each is a
 * single scalar key with no schema in common to protect.
 *
 * [currentSnoozeDuration] is the one-shot suspend read work unit 5-ii's `SnoozeWorker` needs;
 * [snoozeDuration] is the continuously-observed [Flow] the future settings screen (task 6b.5)
 * binds to.
 */
class ReminderSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val snoozeDuration: Flow<SnoozeDuration> = dataStore.data.map { prefs ->
        SnoozeDuration.fromMinutes(prefs[SNOOZE_DURATION_MINUTES_KEY] ?: SnoozeDuration.DEFAULT.minutes)
    }

    suspend fun currentSnoozeDuration(): SnoozeDuration = snoozeDuration.first()

    // RedundantSuspendModifier is suppressed on these three functions, not disabled project-wide:
    // `:app:detektMain`'s hand-rolled task cannot resolve `DataStore<Preferences>.edit`'s suspend
    // signature from the `datastore-preferences` AAR — the exact same class of type-resolution gap
    // this task's own KDoc already documents for `ForbiddenMethodCall` (app/build.gradle.kts).
    // Proven a false positive, not guessed: removing `suspend` here fails `compileDebugKotlin`
    // with "Suspend function ... can only be called from a coroutine or another suspend function"
    // at every one of these three call sites.
    @Suppress("RedundantSuspendModifier")
    suspend fun setSnoozeDuration(duration: SnoozeDuration) {
        dataStore.edit { it[SNOOZE_DURATION_MINUTES_KEY] = duration.minutes }
    }

    @Suppress("RedundantSuspendModifier")
    suspend fun hasRequestedNotificationPermission(): Boolean =
        dataStore.data.map { it[REQUESTED_NOTIFICATION_PERMISSION_KEY] ?: false }.first()

    @Suppress("RedundantSuspendModifier")
    suspend fun recordRequestedNotificationPermission() {
        dataStore.edit { it[REQUESTED_NOTIFICATION_PERMISSION_KEY] = true }
    }

    private companion object {
        val SNOOZE_DURATION_MINUTES_KEY = intPreferencesKey("snooze_duration_minutes")
        val REQUESTED_NOTIFICATION_PERMISSION_KEY = booleanPreferencesKey("requested_notification_permission")
    }
}
