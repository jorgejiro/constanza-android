package com.jjrapps.constanza.tracking

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.habit.HabitRepositoryTestFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val MORNING_MINUTE = 8 * 60
private const val HABIT_NAME = "Journal"
private const val ENTRY_SOURCE_IN_APP = "IN_APP"

/**
 * today-clear-answer, through the real [TodayRoute] UI rather than [EntryWriter] directly (that
 * side, including the midnight-sweep law itself, is [ClearAnswerSweepTest]'s job). Proves:
 * - the change dialog's fourth option, "Not answered", is offered on today...
 * - ...and deliberately withheld on a past day (design.md's asymmetry: "Not answered" and "No"
 *   are the same outcome once the midnight law has already decided, so offering both there would
 *   be offering one outcome under two names).
 * - choosing it removes the entry row rather than writing a sentinel status: the slot's glyph
 *   disappears and its Sí/No pills come back, exactly like a slot that was never answered.
 * - re-answering after clearing writes the new answer normally.
 */
@RunWith(AndroidJUnit4::class)
class TodayClearAnswerComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fixture: HabitRepositoryTestFixture
    private lateinit var viewModel: TodayViewModel

    @Before
    fun setUp() {
        fixture = HabitRepositoryTestFixture(ApplicationProvider.getApplicationContext<Context>())
        viewModel = fixture.todayViewModel()
    }

    @After
    fun tearDown() = fixture.close()

    private fun text(resId: Int) = ApplicationProvider.getApplicationContext<Context>().getString(resId)

    private fun changeDescription(habitName: String, answeredStatusText: String) =
        ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.today_slot_change_a11y, habitName, answeredStatusText)

    private fun goToPreviousDay() {
        composeTestRule.onNodeWithContentDescription(text(R.string.today_previous_day)).performClick()
    }

    @Test
    fun theChangeDialogOffersNotAnsweredOnTodayAndClearingReturnsTheSlotToPending(): Unit = runBlocking {
        fixture.seedHabitWithEnabledSlot(name = HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)

        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }
        composeTestRule.onNodeWithText(text(R.string.today_answer_yes)).performClick()
        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.COMPLETED)
        composeTestRule.onNodeWithContentDescription(text(R.string.today_slot_completed)).assertExists()

        // All four options reachable from today's row.
        composeTestRule.onNodeWithContentDescription(
            changeDescription(HABIT_NAME, text(R.string.today_slot_completed)),
        ).performClick()
        composeTestRule.onNodeWithText(text(R.string.today_answer_yes)).assertExists()
        composeTestRule.onNodeWithText(text(R.string.today_answer_no)).assertExists()
        composeTestRule.onNodeWithText(text(R.string.today_slot_skipped)).assertExists()
        composeTestRule.onNodeWithText(text(R.string.today_answer_not_answered)).performClick()

        // Cleared: glyph gone, pending pills back — the same shape as a never-answered slot.
        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.UNKNOWN)
        composeTestRule.onNodeWithContentDescription(text(R.string.today_slot_completed)).assertDoesNotExist()
        composeTestRule.onNodeWithText(text(R.string.today_answer_yes)).assertExists()
        composeTestRule.onNodeWithText(text(R.string.today_answer_no)).assertExists()

        // Re-answering after clearing writes the new answer normally.
        composeTestRule.onNodeWithText(text(R.string.today_answer_no)).performClick()
        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.MISSED)
        composeTestRule.onNodeWithContentDescription(text(R.string.today_slot_missed)).assertExists()

        val today = fixture.timeProvider.today().toString()
        val habitId = viewModel.uiState.value.rows.single().habitId
        val entries = fixture.database.entryDao().findByHabitAndDate(habitId, today)
        assertEquals(1, entries.size)
        assertEquals(EntryStatus.MISSED.name, entries.single().status)
    }

    @Test
    fun theChangeDialogOffersOnlyThreeOptionsOnAPastDay(): Unit = runBlocking {
        val seeded = fixture.seedHabitWithEnabledSlot(name = HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)
        val pastDate = fixture.timeProvider.today().minusDays(1)
        fixture.database.entryDao().upsert(
            EntryEntity(
                habitId = seeded.habitId,
                date = pastDate.toString(),
                slotId = seeded.slotId,
                status = EntryStatus.MISSED.name,
                value = null,
                answeredAt = fixture.timeProvider.now().toString(),
                source = ENTRY_SOURCE_IN_APP,
            ),
        )

        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }
        goToPreviousDay()
        viewModel.awaitState("slot 0 missed on $pastDate") { state ->
            state.date == pastDate && state.rows.singleOrNull()?.slots?.getOrNull(0)?.status == EntryStatus.MISSED
        }

        composeTestRule.onNodeWithContentDescription(
            changeDescription(HABIT_NAME, text(R.string.today_slot_missed)),
        ).performClick()
        // The complete three-option set is still offered...
        composeTestRule.onNodeWithText(text(R.string.today_answer_yes)).assertExists()
        composeTestRule.onNodeWithText(text(R.string.today_answer_no)).assertExists()
        composeTestRule.onNodeWithText(text(R.string.today_slot_skipped)).assertExists()
        // ...but "Not answered" is deliberately withheld on a past day.
        composeTestRule.onNodeWithText(text(R.string.today_answer_not_answered)).assertDoesNotExist()
    }
}
