package com.jjrapps.constanza.reminding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SNOOZE_SETTINGS_FILE_NAME = "reminder_settings_test.preferences_pb"

/**
 * reminder-response: Snooze Configuration and Re-arm (task 5.6). Uses a real
 * `DataStore<Preferences>` backed by a JVM temp file — no Android dependency, no Robolectric,
 * matching this project's `runBlocking`-only JVM test convention (`OccurrencePlannerTest`).
 */
class ReminderSettingsStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newStore() = ReminderSettingsStore(
        PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile(SNOOZE_SETTINGS_FILE_NAME) }),
    )

    @Test
    fun `default snooze duration is 20 minutes before any setting is written`() = runBlocking {
        assertEquals(SnoozeDuration.TWENTY_MINUTES, newStore().currentSnoozeDuration())
    }

    @Test
    fun `setSnoozeDuration persists and is read back by currentSnoozeDuration`() = runBlocking {
        val store = newStore()

        store.setSnoozeDuration(SnoozeDuration.TWO_HOURS)

        assertEquals(SnoozeDuration.TWO_HOURS, store.currentSnoozeDuration())
    }

    @Test
    fun `hasRequestedNotificationPermission defaults false and flips permanently after recording`() = runBlocking {
        val store = newStore()
        assertFalse(store.hasRequestedNotificationPermission())

        store.recordRequestedNotificationPermission()

        assertTrue(store.hasRequestedNotificationPermission())
    }
}
