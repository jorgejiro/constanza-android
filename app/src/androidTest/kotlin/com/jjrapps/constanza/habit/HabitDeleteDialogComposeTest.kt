package com.jjrapps.constanza.habit

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.domain.model.Schedule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val WAIT_TIMEOUT_MS = 5_000L
private const val ENTRY_COUNT = 7

/**
 * habit-management: Habit Deletion (design.md D2/D3/D4, tasks 4.5/4.6). Every post-click
 * assertion is a bounded [waitForNodeWithText] poll rather than a bare `assertExists()`, matching
 * `HabitListArchiveComposeTest`'s reasoning: [HabitListViewModel.uiState] re-emits asynchronously
 * relative to the click that triggered it.
 */
@RunWith(AndroidJUnit4::class)
class HabitDeleteDialogComposeTest {

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

    private fun text(resId: Int, vararg args: Any) =
        ApplicationProvider.getApplicationContext<Context>().getString(resId, *args)

    private fun waitForNodeWithText(text: String) {
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun showContentFor(habitId: Long) {
        val viewModel = fixture.habitListViewModel()
        composeTestRule.setContent {
            HabitListRoute(onBack = {}, onCreateHabit = {}, onEditHabit = {}, viewModel = viewModel)
        }
        waitForNodeWithText(requireNotNull(runBlocking { fixture.habitRepository.findById(habitId) }).name)
    }

    private fun openDeleteDialog() {
        composeTestRule.onNodeWithContentDescription(text(R.string.habit_list_more_options)).performClick()
        composeTestRule.onNodeWithText(text(R.string.habit_list_delete)).performClick()
    }

    /** Proves: "Confirmation states the exact recorded-answer count" (task 4.5, non-zero half). */
    @Test
    fun openingDeleteFromTheOverflowMenuNamesTheHabitAndItsExactRecordedAnswerCount() = runBlocking {
        val habitId = fixture.habitRepository.create(newHabit(name = "Read"), Schedule.Daily())
        val slotId = fixture.insertEnabledSlot(habitId)
        repeat(ENTRY_COUNT) { index ->
            fixture.database.entryDao().insert(
                EntryEntity(
                    habitId = habitId,
                    date = "2026-09-0${index + 1}",
                    slotId = slotId,
                    status = "COMPLETED",
                    value = null,
                    answeredAt = "2026-09-0${index + 1}T08:00:00Z",
                    source = "IN_APP",
                ),
            )
        }

        showContentFor(habitId)
        openDeleteDialog()

        waitForNodeWithText(text(R.string.habit_delete_dialog_title, "Read"))
        waitForNodeWithText("7 recorded answer")
    }

    /** Proves: "Deleting a habit with no history behaves the same" — the confirmation's zero half
     *  (task 4.5), rendered honestly through the same `<plurals>` category as every other count,
     *  never a dedicated zero-case string. */
    @Test
    fun aZeroHistoryHabitsDeleteDialogStatesZeroRecordedAnswersHonestly() = runBlocking {
        val habitId = fixture.habitRepository.create(newHabit(name = "Stretch"), Schedule.Daily())

        showContentFor(habitId)
        openDeleteDialog()

        waitForNodeWithText("0 recorded answer")
    }

    /** Proves: "Declining the confirmation changes nothing" (task 4.6). */
    @Test
    fun decliningTheDeleteDialogLeavesEveryTableUnchanged() = runBlocking {
        val habitId = fixture.habitRepository.create(newHabit(name = "Read"), Schedule.Daily())
        val slotId = fixture.insertEnabledSlot(habitId)
        fixture.occurrencePlanner.replanAll()
        fixture.database.entryDao().insert(
            EntryEntity(
                habitId = habitId,
                date = "2026-09-01",
                slotId = slotId,
                status = "COMPLETED",
                value = null,
                answeredAt = "2026-09-01T08:00:00Z",
                source = "IN_APP",
            ),
        )

        showContentFor(habitId)
        openDeleteDialog()
        waitForNodeWithText(text(R.string.habit_delete_dialog_title, "Read"))
        composeTestRule.onNodeWithText(text(R.string.action_cancel)).performClick()

        require(fixture.database.habitDao().findById(habitId) != null) { "declining must not delete the habit" }
        require(fixture.database.scheduleDao().findByHabitId(habitId) != null) {
            "declining must not delete the schedule"
        }
        require(fixture.database.reminderSlotDao().findByHabitId(habitId).isNotEmpty()) {
            "declining must not delete reminder slots"
        }
        require(fixture.database.entryDao().findByHabitId(habitId).isNotEmpty()) {
            "declining must not delete entries"
        }
        require(fixture.database.reminderOccurrenceDao().findByHabitId(habitId).isNotEmpty()) {
            "declining must not delete reminder occurrences"
        }
    }
}
