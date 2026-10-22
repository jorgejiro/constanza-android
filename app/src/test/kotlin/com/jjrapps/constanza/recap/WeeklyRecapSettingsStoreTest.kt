package com.jjrapps.constanza.recap

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SETTINGS_FILE_NAME = "weekly_recap_settings_test.preferences_pb"

/** weekly-recap S8: the recap notification is on until the owner turns it off. */
class WeeklyRecapSettingsStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newStore() = WeeklyRecapSettingsStore(
        PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile(SETTINGS_FILE_NAME) }),
    )

    @Test
    fun `the recap notification is enabled before any setting is written`() = runBlocking {
        assertTrue(newStore().currentEnabled())
    }

    @Test
    fun `turning it off persists, and turning it back on persists too`() = runBlocking {
        val store = newStore()

        store.setEnabled(false)
        assertFalse(store.currentEnabled())

        store.setEnabled(true)
        assertTrue(store.currentEnabled())
    }
}
