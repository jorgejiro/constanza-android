package com.jjrapps.constanza.recap

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MorningRecapSettingsStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newStore() = MorningRecapSettingsStore(
        PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("morning_recap_test.preferences_pb") }),
    )

    @Test
    fun `the morning recap is enabled at 07-00 by default`() = runBlocking {
        val store = newStore()

        assertTrue(store.currentEnabled())
        assertEquals(7 * 60, store.currentTimeMinuteOfDay())
    }

    @Test
    fun `setEnabled persists and is read back`() = runBlocking {
        val store = newStore()

        store.setEnabled(false)

        assertFalse(store.currentEnabled())
    }

    @Test
    fun `setTimeMinuteOfDay persists and is read back`() = runBlocking {
        val store = newStore()

        store.setTimeMinuteOfDay(8 * 60 + 15)

        assertEquals(8 * 60 + 15, store.currentTimeMinuteOfDay())
    }
}
