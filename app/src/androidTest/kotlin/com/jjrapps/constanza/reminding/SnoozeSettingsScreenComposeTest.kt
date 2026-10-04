package com.jjrapps.constanza.reminding

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole Settings screen, graphite layout, drawn with empty [SettingsSections] so no
 * `hiltViewModel()` is reached: the snooze durations are pill chips that keep radio semantics, and
 * the chevron still goes back.
 */
@RunWith(AndroidJUnit4::class)
class SnoozeSettingsScreenComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val noSections = SettingsSections(dayReview = {}, language = {}, data = {})

    private fun minutes(duration: SnoozeDuration) = context.getString(R.string.settings_snooze_minutes, duration.minutes)

    @Test
    fun theSnoozeChipsAreRadioOptionsAndTappingOneReportsIt() {
        var picked: SnoozeDuration? = null
        composeTestRule.setContent {
            SnoozeSettingsScreen(
                current = SnoozeDuration.TWENTY_MINUTES,
                onSelect = { picked = it },
                sections = noSections,
            )
        }

        composeTestRule.onNodeWithText(minutes(SnoozeDuration.TWENTY_MINUTES))
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        composeTestRule.onNodeWithText(minutes(SnoozeDuration.TEN_MINUTES)).assertIsNotSelected()

        composeTestRule.onNodeWithText(minutes(SnoozeDuration.TEN_MINUTES)).performClick()

        assertEquals(SnoozeDuration.TEN_MINUTES, picked)
    }

    @Test
    fun theChevronGoesBack() {
        var backs = 0
        composeTestRule.setContent {
            SnoozeSettingsScreen(
                current = SnoozeDuration.TWENTY_MINUTES,
                onSelect = {},
                onBack = { backs++ },
                sections = noSections,
            )
        }

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()

        assertEquals(1, backs)
    }
}
