package com.jjrapps.constanza.tracking

import android.content.Context
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.habit.HabitRepositoryTestFixture
import com.jjrapps.constanza.habit.newHabit
import com.jjrapps.constanza.reminding.NotificationPoster
import com.jjrapps.constanza.scheduling.AlarmScheduler
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SW_600DP_WIDTH = 600
private const val SW_600DP_HEIGHT = 1_000
private const val MORNING_MINUTE = 8 * 60
private const val EVENING_MINUTE = 20 * 60
private const val ANSWER_BUTTON_COUNT_PER_HABIT = 2

/** Bound for awaiting a Room-Flow-fed row; same value the sibling Today tests use. */
/** Bound for awaiting a Room-Flow-fed row. Raised from 5s to 15s with `today-add-habit`: three
 *  more Room-backed Compose classes joined this suite and the extra contention started blowing the
 *  old bound on the matrix while the same tests passed in isolation. The assertions are unchanged;
 *  only the headroom is. See `TodayAddHabitComposeTest` for the measurements. */
private const val WAIT_TIMEOUT_MS = 15_000L

/**
 * Task 6b.8 (ui-adaptive-layout: Today screen scenario). Renders the real [TodayRoute] with a
 * multi-slot habit due today, constrained to an apparent window of `sw = 600dp` via
 * [DeviceConfigurationOverride.WindowSize] — the documented Compose UI testing API for exercising
 * a specific window size in isolation, independent of the connected device's own physical
 * dimensions. Design §13.3's `adb shell wm size` device-level override is a separate manual
 * matrix run (task G.7), not this automated test.
 *
 * "No clipping or overlap" is asserted directly rather than only visually: both slot rows' time
 * labels are displayed, and their vertical bounds do not intersect.
 */
@RunWith(AndroidJUnit4::class)
class TodayAdaptiveComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fixture: HabitRepositoryTestFixture
    private lateinit var viewModel: TodayViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fixture = HabitRepositoryTestFixture(context)
        val entryWriter = EntryWriter(
            fixture.database, fixture.database.entryDao(), fixture.database.reminderOccurrenceDao(),
            mockk<AlarmScheduler>(relaxed = true), NotificationPoster(context), fixture.timeProvider,
        )
        // Explicitly stubbed: this test is about clipping/overlap, not task 6b.9's banner, and a
        // relaxed mock's default false would otherwise add an unrelated banner item to the list.
        val alarmScheduler = mockk<AlarmScheduler>(relaxed = true)
        every { alarmScheduler.canScheduleExactAlarms() } returns true
        viewModel = TodayViewModel(
            fixture.habitRepository, fixture.database.entryDao(), fixture.database.reminderOccurrenceDao(),
            entryWriter, alarmScheduler, grantedNotificationPermission(),
            neverAskedReminderSettingsStore(), fixture.timeProvider,
        )
    }

    /**
     * The scope is cancelled BEFORE the database closes, and that order is the whole point.
     * [TodayViewModel.uiState] is `stateIn(viewModelScope, SharingStarted.Eagerly, ...)`, and this
     * test builds the ViewModel by bare constructor rather than through a `ViewModelProvider`, so
     * nothing ever clears it. Left running, that eager collector keeps querying a database this
     * `close()` has already shut, and the resulting `SQLiteConnectionPool` "connection pool has been
     * closed" surfaces asynchronously — attributed to whichever test happens to be running at the
     * time, not to this one. Found as an intermittent failure in a class it had nothing to do with.
     */
    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        fixture.close()
    }

    private fun text(resId: Int) = ApplicationProvider.getApplicationContext<Context>().getString(resId)

    @Test
    fun todayScreenRendersAMultiSlotHabitWithoutClippingAtSw600dp() = runBlocking {
        val slots = listOf(
            ReminderSlot(id = 0, habitId = 0, minuteOfDay = MORNING_MINUTE, enabled = true),
            ReminderSlot(id = 0, habitId = 0, minuteOfDay = EVENING_MINUTE, enabled = true),
        )
        fixture.habitRepository.create(newHabit("Stretch"), Schedule.TimesPerDay(), slots)

        composeTestRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.WindowSize(DpSize(SW_600DP_WIDTH.dp, SW_600DP_HEIGHT.dp)),
            ) {
                TodayRoute(onManageHabits = {}, viewModel = viewModel)
            }
        }
        awaitNodeWithText(text(R.string.today_expand))
        composeTestRule.onNodeWithText(text(R.string.today_expand)).performClick()

        val morningNode = composeTestRule.onNodeWithText("08:00", substring = true)
        val eveningNode = composeTestRule.onNodeWithText("20:00", substring = true)
        morningNode.assertIsDisplayed()
        eveningNode.assertIsDisplayed()

        val morningBounds = morningNode.fetchSemanticsNode().boundsInRoot
        val eveningBounds = eveningNode.fetchSemanticsNode().boundsInRoot
        assertTrue("slot rows must not overlap vertically", morningBounds.bottom <= eveningBounds.top)

        val answerButtons = composeTestRule.onAllNodesWithText(text(R.string.today_answer_yes)).fetchSemanticsNodes()
        assertEquals(ANSWER_BUTTON_COUNT_PER_HABIT, answerButtons.size)
    }

    /**
     * `performClick()` waits for the composition to be idle, which is NOT the same as waiting for
     * the row to exist: [TodayViewModel.uiState] is fed by Room Flows through `combine`, so an idle
     * composition can simply be one that has not received the first emission yet. Clicking then
     * either misses the node or hits a row whose occurrence handle is still null.
     *
     * That is not theoretical — it failed exactly once in a full-suite run while passing four times
     * out of four in isolation, because the full suite loads the device enough to lose the race.
     * Await the node, never assume it; the same discipline the notification-post visibility and the
     * WorkManager enqueue assertions in this project already needed.
     */
    private fun awaitNodeWithText(label: String) {
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
    }

}
