package com.jjrapps.constanza.habit

import android.content.Context
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 6a.7 (ui-adaptive-layout: Habit create/edit screen survives a landscape rotation).
 *
 * [StateRestorationTester] rather than a real `requestedOrientation` flip: this app declares no
 * `android:configChanges` (design.md §5.7 C1/C4), so an actual device rotation destroys and
 * recreates the host Activity, and nothing re-attaches the previously-set composable content to
 * the new Activity instance automatically — confirmed on-device, where a real rotation left the
 * test unable to find any Compose hierarchy at all ("No compose hierarchies found in the app"),
 * an artifact of how this manually-constructed, non-Hilt [HabitEditorViewModel] is wired into the
 * test, not of the production screen. [StateRestorationTester.emulateSavedInstanceStateRestore]
 * disposes and reconstructs the composition the same way the Compose runtime does across a real
 * configuration change, which is the documented, idiomatic way to test exactly this without an
 * Activity relaunch — and it exercises precisely what "no content loss" depends on: the entered
 * name lives in [HabitEditorUiState], held by the SAME `viewModel` instance the content lambda
 * closes over, matching how a real `ViewModel` instance survives rotation via its retained
 * `ViewModelStore` in production.
 */
@RunWith(AndroidJUnit4::class)
class HabitEditorRotationComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fixture: HabitRepositoryTestFixture

    @Before
    fun setUp() {
        fixture = HabitRepositoryTestFixture(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() = fixture.close()

    private fun text(resId: Int) = ApplicationProvider.getApplicationContext<Context>().getString(resId)

    @Test
    fun rotatingTheEditorMidInputKeepsTheEnteredNameVisible() {
        // Hoisted, not constructed inside the content lambda: emulateSavedInstanceStateRestore()
        // re-invokes that lambda to rebuild the composition, and it must close over the SAME
        // instance (matching how a real ViewModelStore hands back the same retained instance after
        // an actual rotation) rather than a fresh one every recomposition.
        val viewModel = HabitEditorViewModel(fixture.habitRepository, fixture.timeProvider)
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent {
            HabitEditorRoute(habitId = null, onDone = {}, onBack = {}, viewModel = viewModel)
        }
        composeTestRule.onNodeWithText(text(R.string.habit_editor_name_label)).performTextInput("Read before bed")

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText("Read before bed").assertExists()
    }

    /**
     * Task 6a.9's regression guard. The focus latch records **which** field gained focus last, so a
     * configuration change restores the caret where the user actually was. This asserts the harder
     * half: focus goes back to the **notes** field, not to the name field — a bare per-field boolean
     * would have said "name held focus at some point" and thrown the caret back there.
     *
     * The keyboard itself is not asserted here, and deliberately so:
     * [StateRestorationTester.emulateSavedInstanceStateRestore] rebuilds the composition without
     * recreating the Activity, so there is no real IME to observe. That half was measured on the
     * device instead — `mInputShown` `true` → `true` across a real rotation, against `true` → `false`
     * before the fix (design.md §13.5, finding 2). What this test can hold is the saved-state
     * mechanism that made the keyboard restoration possible, which is precisely the part that was
     * broken: the old flag was cleared by the teardown's own `onFocusChanged(false)`.
     */
    @Test
    fun rotatingRestoresFocusToTheFieldThatHadIt() {
        val viewModel = HabitEditorViewModel(fixture.habitRepository, fixture.timeProvider)
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent {
            HabitEditorRoute(habitId = null, onDone = {}, onBack = {}, viewModel = viewModel)
        }
        composeTestRule.onNodeWithText(text(R.string.habit_editor_notes_label))
            .performTextInput("after the news")

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText("after the news").assertIsFocused()
        composeTestRule.onNodeWithText(text(R.string.habit_editor_name_label)).assertIsNotFocused()
    }
}
