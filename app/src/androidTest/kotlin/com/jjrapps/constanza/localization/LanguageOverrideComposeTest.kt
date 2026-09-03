package com.jjrapps.constanza.localization

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.habit.HabitEditorUiState
import com.jjrapps.constanza.habit.ScheduleSection
import com.jjrapps.constanza.domain.model.Schedule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression protection for design.md's Findings A and B. Both were established by reading the
 * compose-ui sources, not the documentation, and neither is guaranteed by a public API contract —
 * so both are pinned here rather than trusted.
 *
 * - **Finding A** — `stringResource` resolves through `LocalResources`, a *computed* local that is
 *   never explicitly provided and that recomputes from `LocalContext`/`LocalConfiguration` at every
 *   read site. [ProvideAppLocale] provides exactly those two and deliberately not `LocalResources`.
 *   If a future compose-ui version starts providing `LocalResources` explicitly, every string in
 *   the app silently reverts to the device language; [spanishOverrideReachesStringResource] is what
 *   notices.
 * - **Finding B** — `LocalLocale` is fed from the Activity's configuration through a local that is
 *   private to compose-ui, so a Compose-root override cannot reach it and it cannot be provided
 *   either. `ScheduleEditors` therefore reads `LocalConfiguration.current.locales[0]`. Its KDoc used
 *   to argue the opposite; a future reader following that old reasoning would revert the line and
 *   [spanishOverrideReachesDayOfWeekNames] fails if they do.
 *
 * **These tests are API-31/32 only, and that is a statement about the mechanism, not a shortcut.**
 * [ProvideAppLocale] returns `content()` untouched on API 33+ by design (D1): there the platform's
 * own `LocaleManager` override is already applied to the process before any composable runs, so
 * there is nothing for the Compose root to wrap. A test that merely calls `ProvideAppLocale`
 * without also driving `LocaleManager` therefore renders in the device language on 33+ — which is
 * correct behaviour, not a defect. An earlier revision of this file asserted the override on both
 * legs on the assumption that "the same assertions hold for a different reason on 33+"; the API 37
 * leg failed and disproved it. The assumption was wrong, not the code.
 *
 * The 33+ path has its own coverage that does not run through Compose at all:
 * `AppLocaleInstrumentedTest` for the wrapping contract and
 * `SpanishColdProcessNotificationInstrumentedTest` for the reminder path, both of which drive
 * `LocaleManager` directly.
 *
 * Only presentational composables are rendered, per this codebase's container/presentational split,
 * so `createComposeRule()` suffices and no Hilt-enabled Activity is needed.
 */
