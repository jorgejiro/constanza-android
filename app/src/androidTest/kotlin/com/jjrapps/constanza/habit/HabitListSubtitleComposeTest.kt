package com.jjrapps.constanza.habit

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.TimeOfDayFormat
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.format.TextStyle

private const val WAIT_TIMEOUT_MS = 5_000L
private const val NINE_THIRTY = 9 * 60 + 30
private const val SEVEN_PM = 19 * 60
private const val NOON = 12 * 60

/**
 * Graphite redesign (`habit-management`: the list row shows a schedule subtitle). End to end through
 * the real repository and Room: a habit's schedule and reminder slots, written by the same
 * [HabitRepository.create] the editor uses, reach the row as "frequency · reminder", and a later
 * [HabitRepository.update] reaches it reactively. Expected copy is built from the resources and the
 * app's [TimeOfDayFormat] so the test holds on a 12- or 24-hour device in any language.
 */
@RunWith(AndroidJUnit4::class)
class HabitListSubtitleComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var fixture: HabitRepositoryTestFixture

    @Before
    fun setUp() {
        fixture = HabitRepositoryTestFixture(context)
    }

    @After
    fun tearDown() = fixture.close()

    private val timeFormat by lazy {
        TimeOfDayFormat(DateFormat.is24HourFormat(context), context.resources.configuration.locales[0])
    }

    private fun habit(name: String) = Habit(
        id = 0, name = name, colorArgb = 0, notes = null,
        archived = false, archivedAt = null, createdAt = Instant.parse("2026-09-01T08:00:00Z"),
    )

    private fun slot(minuteOfDay: Int, enabled: Boolean = true) =
        ReminderSlot(id = 0, habitId = 0, minuteOfDay = minuteOfDay, enabled = enabled)

    private fun showList() {
        val viewModel = fixture.habitListViewModel()
        composeTestRule.setContent {
            HabitListRoute(onBack = {}, onCreateHabit = {}, onEditHabit = {}, viewModel = viewModel)
        }
    }

    private fun awaitText(text: String) {
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(text, useUnmergedTree = true).assertExists()
    }

    @Test
    fun eachRowNamesItsScheduleAndReminder() {
        runBlocking {
            fixture.habitRepository.create(habit("Walk"), Schedule.Daily(), listOf(slot(NINE_THIRTY)))
            fixture.habitRepository.create(
                habit("Drink water"),
                Schedule.TimesPerDay(),
                listOf(slot(NINE_THIRTY), slot(NOON), slot(SEVEN_PM), slot(SEVEN_PM + 1, enabled = false)),
            )
            fixture.habitRepository.create(
                habit("Stretch"),
                Schedule.DaysOfWeek(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
                listOf(slot(SEVEN_PM)),
            )
            fixture.habitRepository.create(habit("Swim"), Schedule.NTimesPerWeek(3))
        }
        showList()

        val res = context.resources
        val locale = res.configuration.locales[0]
        awaitText("${res.getString(R.string.habit_list_summary_daily)} · ${timeFormat.format(NINE_THIRTY)}")
        awaitText(
            "${res.getString(R.string.habit_list_summary_times_per_day)} · " +
                res.getQuantityString(R.plurals.habit_list_summary_reminder_count, 3, 3),
        )
        val days = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
            .replaceFirstChar { it.titlecase(locale) }
        awaitText("$days · ${timeFormat.format(SEVEN_PM)}")
        // No reminder slot: the time part is left out entirely.
        awaitText(res.getQuantityString(R.plurals.habit_list_summary_times_per_week, 3, 3))
    }

    @Test
    fun anEditedScheduleReachesTheRowWithoutAReload() {
        val habitId = runBlocking {
            fixture.habitRepository.create(habit("Walk"), Schedule.Daily(), listOf(slot(NINE_THIRTY)))
        }
        showList()
        val res = context.resources
        awaitText("${res.getString(R.string.habit_list_summary_daily)} · ${timeFormat.format(NINE_THIRTY)}")

        runBlocking {
            val saved = requireNotNull(fixture.habitRepository.findById(habitId))
            fixture.habitRepository.update(saved, Schedule.Monthly(5), emptyList())
        }

        awaitText(res.getString(R.string.habit_list_summary_monthly, 5))
    }
}
