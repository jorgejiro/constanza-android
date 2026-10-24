package com.jjrapps.constanza.recap

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MorningRecapSectionComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val toggle = context.getString(R.string.settings_morning_recap_toggle)
    private val timeLabel = context.getString(R.string.settings_morning_recap_time_label)

    @Test
    fun anEnabledRecapShowsTheSwitchOnAndTheTimeRowAndFlipsOnTap() {
        val changes = mutableListOf<Boolean>()
        composeTestRule.setContent {
            MorningRecapSectionContent(
                uiState = MorningRecapSettingsUiState(enabled = true),
                onEnabledChange = { changes += it },
                onTimeChange = {},
            )
        }

        composeTestRule.onNodeWithText(timeLabel).assertIsDisplayed()
        val row = composeTestRule.onNodeWithText(toggle, substring = true)
        row.assertIsOn()
        row.performClick()

        assertEquals(listOf(false), changes)
    }

    @Test
    fun aDisabledRecapShowsTheSwitchOffAndHidesTheTimeRow() {
        composeTestRule.setContent {
            MorningRecapSectionContent(
                uiState = MorningRecapSettingsUiState(enabled = false),
                onEnabledChange = {},
                onTimeChange = {},
            )
        }

        composeTestRule.onNodeWithText(toggle, substring = true).assertIsOff()
        composeTestRule.onNodeWithText(timeLabel).assertDoesNotExist()
    }
}
