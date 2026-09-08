package com.jjrapps.constanza.localization

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * settings-section-headings: `settings_language_section_title` used to render as a bare
 * [androidx.compose.material3.Text] inheriting `bodyLarge` — identical to the radio rows beside it.
 * Now it renders through [com.jjrapps.constanza.core.ui.component.SectionHeader].
 *
 * [LanguageSectionContent] is the presentational half (this codebase's container/presentational
 * split), so `createComposeRule()` suffices — no `ViewModel`, no Hilt — matching
 * [LanguageOverrideComposeTest]'s own established precedent for this composable.
 */
@RunWith(AndroidJUnit4::class)
class LanguageSectionHeadingComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun theSectionHeadingIsDisplayedAndDiffersFromALanguageOptionsOwnNode() {
        composeTestRule.setContent {
            LanguageSectionContent(selected = AppLanguage.SystemDefault, onSelect = {})
        }

        val headingText = context.getString(R.string.settings_language_section_title).uppercase()
        val systemDefaultText = context.getString(R.string.settings_language_system_default)

        composeTestRule.onNodeWithText(headingText).assertIsDisplayed()
        composeTestRule.onNodeWithText(systemDefaultText).assertIsDisplayed()
        assertNotEquals(
            "the heading and the System default option must not collapse onto the same rendered text",
            headingText,
            systemDefaultText,
        )
    }
}
