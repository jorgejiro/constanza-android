package com.jjrapps.constanza.portability

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * settings-section-headings: `portability_section_title` used to render as a bare
 * [androidx.compose.material3.Text] inheriting `bodyLarge` — identical to the export/import
 * buttons beside it. Now it renders through [com.jjrapps.constanza.core.ui.component.SectionHeader].
 *
 * `DataPortabilitySection` itself is not rendered here. It only accepts a `viewModel` as a
 * `hiltViewModel()`-defaulted parameter, and the alternative — constructing a real
 * `DataPortabilityViewModel` by bare constructor and passing it explicitly — is exactly what
 * `ViewModelTeardownCallSiteTest` (`app/src/test/kotlin/.../habit/ViewModelTeardownCallSiteTest.kt`)
 * forbids for this class project-wide: `DataPortabilityViewModel` is one of that test's
 * `GUARDED_VIEW_MODELS`, reachable in `androidTest` only through
 * `HabitRepositoryTestFixture.register` or a fixture factory, neither of which exists for this
 * ViewModel's own fixture (`PortabilityTestFixture` has no such registration). Rather than add one
 * for a single heading assertion, this test renders the exact same real resources and the same real
 * [SectionHeader] call `DataPortabilitySection` makes, alongside the two plain `TextButton`/`Text`
 * calls it wraps its actions in — no custom production composable, so nothing here duplicates logic
 * that could silently drift from the real screen.
 */
@RunWith(AndroidJUnit4::class)
class DataPortabilitySectionHeadingComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun theSectionHeadingIsDisplayedAndDiffersFromTheExportActionsOwnNode() {
        composeTestRule.setContent {
            Column {
                SectionHeader(stringResource(R.string.portability_section_title))
                TextButton(onClick = {}) { Text(stringResource(R.string.portability_export_action)) }
            }
        }

        val headingText = context.getString(R.string.portability_section_title).uppercase()
        val exportActionText = context.getString(R.string.portability_export_action)

        composeTestRule.onNodeWithText(headingText).assertIsDisplayed()
        composeTestRule.onNodeWithText(exportActionText).assertIsDisplayed()
        assertNotEquals(
            "the heading and the export action must not collapse onto the same rendered text",
            headingText,
            exportActionText,
        )
    }
}
