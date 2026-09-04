package com.jjrapps.constanza.localization

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.di.ReminderSettingsDataStoreEntryPoint
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Carried-forward item `system-settings-language-parity-is-one-directional`. The requirement
 * `API 33+ System-Settings Parity, In-App Picker Only Below` names two directions. The app-to-
 * system direction is already covered by `LanguageOverrideStoreInstrumentedTest`, which writes
 * through the picker's own controller and reads `LocaleManager` back. This class covers the
 * reverse: a language chosen from *outside* the app reaching a picker that never wrote it.
 *
 * **The write is deliberately made straight to [LocaleManager], not through
 * [AppLocaleController.set].** Android Settings' own language picker writes that surface and
 * nothing else; going through the controller would have made the test depend on the same writer
 * the in-app picker uses, which is exactly the coupling this scenario is supposed to rule out.
 *
 * The mechanism under test is `LanguageSection`'s
 * [androidx.lifecycle.compose.LifecycleStartEffect], chosen over observing a Flow because
 * `LocaleManager` exposes none (design.md D2). So the *stateful* half is rendered, with a
 * [LanguageSettingsViewModel] this test constructs itself — the parameter's `hiltViewModel()`
 * default is not used, so no Hilt-enabled Activity is needed. `createAndroidComposeRule` rather
 * than the sibling files' `createComposeRule` because the effect needs a real Activity lifecycle
 * that actually reaches `STARTED`.
 *
 * **This is the mirror image of `LanguageOverrideComposeTest`'s API gate, and for the mirror
 * reason.** That class is 31/32-only because `ProvideAppLocale` is a deliberate pass-through above
 * 33; this one is 33+-only because below 33 there is no `LocaleManager` and no system-Settings
 * surface to change a language from in the first place.
 *
 * **Honest limit.** What is proven is the *first* `ON_START` after an external change: the section
 * starts, re-reads, and shows what the system holds. A second change made while the section sits
 * in the background, followed by a stop/start cycle, is NOT covered — writing
 * `applicationLocales` restarts the app's activities, which would tear down the very composition
 * the assertions read from, and a stop/start with no locale change in between would only prove the
 * effect re-fires rather than that it re-reads anything new. Faking that cycle would have proven
 * less than saying so.
 */
@RunWith(AndroidJUnit4::class)
class SystemSettingsLanguageParityComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val controller = AppLocaleController(
        context,
        ReminderSettingsStore(
            EntryPointAccessors.fromApplication(context, ReminderSettingsDataStoreEntryPoint::class.java)
                .reminderSettingsDataStore(),
        ),
    )

    /** See the class KDoc: below 33 there is no system-Settings surface for an app language, so
     *  there is no reverse direction to prove. */
    @Before
    fun onlyWhereAndroidSettingsCanChangeThisAppsLanguage() {
        assumeTrue(
            "system-Settings parity is an API 33+ mechanism; below that the in-app picker is the " +
                "only surface and LanguageOverrideComposeTest covers it",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        )
    }

    @After
    fun tearDown() = runBlocking {
        controller.set(AppLanguage.SystemDefault)
    }

    /**
     * The external language is set in [BeforeClass] rather than here on purpose: it must land
     * before this rule launches the host Activity. `LocaleManager.setApplicationLocales` restarts
     * the app's activities, so writing it after launch would recreate the Activity the compose rule
     * is holding and take the composition with it.
     */
    @Test
    fun aLanguageSetOutsideThePickerIsShownAsSelectedWhenTheSectionStarts() {
        assertEquals(
            "precondition: the external write must have landed on LocaleManager, not through the picker",
            AppLanguage.Spanish,
            runBlocking { controller.current() },
        )
        val spanishLabel = composeTestRule.activity.getString(R.string.settings_language_spanish)
        val systemDefaultLabel =
            composeTestRule.activity.getString(R.string.settings_language_system_default)
        val viewModel = LanguageSettingsViewModel(controller)

        composeTestRule.setContent { LanguageSection(viewModel = viewModel) }

        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(spanishLabel).fetchSemanticsNodes().isNotEmpty() &&
                viewModel.selected.value == AppLanguage.Spanish
        }
        composeTestRule.onNodeWithText(spanishLabel).assertIsSelected()
        // The view model is seeded with SystemDefault, so this is what the picker would still show
        // if the ON_START re-read had never happened.
        composeTestRule.onNodeWithText(systemDefaultLabel).assertIsNotSelected()
    }

    companion object {
        private const val WAIT_TIMEOUT_MS = 5_000L

        /** Runs before the per-test rule launches the host Activity — see the scenario's KDoc. The
         *  SDK guard mirrors the `@Before` assumption: on an API 31/32 leg every scenario is
         *  skipped and `LocaleManager` must not be touched, since the class does not exist there. */
        @BeforeClass
        @JvmStatic
        fun setLanguageFromOutsideTheApp() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            localeManager().applicationLocales = LocaleList.forLanguageTags(AppLanguage.Spanish.tag)
        }

        @AfterClass
        @JvmStatic
        fun clearTheExternalLanguage() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            localeManager().applicationLocales = LocaleList.getEmptyLocaleList()
        }

        private fun localeManager(): LocaleManager =
            ApplicationProvider.getApplicationContext<Context>()
                .getSystemService(LocaleManager::class.java)
    }
}
