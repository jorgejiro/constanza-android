package com.jjrapps.constanza.habit

import android.content.Context
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
            HabitEditorRoute(habitId = null, onDone = {}, viewModel = viewModel)
        }
        composeTestRule.onNodeWithText(text(R.string.habit_editor_name_label)).performTextInput("Read before bed")

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText("Read before bed").assertExists()
    }
}
