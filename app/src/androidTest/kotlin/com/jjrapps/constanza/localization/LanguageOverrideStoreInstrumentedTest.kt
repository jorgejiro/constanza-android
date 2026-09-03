package com.jjrapps.constanza.localization

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.core.di.ReminderSettingsDataStoreEntryPoint
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * app-localization: Three-State Language Override / Override Persistence Across Process Death
 * (design.md D1, D7). [AppLanguage.SystemDefault] removes the persisted tag (below API 33) or
 * empties `applicationLocales` (API 33+) rather than storing a third value — the split is exactly
 * [AppLocaleController]'s (design.md D1), so this test straddles it with
 * `Build.VERSION.SDK_INT` branches, the same complementary-per-leg pattern already used elsewhere
 * in this suite.
 *
 * "Survives store re-creation" is proven with a brand-new [ReminderSettingsStore] /
 * [AppLocaleController] pair over the same real [android.content.SharedPreferences]-backed
 * DataStore file, reached through [ReminderSettingsDataStoreEntryPoint] exactly as
 * `CoreFlowTestFixture` does — not a second in-memory instance.
 */
@RunWith(AndroidJUnit4::class)
class LanguageOverrideStoreInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dataStore =
        EntryPointAccessors.fromApplication(context, ReminderSettingsDataStoreEntryPoint::class.java)
            .reminderSettingsDataStore()
    private val controller = AppLocaleController(context, ReminderSettingsStore(dataStore))

    @Before
    fun clearAnyPriorOverride() = runBlocking {
        controller.set(AppLanguage.SystemDefault)
    }

    @After
    fun tearDown() = runBlocking {
        controller.set(AppLanguage.SystemDefault)
    }

    @Test
    fun settingSpanishStoresTheTag() = runBlocking {
        controller.set(AppLanguage.Spanish)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            assertEquals("es", manager.applicationLocales.toLanguageTags())
        } else {
            assertEquals("es", ReminderSettingsStore(dataStore).currentLanguageTag())
        }
        assertEquals(AppLanguage.Spanish, controller.current())
    }

    @Test
    fun settingSystemDefaultAfterAnOverrideRemovesItRatherThanStoringIt() = runBlocking {
        controller.set(AppLanguage.Spanish)

        controller.set(AppLanguage.SystemDefault)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            assertTrue(
                "SystemDefault must empty applicationLocales, not store a third value",
                manager.applicationLocales.toLanguageTags().isEmpty(),
            )
        } else {
            assertNull(
                "SystemDefault must remove the persisted key, not store a third value",
                ReminderSettingsStore(dataStore).currentLanguageTag(),
            )
        }
        assertEquals(AppLanguage.SystemDefault, controller.current())
    }

    @Test
    fun theOverrideSurvivesAFreshControllerAndStoreInstance() = runBlocking {
        controller.set(AppLanguage.Spanish)

        val recreatedController = AppLocaleController(context, ReminderSettingsStore(dataStore))

        assertEquals(AppLanguage.Spanish, recreatedController.current())
    }
}
