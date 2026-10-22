package com.jjrapps.constanza.recap

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.theme.ConstanzaTheme
import com.jjrapps.constanza.tracking.TodayScreen
import com.jjrapps.constanza.tracking.TodayUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** weekly-recap S7/S8: Today's header opens the recap, and Settings' row turns the notification
 *  on and off through one tap anywhere on the row. */
class WeeklyRecapEntryComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun todaysHeaderRecapActionOpensTheRecap() {
        var opened = 0
        composeTestRule.setContent {
            ConstanzaTheme {
                TodayScreen(
                    state = TodayUiState(),
                    onToggleExpanded = {},
                    onAnswer = { _, _, _ -> },
                    onManageHabits = {},
                    onOpenWeeklyRecap = { opened++ },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.today_weekly_recap)).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun theSettingsRowShowsTheStateAndFlipsItOnTap() {
        val changes = mutableListOf<Boolean>()
        composeTestRule.setContent {
            ConstanzaTheme { WeeklyRecapSectionContent(enabled = true, onEnabledChange = { changes += it }) }
        }
        val row = composeTestRule.onNodeWithText(context.getString(R.string.settings_weekly_recap_toggle), substring = true)

        row.assertIsOn()
        row.performClick()

        assertEquals(listOf(false), changes)
    }

    @Test
    fun aDisabledRecapShowsTheSwitchOff() {
        composeTestRule.setContent {
            ConstanzaTheme { WeeklyRecapSectionContent(enabled = false, onEnabledChange = {}) }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.settings_weekly_recap_toggle), substring = true)
            .assertIsOff()
    }
}
