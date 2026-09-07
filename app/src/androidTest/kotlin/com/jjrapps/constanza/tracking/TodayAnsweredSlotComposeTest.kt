package com.jjrapps.constanza.tracking

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.ui.expectedTimeOnDevice
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.habit.HabitRepositoryTestFixture
import com.jjrapps.constanza.habit.newHabit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val MORNING_MINUTE = 8 * 60
private const val MIDDAY_MINUTE = 12 * 60
private const val EVENING_MINUTE = 20 * 60
private const val ENTRY_SOURCE_IN_APP = "IN_APP"
private const val STATE_SNOOZED = "SNOOZED"
private const val RESOLVE_DEADLINE_MS = 24L * 60 * 60 * 1_000

/**
 * today-answered-slot-collapse: the maintainer's own screenshot showed two COMPLETED habits still
 * offering Yes/No/Skip at equal weight, so a finished day and an untouched day read the same. This
 * class proves the fix — habit-entry-tracking's Slot Independence and Day-Level Rollup and Per-Slot
 * Display, MODIFIED requirements — end to end through the real [TodayRoute] UI, never a mocked
 * argument capture.
 *
 * today-one-line-row rewrites HOW an answered slot is changed: "Cambiar" (a separate `TextButton`
 * that revealed the row's old Yes/No/Skip buttons again) is gone. An answered slot's own row is the
 * tap target now, and tapping it opens [ChangeAnswerDialog] rather than reopening inline pills — so
 * every scenario below that used to tap "Cambiar" now taps the row itself, by its own
 * `today_slot_change_a11y` accessible label, and picks an option from the dialog instead.
 */
@RunWith(AndroidJUnit4::class)
class TodayAnsweredSlotComposeTest {

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

    private suspend fun seedThreeSlotHabit(name: String = "Stretch"): Long {
        val slots = listOf(
            ReminderSlot(id = 0, habitId = 0, minuteOfDay = MORNING_MINUTE, enabled = true),
            ReminderSlot(id = 0, habitId = 0, minuteOfDay = MIDDAY_MINUTE, enabled = true),
            ReminderSlot(id = 0, habitId = 0, minuteOfDay = EVENING_MINUTE, enabled = true),
        )
        val habitId = fixture.habitRepository.create(newHabit(name), Schedule.TimesPerDay(), slots)
        viewModel.awaitOneRowWithSlots(3)
        return habitId
    }

