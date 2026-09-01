package com.jjrapps.constanza.tracking

import app.cash.turbine.test
import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.dao.ReminderOccurrenceDao
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.domain.model.DayStatus
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.habit.HabitRepository
import com.jjrapps.constanza.scheduling.AlarmScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val TODAY: LocalDate = LocalDate.parse("2026-09-01")
private val FIXED_INSTANT: Instant = Instant.parse("2026-09-01T08:00:00Z")
private val ZONE: ZoneId = ZoneId.of("UTC")
private const val HABIT_ID = 7L
private const val HABIT_COLOR_ARGB = 0xFF5DD6C7.toInt() // HabitColor.TEAL, arbitrary for this test
private const val MORNING_SLOT_ID = 1L
private const val EVENING_SLOT_ID = 2L
private const val OCCURRENCE_ID = 11L
private const val MORNING_MINUTE = 8 * 60
private const val EVENING_MINUTE = 20 * 60
private const val SNOOZE_UNTIL_MS = 1_800_000_000_000L
private const val STATE_ARMED = "ARMED"
private const val STATE_SNOOZED = "SNOOZED"

private fun habit(id: Long = HABIT_ID, name: String = "Read", colorArgb: Int = HABIT_COLOR_ARGB) = Habit(
    id = id, name = name, question = null, colorArgb = colorArgb, notes = null,
    archived = false, archivedAt = null, createdAt = FIXED_INSTANT, sortOrder = 0,
)

private fun slot(id: Long, minuteOfDay: Int) =
    ReminderSlot(id = id, habitId = HABIT_ID, minuteOfDay = minuteOfDay, enabled = true)

private fun entryEntity(slotId: Long, status: EntryStatus) = EntryEntity(
    habitId = HABIT_ID, date = TODAY.toString(), slotId = slotId, status = status.name,
    value = null, answeredAt = FIXED_INSTANT.toString(), source = "IN_APP",
)

private fun occurrence(
    id: Long,
    slotId: Long,
    state: String,
    snoozeUntilEpochMs: Long?,
    scheduledDate: LocalDate = TODAY,
) = ReminderOccurrenceEntity(
    id = id, habitId = HABIT_ID, slotId = slotId, scheduledDate = scheduledDate.toString(), scheduledAtEpochMs = 0,
    state = state, snoozeUntilEpochMs = snoozeUntilEpochMs, snoozeCount = 0, notifiedAtEpochMs = null,
    resolveDeadlineMs = 0,
)

