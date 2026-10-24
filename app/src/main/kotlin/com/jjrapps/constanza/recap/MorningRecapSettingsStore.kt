package com.jjrapps.constanza.recap

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

const val DEFAULT_MORNING_RECAP_ENABLED = true

/** 07:00, as a minute-of-day. No ceiling or floor: the recap reviews a day the midnight sweep has
 *  already closed, so any time on the following day reads the same finished day. */
const val DEFAULT_MORNING_RECAP_MINUTE_OF_DAY = 7 * 60

/**
 * day-review-and-morning-recap (S6): the morning recap's two settings — whether it posts at all and
 * at what time. Shares the app's one injected [DataStore], like [WeeklyRecapSettingsStore]; turning
 * it off gates only the post, so the alarm keeps re-arming and switching it back on needs no
 * reschedule.
 */
class MorningRecapSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val enabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[ENABLED_KEY] ?: DEFAULT_MORNING_RECAP_ENABLED }

    suspend fun currentEnabled(): Boolean = enabled.first()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED_KEY] = enabled }
    }

    val timeMinuteOfDay: Flow<Int> = dataStore.data.map { prefs ->
        prefs[TIME_MINUTE_OF_DAY_KEY] ?: DEFAULT_MORNING_RECAP_MINUTE_OF_DAY
    }

    suspend fun currentTimeMinuteOfDay(): Int = timeMinuteOfDay.first()

    suspend fun setTimeMinuteOfDay(minuteOfDay: Int) {
        dataStore.edit { it[TIME_MINUTE_OF_DAY_KEY] = minuteOfDay }
    }

    private companion object {
        val ENABLED_KEY = booleanPreferencesKey("morning_recap_enabled")
        val TIME_MINUTE_OF_DAY_KEY = intPreferencesKey("morning_recap_minute_of_day")
    }
}
