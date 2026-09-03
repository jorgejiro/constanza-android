package com.jjrapps.constanza.tracking

import app.cash.turbine.test
import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.dao.ReminderOccurrenceDao
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.time.CurrentDateSource
import com.jjrapps.constanza.domain.model.DayStatus
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.habit.HabitRepository
import com.jjrapps.constanza.reminding.NotificationPermission
import com.jjrapps.constanza.reminding.NotificationPermissionDecision
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import com.jjrapps.constanza.scheduling.AlarmScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
private val TOMORROW: LocalDate = TODAY.plusDays(1)
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

private fun entryEntity(slotId: Long, status: EntryStatus, date: LocalDate = TODAY) = EntryEntity(
    habitId = HABIT_ID, date = date.toString(), slotId = slotId, status = status.name,
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

/** today-midnight-rollover, task 3.1: [TimeProvider][com.jjrapps.constanza.core.time.TimeProvider]
 *  was a stateless synchronous clock, so a plain `mockk` returning one fixed [TODAY] was enough.
 *  [CurrentDateSource] is a live stream, so it needs a fake with two independently controllable
 *  values instead of one: [emissions] is what the timer has already pushed through [dates] — a
 *  rollover test moves this — and [current] is a separate synchronous read for [today], moved
 *  independently to simulate [TodayViewModel.refreshDate]'s resume case, where the timer's own
 *  coroutine can be stale (backgrounded) while the real current date has already moved on. */
private class FakeCurrentDateSource(initial: LocalDate, private val zone: ZoneId = ZONE) : CurrentDateSource {
    val emissions = MutableStateFlow(initial)
    var current: LocalDate = initial

    override fun dates(): Flow<LocalDate> = emissions
    override fun today(): LocalDate = current
    override fun zone(): ZoneId = zone

    /** Moves both values together, for the ordinary case where the timer fires normally while the
     *  screen is displayed (task 3.2 / 3.3) — as opposed to [current] alone, for the backgrounded
     *  resume case (task 3.4). */
    fun advanceTo(date: LocalDate) {
        current = date
        emissions.value = date
    }
}

/** today-midnight-rollover, task 3.1: replaces the old fixed
 *  `every { observeByDate(TODAY.toString()) } returns MutableStateFlow(emptyList())` stub, which
 *  had no answer at all for any other date — an unanticipated call surfaced as an opaque
 *  `MockKException` rather than a readable assertion failure. [entriesByDate] registers every date
 *  a test actually needs; any other date fails with a message naming the missing date instead. */
private fun entryDaoStub(entriesByDate: Map<LocalDate, List<EntryEntity>> = mapOf(TODAY to emptyList())): EntryDao {
    val flowsByDate = entriesByDate.mapKeys { (date, _) -> date.toString() }
        .mapValues { (_, entries) -> MutableStateFlow(entries) }
    return mockk {
        every { observeByDate(any()) } answers {
            val date = firstArg<String>()
            flowsByDate[date] ?: error(
                "TodayViewModelTest.entryDaoStub: no fixture registered for date '$date' — " +
                    "add it to buildViewModel()'s entriesByDate map.",
            )
        }
    }
}

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

    /** today-midnight-rollover, task 3.2 / design.md decision 2. The date is a key OUTSIDE the
     *  `combine`, so crossing it must re-subscribe [EntryDao.observeByDate] against the new date
     *  (the rollup moves from `PARTIAL` to `PENDING` because it is now reading TOMORROW's, empty,
     *  entries) and the unresolved-occurrence filter inside [buildTodayHabitRow] must follow it too
     *  (the live occurrence handle moves from the morning slot to the evening one, because that is
     *  the one scheduled for TOMORROW). */
    @Test
    fun `crossing midnight while displayed re-subscribes EntryDao and moves both the rollup and the occurrence filter`() =
        runTest {
            val currentDateSource = FakeCurrentDateSource(TODAY)
            val viewModel = buildViewModel(
                currentDateSource = currentDateSource,
                entriesByDate = mapOf(
                    TODAY to listOf(entryEntity(MORNING_SLOT_ID, EntryStatus.COMPLETED, TODAY)),
                    TOMORROW to emptyList(),
                ),
                unresolvedOccurrences = listOf(
                    occurrence(OCCURRENCE_ID, MORNING_SLOT_ID, STATE_ARMED, null, scheduledDate = TODAY),
                    occurrence(OCCURRENCE_ID + 1, EVENING_SLOT_ID, STATE_ARMED, null, scheduledDate = TOMORROW),
                ),
            )

            viewModel.uiState.test {
                val onToday = awaitItem().rows.single()
                assertEquals(DayStatus.PARTIAL, onToday.dayStatus)
                assertEquals(listOf(EntryStatus.COMPLETED, EntryStatus.UNKNOWN), onToday.slots.map { it.status })
                assertEquals(listOf(OCCURRENCE_ID, null), onToday.slots.map { it.occurrenceId })

                currentDateSource.advanceTo(TOMORROW)

                val onTomorrow = awaitItem().rows.single()
                assertEquals(DayStatus.PENDING, onTomorrow.dayStatus)
                assertEquals(listOf(EntryStatus.UNKNOWN, EntryStatus.UNKNOWN), onTomorrow.slots.map { it.status })
                assertEquals(listOf(null, OCCURRENCE_ID + 1), onTomorrow.slots.map { it.occurrenceId })
            }
        }

    /** today-midnight-rollover, task 3.3 / design.md decision 3 — the write-path corruption fix
     *  itself. [slotFromBeforeMidnight] is captured from the row drawn BEFORE the rollover, exactly
     *  like a tap that lands just after midnight on a row the user was already looking at: the
     *  write must still target the date [TodayUiState] displays at the moment of the tap, never the
     *  date the slot reference happens to have been built against. */
    @Test
    fun `answer writes against the currently displayed date, even for a slot captured before midnight rolled over`() =
        runTest {
            val entryWriter = mockk<EntryWriter>(relaxUnitFun = true)
            val currentDateSource = FakeCurrentDateSource(TODAY)
            val viewModel = buildViewModel(
                entryWriter = entryWriter,
                currentDateSource = currentDateSource,
                entriesByDate = mapOf(TODAY to emptyList(), TOMORROW to emptyList()),
            )
            val slotFromBeforeMidnight = viewModel.uiState.first { it.rows.isNotEmpty() }.rows.single().slots.first()

            currentDateSource.advanceTo(TOMORROW)
            assertEquals(TOMORROW, viewModel.uiState.value.date)

            viewModel.answer(HABIT_ID, slotFromBeforeMidnight, InAppEntryStatus.COMPLETED)

            coVerify(exactly = 1) {
                entryWriter.answerInApp(
                    HABIT_ID,
                    TOMORROW,
                    slotFromBeforeMidnight.slotId,
                    InAppEntryStatus.COMPLETED,
                    slotFromBeforeMidnight.occurrenceId,
                )
            }
            coVerify(exactly = 0) { entryWriter.answerInApp(HABIT_ID, TODAY, any(), any(), any()) }
        }

    /** today-midnight-rollover, task 3.4 — the spec's "backgrounded app corrects the date on
     *  resume" scenario, isolated from the timer path: [FakeCurrentDateSource.current] is moved
     *  directly, standing in for a real device whose wall clock kept moving while its process (and
     *  the timer coroutine inside it) was fully suspended, so [CurrentDateSource.dates] itself never
     *  emitted the new date. */
    @Test
    fun `refreshDate corrects a stale observedDate left over from backgrounding`() = runTest {
        val currentDateSource = FakeCurrentDateSource(TODAY)
        val viewModel = buildViewModel(
            currentDateSource = currentDateSource,
            entriesByDate = mapOf(TODAY to emptyList(), TOMORROW to emptyList()),
        )
        viewModel.uiState.first { it.rows.isNotEmpty() }
        assertEquals(TODAY, viewModel.uiState.value.date)

        currentDateSource.current = TOMORROW

        viewModel.refreshDate()

        assertEquals(TOMORROW, viewModel.uiState.value.date)
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

    /** The Today banner is the only production consumer [NotificationPermission] has, so this is
     *  the guard that the decision actually reaches the screen. `GRANTED` is asserted alongside
     *  `SHOULD_REQUEST` on purpose: [TodayUiState] defaults the field to `GRANTED`, so an assertion
     *  on that value alone would pass even if the flow were never folded into the state at all. */
    @Test
    fun `the notification permission decision surfaces into the ui state`() = runTest {
        val requesting = buildViewModel(
            notificationPermission = mockk {
                every { decide(any(), any()) } returns NotificationPermissionDecision.SHOULD_REQUEST
            },
        )
        assertEquals(NotificationPermissionDecision.SHOULD_REQUEST, requesting.uiState.first().notificationPermission)

        val granted = buildViewModel(
            notificationPermission = mockk {
                every { decide(any(), any()) } returns NotificationPermissionDecision.GRANTED
            },
        )
        assertEquals(NotificationPermissionDecision.GRANTED, granted.uiState.first().notificationPermission)
    }

    /** The `onResume` counterpart of the exact-alarm re-check: `BLOCKED` can only be undone from
     *  system settings, so the screen has to re-read on return rather than cache construction. */
    @Test
    fun `refreshNotificationPermission picks up a permission granted while the screen was paused`() = runTest {
        // Driven by a variable rather than `andThen`: construction seeds the flow with one
        // `decide` call of its own before `init` refreshes it, so a fixed call sequence would
        // encode that internal call count into the test.
        var grantedInSystemSettings = false
        val notificationPermission = mockk<NotificationPermission> {
            every { decide(any(), any()) } answers {
                if (grantedInSystemSettings) {
                    NotificationPermissionDecision.GRANTED
                } else {
                    NotificationPermissionDecision.BLOCKED
                }
            }
        }
        val viewModel = buildViewModel(notificationPermission = notificationPermission)
        assertEquals(NotificationPermissionDecision.BLOCKED, viewModel.uiState.first().notificationPermission)

        grantedInSystemSettings = true
        viewModel.refreshNotificationPermission()

        assertEquals(NotificationPermissionDecision.GRANTED, viewModel.uiState.first().notificationPermission)
    }

    /** The flag means "we have asked", not "the user agreed", which is exactly what turns the next
     *  decision from `SHOULD_REQUEST` into `BLOCKED`. Both halves are asserted: that the store is
     *  actually written, and that the state moves — writing without re-reading would leave the
     *  banner offering a prompt the system will never show again. */
    @Test
    fun `recordNotificationPermissionRequested writes the flag and moves SHOULD_REQUEST to BLOCKED`() = runTest {
        var alreadyAsked = false
        val reminderSettingsStore = mockk<ReminderSettingsStore> {
            coEvery { hasRequestedNotificationPermission() } answers { alreadyAsked }
            coEvery { recordRequestedNotificationPermission() } answers { alreadyAsked = true }
        }
        val notificationPermission = mockk<NotificationPermission> {
            every { decide(any(), any()) } answers {
                if (firstArg<Boolean>()) {
                    NotificationPermissionDecision.BLOCKED
                } else {
                    NotificationPermissionDecision.SHOULD_REQUEST
                }
            }
        }
        val viewModel = buildViewModel(
            notificationPermission = notificationPermission,
            reminderSettingsStore = reminderSettingsStore,
        )
        assertEquals(NotificationPermissionDecision.SHOULD_REQUEST, viewModel.uiState.first().notificationPermission)

        viewModel.recordNotificationPermissionRequested()

        coVerify(exactly = 1) { reminderSettingsStore.recordRequestedNotificationPermission() }
        assertEquals(NotificationPermissionDecision.BLOCKED, viewModel.uiState.first().notificationPermission)
    }

    private fun twoSlots() = listOf(slot(MORNING_SLOT_ID, MORNING_MINUTE), slot(EVENING_SLOT_ID, EVENING_MINUTE))

    private fun buildViewModel(
        entryWriter: EntryWriter = mockk(relaxUnitFun = true),
        alarmScheduler: AlarmScheduler = mockk {
            every { canScheduleExactAlarms() } returns true
        },
        notificationPermission: NotificationPermission = mockk {
            every { decide(any(), any()) } returns NotificationPermissionDecision.GRANTED
        },
        reminderSettingsStore: ReminderSettingsStore = mockk(relaxUnitFun = true) {
            coEvery { hasRequestedNotificationPermission() } returns false
        },
        currentDateSource: FakeCurrentDateSource = FakeCurrentDateSource(TODAY),
        entriesByDate: Map<LocalDate, List<EntryEntity>> = mapOf(TODAY to emptyList()),
        unresolvedOccurrences: List<ReminderOccurrenceEntity> =
            listOf(occurrence(OCCURRENCE_ID, MORNING_SLOT_ID, STATE_ARMED, null)),
    ): TodayViewModel {
        val habitRepository = mockk<HabitRepository> {
            every { observeAll() } returns MutableStateFlow(listOf(habit()))
            coEvery { findScheduleFor(HABIT_ID) } returns Schedule.Daily()
            coEvery { findSlotsFor(HABIT_ID) } returns twoSlots()
        }
        val entryDao = entryDaoStub(entriesByDate)
        val occurrenceDao = mockk<ReminderOccurrenceDao> {
            every { observeUnresolved() } returns MutableStateFlow(unresolvedOccurrences)
        }
        return TodayViewModel(
            habitRepository, entryDao, occurrenceDao, entryWriter, alarmScheduler,
            notificationPermission, reminderSettingsStore, currentDateSource,
        )
    }
}
