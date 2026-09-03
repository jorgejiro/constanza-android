package com.jjrapps.constanza.tracking

import android.content.Context
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.expectedTimeOnDevice
import com.jjrapps.constanza.core.ui.unexpectedTimeOnDevice
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.habit.HabitRepositoryTestFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val MORNING_MINUTE = 8 * 60

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
     * `fix/time-format-consistency`: the slot line is `<time> — <status>`, and the time half now
     * follows the device's 12/24-hour setting instead of always being `HH:mm`.
     *
     * Asserted as the whole sentence rather than as a substring, because the sentence is the thing
     * the row renders and the em dash is the only thing joining its two halves. Both notations are
     * hand-written literals; the device picks which one applies, and the other is asserted absent —
     * a positive check on its own would still pass on a screen that had gone back to hardcoding
     * whichever cycle this device happens to use.
     */
    @Test
    fun theSlotTimeReadsInTheDeviceHourCycle() = runBlocking {
        fixture.seedHabitWithEnabledSlot(name = LONG_HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)

        setPhoneSizedContent()
        val shown = expectedTimeOnDevice(inTwentyFourHour = "08:00", inTwelveHour = "8:00 AM")

        composeTestRule
            .onNodeWithText("$shown — ${text(R.string.today_slot_pending)}")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                unexpectedTimeOnDevice(inTwentyFourHour = "08:00", inTwelveHour = "8:00 AM"),
                substring = true,
            )
            .assertDoesNotExist()
    }

    /** Defect 2: an answered slot reads as copy. `EntryStatus.COMPLETED.name` must not be on
     *  screen anywhere, which is exactly what the row used to render. */
    @Test
    fun anAnsweredSlotReadsAsCopyRatherThanTheEnumConstant() = runBlocking {
        fixture.seedHabitWithEnabledSlot(name = LONG_HABIT_NAME, minuteOfDay = MORNING_MINUTE)
        viewModel.awaitRows(1)

        setPhoneSizedContent()
        composeTestRule.onNodeWithText(text(R.string.today_answer_yes)).performClick()

        viewModel.awaitSlotStatus(slotIndex = 0, status = EntryStatus.COMPLETED)
        composeTestRule.onNodeWithText(text(R.string.today_slot_completed), substring = true).assertExists()
        composeTestRule.onNodeWithText(EntryStatus.COMPLETED.name, substring = true).assertDoesNotExist()
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
