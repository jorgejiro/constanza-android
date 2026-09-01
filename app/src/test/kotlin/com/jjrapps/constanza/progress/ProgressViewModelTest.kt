package com.jjrapps.constanza.progress

import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.habit.HabitRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

private const val HABIT_ID = 1L

/**
 * habit-management: Habit Archiving — "excluded from streak and compliance calculations for any
 * date on or after the archive date". [ProgressViewModel.buildState] and its `effectiveToday`
 * helper are the only place this rule is expressed; `:domain`'s [com.jjrapps.constanza.domain.StreakCalculator]
 * and [com.jjrapps.constanza.domain.ComplianceCalculator] stay pure and take no archive parameter,
 * so this suite is the sole coverage for the archive boundary itself. It also closes the
 * previously-missing `ProgressViewModel` rendering test noted as carried debt in `tasks.md`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    private val habitRepository = mockk<HabitRepository>()
    private val entryDao = mockk<EntryDao>()
    private val timeProvider = mockk<TimeProvider>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = ProgressViewModel(habitRepository, entryDao, timeProvider)

    private fun stub(habit: Habit, schedule: Schedule, entries: List<EntryEntity>, today: LocalDate) {
        coEvery { habitRepository.findById(HABIT_ID) } returns habit
        coEvery { habitRepository.findScheduleFor(HABIT_ID) } returns schedule
        every { entryDao.observeByHabitId(HABIT_ID) } returns flowOf(entries)
        every { timeProvider.today() } returns today
    }

    private fun habit(archived: Boolean, archivedAt: LocalDate?) = Habit(
        id = HABIT_ID,
        name = "Read",
        question = null,
        colorArgb = 0,
        notes = null,
        archived = archived,
        archivedAt = archivedAt,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        sortOrder = 0,
    )

    private fun entry(date: String, status: String) = EntryEntity(
        habitId = HABIT_ID, date = date, slotId = 0, status = status,
        value = null, answeredAt = "${date}T08:00:00Z", source = "IN_APP",
    )

    /**
     * Pins the spec's own scenario (habit-management: "Archived habit excluded from compliance
     * going forward") almost verbatim: archived 2026-09-10, window spans before and after it,
     * dates on or after are excluded, dates before remain included. Pre-archive entries alone give
     * 3 completed / 1 missed = 0.75; the on/after pair (one COMPLETED, one MISSED) is deliberately
     * non-cancelling — if either leaked in, the ratio would read 0.6667, not 0.75 — so the test
     * cannot pass by luck of the pair balancing out.
     */
    @Test
    fun `archived habit is excluded from compliance going forward`() = runTest {
        val archivedAt = LocalDate.parse("2026-09-10")
        val entries = listOf(
            entry("2026-09-06", "COMPLETED"),
            entry("2026-09-07", "MISSED"),
            entry("2026-09-08", "COMPLETED"),
            entry("2026-09-09", "COMPLETED"),
            entry("2026-09-10", "COMPLETED"), // on archivedAt — must be excluded
            entry("2026-09-11", "MISSED"), // after archivedAt — must be excluded
        )
        stub(
            habit(archived = true, archivedAt = archivedAt),
            Schedule.Daily(),
            entries,
            today = LocalDate.parse("2026-09-20"),
        )
        val viewModel = newViewModel()

        viewModel.load(HABIT_ID)

        assertEquals(0.75, viewModel.uiState.value.complianceRatio)
    }

    /**
     * The boundary the spec's "on or after" wording makes explicit: an entry dated exactly on
     * [archivedAt] and one dated after it must BOTH be excluded, while an entry strictly before
     * stays counted. Only the pre-archive MISSED entry may count; if either boundary entry leaked
     * in, the ratio would read 0.6667 (2 completed / 3 total) instead of 0.0.
     */
    @Test
    fun `an entry dated on or after the archive date is excluded, one strictly before is not`() = runTest {
        val archivedAt = LocalDate.parse("2026-09-10")
        val entries = listOf(
            entry("2026-09-08", "MISSED"), // strictly before — counted
            entry("2026-09-10", "COMPLETED"), // on archivedAt — excluded
            entry("2026-09-12", "COMPLETED"), // after archivedAt — excluded
        )
        stub(
            habit(archived = true, archivedAt = archivedAt),
            Schedule.Daily(),
            entries,
            today = LocalDate.parse("2026-09-20"),
        )
        val viewModel = newViewModel()

        viewModel.load(HABIT_ID)

        assertEquals(0.0, viewModel.uiState.value.complianceRatio)
    }

    /**
     * Regression guard: a non-archived habit's numbers are exactly what `:domain` would compute
     * directly against unfiltered entries and the real `today` — the archive filter must not fire
     * for it.
     */
    @Test
    fun `a non-archived habit's compliance is unaffected by the archive filter`() = runTest {
        val entries = listOf(
            entry("2026-09-06", "COMPLETED"),
            entry("2026-09-07", "MISSED"),
        )
        stub(
            habit(archived = false, archivedAt = null),
            Schedule.Daily(),
            entries,
            today = LocalDate.parse("2026-09-07"),
        )
        val viewModel = newViewModel()

        viewModel.load(HABIT_ID)

        assertEquals(0.5, viewModel.uiState.value.complianceRatio)
    }

    /**
     * Design decision: a CURRENT streak freezes at the moment of archiving rather than reading
     * live against real "today" — an archived habit is closed history, not an ongoing run. This
     * matters concretely for `N_TIMES_PER_WEEK`: without clamping `today`, every entry-less week
     * the walk crosses after archiving reads as a missed quota (unlike `UNKNOWN`'s neutral
     * pass-through for a daily schedule), so an unclamped implementation would zero the streak
     * instead of freezing it at what it was when the habit was archived.
     */
    @Test
    fun `current streak freezes at the archive date instead of reading against real today`() = runTest {
        val archivedAt = LocalDate.parse("2026-09-10") // Thursday
        val schedule = Schedule.NTimesPerWeek(times = 3, weekStart = DayOfWeek.MONDAY)
        val entries = listOf(
            // Week of 2026-08-31 (Mon): quota of 3 met -> streak run = 1
            entry("2026-08-31", "COMPLETED"),
            entry("2026-09-01", "COMPLETED"),
            entry("2026-09-02", "COMPLETED"),
            // Week of 2026-09-07 (Mon): still in progress when archived, quota not met (2 of 3)
            entry("2026-09-07", "COMPLETED"),
            entry("2026-09-08", "COMPLETED"),
        )
        stub(
            habit(archived = true, archivedAt = archivedAt),
            schedule,
            entries,
            today = LocalDate.parse("2026-09-25"), // long after archiving
        )
        val viewModel = newViewModel()

        viewModel.load(HABIT_ID)

        assertEquals(1, viewModel.uiState.value.currentStreak)
    }
}
