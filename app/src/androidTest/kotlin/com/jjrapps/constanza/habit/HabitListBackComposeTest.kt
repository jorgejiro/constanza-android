package com.jjrapps.constanza.habit

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.jjrapps.constanza.R
import com.jjrapps.constanza.domain.model.Schedule
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
private const val HABIT_NAME = "Read"

/**
 * habit-list-back-navigation: the habit list had no exit at all. Its top bar carried a title and
 * nothing else, `HabitListRoute` took no `onBack`, and no `BackHandler` covered it — so the system
 * back gesture fell through to `MainActivity`'s default and closed the app on a user who had come
 * here only to manage a habit.
 *
 * **The back gesture is a real `KEYCODE_BACK` here, not a call on the Activity's dispatcher.** That
 * is a deliberate correction, and the difference is not cosmetic. `KEYCODE_BACK` is delivered to
 * whichever WINDOW holds focus; [androidx.activity.OnBackPressedDispatcher.onBackPressed] on the
 * host Activity is a different entry point that skips window routing entirely. The two agree only
 * while the Activity's own window is the focused one — and the delete confirmation is a
 * `androidx.compose.ui.window.Dialog` with a window of its own, so the moment it is up they stop
 * agreeing. Measured on both matrix legs: with the confirmation showing, a real back press dismisses
 * it and leaves the Activity alive, while the Activity-dispatcher call reaches no enabled callback
 * and runs `OnBackPressedDispatcher`'s fallback, `Activity.onBackPressed()`. On API 31 that method
 * still contains `if (!isTaskRoot()) { finishAfterTransition(); return; }`, so it FINISHED the
 * instrumentation-hosted Activity and every later Compose finder threw "No compose hierarchies found
 * in the app"; from API 36 that branch is gone and the same call is inert, which is why the same
 * assertion passed on API 37 and failed on API 31. Dispatching that way proved nothing about the app
 * and made the leg that could not survive it look like a defect.
 *
 * **[createAndroidComposeRule] rather than the sibling files' `createComposeRule`**, still, and now
 * for two reasons: the real back press has to land on a host Activity that exists, and these
 * scenarios read that Activity directly — `isFinishing` is how "the app closed" is observable at
 * all, and [androidx.activity.OnBackPressedDispatcher.hasEnabledCallbacks] is how "this screen
 * claims the back gesture" is. `ComponentActivity` is the same host `createComposeRule` launches, so
 * nothing else about the setup changes.
 *
 * Every scenario checks the callback count rather than a screen change: this test hosts
 * [HabitListRoute] directly, with no navigation host above it, so "left the list" is observable
 * exactly as `onBack` having fired — which is also all
 * [com.jjrapps.constanza.core.ui.MainActivity] itself observes.
 */
