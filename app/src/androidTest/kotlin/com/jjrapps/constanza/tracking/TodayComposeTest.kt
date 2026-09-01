package com.jjrapps.constanza.tracking

import android.content.Context
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
import com.jjrapps.constanza.reminding.NotificationPoster
import com.jjrapps.constanza.scheduling.AlarmScheduler
import io.mockk.mockk
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
        viewModel = TodayViewModel(
            fixture.habitRepository, fixture.database.entryDao(), fixture.database.reminderOccurrenceDao(),
            entryWriter, fixture.timeProvider,
        )
    }

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

        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }
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
}
