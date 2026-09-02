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
import com.jjrapps.constanza.core.ui.deviceUses24HourTime
import com.jjrapps.constanza.core.ui.expectedTimeOnDevice
import com.jjrapps.constanza.core.ui.unexpectedTimeOnDevice
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
private const val MINUTES_PER_HOUR = 60

/** 21:05 — chosen so BOTH halves prove the zero-padding claim on a 24-hour device: an hour that
 *  needs none and a minute that does. The old two-field editor rendered this exact slot as `21`
 *  and `0`. It is also an afternoon time, which is what makes the 12-hour rendering interesting:
 *  it has to wrap past noon AND carry a `PM`. */
private const val STORED_MINUTE_OF_DAY = 21 * MINUTES_PER_HOUR + 5

/** 07:45 — the hour proves padding survives a round trip through the picker on a 24-hour device,
 *  neither digit collides with the stored time's, and it is in the OTHER half of the day, so on a
 *  12-hour device confirming it also has to move the AM/PM selector. */
private const val PICKED_MINUTE_OF_DAY = 7 * MINUTES_PER_HOUR + 45

/** M3's own AM label (`Strings.TimePickerAM`), which is what the period toggle renders. */
private const val MORNING_PERIOD_LABEL = "AM"

/**
 * The habit editor's reminder-time control (`fix/habit-editor-time-picker`). Before this, the time
 * was two bare `OutlinedTextField`s labelled "Hour" and "Minute" that you typed raw integers into,
 * and that rendered five past nine in the evening as `21` and `0`.
 *
 * **This class now runs in whichever hour cycle the device is set to** (`fix/time-format-consistency`).
 * The control used to hardcode 24-hour; it follows `Settings > System > Date & time` now, so every
 * expected string here is one of two hand-written literals chosen by
 * [com.jjrapps.constanza.core.ui.expectedTimeOnDevice] — not a string produced by asking the
 * production formatter what it would print, which would assert nothing. The emulators
 * `:app:emulatorMatrixGroupDebugAndroidTest` provisions run `en-US` and therefore take the 12-hour
 * branch; the 24-hour branch runs on a device set that way.
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

    /** How the row renders [STORED_MINUTE_OF_DAY] on this device. */
    private val storedTime = expectedTimeOnDevice(inTwentyFourHour = "21:05", inTwelveHour = "9:05 PM")

    /** How the row renders [PICKED_MINUTE_OF_DAY] on this device. */
    private val pickedTime = expectedTimeOnDevice(inTwentyFourHour = "07:45", inTwelveHour = "7:45 AM")

    /** M3 pads the keyboard-entry hour field to two digits in BOTH cycles — `hourForDisplay` of
     *  21 is 9 on a 12-hour clock, and the field still shows `09`. */
    private val storedHourField = expectedTimeOnDevice(inTwentyFourHour = "21", inTwelveHour = "09")
    private val storedMinuteField = "05"
    private val pickedHourField = expectedTimeOnDevice(inTwentyFourHour = "07", inTwelveHour = "07")
    private val pickedMinuteField = "45"

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
            composeTestRule.onAllNodesWithText(storedTime).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Opens the dialog and switches it to keyboard entry — see the class KDoc for why. */
    private fun openPickerInInputMode() {
        composeTestRule.onNodeWithText(storedTime).performClick()
        composeTestRule.onNodeWithTag(REMINDER_TIME_MODE_TOGGLE_TEST_TAG).performClick()
    }

    /**
     * Moves the picker into the morning half of the day, which only exists on a 12-hour device.
     *
     * Load-bearing rather than tidy-up: [STORED_MINUTE_OF_DAY] is `PM` and [PICKED_MINUTE_OF_DAY]
     * is `AM`, and M3's keyboard entry maps a typed `07` onto `19:xx` while the period toggle still
     * says PM. Without this tap the confirmed value would be an hour the test never asked for. On a
     * 24-hour device there is no toggle and nothing to do — the hour field carries the whole answer.
     */
    private fun selectMorningPeriod() {
        if (!deviceUses24HourTime()) {
            composeTestRule.onNodeWithText(MORNING_PERIOD_LABEL).performClick()
        }
    }

    /** The keyboard-entry field currently reading [value]. `hasSetTextAction` is load-bearing, not
     *  belt-and-braces: Material 3 renders each half of the time twice in this mode — once as the
     *  editable field and once as the selectable display above it — so plain text alone matches two
     *  nodes and the action would land on whichever the tree happened to yield first. */
    private fun timeField(value: String) =
        composeTestRule.onNode(hasText(value) and hasSetTextAction())

    /**
     * The row renders the stored slot in the device's own notation — and, just as importantly, not
     * in the other one. The negative half is what actually catches a screen that has gone back to
     * hardcoding a cycle: a positive assertion alone would still pass on whichever device happened
     * to agree with the hardcoded choice.
     *
     * Both literals keep a padded minute (`21:05` / `9:05 PM`), which is the original `21` and `0`
     * defect this class was written for.
     */
    @Test
    fun theReminderTimeRowShowsTheStoredTimeInTheDeviceHourCycle() {
        openEditor(habitWithStoredReminder())

        composeTestRule.onNodeWithText(text(R.string.habit_editor_reminder_time_label)).assertExists()
        composeTestRule.onNodeWithText(storedTime).assertExists()
        composeTestRule
            .onNodeWithText(unexpectedTimeOnDevice(inTwentyFourHour = "21:05", inTwelveHour = "9:05 PM"))
            .assertDoesNotExist()
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

        composeTestRule.onNodeWithText(storedTime)
            .assert(hasText(text(R.string.habit_editor_reminder_time_label)))
            .assert(hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun tappingTheReminderTimeRowOpensTheTimePicker() {
        openEditor(habitWithStoredReminder())

        composeTestRule.onNodeWithText(storedTime).performClick()

        composeTestRule.onNodeWithText(text(R.string.action_ok)).assertExists()
        composeTestRule.onNodeWithTag(REMINDER_TIME_MODE_TOGGLE_TEST_TAG).assertExists()
    }

    /**
     * The picker itself follows the device too, which is the half of `fix/time-format-consistency`
     * a row assertion cannot see: `ReminderTimeField` hands the same `is24Hour` to the row's format
     * and to `TimePickerState`, so the AM/PM toggle is present exactly when the row is writing
     * `9:05 PM` and absent exactly when it is writing `21:05`.
     */
    @Test
    fun thePickerOffersAPeriodSelectorOnlyWhenTheDeviceUsesA12HourClock() {
        openEditor(habitWithStoredReminder())

        openPickerInInputMode()

        if (deviceUses24HourTime()) {
            composeTestRule.onNodeWithText(MORNING_PERIOD_LABEL).assertDoesNotExist()
        } else {
            composeTestRule.onNodeWithText(MORNING_PERIOD_LABEL).assertExists()
        }
    }

    @Test
    fun confirmingANewTimeUpdatesTheRowAndPersistsThatMinuteOfDay() {
        var done = false
        val habitId = habitWithStoredReminder()
        openEditor(habitId) { done = true }

        openPickerInInputMode()
        selectMorningPeriod()
        timeField(storedHourField).performTextReplacement(pickedHourField)
        timeField(storedMinuteField).performTextReplacement(pickedMinuteField)
        composeTestRule.onNodeWithText(text(R.string.action_ok)).performClick()

        composeTestRule.onNodeWithText(pickedTime).assertExists()
        composeTestRule.onNodeWithText(text(R.string.habit_editor_save)).performClick()
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) { done }
        val persisted = runBlocking { fixture.database.reminderSlotDao().findByHabitId(habitId) }.single()
        assertEquals(PICKED_MINUTE_OF_DAY, persisted.minuteOfDay)
    }

    @Test
    fun dismissingThePickerLeavesTheStoredTimeUntouched() {
        openEditor(habitWithStoredReminder())

        openPickerInInputMode()
        timeField(storedHourField).performTextReplacement(pickedHourField)
        composeTestRule.onNodeWithText(text(R.string.action_cancel)).performClick()

        composeTestRule.onNodeWithText(storedTime).assertExists()
        composeTestRule.onNodeWithText(text(R.string.action_ok)).assertDoesNotExist()
    }
}
