package com.jjrapps.constanza.tracking

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.habit.HabitRepositoryTestFixture
import com.jjrapps.constanza.habit.newHabit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val MORNING_MINUTE = 8 * 60
private const val EVENING_MINUTE = 20 * 60

/**
 * Task 6b.7 — the test that proves slot independence survived the expandable-row design
 * (habit-entry-tracking: Slot Independence). Drives the real [TodayRoute] UI, not a mocked
 * argument capture: expand a two-slot habit, tap "Yes" on the first slot, and assert the second
 * slot's row still reads pending — `UNKNOWN` is never persisted (design.md §8.1), so "one `Entry`
 * row exists, for the tapped slot only" IS that assertion.
 */
@RunWith(AndroidJUnit4::class)
class TodayComposeTest {

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
    fun answeringOneSlotLeavesTheSiblingSlotUnknown() = runBlocking {
        val slots = listOf(
            ReminderSlot(id = 0, habitId = 0, minuteOfDay = MORNING_MINUTE, enabled = true),
            ReminderSlot(id = 0, habitId = 0, minuteOfDay = EVENING_MINUTE, enabled = true),
        )
        val habitId = fixture.habitRepository.create(newHabit("Stretch"), Schedule.TimesPerDay(), slots)

        viewModel.awaitOneRowWithSlots(2)

        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }
        composeTestRule.onNodeWithText(text(R.string.today_expand)).performClick()
        composeTestRule.onAllNodesWithText(text(R.string.today_answer_yes))[0].performClick()
        // The localised label, not `EntryStatus.COMPLETED.name`, which is what this row used to
        // render (today-row-answering-is-cramped-and-always-on, defect 2). Keeping the assertion on
        // the string resource is also what stops the raw constant coming back unnoticed — the
        // explicit check below says so directly.
        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.COMPLETED)
        composeTestRule.onNodeWithText(text(R.string.today_slot_completed), substring = true).assertExists()
        composeTestRule.onNodeWithText(EntryStatus.COMPLETED.name, substring = true).assertDoesNotExist()

        // The sibling slot's own row still reads pending — never touched by the first slot's answer.
        composeTestRule.onNodeWithText(text(R.string.today_slot_pending), substring = true).assertExists()

        val entries = fixture.database.entryDao().findByHabitId(habitId)
        assertEquals(1, entries.size)
        val answeredSlot = fixture.database.reminderSlotDao().findByHabitId(habitId)
            .first { it.id == entries.single().slotId }
        assertEquals(EntryStatus.COMPLETED.name, entries.single().status)
        assertEquals(MORNING_MINUTE, answeredSlot.minuteOfDay)
    }

    /**
     * today-row-alignment, decision 2. A collapsed multi-slot row is the ONE row on this screen with
     * no slot line under it, so its day rollup is the only state it shows at all — collapse it and a
     * row with no rollup says nothing about the habit whatsoever.
     *
     * That is not hypothetical. The rollup used to be `ListItem`'s `supportingContent`, and dropping
     * `ListItem` — which today-row-alignment had to do, because its 56dp headline would have been a
     * third left edge — took the rollup with it. Nothing failed: every multi-slot assertion in the
     * shipped suite taps `today_expand` first, so all of them ran against the expanded state, where
     * the slot lines carry their own status. It was caught by looking at a render, which is not a
     * guard.
     *
     * Asserted in the collapsed state deliberately, and before any expand: `today_status_partial` is
     * a DAY rollup string that no slot line can produce, so a passing assertion here cannot be
     * satisfied by an expanded slot's own text. The row is seeded PARTIAL rather than PENDING for
     * the same reason — `today_status_pending` and `today_slot_pending` are both "Pendiente", so
     * pending would have been indistinguishable from a slot line leaking in.
     */
    @Test
    // Explicit `: Unit`, not inferred: this body ends on an `assertExists()` that returns a
    // `SemanticsNodeInteraction`, so `= runBlocking { … }` infers THAT as the return type and JUnit
    // rejects the whole class with "should be void" at runner-construction time — taking every other
    // test in the file down with it. It compiles cleanly either way; only the matrix catches it.
    // `TodayAnsweredSlotComposeTest.aHabitWithNoReminderTimeReopensAndRecollapsesItsSingleNullSlot`
    // carries the same annotation for the same reason.
    fun aCollapsedMultiSlotRowStillNamesItsDayStatus(): Unit = runBlocking {
        val slots = listOf(
            ReminderSlot(id = 0, habitId = 0, minuteOfDay = MORNING_MINUTE, enabled = true),
            ReminderSlot(id = 0, habitId = 0, minuteOfDay = EVENING_MINUTE, enabled = true),
        )
        fixture.habitRepository.create(newHabit("Stretch"), Schedule.TimesPerDay(), slots)
        viewModel.awaitOneRowWithSlots(2)

        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }

        // Collapsed: the expand affordance is on screen, so no slot line is.
        composeTestRule.onNodeWithText(text(R.string.today_expand)).assertExists()
        composeTestRule.onAllNodesWithText(text(R.string.today_answer_yes)).assertCountEquals(0)

        // Answer one slot through the expanded view, then collapse again, so the row has a rollup
        // that is neither "all done" nor untouched — PARTIAL is the state a collapsed row is least
        // able to imply from anything else on screen.
        composeTestRule.onNodeWithText(text(R.string.today_expand)).performClick()
        composeTestRule.onAllNodesWithText(text(R.string.today_answer_yes))[0].performClick()
        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.COMPLETED)
        composeTestRule.onNodeWithText(text(R.string.today_collapse)).performClick()

        composeTestRule.onNodeWithText(text(R.string.today_expand)).assertExists()
        composeTestRule.onAllNodesWithText(text(R.string.today_answer_yes)).assertCountEquals(0)
        composeTestRule.onNodeWithText(text(R.string.today_status_partial), substring = true).assertExists()
    }

    /**
     * Task 6b.7 debt item 1 — the positive half of habit-entry-tracking's `SKIPPED` MUST: an in-app
     * Skip really does persist [EntryStatus.SKIPPED] against the answered `(habitId, date, slotId)`.
     *
     * The negative half — `SKIPPED` "MUST be settable only through an explicit in-app user action,
     * never through a notification action" — is deliberately left untested, because it is already
     * enforced at compile time: [NotificationEntryStatus] has only `COMPLETED` and `MISSED`, so the
     * test that would prove it does not compile. There is no member to pass and no runtime branch to
     * reach. Do not "close that gap" by widening the enum and adding a runtime guard: the enum's
     * shape IS the guarantee, and the absent test is the evidence that nothing can express the write.
     *
     * Ratified decision 4's other half — a skip neither breaks a streak nor counts as a failure — is
     * already covered by `:domain`'s `StreakCalculatorTest` ("a skipped day bridges a streak without
     * lengthening it", "only missed breaks the streak, skipped and unknown pass through unaffected"),
     * so it is not duplicated here.
     */
    @Test
    fun skippingInAppPersistsSkippedOnTheAnsweredSlotAndDate() = runBlocking {
        val (habitId, slotId) = fixture.seedHabitWithEnabledSlot(name = "Journal", minuteOfDay = EVENING_MINUTE)
        viewModel.awaitRows(1)

        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }
        composeTestRule.onNodeWithText(text(R.string.today_answer_skip)).performClick()
        // Again the localised label rather than the enum constant; see the sibling test above.
        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.SKIPPED)
        composeTestRule.onNodeWithText(text(R.string.today_slot_skipped), substring = true).assertExists()
        composeTestRule.onNodeWithText(EntryStatus.SKIPPED.name, substring = true).assertDoesNotExist()

        val entry = fixture.database.entryDao().findByHabitId(habitId).single()
        assertEquals(EntryStatus.SKIPPED.name, entry.status)
        assertEquals(fixture.timeProvider.today().toString(), entry.date)
        assertEquals(slotId, entry.slotId)
    }
}
