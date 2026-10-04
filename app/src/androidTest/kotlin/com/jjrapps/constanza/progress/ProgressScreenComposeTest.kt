package com.jjrapps.constanza.progress

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Graphite redesign of the Progress screen: the same three computed figures, now laid out as two
 * streak columns (figure plus a plural-aware "days" unit) and a compliance row. Asserts the data
 * still reaches the screen, the unit follows the count, and the chevron still goes back.
 */
@RunWith(AndroidJUnit4::class)
class ProgressScreenComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun bothStreaksAndTheCompliancePercentageAreShown() {
        composeTestRule.setContent {
            ProgressScreen(
                ProgressUiState(
                    habitName = "Walk",
                    habitColorArgb = WALK_COLOUR,
                    currentStreak = 12,
                    bestStreak = 21,
                    complianceRatio = 0.87,
                    loaded = true,
                ),
            )
        }

        composeTestRule.onNodeWithText("Walk").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.progress_current_streak)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.progress_best_streak)).assertIsDisplayed()
        composeTestRule.onNodeWithText("12").assertIsDisplayed()
        composeTestRule.onNodeWithText("21").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.progress_compliance_label)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.progress_compliance, 87)).assertIsDisplayed()
    }

    @Test
    fun theDaysUnitFollowsTheCount() {
        composeTestRule.setContent {
            ProgressScreen(ProgressUiState(habitName = "Walk", currentStreak = 1, bestStreak = 2, loaded = true))
        }
        val one = context.resources.getQuantityString(R.plurals.progress_streak_days, 1)
        val two = context.resources.getQuantityString(R.plurals.progress_streak_days, 2)

        assertTrue("a count of 1 and a count of 2 must read different units", one != two)
        composeTestRule.onNodeWithText(one).assertIsDisplayed()
        composeTestRule.onNodeWithText(two).assertIsDisplayed()
    }

    @Test
    fun theChevronGoesBack() {
        var backs = 0
        composeTestRule.setContent {
            ProgressScreen(ProgressUiState(habitName = "Walk", loaded = true), onBack = { backs++ })
        }

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()

        assertTrue("the back chevron must call onBack once", backs == 1)
    }

    private companion object {
        const val WALK_COLOUR = 0xFF7FA98A.toInt()
    }
}