    private fun expandRow() {
        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }
        composeTestRule.onNodeWithText(text(R.string.today_expand)).performClick()
    }

    /**
     * Covers the sibling-independence scenario ("Reopening one answered slot leaves a same-habit
     * sibling slot collapsed") and the answered-text scenario ("An answered slot names its specific
     * answer and offers one route, without colour") together: each slot is answered a different
     * way, each collapse is asserted against its sibling's untouched state, one slot is reopened and
     * re-answered through [ChangeAnswerDialog], and the other two are checked unaffected at every
     * step.
     *
     * today-one-line-row: this used to answer the third slot with Skip directly on the pending row.
     * That route is gone (`TodayComposeTest.skippingInAppPersistsSkippedOnTheAnsweredSlotAndDate`
     * covers Skip's own two-step route through the change dialog); every slot here is answered
     * Yes/No instead, which is enough to prove sibling independence without duplicating that
     * coverage.
     */
    @Test
    fun answeringEachSlotCollapsesItLeavesSiblingsUntouchedAndReopeningReAnswersOnlyThatSlot(): Unit = runBlocking {
        seedThreeSlotHabit()
        expandRow()

        // All three pending: three full sets of answer pills on screen.
        assertEquals(3, composeTestRule.onAllNodesWithText(text(R.string.today_answer_yes)).fetchSemanticsNodes().size)

        // Slot 0 -> Yes (Done). Answered slots always sit first among the remaining pending ones,
        // since an answered slot drops its own pending pills — deterministic without needing a
        // node index that survives the collapse.
        composeTestRule.onAllNodesWithText(text(R.string.today_answer_yes))[0].performClick()
        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.COMPLETED)
        composeTestRule.onNodeWithContentDescription(text(R.string.today_slot_completed)).assertExists()
        assertEquals(2, composeTestRule.onAllNodesWithText(text(R.string.today_answer_yes)).fetchSemanticsNodes().size)

        // Slot 1 -> No (Missed). Slot 0's Done glyph is untouched by this.
        composeTestRule.onAllNodesWithText(text(R.string.today_answer_no))[0].performClick()
        viewModel.awaitSlotStatus(slotIndex = 1, status = EntryStatus.MISSED)
        composeTestRule.onNodeWithContentDescription(text(R.string.today_slot_missed)).assertExists()
        composeTestRule.onNodeWithContentDescription(text(R.string.today_slot_completed)).assertExists()

        // Slot 2 -> Yes (Done). No pending pills remain anywhere on the row.
        composeTestRule.onAllNodesWithText(text(R.string.today_answer_yes))[0].performClick()
        viewModel.awaitSlotStatus(slotIndex = 2, status = EntryStatus.COMPLETED)
        composeTestRule.onAllNodesWithText(text(R.string.today_answer_yes)).assertCountEquals(0)
        composeTestRule.onAllNodesWithText(text(R.string.today_answer_no)).assertCountEquals(0)
        assertEquals(2, composeTestRule.onAllNodesWithContentDescription(text(R.string.today_slot_completed)).fetchSemanticsNodes().size)

        // Reopen slot 0 only, by its own distinct row description (time-led, since this is a
        // multi-slot habit — today-row-alignment), and answer No instead: its glyph changes; slots
        // 1 and 2 keep their own untouched state — the sibling-independence assertion itself.
        val morning = expectedTimeOnDevice(inTwentyFourHour = "08:00", inTwelveHour = "8:00 AM")
        composeTestRule.onNodeWithContentDescription(
            changeDescription("Stretch", "$morning ${text(R.string.today_slot_completed)}"),
        ).performClick()
        composeTestRule.onNodeWithText(text(R.string.today_answer_no)).performClick()
        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.MISSED)

        // Slot 1 is ALSO Missed at this point (from the earlier step above) — asserting a single
        // "Missed" glyph exists would be ambiguous and was, in fact, wrong: run on a real device
        // this failed with "found 2 nodes", the two siblings' merged content descriptions BOTH
        // containing "Missed" (the row's own change-dialog sentence merges with the glyph's own
        // description — `Modifier.clickable`'s `shouldMergeDescendantSemantics`). The count-based
        // assertion below is what actually proves the reopen worked: two Missed glyphs now, not one.
        assertEquals(2, composeTestRule.onAllNodesWithContentDescription(text(R.string.today_slot_missed)).fetchSemanticsNodes().size)
        composeTestRule.onNodeWithContentDescription(text(R.string.today_slot_completed)).assertExists()
        composeTestRule.onAllNodesWithText(text(R.string.today_answer_yes)).assertCountEquals(0)
        composeTestRule.onAllNodesWithText(text(R.string.today_answer_no)).assertCountEquals(0)
    }

    /**
     * Scenario: "The change route is reachable without a gesture and names its own slot." Each
     * slot's whole row carries the click action now (today-one-line-row) — reachable by an
     * ordinary [performClick], no swipe or other gesture — and its accessible label differs from
     * every sibling's, asserted here by exact match against each slot's own expected sentence
     * rather than only counting nodes.
     *
     * today-row-alignment: these are multi-slot rows, so each sentence leads with its own reminder
     * time — that time is what tells the three siblings apart, and it is precisely what this test
     * exists to prove is distinct. Kept as exact matches, not substrings — the whole point is that
     * no two labels coincide, and a substring match cannot say that.
     */
    @Test
    fun eachChangeControlHasAnAccessibleLabelDistinctFromItsSiblings(): Unit = runBlocking {
        seedThreeSlotHabit("Stretch")
        expandRow()

        composeTestRule.onAllNodesWithText(text(R.string.today_answer_yes))[0].performClick()
        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.COMPLETED)
        composeTestRule.onAllNodesWithText(text(R.string.today_answer_no))[0].performClick()
        viewModel.awaitSlotStatus(slotIndex = 1, status = EntryStatus.MISSED)
        composeTestRule.onAllNodesWithText(text(R.string.today_answer_yes))[0].performClick()
        viewModel.awaitSlotStatus(slotIndex = 2, status = EntryStatus.COMPLETED)

        val morning = expectedTimeOnDevice(inTwentyFourHour = "08:00", inTwelveHour = "8:00 AM")
        val midday = expectedTimeOnDevice(inTwentyFourHour = "12:00", inTwelveHour = "12:00 PM")
        val evening = expectedTimeOnDevice(inTwentyFourHour = "20:00", inTwelveHour = "8:00 PM")
        val descriptions = listOf(
            changeDescription("Stretch", "$morning ${text(R.string.today_slot_completed)}"),
            changeDescription("Stretch", "$midday ${text(R.string.today_slot_missed)}"),
            changeDescription("Stretch", "$evening ${text(R.string.today_slot_completed)}"),
        )
        assertEquals(3, descriptions.toSet().size)
        descriptions.forEachIndexed { index, description ->
            assertEquals(1, composeTestRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size)
            // Each row's own distinct label opens ITS OWN dialog, not always the same one: reopen
            // it and flip that one slot to Missed, proving the route reached exactly that slot,
            // before moving on to the next row's own distinct label.
            composeTestRule.onNodeWithContentDescription(description).performClick()
            composeTestRule.onNodeWithText(text(R.string.today_answer_no)).performClick()
            viewModel.awaitSlotStatus(slotIndex = index, status = EntryStatus.MISSED)
        }
        // All three now read Missed — reached through three distinct routes, never a shared one.
        assertEquals(
            3,
            composeTestRule.onAllNodesWithContentDescription(text(R.string.today_slot_missed)).fetchSemanticsNodes().size,
        )
    }

    /**
     * Scenario: "A single-slot habit remains independently answerable" combined with
     * today-answered-slot-collapse's own reopen mechanics, now through [ChangeAnswerDialog] rather
     * than an inline "Cambiar" button.
     */
    @Test
    fun aHabitWithNoReminderTimeReopensAndRecollapsesItsSingleNullSlot(): Unit = runBlocking {
        fixture.habitRepository.create(newHabit("Read"), Schedule.Daily())
        viewModel.awaitOneRowWithSlots(1)

        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }
        composeTestRule.onNodeWithText(text(R.string.today_answer_yes)).performClick()
        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.COMPLETED)
        composeTestRule.onNodeWithContentDescription(text(R.string.today_slot_completed)).assertExists()
        composeTestRule.onAllNodesWithText(text(R.string.today_answer_yes)).assertCountEquals(0)

        composeTestRule.onNodeWithContentDescription(
            changeDescription("Read", text(R.string.today_slot_completed)),
        ).performClick()
        composeTestRule.onNodeWithText(text(R.string.today_answer_no)).assertExists()

        composeTestRule.onNodeWithText(text(R.string.today_answer_no)).performClick()
        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.MISSED)
        composeTestRule.onNodeWithContentDescription(text(R.string.today_slot_missed)).assertExists()
        composeTestRule.onAllNodesWithText(text(R.string.today_answer_no)).assertCountEquals(0)
        // Recollapsed with its OWN new change route, proving the row offers one again.
        composeTestRule.onNodeWithContentDescription(
            changeDescription("Read", text(R.string.today_slot_missed)),
        ).assertExists()
    }

    /**
     * design.md decision 2: `slotStatusText`'s pending branch used to prefer the snooze sentence
     * whenever `snoozedUntilEpochMs != null`, checked ahead of the status itself — that whole
     * status-text machinery is gone with today-one-line-row (a pending row shows only its answer
     * pills), but the snooze bypass this test exists to prove still applies at the data layer: an
     * `Entry` already resolved for the slot must never let a stale `SNOOZED` occurrence resurrect a
     * pending presentation over it. This constructs that state directly at the data layer — an
     * `Entry` already resolved for the slot, while its `reminder_occurrences` row is still `SNOOZED`
     * and unresolved — since answering through the UI always resolves the occurrence in the same
     * transaction and could never reproduce it.
     */
    @Test
    fun anAnsweredSlotNeverRendersTheSnoozeSentenceEvenWhileItsOccurrenceIsStillSnoozed(): Unit = runBlocking {
        val seeded = fixture.seedHabitWithEnabledSlot(name = "Journal", minuteOfDay = MORNING_MINUTE)
        val today = fixture.timeProvider.today().toString()
        val now = fixture.timeProvider.now().toString()

        fixture.database.entryDao().upsert(
            EntryEntity(
                habitId = seeded.habitId,
                date = today,
                slotId = seeded.slotId,
                status = EntryStatus.COMPLETED.name,
                value = null,
                answeredAt = now,
                source = ENTRY_SOURCE_IN_APP,
            ),
        )
        fixture.database.reminderOccurrenceDao().upsert(
            ReminderOccurrenceEntity(
                habitId = seeded.habitId,
                slotId = seeded.slotId,
                scheduledDate = today,
                scheduledAtEpochMs = fixture.timeProvider.now().toEpochMilli(),
                state = STATE_SNOOZED,
                snoozeUntilEpochMs = fixture.timeProvider.now().toEpochMilli(),
                notifiedAtEpochMs = fixture.timeProvider.now().toEpochMilli(),
                resolveDeadlineMs = fixture.timeProvider.now().toEpochMilli() + RESOLVE_DEADLINE_MS,
            ),
        )
        viewModel.awaitState("slot 0 completed and still snoozed") { state ->
            val slot = state.rows.singleOrNull()?.slots?.getOrNull(0)
            slot?.status == EntryStatus.COMPLETED && slot.snoozedUntilEpochMs != null
        }

        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }
        composeTestRule.onNodeWithContentDescription(text(R.string.today_slot_completed)).assertExists()
        composeTestRule.onNodeWithContentDescription(
            changeDescription("Journal", text(R.string.today_slot_completed)),
        ).assertExists()
        val snoozedPrefix = text(R.string.today_slot_pending_snoozed_until).substringBefore("%")
        composeTestRule.onNodeWithText(snoozedPrefix, substring = true).assertDoesNotExist()
        assertFalse(composeTestRule.onAllNodesWithText(text(R.string.today_answer_yes)).fetchSemanticsNodes().isNotEmpty())
    }
}
