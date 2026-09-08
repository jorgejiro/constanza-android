package com.jjrapps.constanza.reminding

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.rememberTimeOfDayFormat
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * day-review, slice C (day-review-notification). Unlike [SnoozeSectionHeadingComposeTest]'s own
 * wall — `SnoozeSettingsScreen` cannot be rendered whole in this suite because its body resolves
 * `hiltViewModel()` with no test seam — [DayReviewSectionContent] itself hits no such wall: it is
 * the presentational half, taking state and callbacks only, with no `hiltViewModel()` call of its
 * own (that lives one level up, in [DayReviewSection]). So this test renders the real production
 * content composable directly, exercising both settings it puts on screen: the review time
 * (through [com.jjrapps.constanza.habit.ReminderTimeField], asserted here by its own label) and
 * the two review-mode rows ([DayReviewModeRow]).
 */
@RunWith(AndroidJUnit4::class)
class DayReviewSectionComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun theSectionShowsItsHeadingTimeFieldAndBothModeRowsDistinctly() {
        var ceilingText = ""
        composeTestRule.setContent {
            // Computed in the same composition, through the same `rememberTimeOfDayFormat` the
            // production content itself uses, so this assertion never hardcodes a 12/24-hour
            // format the device under test might disagree with.
            ceilingText = rememberTimeOfDayFormat().format(REVIEW_TIME_LATEST_MINUTE_OF_DAY)
            DayReviewSectionContent(
                uiState = DayReviewSettingsUiState(),
                onReviewTimeChange = {},
                onFiresEveryNightChange = {},
            )
        }

        val headingText = context.getString(R.string.settings_day_review_section_title).uppercase()
        val timeLabel = context.getString(R.string.settings_day_review_time_label)
        val everyNightLabel = context.getString(R.string.settings_day_review_mode_every_night)
        val onlyIfUnansweredLabel = context.getString(R.string.settings_day_review_mode_only_if_unanswered)
        val supportingText = context.getString(R.string.settings_day_review_time_supporting, ceilingText)

        composeTestRule.onNodeWithText(headingText).assertIsDisplayed()
        composeTestRule.onNodeWithText(timeLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(supportingText).assertIsDisplayed()
        composeTestRule.onNodeWithText(everyNightLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(onlyIfUnansweredLabel).assertIsDisplayed()
        assertNotEquals(
            "the two review-mode rows must not collapse onto the same rendered text",
            everyNightLabel,
            onlyIfUnansweredLabel,
        )
    }
}
