package com.jjrapps.constanza.tracking

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
 * today-add-habit-is-not-a-fab: Today offers ONE create action, in one shape, whether or not there
 * is anything on the list — a [androidx.compose.material3.FloatingActionButton] in the `Scaffold`
 * slot, exactly as `HabitListScreen` does it.
 *
 * That is the claim these tests exist to hold. Before this change there were two centred `Button`s
 * — one inside the empty state, one after the last habit row — and the tests here proved which of
 * the two was on screen. What replaces that is the assertion that the SAME node survives the empty
 * to populated transition and that no second add affordance appears beside it.
 *
 * The FAB carries an icon, so `today_add_habit` reaches the tree as a `contentDescription` and
 * never as visible text; assertions address it by [TODAY_ADD_HABIT_FAB_TEST_TAG].
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
    fun anEmptyTodayShowsTheAddHabitFabBesideTheEmptySentence() {
        var addHabitTaps = 0
        composeTestRule.setContent {
            TodayRoute(onManageHabits = {}, onAddHabit = { addHabitTaps++ }, viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag(TODAY_ADD_HABIT_FAB_TEST_TAG).assertIsDisplayed()
        // The sentence stays and the button inside it is gone: the sentence says what the state IS,
        // and the FAB — the one create affordance on the screen — says what to do about it.
        composeTestRule.onNodeWithText(text(R.string.today_empty)).assertIsDisplayed()

        composeTestRule.onNodeWithTag(TODAY_ADD_HABIT_FAB_TEST_TAG).performClick()
        assertTrue("tapping the add-habit FAB must invoke the route's onAddHabit", addHabitTaps == 1)
    }

    @Test
    fun aPopulatedTodayShowsTheSameAddHabitFab() = runBlocking {
        fixture.seedHabitWithEnabledSlot(name = HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)

        var addHabitTaps = 0
        composeTestRule.setContent {
            TodayRoute(onManageHabits = {}, onAddHabit = { addHabitTaps++ }, viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag(TODAY_ADD_HABIT_FAB_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.today_empty)).assertDoesNotExist()
        // One add affordance, not two. The deleted centred `Button`s rendered `today_add_habit` as
        // visible text; the FAB carries it as a `contentDescription`. So exactly one node describes
        // itself that way, and nothing on the screen spells it out in text any more.
        val described = composeTestRule
            .onAllNodesWithContentDescription(text(R.string.today_add_habit))
            .fetchSemanticsNodes()
        assertTrue(
            "Today must offer exactly one add-habit affordance, found ${described.size}",
            described.size == 1,
        )
        composeTestRule.onNodeWithText(text(R.string.today_add_habit)).assertDoesNotExist()

        composeTestRule.onNodeWithTag(TODAY_ADD_HABIT_FAB_TEST_TAG).performClick()
        assertTrue("tapping the add-habit FAB must invoke the route's onAddHabit", addHabitTaps == 1)
    }

    /**
     * The FAB floats in the bottom-right corner, over the list rather than inside it.
     *
     * Asserted geometrically rather than by eye, for the reason the deleted trailing-action test
     * gave: this is the assertion that fails if the button is ever put back into the `LazyColumn`
     * as an item, where it would scroll away and could be misread as one more habit row. Its left
     * edge past the horizontal midpoint and its bottom within a FAB's height of the viewport floor
     * is a corner and nowhere else.
     */
    @Test
    fun theAddHabitFabSitsInTheBottomRightCornerOverTheList() = runBlocking {
        fixture.seedHabitWithEnabledSlot(name = HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)

        composeTestRule.setContent {
            TodayRoute(onManageHabits = {}, viewModel = viewModel)
        }

        val root = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        val rowName = composeTestRule.onNodeWithText(HABIT_NAME).fetchSemanticsNode().boundsInRoot
        val fab = composeTestRule.onNodeWithTag(TODAY_ADD_HABIT_FAB_TEST_TAG)
            .fetchSemanticsNode().boundsInRoot

        assertTrue("the FAB must sit in the right half of the screen", fab.left >= root.center.x)
        assertTrue(
            "the FAB must sit in the bottom quarter of the screen",
            fab.top >= root.top + root.height * BOTTOM_QUARTER,
        )
        assertTrue("the FAB must sit below the habit's name", rowName.bottom <= fab.top)
    }

    private companion object {
        /** Three quarters down the viewport: anything below this is unambiguously the bottom edge,
         *  and a FAB pushed back into the list would fail it the moment a second row existed. */
        const val BOTTOM_QUARTER = 0.75f
    }
}
