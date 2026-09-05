package com.jjrapps.constanza.habit

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.domain.model.Schedule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val WAIT_TIMEOUT_MS = 5_000L

/**
 * Task 6a.6 (habit-scheduling: Six Frequency Kinds). One test method per kind rather than a single
 * parameterised table: each kind drives a structurally different follow-up interaction before save
 * is safe to click — `TIMES_PER_DAY` alone needs an added slot, the other five need only the kind
 * selection itself — which a shared data table would obscure more than it would save (six of
 * something has repeatedly been this change's own multiplier signal for hidden complexity).
 *
 * Every default the six per-kind editors seed on kind switch ([defaultScheduleFor] in
 * [HabitEditorViewModel]) is already save-valid, so no test needs to touch a parameter editor —
 * only the kind picker, and for `TIMES_PER_DAY`, its own "Add time" button.
 */
@RunWith(AndroidJUnit4::class)
class HabitScheduleKindComposeTest {

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

    /** Creates a habit through the real editor UI. [targetKindLabelRes] left `null` keeps the
     *  default `DAILY` kind untouched; otherwise opens the kind picker (its anchor always shows the
     *  currently selected kind's label, `Daily` at the start of every test) and selects the target. */
    private fun createHabit(name: String, targetKindLabelRes: Int?, beforeSave: () -> Unit = {}) {
        var done = false
        // Constructed outside `setContent`, not inside it: a view model built in the composable
        // lambda is rebuilt on every recomposition, so the editor would silently lose the state
        // each test is driving. Hoisting it gives the whole test one stable instance.
        val viewModel = fixture.habitEditorViewModel()
        composeTestRule.setContent {
            HabitEditorRoute(habitId = null, onDone = { done = true }, onBack = {}, viewModel = viewModel)
        }
        composeTestRule.onNodeWithText(text(R.string.habit_editor_name_label)).performTextInput(name)
        if (targetKindLabelRes != null) {
            composeTestRule.onNodeWithText(text(R.string.schedule_kind_daily)).performClick()
            composeTestRule.onNodeWithText(text(targetKindLabelRes)).performClick()
        }
        beforeSave()
        composeTestRule.onNodeWithText(text(R.string.habit_editor_save)).performClick()
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) { done }
    }

    /** The persisted [Schedule] for the single habit each test creates — verifies both halves of
     *  6a.6's "persisted `Habit` + `Schedule`" requirement: [Habit] via the [name] match itself
     *  (only found if it was actually written), [Schedule] via its returned kind. */
    private fun persistedScheduleFor(name: String): Schedule? = runBlocking {
        val habit = fixture.database.habitDao().findAllSnapshot().single { it.name == name }
        fixture.habitRepository.findScheduleFor(habit.id)
    }

    @Test
    fun dailyKindIsPersisted() {
        createHabit("Meditate", targetKindLabelRes = null)

        assertEquals(ScheduleKind.DAILY, persistedScheduleFor("Meditate")?.kind)
    }

    @Test
    fun timesPerDayKindIsPersistedWithItsAddedSlot() {
        createHabit("Drink water", R.string.schedule_kind_times_per_day) {
            composeTestRule.onNodeWithText(text(R.string.habit_editor_slot_add)).performClick()
        }

        assertEquals(ScheduleKind.TIMES_PER_DAY, persistedScheduleFor("Drink water")?.kind)
    }

    @Test
    fun nTimesPerWeekKindIsPersisted() {
        createHabit("Gym", R.string.schedule_kind_n_times_per_week)

        assertEquals(ScheduleKind.N_TIMES_PER_WEEK, persistedScheduleFor("Gym")?.kind)
    }

    @Test
    fun daysOfWeekKindIsPersisted() {
        createHabit("Laundry", R.string.schedule_kind_days_of_week)

        assertEquals(ScheduleKind.DAYS_OF_WEEK, persistedScheduleFor("Laundry")?.kind)
    }

    @Test
    fun monthlyKindIsPersisted() {
        createHabit("Pay rent", R.string.schedule_kind_monthly)

        assertEquals(ScheduleKind.MONTHLY, persistedScheduleFor("Pay rent")?.kind)
    }

    @Test
    fun everyNDaysKindIsPersisted() {
        createHabit("Water plants", R.string.schedule_kind_every_n_days)

        assertEquals(ScheduleKind.EVERY_N_DAYS, persistedScheduleFor("Water plants")?.kind)
    }
}
