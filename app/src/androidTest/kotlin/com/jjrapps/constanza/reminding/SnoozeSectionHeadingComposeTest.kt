package com.jjrapps.constanza.reminding

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.SectionHeader
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * settings-section-headings. `SnoozeSettingsScreen` cannot be rendered whole in this suite: its
 * body unconditionally calls `DataPortabilitySection()` and `LanguageSection()` with no explicit
 * `viewModel` argument, so both resolve `hiltViewModel()` — and this project has neither the
 * `hilt-android-testing` dependency nor a `HiltAndroidRule`/custom test runner, so nothing in
 * `androidTest` can satisfy that call today. Rather than stand up that infrastructure for one
 * heading assertion, this test renders the exact two production composables the screen's snooze
 * section is built from — [SectionHeader] with the real `settings_snooze_section_title` resource,
 * and [SnoozeDurationRow] (widened to `internal` for exactly this) — side by side, the same way
 * `SnoozeSettingsScreen` places them.
 */
@RunWith(AndroidJUnit4::class)
class SnoozeSectionHeadingComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun theSnoozeHeadingIsDisplayedAndDiffersFromADurationOptionsOwnNode() {
        composeTestRule.setContent {
            Column {
                SectionHeader(stringResource(R.string.settings_snooze_section_title))
                SnoozeDurationRow(SnoozeDuration.TEN_MINUTES, selected = false, onSelect = {})
            }
        }

        val headingText = context.getString(R.string.settings_snooze_section_title).uppercase()
        val optionText = context.getString(R.string.settings_snooze_minutes, SnoozeDuration.TEN_MINUTES.minutes)

        composeTestRule.onNodeWithText(headingText).assertIsDisplayed()
        composeTestRule.onNodeWithText(optionText).assertIsDisplayed()
        assertNotEquals(
            "the heading and the duration option must not collapse onto the same rendered text",
            headingText,
            optionText,
        )
    }
}
