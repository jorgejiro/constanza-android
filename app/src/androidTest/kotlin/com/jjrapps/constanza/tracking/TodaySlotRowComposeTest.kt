package com.jjrapps.constanza.tracking

import android.content.Context
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.ui.expectedTimeOnDevice
import com.jjrapps.constanza.core.ui.unexpectedTimeOnDevice
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.habit.HabitRepositoryTestFixture
import com.jjrapps.constanza.habit.newHabit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val MORNING_MINUTE = 8 * 60
private const val EVENING_MINUTE = 20 * 60
private const val STATE_SNOOZED = "SNOOZED"
private const val RESOLVE_DEADLINE_MS = 24L * 60 * 60 * 1_000

/** A narrow phone, pinned rather than inherited: the defect this class guards is a width defect,
 *  and a test that ran at whatever width the connected emulator happens to have would pass on a
 *  tablet leg of the matrix while the phone leg was broken. 360dp is the common Android phone
 *  width and comfortably narrower than the `sw = 600dp` `TodayAdaptiveComposeTest` pins. */
private const val PHONE_WIDTH_DP = 360
private const val PHONE_HEIGHT_DP = 800

/** The habit name from the reported Galaxy S25 screenshot, kept verbatim. A short name does not
 *  reproduce the defect, so a short name cannot prove the fix. */
private const val LONG_HABIT_NAME = "Hacer ejercicios de movilidad en la primera hora tras despertarme"

/**
 * today-row-answering-is-cramped-and-always-on, defects 1 and 2: a habit row's answer buttons must
 * fit on a phone next to a long habit name, and the slot's status must be copy rather than a Kotlin
 * constant.
 *
 * Defect 1 was reported as "Skip" wrapping mid-word to "Ski / p". That is asserted here relatively
 * rather than against a pixel constant: "Yes" is three characters and cannot wrap at any plausible
 * width, so it is the reference height a single-line button has on this device, and "Skip" must
 * match it. A wrapped label makes its button taller, and nothing else about the row does.
 *
 * today-status-icons folds in the slice that replaced the answered-slot status WORD with a glyph:
 * the scheduled-time and answered-copy scenarios below were rewritten against that design rather
 * than deleted, since their original premises (a visible status word; a single-slot row showing
 * its scheduled time) no longer hold.
 */
