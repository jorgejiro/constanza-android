package com.jjrapps.constanza.habit

import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val WAIT_TIMEOUT_MS = 5_000L

/** 21:05 — chosen so BOTH halves prove the zero-padding claim: an hour that needs none and a
 *  minute that does. The old two-field editor rendered this exact slot as `21` and `0`. */
private const val STORED_MINUTE_OF_DAY = 21 * 60 + 5
private const val STORED_TIME = "21:05"
private const val STORED_HOUR = "21"
private const val STORED_MINUTE = "05"

/** 07:45 — the hour proves padding survives a round trip through the picker, and neither digit
 *  collides with [STORED_TIME]'s. */
private const val PICKED_HOUR = "07"
private const val PICKED_MINUTE = "45"
private const val PICKED_MINUTE_OF_DAY = 7 * 60 + 45
private const val PICKED_TIME = "07:45"

/**
 * The habit editor's reminder-time control (`fix/habit-editor-time-picker`). Before this, the time
 * was two bare `OutlinedTextField`s labelled "Hour" and "Minute" that you typed raw integers into,
 * and that rendered five past nine in the evening as `21` and `0`.
 *
 * Every test drives the **dialog's keyboard-entry mode** rather than the clock dial, and that is a
 * deliberate choice about what an instrumented test can hold honestly. Asserting a `minuteOfDay` a
 * dial gesture produced would mean injecting a touch at a computed angle on a circle whose radius
 * and number placement are Material 3's to change, so a passing test would be an assertion about
 * `material3`'s layout maths rather than about this app. Keyboard entry reaches the same
 * `TimePickerState` through the same dialog and the same confirm button, so the contract this app
 * actually owns — the row's format, what the confirm button emits, and what dismissing does not
 * emit — is fully covered. The dial's own correctness is `material3`'s test suite's job; that it
 * *renders* here, in this palette, is a look-at-it check, not an assertion.
 */
@RunWith(AndroidJUnit4::class)
class HabitEditorTimePickerComposeTest {

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

    /** A `DAILY` habit carrying one reminder slot at [STORED_MINUTE_OF_DAY], so the editor opens
     *  with the switch already on and the reminder-time row already showing a real time. */
    private fun habitWithStoredReminder(): Long = runBlocking {
        fixture.habitRepository.create(
            newHabit("Read before bed"),
            Schedule.Daily(),
            listOf(ReminderSlot(id = 0, habitId = 0, minuteOfDay = STORED_MINUTE_OF_DAY, enabled = true)),
        )
    }

    private fun openEditor(habitId: Long, onDone: () -> Unit = {}) {
        val viewModel = HabitEditorViewModel(fixture.habitRepository, fixture.timeProvider)
        composeTestRule.setContent {
            HabitEditorRoute(habitId = habitId, onDone = onDone, onBack = {}, viewModel = viewModel)
        }
        // startEdit loads through a coroutine, so the row is not there the instant setContent
        // returns: wait for the stored time to actually render before any test touches it.
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(STORED_TIME).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Opens the dialog and switches it to keyboard entry — see the class KDoc for why. */
    private fun openPickerInInputMode() {
        composeTestRule.onNodeWithText(STORED_TIME).performClick()
        composeTestRule.onNodeWithTag(REMINDER_TIME_MODE_TOGGLE_TEST_TAG).performClick()
    }

    /** The keyboard-entry field currently reading [value]. `hasSetTextAction` is load-bearing, not
     *  belt-and-braces: Material 3 renders each half of the time twice in this mode — once as the
     *  editable field and once as the selectable display above it — so plain text alone matches two
     *  nodes and the action would land on whichever the tree happened to yield first. */
    private fun timeField(value: String) =
        composeTestRule.onNode(hasText(value) and hasSetTextAction())

    @Test
    fun theReminderTimeRowShowsTheStoredTimeZeroPadded() {
        openEditor(habitWithStoredReminder())

        composeTestRule.onNodeWithText(text(R.string.habit_editor_reminder_time_label)).assertExists()
        composeTestRule.onNodeWithText(STORED_TIME).assertExists()
    }

    /**
     * The row is ONE node, not a label and a value that happen to sit next to a hit target: the
     * same node carries the label, the time, the click action and [Role.Button], which is what
     * makes a screen reader announce it as "Reminder time, 21:05, button" instead of reading three
     * unrelated fragments. Asserted rather than assumed because it does not come for free —
     * `Surface`'s own `onClick` overload sets no role, and `ReminderTimeField` has a comment
     * explaining the arrangement that does.
     */
    @Test
    fun theReminderTimeRowIsASingleButtonNodeCarryingBothItsLabelAndItsValue() {
        openEditor(habitWithStoredReminder())

        composeTestRule.onNodeWithText(STORED_TIME)
            .assert(hasText(text(R.string.habit_editor_reminder_time_label)))
            .assert(hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun tappingTheReminderTimeRowOpensTheTimePicker() {
        openEditor(habitWithStoredReminder())

        composeTestRule.onNodeWithText(STORED_TIME).performClick()

        composeTestRule.onNodeWithText(text(R.string.action_ok)).assertExists()
        composeTestRule.onNodeWithTag(REMINDER_TIME_MODE_TOGGLE_TEST_TAG).assertExists()
    }

    @Test
    fun confirmingANewTimeUpdatesTheRowAndPersistsThatMinuteOfDay() {
        var done = false
        val habitId = habitWithStoredReminder()
        openEditor(habitId) { done = true }

        openPickerInInputMode()
        timeField(STORED_HOUR).performTextReplacement(PICKED_HOUR)
        timeField(STORED_MINUTE).performTextReplacement(PICKED_MINUTE)
        composeTestRule.onNodeWithText(text(R.string.action_ok)).performClick()

        composeTestRule.onNodeWithText(PICKED_TIME).assertExists()
        composeTestRule.onNodeWithText(text(R.string.habit_editor_save)).performClick()
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) { done }
        val persisted = runBlocking { fixture.database.reminderSlotDao().findByHabitId(habitId) }.single()
        assertEquals(PICKED_MINUTE_OF_DAY, persisted.minuteOfDay)
    }

    @Test
    fun dismissingThePickerLeavesTheStoredTimeUntouched() {
        openEditor(habitWithStoredReminder())

        openPickerInInputMode()
        timeField(STORED_HOUR).performTextReplacement(PICKED_HOUR)
        composeTestRule.onNodeWithText(text(R.string.action_cancel)).performClick()

        composeTestRule.onNodeWithText(STORED_TIME).assertExists()
        composeTestRule.onNodeWithText(text(R.string.action_ok)).assertDoesNotExist()
    }
}