@RunWith(AndroidJUnit4::class)
class HabitListBackComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fixture: HabitRepositoryTestFixture

    @Before
    fun setUp() {
        fixture = HabitRepositoryTestFixture(ApplicationProvider.getApplicationContext<Context>())
        runBlocking { fixture.habitRepository.create(newHabit(HABIT_NAME), Schedule.Daily()) }
    }

    @After
    fun tearDown() = fixture.close()

    private fun text(resId: Int) = ApplicationProvider.getApplicationContext<Context>().getString(resId)

    private fun nodeCountWithText(text: String) =
        composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    /** Bounded poll rather than a bare `assertExists()`, matching [HabitListArchiveComposeTest]:
     *  [HabitListViewModel.uiState] re-emits through Room asynchronously relative to `setContent`. */
    private fun waitForNodeWithText(text: String) {
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) { nodeCountWithText(text) > 0 }
    }

    /** The counterpart poll. A real key press is delivered by the window manager, so the dismissal
     *  it causes is not complete when [UiDevice.pressBack] returns — waiting for the node to go is
     *  what makes the assertions after it meaningful rather than racy. */
    private fun waitForNoNodeWithText(text: String) {
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) { nodeCountWithText(text) == 0 }
    }

    private class ListHost(var backCount: Int = 0)

    /** Built outside `setContent` for the reason `HabitEditorComposeTest` documents: a view model
     *  constructed in the content lambda is rebuilt on every recomposition. Built through the
     *  fixture so its scope is cancelled before the database closes — see
     *  [HabitRepositoryTestFixture.close]. */
    private fun launchList(): ListHost {
        val host = ListHost()
        val viewModel = fixture.habitListViewModel()
        composeTestRule.setContent {
            HabitListRoute(
                onBack = { host.backCount++ },
                onCreateHabit = {},
                onEditHabit = {},
                viewModel = viewModel,
            )
        }
        waitForNodeWithText(HABIT_NAME)
        return host
    }

    /** The system back gesture as a user performs it: a real `KEYCODE_BACK`, routed by the window
     *  manager to whichever window has focus. See this class's KDoc for why nothing here calls
     *  [androidx.activity.OnBackPressedDispatcher.onBackPressed] instead.
     *
     *  [androidx.compose.ui.test.junit4.v2.createAndroidComposeRule] composes on a
     *  `StandardTestDispatcher`, so nothing recomposes until the test rule idles. The Compose
     *  finders do that for themselves; the key press is outside the framework and does not, so a
     *  bare press would dispatch back into a composition that has not yet seen what the test just
     *  did. */
    private fun pressSystemBack() {
        composeTestRule.waitForIdle()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
    }

    /** Whether this screen is currently a claimant on the system back gesture — that is, whether a
     *  back press reaching the host Activity's window would be taken by the screen's `BackHandler`
     *  instead of falling through to the Activity default. This is the exact state the platform
     *  itself consults, and reading it is how the `enabled` guard is observable without dispatching
     *  anything. */
    private fun listClaimsSystemBack(): Boolean {
        composeTestRule.waitForIdle()
        var claims = false
        composeTestRule.runOnUiThread {
            claims = composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks()
        }
        return claims
    }

    private fun openDeleteDialog() {
        composeTestRule.onNodeWithContentDescription(text(R.string.habit_list_more_options)).performClick()
        composeTestRule.onNodeWithText(text(R.string.habit_list_delete)).performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun theTopBarCarriesABackArrowThatLeavesTheList() {
        val host = launchList()

        composeTestRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, host.backCount)
    }

    @Test
    fun theSystemBackGestureLeavesTheListInsteadOfClosingTheApp() {
        val host = launchList()

        pressSystemBack()
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) { host.backCount == 1 }

        assertFalse("back closed the app instead of leaving the list", composeTestRule.activity.isFinishing)
        assertEquals(1, host.backCount)
    }

    /**
     * What the user gets: back with the confirmation up cancels the delete, and the habit and the
     * list it was on are both still there afterwards. The delete confirmation is its own window, so
     * this is the dialog's own `onDismissRequest` answering — and this scenario exists to prove that
     * it really is the whole answer, that nothing behind the dialog also acts on the same gesture
     * and walks the user out of the screen or out of the app.
     *
     * It does NOT prove the `enabled` guard on the screen's `BackHandler`, and cannot: the dialog's
     * window consumes the key before the Activity's window ever sees it, so this scenario stays
     * green with the guard removed. [theListStopsClaimingTheSystemBackGestureWhileTheConfirmationIsUp]
     * is the one that goes red — verified by removing the guard and running it.
     */
    @Test
    fun systemBackWhileTheConfirmationIsUpCancelsTheDeleteAndKeepsTheList() {
        val host = launchList()
        openDeleteDialog()
        composeTestRule.onNodeWithText(text(R.string.habit_delete_dialog_confirm)).assertExists()

        pressSystemBack()
        waitForNoNodeWithText(text(R.string.habit_delete_dialog_confirm))

        assertFalse("back closed the app instead of cancelling the delete", composeTestRule.activity.isFinishing)
        assertEquals(0, host.backCount)
        composeTestRule.onNodeWithText(HABIT_NAME).assertExists()
        assertEquals(
            listOf(HABIT_NAME),
            runBlocking { fixture.database.habitDao().findAllSnapshot() }.map { it.name },
        )
    }

    /**
     * The `enabled` guard on the screen's `BackHandler`, which exists so the gesture cannot mean two
     * things at once. With the delete confirmation up, back belongs to that dialog — it is its own
     * window and answers through `onDismissRequest` — and the screen behind it must stop claiming
     * the gesture, so that it can never also walk the user out of the list.
     *
     * Asserted by reading the claim itself rather than by dispatching a press, because a press
     * cannot distinguish the two cases at all: the dialog's window takes the key either way. Both
     * directions are checked in one scenario so that the assertion is about the CHANGE the
     * confirmation causes — a screen that claimed back in neither state would satisfy half of this
     * and is a different defect.
     */
    @Test
    fun theListStopsClaimingTheSystemBackGestureWhileTheConfirmationIsUp() {
        launchList()
        assertTrue("the list never claimed the back gesture at all", listClaimsSystemBack())

        openDeleteDialog()

        assertFalse("the list still claims back from behind the confirmation", listClaimsSystemBack())
    }
}