@RunWith(AndroidJUnit4::class)
class TodaySlotRowComposeTest {

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
    fun theAnswerLabelsStayOnOneLineNextToALongHabitNameOnAPhone() = runBlocking {
        fixture.seedHabitWithEnabledSlot(name = LONG_HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)

        setPhoneSizedContent()

        composeTestRule.onNodeWithText(text(R.string.today_answer_yes)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.today_answer_no)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.today_answer_skip)).assertIsDisplayed()

        val yes = composeTestRule.onNodeWithText(text(R.string.today_answer_yes))
            .fetchSemanticsNode().boundsInRoot
        val skip = composeTestRule.onNodeWithText(text(R.string.today_answer_skip))
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "\"${text(R.string.today_answer_skip)}\" wrapped: it is ${skip.height}px tall against " +
                "\"${text(R.string.today_answer_yes)}\"'s ${yes.height}px, and a single-line label " +
                "cannot be taller than another single-line label in the same row",
            skip.height <= yes.height,
        )
        assertTrue(
            "the answer buttons ran past the right edge of a ${PHONE_WIDTH_DP}dp screen",
            skip.right <= composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot.right,
        )
    }

    /**
     * today-status-icons, point 3: on a single-slot habit [TodaySlot.minuteOfDay] renders nothing
     * at all any more — not even demoted — because the row's name and (once answered) its status
     * glyph already say everything it has to say. This replaces
     * `theSlotTimeReadsInTheDeviceHourCycle`, which asserted the opposite of this on the identical
     * single-slot shape; that premise is exactly what today-status-icons retired.
     */
    @Test
    fun theSlotTimeIsAbsentOnASingleSlotHabit() = runBlocking {
        fixture.seedHabitWithEnabledSlot(name = LONG_HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)

        setPhoneSizedContent()

        // Exact match, not substring: if a time were still appended after a gap, this node's text
        // would no longer equal the bare word and this assertion would fail.
        composeTestRule.onNodeWithText(text(R.string.today_slot_pending)).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(expectedTimeOnDevice(inTwentyFourHour = "08:00", inTwelveHour = "8:00 AM"), substring = true)
            .assertDoesNotExist()
    }

    /**
     * The multi-slot counterpart of the test above, and what
     * `fix/time-format-consistency`'s original scenario (the slot time follows the device's
     * 12/24-hour setting) now lives as: on a habit with more than one slot the scheduled time MUST
     * still render, per today-status-icons point 3, and it still has to respect the device's own
     * hour-cycle setting.
     *
     * today-row-alignment: the sentence is `<time><gap><status>` here — the time leads — because a
     * multi-slot slot's own reminder time is its identity, the opposite order from a single-slot
     * row. See `slotStatusText`'s decision 4 for why the two orders are deliberate.
     * [TODAY_SLOT_STATUS_GAP] is read from the screen rather than re-typed as three invisible
     * spaces so this assertion cannot quietly rot into a whitespace mismatch.
     */
    @Test
    fun theSlotTimeReadsInTheDeviceHourCycleOnAMultiSlotHabit() = runBlocking {
        val slots = listOf(
            ReminderSlot(id = 0, habitId = 0, minuteOfDay = MORNING_MINUTE, enabled = true),
            ReminderSlot(id = 0, habitId = 0, minuteOfDay = EVENING_MINUTE, enabled = true),
        )
        fixture.habitRepository.create(newHabit(LONG_HABIT_NAME), Schedule.TimesPerDay(), slots)
        viewModel.awaitOneRowWithSlots(2)

        setPhoneSizedContent()
        composeTestRule.onNodeWithText(text(R.string.today_expand)).performClick()
        val shown = expectedTimeOnDevice(inTwentyFourHour = "08:00", inTwelveHour = "8:00 AM")

        composeTestRule
            .onNodeWithText("$shown$TODAY_SLOT_STATUS_GAP${text(R.string.today_slot_pending)}")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                unexpectedTimeOnDevice(inTwentyFourHour = "08:00", inTwelveHour = "8:00 AM"),
                substring = true,
            )
            .assertDoesNotExist()
    }

    /** Defect 2, today-status-icons revision: an answered slot's state is carried by its glyph's
     *  `contentDescription`, which reads as copy, rather than by `EntryStatus.COMPLETED.name` ever
     *  reaching the screen in any form — as text, or as an accessible label. */
    @Test
    fun anAnsweredSlotReadsAsCopyRatherThanTheEnumConstant() = runBlocking {
        fixture.seedHabitWithEnabledSlot(name = LONG_HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)

        setPhoneSizedContent()
        composeTestRule.onNodeWithText(text(R.string.today_answer_yes)).performClick()

        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.COMPLETED)
        composeTestRule.onNodeWithContentDescription(text(R.string.today_slot_completed)).assertExists()
        composeTestRule.onNodeWithText(EntryStatus.COMPLETED.name, substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(EntryStatus.COMPLETED.name, substring = true).assertDoesNotExist()
    }

    /**
     * today-status-icons, point 1: each of the three answered states renders its own glyph, and the
     * glyph's `contentDescription` is the existing status string resource — never blank — since the
     * glyph is now the ONLY carrier of that state on screen.
     */
    @Test
    fun eachAnsweredStatusRendersItsOwnGlyphWithANonEmptyContentDescription() = runBlocking {
        fixture.seedHabitWithEnabledSlot(name = LONG_HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)
        setPhoneSizedContent()

        composeTestRule.onNodeWithText(text(R.string.today_answer_yes)).performClick()
        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.COMPLETED)
        val completedLabel = text(R.string.today_slot_completed)
        assertTrue(completedLabel.isNotBlank())
        composeTestRule.onNodeWithContentDescription(completedLabel).assertExists()

        composeTestRule.onNodeWithText(text(R.string.today_slot_change)).performClick()
        composeTestRule.onNodeWithText(text(R.string.today_answer_no)).performClick()
        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.MISSED)
        val missedLabel = text(R.string.today_slot_missed)
        assertTrue(missedLabel.isNotBlank())
        composeTestRule.onNodeWithContentDescription(missedLabel).assertExists()

        composeTestRule.onNodeWithText(text(R.string.today_slot_change)).performClick()
        composeTestRule.onNodeWithText(text(R.string.today_answer_skip)).performClick()
        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.SKIPPED)
        val skippedLabel = text(R.string.today_slot_skipped)
        assertTrue(skippedLabel.isNotBlank())
        composeTestRule.onNodeWithContentDescription(skippedLabel).assertExists()
    }

    /**
     * today-status-icons: "Do not touch the snoozed row" — a snoozed slot is `UNKNOWN`/pending, not
     * answered, so [SlotRow] never reaches [AnsweredStatusRow] for it. Written directly at the data
     * layer, mirroring [TodayAnsweredSlotComposeTest]'s established shape for constructing a snooze
     * state: an unresolved, `SNOOZED` `reminder_occurrences` row with no [EntryEntity] behind it at
     * all, which answering through the UI could never reproduce (that always resolves the
     * occurrence in the same transaction).
     */
    @Test
    fun aSnoozedPendingSlotShowsItsAplazadoTextAndNoStatusGlyph() = runBlocking {
        val seeded = fixture.seedHabitWithEnabledSlot(name = "Journal", minuteOfDay = MORNING_MINUTE)
        val today = fixture.timeProvider.today().toString()
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
        viewModel.awaitState("slot 0 pending and snoozed") { state ->
            val slot = state.rows.singleOrNull()?.slots?.getOrNull(0)
            slot?.status == EntryStatus.UNKNOWN && slot.snoozedUntilEpochMs != null
        }

        setPhoneSizedContent()
        val snoozedPrefix = text(R.string.today_slot_pending_snoozed_until).substringBefore("%")
        composeTestRule.onNodeWithText(snoozedPrefix, substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.today_answer_yes)).assertIsDisplayed()

        // No answered glyph exists anywhere on this single-row screen: a pending/snoozed slot never
        // reaches AnsweredStatusRow, whichever of the three status words its contentDescription
        // would otherwise have carried.
        composeTestRule.onNodeWithContentDescription(text(R.string.today_slot_completed)).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(text(R.string.today_slot_missed)).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(text(R.string.today_slot_skipped)).assertDoesNotExist()
    }

    private fun setPhoneSizedContent() {
        composeTestRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.WindowSize(DpSize(PHONE_WIDTH_DP.dp, PHONE_HEIGHT_DP.dp)),
            ) {
                TodayRoute(onManageHabits = {}, viewModel = viewModel)
            }
        }
    }
}
