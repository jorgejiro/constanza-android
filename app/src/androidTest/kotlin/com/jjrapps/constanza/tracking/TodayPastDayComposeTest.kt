package com.jjrapps.constanza.tracking

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.habit.HabitRepositoryTestFixture
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val MORNING_MINUTE = 8 * 60
private const val HABIT_NAME = "Stretch"
private const val ENTRY_SOURCE_IN_APP = "IN_APP"

/**
 * today-past-day-correction: proves the date bar and past-day branch end to end through the real
 * [TodayRoute] UI — navigation (5.1-5.3), the hidden add-habit affordance (5.2), and unrestricted
 * re-editing (5.4). All four scenarios stay within [MORNING_MINUTE]'s single habit rather than
 * seeding four fixtures, since navigating the SAME screen across dates is exactly the mechanism
 * under test.
 *
 * Force-resolved past entries are written directly at the data layer, mirroring
 * [TodayAnsweredSlotComposeTest]'s established shape: driving them through a real occurrence
 * resolution is [TodayViewModelTest]'s job (JVM, task 2.9/2.15), and this class's job is the
 * screen that displays the result.
 */
@RunWith(AndroidJUnit4::class)
class TodayPastDayComposeTest {

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

    /** The same enum-to-copy mapping `slotStatusLabel` uses in `app/src/main`, restated here rather
     *  than called: `internal` visibility from `app/src/main` is not reliably exposed to
     *  `app/src/androidTest` in this project's AGP setup (see `TodayOneLineRowPrototype`'s own
     *  KDoc for the identical reason it duplicates colour literals instead of importing them). */
    private fun statusLabelText(status: EntryStatus): String = when (status) {
        EntryStatus.COMPLETED -> text(R.string.today_slot_completed)
        EntryStatus.MISSED -> text(R.string.today_slot_missed)
        EntryStatus.SKIPPED -> text(R.string.today_slot_skipped)
        EntryStatus.UNKNOWN -> text(R.string.today_slot_pending)
    }

    private suspend fun writePastEntry(habitId: Long, slotId: Long, date: String, status: EntryStatus) {
        fixture.database.entryDao().upsert(
            EntryEntity(
                habitId = habitId,
                date = date,
                slotId = slotId,
                status = status.name,
                value = null,
                answeredAt = fixture.timeProvider.now().toString(),
                source = ENTRY_SOURCE_IN_APP,
            ),
        )
    }

    private fun goToPreviousDay() {
        composeTestRule.onNodeWithContentDescription(text(R.string.today_previous_day)).performClick()
    }

    /**
     * `today_back_to_today` and `today_title` are both literally "Today" in English, so a plain
     * `onNodeWithText` here would ambiguously match the `TopAppBar` title as well as this
     * `TextButton` — found by the emulator matrix, not by a JVM test, since `TodayScreenTest`-style
     * unit coverage never renders the real app bar alongside this screen. `hasClickAction()`
     * disambiguates: the title `Text` carries no click action, only the button does.
     */
    private fun goToToday() {
        composeTestRule.onNode(hasText(text(R.string.today_back_to_today)) and hasClickAction()).performClick()
    }

    /**
     * Scenario 12 (design.md's testing strategy table) / task 5.1: navigate back, a force-resolved
     * `MISSED` slot shows Missed + its own change route; reopen it -> Yes -> Done. The write is
     * asserted against the PAST date's `Entry`, never today's — the load-bearing proof the whole
     * change exists for.
     *
     * today-one-line-row: the row's own change-dialog route replaces the old "Cambiar"
     * `TextButton`.
     */
    @Test
    fun navigatingToAPastMissedSlotShowsMissedAndCorrectingItWritesThePastDate(): Unit = runBlocking {
        val seeded = fixture.seedHabitWithEnabledSlot(name = HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)
        val pastDate = fixture.timeProvider.today().minusDays(1)
        writePastEntry(seeded.habitId, seeded.slotId, pastDate.toString(), EntryStatus.MISSED)

        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }
        goToPreviousDay()
        viewModel.awaitState("slot 0 missed on $pastDate") { state ->
            state.date == pastDate && state.rows.singleOrNull()?.slots?.getOrNull(0)?.status == EntryStatus.MISSED
        }

        composeTestRule.onNodeWithContentDescription(text(R.string.today_slot_missed)).assertExists()
        composeTestRule.onNodeWithContentDescription(
            changeDescription(HABIT_NAME, text(R.string.today_slot_missed)),
        ).performClick()
        composeTestRule.onNodeWithText(text(R.string.today_answer_yes)).performClick()
        viewModel.awaitState("slot 0 completed on $pastDate") { state ->
            state.date == pastDate && state.rows.singleOrNull()?.slots?.getOrNull(0)?.status == EntryStatus.COMPLETED
        }
        composeTestRule.onNodeWithContentDescription(text(R.string.today_slot_completed)).assertExists()