@RunWith(AndroidJUnit4::class)
class LanguageOverrideComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** See the class KDoc: below 33 the Compose root does the wrapping and these assertions are
     *  meaningful; on 33+ `ProvideAppLocale` is deliberately a pass-through and the platform, not
     *  composition, carries the override. */
    @Before
    fun onlyWhereTheComposeRootIsTheMechanism() {
        assumeTrue(
            "ProvideAppLocale only wraps below API 33; the 33+ path is covered by AppLocaleInstrumentedTest",
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU,
        )
    }

    /** Finding A: static copy resolved through `stringResource` follows the override. */
    @Test
    fun spanishOverrideReachesStringResource() {
        composeTestRule.setContent {
            ProvideAppLocale(AppLanguage.Spanish) {
                LanguageSectionContent(selected = AppLanguage.SystemDefault, onSelect = {})
            }
        }

        composeTestRule.onNodeWithText(SPANISH_SECTION_TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText(SPANISH_SYSTEM_DEFAULT).assertIsDisplayed()
    }

    /** English is the base `values/` folder, which doubles as the universal fallback. */
    @Test
    fun englishOverrideReachesStringResource() {
        composeTestRule.setContent {
            ProvideAppLocale(AppLanguage.English) {
                LanguageSectionContent(selected = AppLanguage.SystemDefault, onSelect = {})
            }
        }

        composeTestRule.onNodeWithText(ENGLISH_SECTION_TITLE).assertIsDisplayed()
    }

    /**
     * The language names inside the picker are deliberately NOT translated — each names itself in
     * its own language, so a user hunting for their language can always read it. Under a Spanish
     * override, "English" must still read "English".
     */
    @Test
    fun eachLanguageNamesItselfInItsOwnLanguage() {
        composeTestRule.setContent {
            ProvideAppLocale(AppLanguage.Spanish) {
                LanguageSectionContent(selected = AppLanguage.Spanish, onSelect = {})
            }
        }

        composeTestRule.onNodeWithText(ENGLISH_LANGUAGE_NAME).assertIsDisplayed()
        composeTestRule.onNodeWithText(SPANISH_LANGUAGE_NAME).assertIsDisplayed()
    }

    /**
     * Finding B: day-of-week names are NOT `stringResource` output — they come from
     * `DayOfWeek.getDisplayName`, fed by whichever locale the picker reads. Expected values are
     * computed with the same JDK API rather than hardcoded, for the reason `TimeOfDayFormatTest`
     * already documents: pinning a CLDR spelling makes the test a statement about the bundled data
     * instead of about this code. The English name is asserted absent so a picker that ignored the
     * override entirely could not pass.
     */
    @Test
    fun spanishOverrideReachesDayOfWeekNames() {
        composeTestRule.setContent {
            ProvideAppLocale(AppLanguage.Spanish) {
                ScheduleSection(
                    state = HabitEditorUiState(schedule = Schedule.Weekly(dayOfWeek = DayOfWeek.MONDAY)),
                    onScheduleParamChange = {},
                    onSlotAction = {},
                )
            }
        }

        val spanishMonday = DayOfWeek.MONDAY.getDisplayName(TextStyle.SHORT, SPAIN)
        val englishMonday = DayOfWeek.MONDAY.getDisplayName(TextStyle.SHORT, Locale.US)
        composeTestRule.onNodeWithText(spanishMonday).assertIsDisplayed()
        composeTestRule.onNodeWithText(englishMonday).assertDoesNotExist()
    }

    /**
     * `StringResourceParityTest` is a static XML parser: it proves both `one` and `other` items
     * EXIST in `values-es/` with matching format specifiers, and nothing more. It cannot prove that
     * Android's CLDR plural rules actually SELECT them at runtime — a different claim, and the one
     * a user meets. This test makes that claim separately, because a document describing its test
     * accurately can still overclaim which scenarios the test satisfies.
     *
     * The two renderings are asserted to differ and to carry their own count rather than being
     * compared against pinned Spanish sentences, so a future retranslation does not break a test
     * that is about plural selection, not about wording.
     */
    @Test
    fun spanishPluralsSelectBothQuantitiesAtRuntime() {
        composeTestRule.setContent {
            ProvideAppLocale(AppLanguage.Spanish) {
                Column {
                    Text(pluralStringResource(R.plurals.habit_delete_dialog_body, 1, 1), Modifier.testTag(ONE_TAG))
                    Text(pluralStringResource(R.plurals.habit_delete_dialog_body, 2, 2), Modifier.testTag(OTHER_TAG))
                }
            }
        }

        val one = composeTestRule.onNodeWithTag(ONE_TAG).fetchSemanticsNode()
            .config[SemanticsProperties.Text].first().text
        val other = composeTestRule.onNodeWithTag(OTHER_TAG).fetchSemanticsNode()
            .config[SemanticsProperties.Text].first().text

        assertTrue("the one-form must carry its own count, got \"$one\"", one.contains("1"))
        assertTrue("the other-form must carry its own count, got \"$other\"", other.contains("2"))
        assertNotEquals("quantity 1 and quantity 2 must select different plural items", one, other)
        // Spanish, not the English fallback: the singular says "eliminará", the plural "eliminarán".
        assertTrue("expected Spanish plural copy, got \"$one\"", one.startsWith("Se elimina"))
    }

    private companion object {
        const val ONE_TAG = "plural-one"
        const val OTHER_TAG = "plural-other"
        const val SPANISH_SECTION_TITLE = "Idioma"
        const val ENGLISH_SECTION_TITLE = "Language"
        const val SPANISH_SYSTEM_DEFAULT = "Predeterminado del sistema"
        const val ENGLISH_LANGUAGE_NAME = "English"
        const val SPANISH_LANGUAGE_NAME = "Español"
        val SPAIN: Locale = Locale.forLanguageTag("es-ES")
    }
}
