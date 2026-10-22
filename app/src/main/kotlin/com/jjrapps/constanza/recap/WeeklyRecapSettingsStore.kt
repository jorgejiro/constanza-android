package com.jjrapps.constanza.recap

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** weekly-recap S8: on by default, so an install that never opens Settings still gets the recap. */
const val DEFAULT_WEEKLY_RECAP_ENABLED = true

/**
 * weekly-recap S8: whether the Monday recap notification posts. One key in the app's shared
 * Preferences store, the way [com.jjrapps.constanza.reminding.DayReviewSettingsStore] keeps its own.
 * Turning it off silences only the notification; the alarm keeps its weekly cycle (a disabled fire
 * posts nothing and re-arms), so turning it back on needs no rescheduling.
 */
class WeeklyRecapSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val enabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[ENABLED_KEY] ?: DEFAULT_WEEKLY_RECAP_ENABLED }

    suspend fun currentEnabled(): Boolean = enabled.first()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED_KEY] = enabled }
    }

    private companion object {
        val ENABLED_KEY = booleanPreferencesKey("weekly_recap_enabled")
    }
}
