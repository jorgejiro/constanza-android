package com.jjrapps.constanza.reminding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private const val DAY_REVIEW_SETTINGS_FILE_NAME = "day_review_settings_test.preferences_pb"

/**
 * day-review, slice A (day-review-data): [DayReviewSettingsStore]'s two settings, exercised the
 * same way [ReminderSettingsStoreTest] exercises its own — a real `DataStore<Preferences>` backed
 * by a JVM temp file, no Android dependency, no Robolectric.
 */
class DayReviewSettingsStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newStore() = DayReviewSettingsStore(
        PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile(DAY_REVIEW_SETTINGS_FILE_NAME) }),
    )

    @Test
    fun `default review time is 23-30 before any setting is written`() = runBlocking {
        assertEquals(DEFAULT_REVIEW_TIME_MINUTE_OF_DAY, newStore().currentReviewTimeMinuteOfDay())
    }

    @Test
    fun `default review mode is fires-every-night before any setting is written`() = runBlocking {
        assertEquals(DEFAULT_REVIEW_FIRES_EVERY_NIGHT, newStore().currentReviewFiresEveryNight())
    }

    @Test
    fun `setReviewTimeMinuteOfDay persists and is read back by currentReviewTimeMinuteOfDay`() = runBlocking {
        val store = newStore()

        store.setReviewTimeMinuteOfDay(20 * 60)

        assertEquals(20 * 60, store.currentReviewTimeMinuteOfDay())
    }

    @Test
    fun `setReviewFiresEveryNight persists and is read back by currentReviewFiresEveryNight`() = runBlocking {
        val store = newStore()

        store.setReviewFiresEveryNight(false)

        assertFalse(store.currentReviewFiresEveryNight())
    }

    @Test
    fun `setReviewTimeMinuteOfDay clamps a value past 23-45 down to the ceiling`() = runBlocking {
        val store = newStore()

        store.setReviewTimeMinuteOfDay(23 * 60 + 59)

        assertEquals(REVIEW_TIME_LATEST_MINUTE_OF_DAY, store.currentReviewTimeMinuteOfDay())
    }

    /** The clamp is a data invariant of the store, not only of its own setter: a value written
     *  directly (standing in for an older build that persisted before the ceiling existed, or
     *  before it was lowered) must still come back clamped on read. */
    @Test
    fun `a value written above the ceiling by an older build reads back clamped`() = runBlocking {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile(DAY_REVIEW_SETTINGS_FILE_NAME) },
        )
        dataStore.edit { it[DayReviewSettingsStore.REVIEW_TIME_MINUTE_OF_DAY_KEY] = 23 * 60 + 59 }

        val store = DayReviewSettingsStore(dataStore)

        assertEquals(REVIEW_TIME_LATEST_MINUTE_OF_DAY, store.currentReviewTimeMinuteOfDay())
    }
}