        val pastEntry = fixture.database.entryDao().findByHabitAndDate(seeded.habitId, pastDate.toString()).single()
        assertEquals(EntryStatus.COMPLETED.name, pastEntry.status)
        val today = fixture.timeProvider.today().toString()
        assertEquals(
            "correcting a past slot must not also write today's entry",
            0,
            fixture.database.entryDao().findByHabitAndDate(seeded.habitId, today).size,
        )
    }

    /**
     * Task 5.2: the add-habit affordance is absent while viewing a past day, and returns once the
     * user taps back to Today. Since today-add-habit-is-not-a-fab there is one affordance rather
     * than two presentations of it, so this addresses [TODAY_ADD_HABIT_FAB_TEST_TAG] in both
     * branches. `assertDoesNotExist`, not `assertIsNotEnabled`: the FAB is left out of the
     * `Scaffold` slot entirely on a past day, because the alternative — a greyed-out button — is
     * exactly the shape `today-past-day-correction` decided against for unavailable actions.
     */
    @Test
    fun addHabitAffordanceIsAbsentOnAPastDayAndReturnsAfterToday(): Unit = runBlocking {
        fixture.seedHabitWithEnabledSlot(name = HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)

        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }
        composeTestRule.onNodeWithTag(TODAY_ADD_HABIT_FAB_TEST_TAG).assertIsDisplayed()

        goToPreviousDay()
        viewModel.awaitState("viewing a past day") { it.isPastDay }
        composeTestRule.onNodeWithTag(TODAY_ADD_HABIT_FAB_TEST_TAG).assertDoesNotExist()

        goToToday()
        viewModel.awaitState("back at the live edge") { !it.isPastDay }
        // `Unit` return type declared above: this chain's own return value (`SemanticsNodeInteraction`)
        // would otherwise make this function's inferred return type non-`void`, which JUnit4 rejects
        // as an invalid test method at class-load time — found by the emulator matrix, not by
        // `compileDebugAndroidTestKotlin`, which does not check this.
        composeTestRule.onNodeWithTag(TODAY_ADD_HABIT_FAB_TEST_TAG).assertIsDisplayed()
    }

    /**
     * Task 5.3: an empty past day — nothing seeded at all, so the live-today view is already empty
     * — shows [R.string.today_empty_past], with no add-habit affordance floating over it.
     */
    @Test
    fun anEmptyPastDayShowsThePastEmptyTextAndNoButton(): Unit = runBlocking {
        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }
        composeTestRule.onNodeWithTag(TODAY_ADD_HABIT_FAB_TEST_TAG).assertIsDisplayed()

        goToPreviousDay()
        viewModel.awaitState("viewing an empty past day") { it.isPastDay }

        composeTestRule.onNodeWithText(text(R.string.today_empty_past)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TODAY_ADD_HABIT_FAB_TEST_TAG).assertDoesNotExist()
    }

    /**
     * Task 5.4 / design.md's flagged coverage gap: "any past slot is freely re-editable to any
     * status" is not restricted to `MISSED -> COMPLETED`. Drives one past slot through
     * `COMPLETED -> MISSED -> SKIPPED` twice via the row's own change dialog, exactly the JVM shape
     * `TodayViewModelTest`'s 2.15 already proved at the ViewModel level — this is that same claim
     * proved through the real screen.
     *
     * today-one-line-row: [changeSlotTo] now reopens through the row's own accessible label rather
     * than a shared "Cambiar" `TextButton`, so it has to know the CURRENT status to build that
     * label — [current] threads that through the whole `repeat` chain.
     */
    @Test
    fun aPastSlotIsFreelyReEditableThroughEveryStatusTwiceInARow(): Unit = runBlocking {
        val seeded = fixture.seedHabitWithEnabledSlot(name = HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)
        val pastDate = fixture.timeProvider.today().minusDays(1)
        writePastEntry(seeded.habitId, seeded.slotId, pastDate.toString(), EntryStatus.COMPLETED)

        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }
        goToPreviousDay()
        viewModel.awaitState("slot 0 completed on $pastDate") { state ->
            state.date == pastDate && state.rows.singleOrNull()?.slots?.getOrNull(0)?.status == EntryStatus.COMPLETED
        }

        var current = EntryStatus.COMPLETED
        repeat(2) {
            current = changeSlotTo(current, R.string.today_answer_no, EntryStatus.MISSED, pastDate)
            current = changeSlotTo(current, R.string.today_slot_skipped, EntryStatus.SKIPPED, pastDate)
            current = changeSlotTo(current, R.string.today_answer_yes, EntryStatus.COMPLETED, pastDate)
        }

        val pastEntry = fixture.database.entryDao().findByHabitAndDate(seeded.habitId, pastDate.toString()).single()
        assertEquals(EntryStatus.COMPLETED.name, pastEntry.status)
    }

    private suspend fun changeSlotTo(
        current: EntryStatus,
        optionLabelRes: Int,
        expected: EntryStatus,
        pastDate: LocalDate,
    ): EntryStatus {
        composeTestRule.onNodeWithContentDescription(
            changeDescription(HABIT_NAME, statusLabelText(current)),
        ).performClick()
        composeTestRule.onNodeWithText(text(optionLabelRes)).performClick()
        viewModel.awaitState("slot 0 reading $expected on $pastDate") { state ->
            state.date == pastDate && state.rows.singleOrNull()?.slots?.getOrNull(0)?.status == expected
        }
        return expected
    }
}
