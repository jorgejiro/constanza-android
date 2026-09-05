package com.jjrapps.constanza.habit

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.domain.model.Schedule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val WAIT_TIMEOUT_MS = 5_000L
private const val HABIT_NAME = "Read"

/** Wide enough for a real row, narrow enough to force wrapping deterministically — the row is
 *  rendered inside this fixed width rather than the device's full screen width so the two name
 *  scenarios below do not depend on which emulator image happens to be running. */
private const val ROW_WIDTH_DP = 360

/** Short enough to render with no visual overflow at [ROW_WIDTH_DP] — the exact line count is not
 *  asserted, only the absence of an ellipsis; whether it lands on one line or wraps onto a second
 *  is immaterial to what this test proves. */
private const val TWO_LINE_NAME = "A short habit name"

/** Deliberately far longer than [TWO_LINE_NAME] — several times what two lines at [ROW_WIDTH_DP]
 *  could ever hold — so the "needs more than two lines" scenario holds regardless of exact font
 *  metrics on either emulator image. */
private const val OVERFLOWING_NAME =
    "A very long habit name that keeps going and going, well past what two lines could ever hold, " +
        "repeating itself again and again until there is no doubt at all that it needs a third line " +
        "and then a fourth one too, no matter which device renders it"

/**
 * habit-management: Habit List Row Actions And Name Display (design.md D5) — the four ADDED
 * scenarios. `HabitRow`'s `trailingContent` collapsed from three always-visible controls down to
 * the bare overflow launcher, with Progress, Archive/Un-archive and Delete all moved inside its
 * [androidx.compose.material3.DropdownMenu]; `headlineContent` gained `maxLines = 2, overflow =
 * TextOverflow.Ellipsis`. [HabitListArchiveComposeTest] and [HabitDeleteDialogComposeTest] already
 * cover what those actions DO (archiving, un-archiving, deleting); this file covers only where
 * they are reachable from and how the name renders.
 */
@RunWith(AndroidJUnit4::class)
class HabitListRowMenuComposeTest {

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

    /** Renders the real [HabitListRoute] with one habit named [name], constrained to [ROW_WIDTH_DP]
     *  so the name-wrapping assertions are deterministic across emulator images. */
    private fun showListWithHabitNamed(name: String) {
        runBlocking { fixture.habitRepository.create(newHabit(name = name), Schedule.Daily()) }
        val viewModel = fixture.habitListViewModel()
        composeTestRule.setContent {
            Box(modifier = Modifier.width(ROW_WIDTH_DP.dp)) {
                HabitListRoute(onBack = {}, onCreateHabit = {}, onEditHabit = {}, viewModel = viewModel)
            }
        }
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(name, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Whether the visible [name] node was actually capped at fewer lines than it needed, read
     *  through the same [SemanticsActions.GetTextLayoutResult] accessibility action a screen
     *  reader would invoke — the ellipsis itself is a rendering effect, not a change to the
     *  node's semantic text, so no assertion on the text string itself could ever tell the two
     *  scenarios apart.
     *
     *  [androidx.compose.ui.text.TextLayoutResult.hasVisualOverflow] is deliberately NOT used
     *  here: measured directly against this row on a real emulator, it reported `true` even for
     *  a one-line, nowhere-near-overflowing name. Its `didOverflowWidth` half
     *  (`size.width < multiParagraph.width`) compares a rounded placement size against
     *  `multiParagraph.width`, which [androidx.compose.ui.text.MultiParagraph]'s own
     *  implementation sets to the raw incoming constraint's max width, not the text's needed
     *  width — so it goes spuriously true for ordinary sub-pixel rounding whenever rendered text
     *  does not exactly fill its column, which is the common case, not the exceptional one.
     *  [androidx.compose.ui.text.MultiParagraph.didExceedMaxLines] is the flag actually documented
     *  to mean "this content needed more lines (or, with an ellipsis, a wider line) than
     *  `maxLines` allowed" and is what [HabitRow]'s `overflow = TextOverflow.Ellipsis` truly
     *  depends on. */
    private fun nameHasVisualOverflow(name: String): Boolean {
        val results = mutableListOf<TextLayoutResult>()
        val node = composeTestRule.onNodeWithText(name, substring = true).fetchSemanticsNode()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.firstOrNull()?.multiParagraph?.didExceedMaxLines == true
    }

    @Test
    fun theRowShowsOnlyTheOverflowLauncher() {
        showListWithHabitNamed(HABIT_NAME)

        composeTestRule.onNodeWithContentDescription(text(R.string.habit_list_more_options)).assertExists()
        composeTestRule.onNodeWithText(text(R.string.habit_list_progress)).assertDoesNotExist()
        composeTestRule.onNodeWithText(text(R.string.habit_list_archive)).assertDoesNotExist()
        composeTestRule.onNodeWithText(text(R.string.habit_list_delete)).assertDoesNotExist()
    }

    @Test
    fun openingTheOverflowMenuOffersProgressArchiveAndDelete() {
        showListWithHabitNamed(HABIT_NAME)

        composeTestRule.onNodeWithContentDescription(text(R.string.habit_list_more_options)).performClick()

        composeTestRule.onNodeWithText(text(R.string.habit_list_progress)).assertExists()
        composeTestRule.onNodeWithText(text(R.string.habit_list_archive)).assertExists()
        composeTestRule.onNodeWithText(text(R.string.habit_list_delete)).assertExists()
    }

    @Test
    fun aNameThatFitsTwoLinesShowsNoEllipsis() {
        showListWithHabitNamed(TWO_LINE_NAME)

        assertFalse(
            "a name that fits within two lines must render in full, with no ellipsis",
            nameHasVisualOverflow(TWO_LINE_NAME),
        )
    }

    @Test
    fun aNameExceedingTwoLinesIsEllipsized() {
        showListWithHabitNamed(OVERFLOWING_NAME)

        assertTrue(
            "a name needing more than two lines must be capped at two and end with an ellipsis",
            nameHasVisualOverflow(OVERFLOWING_NAME),
        )
    }
}
