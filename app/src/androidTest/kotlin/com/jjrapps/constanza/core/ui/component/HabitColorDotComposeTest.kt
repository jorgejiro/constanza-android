package com.jjrapps.constanza.core.ui.component

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.habit.HabitListRoute
import com.jjrapps.constanza.habit.HabitRepositoryTestFixture
import com.jjrapps.constanza.tracking.TodayRoute
import com.jjrapps.constanza.tracking.todayViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

private const val WAIT_TIMEOUT_MS = 5_000L
private const val HABIT_NAME = "Stretch"
private const val HABIT_COLOR_ARGB = 0xFF8FC5FF.toInt() // HabitColor.BLUE, arbitrary for this test

/**
 * Task 4.7 (habit-management: Habit Colour Visible Where Habits Are Listed). Renders the real
 * [TodayRoute] and [HabitListRoute] and asserts [HabitColorDot] is actually on screen for each,
 * rather than only unit-testing [HabitColorDot] in isolation — the spec requirement is about
 * visibility on the listing screens, not the composable's own rendering. Asserted via
 * [HABIT_COLOR_DOT_TEST_TAG], never a `contentDescription` — the dot deliberately carries none
 * (design.md decision 6).
 */
@RunWith(AndroidJUnit4::class)
class HabitColorDotComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fixture: HabitRepositoryTestFixture

    @Before
    fun setUp() {
        fixture = HabitRepositoryTestFixture(ApplicationProvider.getApplicationContext<Context>())
    }

    /** Ordering lives in [HabitRepositoryTestFixture.close] — see its KDoc for why the ViewModel
     *  scopes must die before the database. The nullable `todayViewModel` field this class used to
     *  carry existed only to write that ordering by hand for one of its two ViewModels; the fixture
     *  now covers both, including the habit-list one that was previously left running. */
    @After
    fun tearDown() = fixture.close()

    @Test
    fun theDotRendersOnTheTodayScreenForAHabitDueToday() = runBlocking {
        fixture.habitRepository.create(habitWithColor(HABIT_NAME, HABIT_COLOR_ARGB), Schedule.Daily())
        val viewModel = fixture.todayViewModel()

        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }
        awaitNodeWithText(HABIT_NAME)

        assertTrue(
            "the habit-colour dot must be present on the today screen",
            composeTestRule.onAllNodesWithTag(HABIT_COLOR_DOT_TEST_TAG, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun theDotRendersOnTheHabitListScreenAndDistinguishesTwoHabits() = runBlocking {
        fixture.habitRepository.create(habitWithColor("Read", HABIT_COLOR_ARGB), Schedule.Daily())
        fixture.habitRepository.create(habitWithColor("Journal", 0xFF8BDB95.toInt()), Schedule.Daily())
        val viewModel = fixture.habitListViewModel()

        composeTestRule.setContent {
            HabitListRoute(onBack = {}, onCreateHabit = {}, onEditHabit = {}, viewModel = viewModel)
        }
        awaitNodeWithText("Read")

        // `useUnmergedTree = true`: `HabitRow`'s `ListItem` sits under `Modifier.clickable`, which
        // merges its descendants' semantics into one accessibility node for the row — the default
        // (merged) tree a finder searches would hide the dot's own testTag inside that merge.
        val dots = composeTestRule.onAllNodesWithTag(HABIT_COLOR_DOT_TEST_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("each listed habit must render its own colour dot", dots.size >= 2)
    }

    private fun habitWithColor(name: String, colorArgb: Int) = Habit(
        id = 0, name = name, question = null, colorArgb = colorArgb, notes = null,
        archived = false, archivedAt = null, createdAt = Instant.parse("2026-09-01T08:00:00Z"), sortOrder = 0,
    )

    private fun awaitNodeWithText(label: String) {
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
