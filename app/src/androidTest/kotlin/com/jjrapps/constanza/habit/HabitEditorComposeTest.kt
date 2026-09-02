package com.jjrapps.constanza.habit

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val WAIT_TIMEOUT_MS = 5_000L

/**
 * Tasks 6a.2/6a.3 (habit-management: Creation requires a name; Habit Editing). Compose's
 * [waitUntil][androidx.compose.ui.test.junit4.ComposeContentTestRule.waitUntil] awaits the
 * asynchronous save rather than assuming it has completed once the click returns — the same
 * lesson `NotificationPosterInstrumentedTest` already paid for `NotificationManager.notify`.
 */
@RunWith(AndroidJUnit4::class)
class HabitEditorComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fixture: HabitRepositoryTestFixture

    @Before
    fun setUp() {
        fixture = HabitRepositoryTestFixture(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() = fixture.close()

    /** Call this outside `setContent`, never inside it: a view model built in the composable
     *  lambda is rebuilt on every recomposition and the editor would silently lose its state.
     *  Lint's `ViewModelConstructorInComposable` cannot see the construction through this helper,
     *  so the call site is the only place the rule can be honoured. */
    private fun newViewModel() = HabitEditorViewModel(fixture.habitRepository, fixture.timeProvider)

    private fun text(resId: Int) = ApplicationProvider.getApplicationContext<Context>().getString(resId)

    @Test
    fun creatingAHabitEndToEndPersistsTheHabitAndADailySchedule() {
        var done = false
        val viewModel = newViewModel()
        composeTestRule.setContent {
            HabitEditorRoute(habitId = null, onDone = { done = true }, viewModel = viewModel)
        }

        composeTestRule.onNodeWithText(text(R.string.habit_editor_name_label)).performTextInput("Drink water")
        composeTestRule.onNodeWithText(text(R.string.habit_editor_save)).performClick()
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) { done }

        val persistedHabit = runBlocking { fixture.database.habitDao().findAllSnapshot().single() }
        assertEquals("Drink water", persistedHabit.name)
        val persistedSchedule = runBlocking { fixture.database.scheduleDao().findByHabitId(persistedHabit.id) }
        assertEquals("DAILY", persistedSchedule?.kind)
    }

    @Test
    fun saveIsBlockedAndNoHabitIsPersistedWhileTheNameIsEmpty() {
        var done = false
        val viewModel = newViewModel()
        composeTestRule.setContent {
            HabitEditorRoute(habitId = null, onDone = { done = true }, viewModel = viewModel)
        }

        composeTestRule.onNodeWithText(text(R.string.habit_editor_save)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(text(R.string.habit_editor_name_error)).assertExists()
        assertFalse(done)
        assertTrue(runBlocking { fixture.database.habitDao().findAllSnapshot() }.isEmpty())
    }
}
