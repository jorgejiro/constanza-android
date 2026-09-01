package com.jjrapps.constanza.tracking

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.habit.HabitRepositoryTestFixture
import com.jjrapps.constanza.habit.newHabit
import com.jjrapps.constanza.reminding.NotificationPoster
import com.jjrapps.constanza.scheduling.AlarmScheduler
import com.jjrapps.constanza.scheduling.insertHabitWithSchedule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val WAIT_TIMEOUT_MS = 5_000L
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
        val context = ApplicationProvider.getApplicationContext<Context>()
        fixture = HabitRepositoryTestFixture(context)
        val entryWriter = EntryWriter(
            fixture.database, fixture.database.entryDao(), fixture.database.reminderOccurrenceDao(),
            mockk<AlarmScheduler>(relaxed = true), NotificationPoster(context), fixture.timeProvider,
        )
        // Explicitly stubbed for the same reason as EntryWriteParityTest: a relaxed mock's default
        // false would spuriously arm task 6b.9's banner branch in a test that is not about it.
        val alarmScheduler = mockk<AlarmScheduler>(relaxed = true)
        every { alarmScheduler.canScheduleExactAlarms() } returns true
        viewModel = TodayViewModel(
            fixture.habitRepository, fixture.database.entryDao(), fixture.database.reminderOccurrenceDao(),
            entryWriter, alarmScheduler, fixture.timeProvider,
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
    fun answeringOneSlotLeavesTheSiblingSlotUnknown() = runBlocking {
        val slots = listOf(
            ReminderSlot(id = 0, habitId = 0, minuteOfDay = MORNING_MINUTE, enabled = true),
            ReminderSlot(id = 0, habitId = 0, minuteOfDay = EVENING_MINUTE, enabled = true),
        )
        val habitId = fixture.habitRepository.create(newHabit("Stretch"), Schedule.TimesPerDay(), slots)

        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }
        awaitNodeWithText(text(R.string.today_expand))
        composeTestRule.onNodeWithText(text(R.string.today_expand)).performClick()
        composeTestRule.onAllNodesWithText(text(R.string.today_answer_yes))[0].performClick()
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(EntryStatus.COMPLETED.name, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

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
        val habitId = fixture.database.insertHabitWithSchedule(name = "Journal")
        val slotId = fixture.insertEnabledSlot(habitId, EVENING_MINUTE)

        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }
        awaitNodeWithText(text(R.string.today_answer_skip))
        composeTestRule.onNodeWithText(text(R.string.today_answer_skip)).performClick()
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(EntryStatus.SKIPPED.name, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val entry = fixture.database.entryDao().findByHabitId(habitId).single()
        assertEquals(EntryStatus.SKIPPED.name, entry.status)
        assertEquals(fixture.timeProvider.today().toString(), entry.date)
        assertEquals(slotId, entry.slotId)
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
