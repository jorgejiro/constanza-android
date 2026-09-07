package com.jjrapps.constanza.tracking

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.habit.HabitRepositoryTestFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val MORNING_MINUTE = 8 * 60
private const val HABIT_NAME = "Journal"

/**
 * today-one-line-row: dedicated coverage for the redesigned row shape and its new change dialog,
 * as distinct from [TodaySlotRowComposeTest] (the older cramped-row regression suite, now updated
 * in place) and [TodayAnsweredSlotComposeTest] (the reopen/re-answer mechanics). Each scenario here
 * is one the brief asked for by name: a pending row's exact control count, the answered row's own
 * click opening [ChangeAnswerDialog], an option write, and the 48dp touch-target floor.
 */
@RunWith(AndroidJUnit4::class)
class TodayOneLineRowComposeTest {

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

    private fun changeDescription(habitName: String, answeredStatusText: String) =
        ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.today_slot_change_a11y, habitName, answeredStatusText)

    /**
     * "A pending row shows exactly two answer controls and no third." [today_answer_skip] no
     * longer exists as a resource at all (today-one-line-row deletes it outright, see
     * `strings.xml`'s own comment) — the literals below assert the absence of ANY third control by
     * that name, not merely of the one historical resource id.
     */
    @Test
    fun aPendingRowShowsExactlyTwoAnswerControlsAndNoThird(): Unit = runBlocking {
        fixture.seedHabitWithEnabledSlot(name = HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)
        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }

        composeTestRule.onAllNodesWithText(text(R.string.today_answer_yes)).assertCountEquals(1)
        composeTestRule.onAllNodesWithText(text(R.string.today_answer_no)).assertCountEquals(1)
        composeTestRule.onNodeWithText("Omitir").assertDoesNotExist()
        composeTestRule.onNodeWithText("Skip").assertDoesNotExist()
    }

    /** "Tapping an answered row opens the dialog." The dialog is titled with the habit name and
     *  offers exactly Sí/No/Omitido, per the brief's own ordering. */
    @Test
    fun tappingAnAnsweredRowOpensTheChangeDialog(): Unit = runBlocking {
        fixture.seedHabitWithEnabledSlot(name = HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)
        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }

        composeTestRule.onNodeWithText(text(R.string.today_answer_yes)).performClick()
        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.COMPLETED)

        composeTestRule.onNodeWithContentDescription(
            changeDescription(HABIT_NAME, text(R.string.today_slot_completed)),
        ).performClick()

        // The row itself still carries "Journal" behind the dialog (its own name text), so the
        // dialog's presence is asserted through its three options instead — none of which the row,
        // now answered and showing only a glyph, renders on its own.
        composeTestRule.onNodeWithText(text(R.string.today_answer_yes)).assertExists()
        composeTestRule.onNodeWithText(text(R.string.today_answer_no)).assertExists()
        composeTestRule.onNodeWithText(text(R.string.today_slot_skipped)).assertExists()
    }

    /** "Choosing an option writes that answer." Picking "Omitido" from an already-answered row's
     *  dialog persists [EntryStatus.SKIPPED] against the right slot, and the dialog closes itself —
     *  no separate confirm step exists (`ChangeAnswerDialog`'s own KDoc). */
    @Test
    fun choosingAnOptionInTheDialogWritesThatAnswer(): Unit = runBlocking {
        val (habitId, slotId) = fixture.seedHabitWithEnabledSlot(name = HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)
        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }

        composeTestRule.onNodeWithText(text(R.string.today_answer_yes)).performClick()
        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.COMPLETED)

        composeTestRule.onNodeWithContentDescription(
            changeDescription(HABIT_NAME, text(R.string.today_slot_completed)),
        ).performClick()
        composeTestRule.onNodeWithText(text(R.string.today_slot_skipped)).performClick()
        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.SKIPPED)

        val entry = fixture.database.entryDao().findByHabitId(habitId).single()
        assertEquals(EntryStatus.SKIPPED.name, entry.status)
        assertEquals(slotId, entry.slotId)
        // The dialog's own option text ("Omitido") only ever appears inside it — the row itself
        // shows a glyph, never this word — so its absence here proves the dialog closed itself.
        composeTestRule.onNodeWithText(text(R.string.today_slot_skipped)).assertDoesNotExist()
    }

    /**
     * "A tap target below 48dp anywhere in this row is a defect." Both answer pills are queried by
     * their own visible text: `Modifier.clickable`'s `shouldMergeDescendantSemantics` merges a
     * clickable node's descendants into itself for semantics purposes, so the node `onNodeWithText`
     * resolves here IS the pill's outer touch-target `Box` — sized [Dimens.AnswerPillTouchTarget]
     * — not the smaller label `Text` alone. The answered row's own click target is checked the same
     * way, by its change-dialog `contentDescription`.
     */
    @Test
    fun everyInteractiveElementInTheRowMeetsTheMinimumTouchTarget(): Unit = runBlocking {
        fixture.seedHabitWithEnabledSlot(name = HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)
        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }

        val minimumPx = with(composeTestRule.density) { Dimens.AnswerPillTouchTarget.toPx() }
        val yesBounds = composeTestRule.onNodeWithText(text(R.string.today_answer_yes))
            .fetchSemanticsNode().boundsInRoot
        val noBounds = composeTestRule.onNodeWithText(text(R.string.today_answer_no))
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "the Sí pill's hit box is ${yesBounds.width}x${yesBounds.height}px, below ${minimumPx}px",
            yesBounds.width >= minimumPx && yesBounds.height >= minimumPx,
        )
        assertTrue(
            "the No pill's hit box is ${noBounds.width}x${noBounds.height}px, below ${minimumPx}px",
            noBounds.width >= minimumPx && noBounds.height >= minimumPx,
        )

        composeTestRule.onNodeWithText(text(R.string.today_answer_yes)).performClick()
        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.COMPLETED)

        val rowBounds = composeTestRule.onNodeWithContentDescription(
            changeDescription(HABIT_NAME, text(R.string.today_slot_completed)),
        ).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "the answered row's own tap target is only ${rowBounds.height}px tall, below ${minimumPx}px",
            rowBounds.height >= minimumPx,
        )
    }
}
