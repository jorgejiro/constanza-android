package com.jjrapps.constanza.tracking

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.habit.HabitRepositoryTestFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val MORNING_MINUTE = 8 * 60
private const val HABIT_NAME = "Stretch"

/**
 * today-add-habit: Today offers the same create action in two presentations, and which one is on
 * screen is decided by whether there is anything on the list.
 *
 * Both presentations render the same `today_add_habit` label, so every assertion here addresses
 * them by [TODAY_ADD_HABIT_EMPTY_TEST_TAG] / [TODAY_ADD_HABIT_TRAILING_TEST_TAG] rather than by
 * text — a text finder cannot tell one from the other, and "the empty presentation is gone once
 * habits exist" is precisely what needs proving.
 *
 * Routing is asserted here only as far as "the callback the Activity binds actually fires". That
 * the callback reaches the habit editor, and that leaving the editor comes back to Today rather
 * than stranding the user on the back-routeless habit list, is
 * [com.jjrapps.constanza.e2e.TodayAddHabitE2ETest]'s job, because only a real `MainActivity` has
 * the hoisted `ConstanzaRoute` that decides it.
 */
@RunWith(AndroidJUnit4::class)
class TodayAddHabitComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fixture: HabitRepositoryTestFixture
    private lateinit var viewModel: TodayViewModel

    @Before
    fun setUp() {
        fixture = HabitRepositoryTestFixture(ApplicationProvider.getApplicationContext<Context>())
        viewModel = fixture.todayViewModel()
    }

    /** Ordering lives in [HabitRepositoryTestFixture.close] — see its KDoc for why the ViewModel
     *  scopes must die before the database, and what the old per-class teardown was preventing. */
    @After
    fun tearDown() = fixture.close()

    private fun text(resId: Int) = ApplicationProvider.getApplicationContext<Context>().getString(resId)

    @Test
    fun anEmptyTodayShowsTheCentredAddActionAndNoTrailingOne() {
        var addHabitTaps = 0
        composeTestRule.setContent {
            TodayRoute(onManageHabits = {}, onAddHabit = { addHabitTaps++ }, viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag(TODAY_ADD_HABIT_EMPTY_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TODAY_ADD_HABIT_TRAILING_TEST_TAG).assertDoesNotExist()
        // The sentence stays: it says what the state IS, and the button says what to do about it.
        composeTestRule.onNodeWithText(text(R.string.today_empty)).assertIsDisplayed()

        composeTestRule.onNodeWithTag(TODAY_ADD_HABIT_EMPTY_TEST_TAG).performClick()
        assertTrue("tapping the centred action must invoke the route's onAddHabit", addHabitTaps == 1)
    }

    @Test
    fun aPopulatedTodayShowsTheTrailingAddActionAndNoCentredOne() = runBlocking {
        fixture.seedHabitWithEnabledSlot(name = HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)

        var addHabitTaps = 0
        composeTestRule.setContent {
            TodayRoute(onManageHabits = {}, onAddHabit = { addHabitTaps++ }, viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag(TODAY_ADD_HABIT_TRAILING_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TODAY_ADD_HABIT_EMPTY_TEST_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText(text(R.string.today_empty)).assertDoesNotExist()

        composeTestRule.onNodeWithTag(TODAY_ADD_HABIT_TRAILING_TEST_TAG).performClick()
        assertTrue("tapping the trailing action must invoke the route's onAddHabit", addHabitTaps == 1)
    }

    /**
     * The trailing action is an action at the end of the list, not an extra habit row inside it.
     *
     * Asserted geometrically rather than by eye: the action's top edge is below the last row's
     * answer buttons, so nothing that belongs to a habit can be sitting beside or beneath it in the
     * place a row would occupy. This is the assertion that fails if the item is ever moved above
     * `items(state.rows)` or given a row's leading-dot layout.
     */
    @Test
    fun theTrailingAddActionSitsBelowEveryHabitRow() = runBlocking {
        fixture.seedHabitWithEnabledSlot(name = HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)

        composeTestRule.setContent {
            TodayRoute(onManageHabits = {}, viewModel = viewModel)
        }

        val rowName = composeTestRule.onNodeWithText(HABIT_NAME).fetchSemanticsNode().boundsInRoot
        val answerButton = composeTestRule.onNodeWithText(text(R.string.today_answer_yes))
            .fetchSemanticsNode().boundsInRoot
        val action = composeTestRule.onNodeWithTag(TODAY_ADD_HABIT_TRAILING_TEST_TAG)
            .fetchSemanticsNode().boundsInRoot

        assertTrue("the add action must sit below the habit's name", rowName.bottom <= action.top)
        assertTrue("the add action must sit below the habit's answer buttons", answerButton.bottom <= action.top)
    }
}
