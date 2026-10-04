package com.jjrapps.constanza.reminding

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.rememberTimeOfDayFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * day-review, slice C (day-review-notification), graphite layout. [DayReviewSectionContent] is the
 * presentational half (no `hiltViewModel()` of its own), so this test renders the real production
 * content directly: the heading, the review-time row (through
 * [com.jjrapps.constanza.habit.ReminderTimeField], asserted by its own label) with its ceiling
 * note, and the "When to notify" row, whose dialog holds the two named review modes.
 */
@RunWith(AndroidJUnit4::class)
class DayReviewSectionComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val modeLabel = context.getString(R.string.settings_day_review_mode_label)
    private val everyNightLabel = context.getString(R.string.settings_day_review_mode_every_night)
    private val onlyIfUnansweredLabel = context.getString(R.string.settings_day_review_mode_only_if_unanswered)

    private fun inDialog(text: String) = composeTestRule.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test
    fun theSectionShowsItsHeadingTimeRowAndTheCurrentMode() {
        var ceilingText = ""
        composeTestRule.setContent {
            // Computed through the same `rememberTimeOfDayFormat` the production content uses, so
            // this never hardcodes a 12/24-hour format the device might disagree with.
            ceilingText = rememberTimeOfDayFormat().format(REVIEW_TIME_LATEST_MINUTE_OF_DAY)
            DayReviewSectionContent(
                uiState = DayReviewSettingsUiState(reviewFiresEveryNight = false),
                onReviewTimeChange = {},
                onFiresEveryNightChange = {},
            )
        }

        val headingText = context.getString(R.string.settings_day_review_section_title).uppercase()
        val timeLabel = context.getString(R.string.settings_day_review_time_label)
        val supportingText = context.getString(R.string.settings_day_review_time_supporting, ceilingText)

        composeTestRule.onNodeWithText(headingText).assertIsDisplayed()
        composeTestRule.onNodeWithText(timeLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(supportingText).assertIsDisplayed()
        // The mode row carries its title and the current mode as one merged node.
        composeTestRule.onNode(hasText(modeLabel) and hasText(onlyIfUnansweredLabel)).assertIsDisplayed()
        composeTestRule.onNodeWithText(everyNightLabel).assertDoesNotExist()
    }

    @Test
    fun theModeRowOpensBothNamedOptionsAndChoosingOneReportsIt() {
        var reported: Boolean? = null
        composeTestRule.setContent {
            DayReviewSectionContent(
                uiState = DayReviewSettingsUiState(reviewFiresEveryNight = false),
                onReviewTimeChange = {},
                onFiresEveryNightChange = { reported = it },
            )
        }

        composeTestRule.onNodeWithText(modeLabel).performClick()

        inDialog(everyNightLabel).assertIsDisplayed().assertIsNotSelected()
        inDialog(onlyIfUnansweredLabel).assertIsDisplayed().assertIsSelected()
        assertNotEquals(
            "the two review modes must not collapse onto the same rendered text",
            everyNightLabel,
            onlyIfUnansweredLabel,
        )

        inDialog(everyNightLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, reported)
        inDialog(everyNightLabel).assertDoesNotExist()
    }
}
