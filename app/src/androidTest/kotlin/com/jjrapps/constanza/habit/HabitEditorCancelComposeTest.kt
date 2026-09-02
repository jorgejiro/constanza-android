package com.jjrapps.constanza.habit

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Carried-forward item `habit-editor-has-no-cancel-affordance`: the editor could not be backed out
 * of at all. The top bar had no navigation icon, `onDone` only fired after a successful save, and
 * nothing in the app handled system back — so the gesture reached the Activity default and closed
 * the app. These scenarios hold both exits open and hold the confirmation honest in both
 * directions: silent when nothing was typed, blocking when something was.
 *
 * **[createAndroidComposeRule] rather than the sibling files' `createComposeRule`**, and
 * deliberately: this is the only test here that needs the host Activity itself, because the system
 * back gesture is dispatched through its `OnBackPressedDispatcher`. Asserting the icon alone would
 * leave the half that actually loses a user's work — the hardware/gesture back — unproven, and the
 * two paths only share behaviour because they share one `requestBack` lambda in
 * [HabitEditorRoute]. `ComponentActivity` is the same host `createComposeRule` launches (registered
 * by the `ui-test-manifest` debug dependency), so nothing else about the setup changes.
 *
 * Every scenario checks the callback flag rather than a screen change: this test hosts
 * [HabitEditorRoute] directly, with no navigation host above it, so "left the editor" is observable
 * exactly as `onBack` having fired — which is also all [com.jjrapps.constanza.core.ui.MainActivity]
 * itself observes.
 */
@RunWith(AndroidJUnit4::class)
class HabitEditorCancelComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fixture: HabitRepositoryTestFixture

    @Before
    fun setUp() {
        fixture = HabitRepositoryTestFixture(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() = fixture.close()

    /** Built outside `setContent` for the reason `HabitEditorComposeTest` documents: a view model
     *  constructed in the content lambda is rebuilt on every recomposition and the editor would
     *  silently lose the very state these scenarios are about. Built through the fixture so its
     *  scope is cancelled before the database closes — see [HabitRepositoryTestFixture.close]. */
    private fun newViewModel() = fixture.habitEditorViewModel()

    private fun text(resId: Int) = ApplicationProvider.getApplicationContext<Context>().getString(resId)

    private class EditorHost(var backCount: Int = 0, var doneCount: Int = 0)

    private fun launchEditor(): EditorHost {
        val host = EditorHost()
        val viewModel = newViewModel()
        composeTestRule.setContent {
            HabitEditorRoute(
                habitId = null,
                onDone = { host.doneCount++ },
                onBack = { host.backCount++ },
                viewModel = viewModel,
            )
        }
        return host
    }

    private fun typeAName() =
        composeTestRule.onNodeWithText(text(R.string.habit_editor_name_label)).performTextInput("Drink water")

    /** [androidx.compose.ui.test.junit4.v2.createAndroidComposeRule] composes on a
     *  `StandardTestDispatcher`, so nothing recomposes until the test rule idles. The Compose
     *  finders do that for themselves; [androidx.activity.OnBackPressedDispatcher] is outside the
     *  framework and does not, so a bare `runOnUiThread` here would dispatch back into a
     *  composition that has not yet seen the text the test just typed and the editor would read
     *  itself as pristine. The explicit idle is what makes the two back paths comparable. */
    private fun pressSystemBack() {
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
    }

    private fun assertNoDiscardDialog() =
        composeTestRule.onNodeWithText(text(R.string.habit_editor_discard_title)).assertDoesNotExist()

    @Test
    fun theBackArrowLeavesImmediatelyWhenNothingHasBeenEdited() {
        val host = launchEditor()

        composeTestRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, host.backCount)
        assertNoDiscardDialog()
    }

    @Test
    fun systemBackLeavesImmediatelyWhenNothingHasBeenEdited() {
        val host = launchEditor()

        pressSystemBack()
        composeTestRule.waitForIdle()

        assertEquals(1, host.backCount)
        assertNoDiscardDialog()
    }

    @Test
    fun theBackArrowAsksBeforeDiscardingUnsavedEdits() {
        val host = launchEditor()
        typeAName()

        composeTestRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(text(R.string.habit_editor_discard_title)).assertExists()
        assertEquals(0, host.backCount)
    }

    @Test
    fun systemBackAsksBeforeDiscardingUnsavedEdits() {
        val host = launchEditor()
        typeAName()

        pressSystemBack()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(text(R.string.habit_editor_discard_title)).assertExists()
        assertEquals(0, host.backCount)
    }

    @Test
    fun confirmingTheDiscardLeavesTheEditorAndPersistsNothing() {
        val host = launchEditor()
        typeAName()
        composeTestRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(text(R.string.habit_editor_discard_confirm)).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, host.backCount)
        assertEquals(0, host.doneCount)
        assertNoDiscardDialog()
        assertTrue(runBlocking { fixture.database.habitDao().findAllSnapshot() }.isEmpty())
    }

    @Test
    fun dismissingTheDiscardKeepsTheEditorOpenWithTheEditsIntact() {
        val host = launchEditor()
        typeAName()
        composeTestRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(text(R.string.action_cancel)).performClick()
        composeTestRule.waitForIdle()

        assertEquals(0, host.backCount)
        assertNoDiscardDialog()
        composeTestRule.onNodeWithText("Drink water").assertExists()
    }

    @Test
    fun typingAndThenUndoingEveryEditLetsTheEditorCloseWithoutAsking() {
        val host = launchEditor()
        typeAName()
        composeTestRule.onNodeWithText("Drink water").performTextClearance()

        composeTestRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeTestRule.waitForIdle()

        assertNoDiscardDialog()
        assertEquals(1, host.backCount)
    }
}
