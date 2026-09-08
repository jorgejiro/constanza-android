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
 * day-review, slice A (day-review-data): the default time for the once-a-day "review your day"
 * notification — 23:30, as a minute-of-day. This is deliberately the LAST CHANCE before the
 * midnight sweep turns any still-`UNKNOWN` slot into `MISSED`; it is not an arbitrary "late
 * evening" pick. Do not move it without understanding that relationship (see
 * [REVIEW_TIME_LATEST_MINUTE_OF_DAY]).
 */
const val DEFAULT_REVIEW_TIME_MINUTE_OF_DAY = 23 * 60 + 30

/**
 * day-review, slice A: the latest minute-of-day the review notification's time may ever be
 * persisted as — 23:45. This exists to keep the notification strictly BEFORE the midnight sweep
 * that turns unanswered slots into `MISSED`, not for tidiness: a "review your day" notification
 * that could fire after — or right at — that sweep would be reviewing a day already partly
 * rewritten. Enforced as a data invariant in [DayReviewSettingsStore] itself, on both read and
 * write, so a value persisted by an older build can never escape it either.
 */
const val REVIEW_TIME_LATEST_MINUTE_OF_DAY = 23 * 60 + 45

/** day-review, slice A: the default review mode — fires every night as a closing ritual, rather
 *  than only when something is still unanswered. */
const val DEFAULT_REVIEW_FIRES_EVERY_NIGHT = true

/**
 * day-review's two settings (slice A, day-review-data): the once-a-day "review your day"
 * notification's time ([reviewTimeMinuteOfDay]/[setReviewTimeMinuteOfDay], default
 * [DEFAULT_REVIEW_TIME_MINUTE_OF_DAY]) and whether it fires every night or only when something is
 * still unanswered ([reviewFiresEveryNight]/[setReviewFiresEveryNight], default
 * [DEFAULT_REVIEW_FIRES_EVERY_NIGHT]). This slice stores both; slices B/C schedule and show the
 * notification.
 *
 * A separate class from [ReminderSettingsStore] on purpose, even though both share the exact same
 * injected [DataStore] instance ([com.jjrapps.constanza.core.di.DataStoreModule]) — nothing about
 * the underlying file is split, only the Kotlin surface. Folding these two settings into
 * [ReminderSettingsStore] would have pushed it past detekt's `TooManyFunctions` ceiling; this
 * codebase's answer to that is to split the class, never to suppress the rule (`TodayScreen.kt`'s
 * `TodayDateBar.kt`/`TodayBanners.kt`/`TodayAddHabitAction.kt` split is the precedent). A second
 * benefit beyond the lint rule: slices B/C now depend on a class that owns exactly the day-review
 * feature, not one that also owns snooze duration and the notification-permission latch.
 */
class DayReviewSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** day-review's notification time, as a minute-of-day. Clamped to
     *  [REVIEW_TIME_LATEST_MINUTE_OF_DAY] on read, so a value persisted by an older build (before
     *  the ceiling existed, or before it was lowered) still comes back safely before the midnight
     *  sweep rather than being trusted verbatim. */
    val reviewTimeMinuteOfDay: Flow<Int> = dataStore.data.map { prefs ->
        clampReviewTimeMinuteOfDay(prefs[REVIEW_TIME_MINUTE_OF_DAY_KEY] ?: DEFAULT_REVIEW_TIME_MINUTE_OF_DAY)
    }

    suspend fun currentReviewTimeMinuteOfDay(): Int = reviewTimeMinuteOfDay.first()

    /** Clamped on write too, not only on read — see [REVIEW_TIME_LATEST_MINUTE_OF_DAY]'s KDoc: the
     *  ceiling is a data invariant of this store, not a UI-only guard the store merely trusts. */
    @Suppress("RedundantSuspendModifier")
    suspend fun setReviewTimeMinuteOfDay(minuteOfDay: Int) {
        dataStore.edit { it[REVIEW_TIME_MINUTE_OF_DAY_KEY] = clampReviewTimeMinuteOfDay(minuteOfDay) }
    }

    /** `true` (the default) fires day-review every night as a closing ritual; `false` fires only
     *  when something is still unanswered. */
    val reviewFiresEveryNight: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[REVIEW_FIRES_EVERY_NIGHT_KEY] ?: DEFAULT_REVIEW_FIRES_EVERY_NIGHT
    }

    suspend fun currentReviewFiresEveryNight(): Boolean = reviewFiresEveryNight.first()

    @Suppress("RedundantSuspendModifier")
    suspend fun setReviewFiresEveryNight(fireEveryNight: Boolean) {
        dataStore.edit { it[REVIEW_FIRES_EVERY_NIGHT_KEY] = fireEveryNight }
    }

    internal companion object {
        val REVIEW_TIME_MINUTE_OF_DAY_KEY = intPreferencesKey("review_time_minute_of_day")
        val REVIEW_FIRES_EVERY_NIGHT_KEY = booleanPreferencesKey("review_fires_every_night")

        fun clampReviewTimeMinuteOfDay(minuteOfDay: Int): Int =
            minuteOfDay.coerceAtMost(REVIEW_TIME_LATEST_MINUTE_OF_DAY)
    }
}
