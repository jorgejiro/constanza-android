package com.jjrapps.constanza.tracking

import android.content.Context
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.habit.HabitRepositoryTestFixture
import com.jjrapps.constanza.scheduling.insertHabitWithSchedule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bound for awaiting a Room-Flow-fed row. 15s rather than the 5s the older Today tests use, and
 * matched to `CoreFlowE2ETest`'s own bound, because 5s is not a statement about correctness — it is
 * headroom, and there was not enough of it. Measured: these five tests take ~7.5s in total in
 * isolation, so ~1.5s each; under the full 91-test suite on the api37 emulator two of them blew a
 * 5s wait while their identically-seeded siblings passed, and all five pass three runs out of three
 * alone. The same load-induced race `openspec/config.yaml`'s `compose-test-db-teardown-race` and
 * `TodayComposeTest`'s own KDoc already describe. Waiting longer costs nothing when the row arrives
 * on time.
 */
private const val WAIT_TIMEOUT_MS = 15_000L
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
        val habitId = fixture.database.insertHabitWithSchedule(name = LONG_HABIT_NAME)
        fixture.insertEnabledSlot(habitId, MORNING_MINUTE)

        setPhoneSizedContent()
        awaitNodeWithText(text(R.string.today_answer_skip))

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

    /** Defect 2: an answered slot reads as copy. `EntryStatus.COMPLETED.name` must not be on
     *  screen anywhere, which is exactly what the row used to render. */
    @Test
    fun anAnsweredSlotReadsAsCopyRatherThanTheEnumConstant() = runBlocking {
        val habitId = fixture.database.insertHabitWithSchedule(name = LONG_HABIT_NAME)
        fixture.insertEnabledSlot(habitId, MORNING_MINUTE)

        setPhoneSizedContent()
        awaitNodeWithText(text(R.string.today_answer_yes))
        composeTestRule.onNodeWithText(text(R.string.today_answer_yes)).performClick()

        awaitNodeWithText(text(R.string.today_slot_completed))
        composeTestRule.onNodeWithText("COMPLETED", substring = true).assertDoesNotExist()
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

    /** An idle composition is not one that has received Room's first emission; the same race, and
     *  the same fix, [TodayComposeTest] documents for its own finders. */
    private fun awaitNodeWithText(label: String) {
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