/**
 * Task 6b.7 debt item 3 — the JVM-level cover for the Today state that needed no Android at all.
 * [buildTodayHabitRow] is a pure join, so the rollup, the per-slot identity and the pending/snoozed
 * state are all assertable here; only the actual string rendering stays in `TodayComposeTest`.
 * [TodayViewModel]'s own contribution — the never-persisted expansion set and the single write path
 * — is exercised over the same fixtures rather than a second set.
 *
 * [TodayViewModel]'s collaborators are stubbed explicitly, never `mockk(relaxed = true)`: a relaxed
 * mock answers `false` for `Habit.archived` and empty for every `Flow`, which would quietly produce
 * a screen with no rows and an assertion that proves nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Two slots, one live occurrence: the rollup collapses the day (`:domain`'s `rollupDay`, never
     *  reimplemented) while each slot keeps the identity an independent answer needs. */
    @Test
    fun `the rollup collapses a mixed multi-slot day while each slot keeps its own status`() {
        val snapshot = TodaySnapshot(
            entriesToday = listOf(entryEntity(MORNING_SLOT_ID, EntryStatus.COMPLETED)),
            unresolvedOccurrences = listOf(occurrence(OCCURRENCE_ID, EVENING_SLOT_ID, STATE_ARMED, null)),
            today = TODAY,
        )

        val row = requireNotNull(buildTodayHabitRow(habit(), Schedule.Daily(), twoSlots(), snapshot))

        assertEquals(DayStatus.PARTIAL, row.dayStatus)
        assertEquals(listOf(EntryStatus.COMPLETED, EntryStatus.UNKNOWN), row.slots.map { it.status })
        assertEquals(listOf(null, OCCURRENCE_ID), row.slots.map { it.occurrenceId })
        // TodayHabitRow.colorArgb (task 4.2, correction C3) has no default — this is the guard that
        // buildTodayHabitRow actually fills it from the Habit it holds, not merely that it compiles.
        assertEquals(HABIT_COLOR_ARGB, row.colorArgb)
    }

    /**
     * Regression guard. `observeUnresolved()` spans every unresolved date on purpose, so it agrees
     * with what re-arming considers live, and `OccurrencePlanner` arms today, today+1 and today+2.
     * Before the date bound in [buildTodayHabitRow], a slot took whichever row the query returned
     * first — and that query has no `ORDER BY`, so not even that was stable. Once today's occurrence
     * went `RESOLVED` and dropped out, the slot surfaced **tomorrow's** `ARMED` one, leaving answer
     * buttons on an already-answered slot; a correcting tap then wrote the `Entry` against
     * tomorrow's date and cancelled tomorrow's alarm.
     *
     * Tomorrow's occurrence is given the LOWER id here so a `firstOrNull` with no date filter picks
     * it — the test has to be able to fail for the original reason, not merely pass by luck of
     * insertion order.
     */
    @Test
    fun `a slot never adopts another day's occurrence, even when today's has resolved away`() {
        val snapshot = TodaySnapshot(
            entriesToday = emptyList(),
            unresolvedOccurrences = listOf(
                occurrence(OCCURRENCE_ID, MORNING_SLOT_ID, STATE_ARMED, null, scheduledDate = TODAY.plusDays(1)),
                occurrence(OCCURRENCE_ID + 1, EVENING_SLOT_ID, STATE_ARMED, null, scheduledDate = TODAY.plusDays(2)),
            ),
            today = TODAY,
        )

        val row = requireNotNull(buildTodayHabitRow(habit(), Schedule.Daily(), twoSlots(), snapshot))

        assertEquals(
            listOf(null, null),
            row.slots.map { it.occurrenceId },
            "no slot may carry an occurrence handle from another day",
        )
    }

    /** Task 6b.3 / design.md D3: the snooze deadline is gated on the occurrence's `state`, not on the
     *  `snoozeUntilEpochMs` column, which a re-armed occurrence still carries from its last snooze. */
    @Test
    fun `only a SNOOZED occurrence surfaces a snooze deadline, an armed one reads plain pending`() {
        val snapshot = TodaySnapshot(
            entriesToday = emptyList(),
            unresolvedOccurrences = listOf(
                occurrence(OCCURRENCE_ID, MORNING_SLOT_ID, STATE_SNOOZED, SNOOZE_UNTIL_MS),
                occurrence(OCCURRENCE_ID + 1, EVENING_SLOT_ID, STATE_ARMED, SNOOZE_UNTIL_MS),
            ),
            today = TODAY,
        )

        val row = requireNotNull(buildTodayHabitRow(habit(), Schedule.Daily(), twoSlots(), snapshot))

        assertEquals(DayStatus.PENDING, row.dayStatus)
        assertEquals(listOf(SNOOZE_UNTIL_MS, null), row.slots.map { it.snoozedUntilEpochMs })
        assertTrue(row.slots.all { it.status == EntryStatus.UNKNOWN })
    }

    /** Mirrors `rollupDay`'s [DayStatus.NOT_DUE] exactly: no row, rather than an empty one. */
    @Test
    fun `a habit that is not due today produces no row at all`() {
        val notToday = TODAY.dayOfWeek.plus(1)
        val snapshot = TodaySnapshot(emptyList(), emptyList(), TODAY)

        assertNull(buildTodayHabitRow(habit(), Schedule.Weekly(notToday), emptyList(), snapshot))
    }

    @Test
    fun `toggling a habit row expands it and toggling again collapses it`() = runTest {
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(listOf("Read"), initial.rows.map { it.habitName })
            assertTrue(initial.expandedHabitIds.isEmpty())

            viewModel.toggleExpanded(HABIT_ID)
            assertEquals(setOf(HABIT_ID), awaitItem().expandedHabitIds)

            viewModel.toggleExpanded(HABIT_ID)
            assertTrue(awaitItem().expandedHabitIds.isEmpty())
        }
    }

    /** The screen's only write path: the slot's own live occurrence handle and the injected today,
     *  never an ambient clock. What the write then does with them is `EntryWriteParityTest`'s job. */
    @Test
    fun `answering hands EntryWriter the slot's live occurrence handle and the injected today`() = runTest {
        val entryWriter = mockk<EntryWriter>(relaxUnitFun = true)
        val viewModel = buildViewModel(entryWriter)

        val slot = viewModel.uiState.first { it.rows.isNotEmpty() }.rows.single().slots.first()
        viewModel.answer(HABIT_ID, slot, InAppEntryStatus.SKIPPED)

        coVerify(exactly = 1) {
            entryWriter.answerInApp(HABIT_ID, TODAY, MORNING_SLOT_ID, InAppEntryStatus.SKIPPED, OCCURRENCE_ID)
        }
    }

    /** Task 6b.9 — [alarmScheduler] backs the exact-alarm banner. Explicitly stubbed for the same
     *  reason as every other collaborator here: a relaxed mock answers `false` for a `Boolean`,
     *  which would silently arm the banner branch in every test that is not about it. */
    @Test
    fun `the banner state mirrors canScheduleExactAlarms, both when denied and when granted`() = runTest {
        val deniedViewModel = buildViewModel(
            alarmScheduler = mockk { every { canScheduleExactAlarms() } returns false },
        )
        assertTrue(deniedViewModel.uiState.first().canScheduleExactAlarms.not())

        val grantedViewModel = buildViewModel(
            alarmScheduler = mockk { every { canScheduleExactAlarms() } returns true },
        )
        assertTrue(grantedViewModel.uiState.first().canScheduleExactAlarms)
    }

    /** Task 6b.9 — the `onResume` re-check re-reads the permission rather than caching the
     *  construction-time value, since the user can grant it from system Settings and return with
     *  no Room write to react to. */
    @Test
    fun `refreshExactAlarmPermission re-reads a permission granted after construction`() = runTest {
        val alarmScheduler = mockk<AlarmScheduler> {
            every { canScheduleExactAlarms() } returns false andThen true
        }
        val viewModel = buildViewModel(alarmScheduler = alarmScheduler)
        assertTrue(viewModel.uiState.first().canScheduleExactAlarms.not())

        viewModel.refreshExactAlarmPermission()

        assertTrue(viewModel.uiState.first().canScheduleExactAlarms)
    }

    private fun twoSlots() = listOf(slot(MORNING_SLOT_ID, MORNING_MINUTE), slot(EVENING_SLOT_ID, EVENING_MINUTE))

    private fun buildViewModel(
        entryWriter: EntryWriter = mockk(relaxUnitFun = true),
        alarmScheduler: AlarmScheduler = mockk {
            every { canScheduleExactAlarms() } returns true
        },
    ): TodayViewModel {
        val habitRepository = mockk<HabitRepository> {
            every { observeAll() } returns MutableStateFlow(listOf(habit()))
            coEvery { findScheduleFor(HABIT_ID) } returns Schedule.Daily()
            coEvery { findSlotsFor(HABIT_ID) } returns twoSlots()
        }
        val entryDao = mockk<EntryDao> {
            every { observeByDate(TODAY.toString()) } returns MutableStateFlow(emptyList())
        }
        val occurrenceDao = mockk<ReminderOccurrenceDao> {
            every { observeUnresolved() } returns
                MutableStateFlow(listOf(occurrence(OCCURRENCE_ID, MORNING_SLOT_ID, STATE_ARMED, null)))
        }
        val timeProvider = mockk<TimeProvider> {
            every { today() } returns TODAY
            every { zone() } returns ZONE
        }
        return TodayViewModel(habitRepository, entryDao, occurrenceDao, entryWriter, alarmScheduler, timeProvider)
    }
}
