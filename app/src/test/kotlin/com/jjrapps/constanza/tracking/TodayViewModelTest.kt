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
import io.mockk.verify
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
private val YESTERDAY: LocalDate = TODAY.minusDays(1)
private val TWO_DAYS_AGO: LocalDate = TODAY.minusDays(2)
private val THREE_DAYS_AGO: LocalDate = TODAY.minusDays(3)
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
     *  the one scheduled for TOMORROW).
     *
     *  today-past-day-correction, task 2.14 / design.md decision 3: this is also the vehicle for
     *  the ONE assertion Decision 3's "a live-edge rollover leaves the expansion sets alone"
     *  justification was resting on with no coverage at all — [expandedHabitIds] and
     *  [TodayUiState.reopenedSlots] are populated BEFORE the rollover and asserted UNCHANGED after
     *  it, proving [clearPresentedSlotState] is never reached by the [init] collector. */
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
                val onTodayState = awaitItem()
                val onToday = onTodayState.rows.single()
                assertEquals(DayStatus.PARTIAL, onToday.dayStatus)
                assertEquals(listOf(EntryStatus.COMPLETED, EntryStatus.UNKNOWN), onToday.slots.map { it.status })
                assertEquals(listOf(OCCURRENCE_ID, null), onToday.slots.map { it.occurrenceId })

                viewModel.toggleExpanded(HABIT_ID)
                val expandedState = awaitItem()
                assertEquals(setOf(HABIT_ID), expandedState.expandedHabitIds)
                viewModel.requestChange(onToday.slots.first().keyIn(HABIT_ID))
                val reopenedState = awaitItem()
                assertEquals(setOf(onToday.slots.first().keyIn(HABIT_ID)), reopenedState.reopenedSlots)

                currentDateSource.advanceTo(TOMORROW)

                val onTomorrow = awaitItem()
                assertEquals(DayStatus.PENDING, onTomorrow.rows.single().dayStatus)
                assertEquals(
                    listOf(EntryStatus.UNKNOWN, EntryStatus.UNKNOWN),
                    onTomorrow.rows.single().slots.map { it.status },
                )
                assertEquals(listOf(null, OCCURRENCE_ID + 1), onTomorrow.rows.single().slots.map { it.occurrenceId })
                assertEquals(
                    setOf(HABIT_ID),
                    onTomorrow.expandedHabitIds,
                    "a live-edge midnight rollover must leave expandedHabitIds alone",
                )
                assertEquals(
                    setOf(onToday.slots.first().keyIn(HABIT_ID)),
                    onTomorrow.reopenedSlots,
                    "a live-edge midnight rollover must leave reopenedSlots alone",
                )
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

    /** today-past-day-correction, task 2.2 / design.md decision 1. [TodayDate.clock] moves via the
     *  ordinary timer path, but with [TodayDate.navigated] set, [TodayDate.viewed] must not follow
     *  it — the whole point of the projection over a guard. */
    @Test
    fun `a midnight tick while on a past day does not move uiState's date`() = runTest {
        val currentDateSource = FakeCurrentDateSource(TODAY)
        val viewModel = buildViewModel(
            currentDateSource = currentDateSource,
            entriesByDate = mapOf(TODAY to emptyList(), YESTERDAY to emptyList(), TOMORROW to emptyList()),
        )
        viewModel.uiState.first { it.rows.isNotEmpty() }

        viewModel.showPreviousDay()
        viewModel.uiState.first { it.date == YESTERDAY }

        currentDateSource.advanceTo(TOMORROW)

        assertEquals(YESTERDAY, viewModel.uiState.value.date)
    }

    /** today-past-day-correction, task 2.3 / design.md decision 1. [refreshDate] is the resume
     *  path, distinct from the timer: it must be equally unconditional, and equally unable to move
     *  [TodayDate.viewed] away from a deliberately navigated date. */
    @Test
    fun `refreshDate while on a past day does not move it`() = runTest {
        val currentDateSource = FakeCurrentDateSource(TODAY)
        val viewModel = buildViewModel(
            currentDateSource = currentDateSource,
            entriesByDate = mapOf(TODAY to emptyList(), YESTERDAY to emptyList(), TOMORROW to emptyList()),
        )
        viewModel.uiState.first { it.rows.isNotEmpty() }

        viewModel.showPreviousDay()
        viewModel.uiState.first { it.date == YESTERDAY }

        currentDateSource.current = TOMORROW
        viewModel.refreshDate()

        assertEquals(YESTERDAY, viewModel.uiState.value.date)
    }

    /** today-past-day-correction, task 2.4 / design.md decision 1, invariant 3. [showToday] sets
     *  `navigated = null`, which re-attaches [TodayDate.viewed] to whatever [TodayDate.clock]
     *  CURRENTLY is — a date change that happened while away must be caught up, not ignored. */
    @Test
    fun `showToday after a tick that fired while away lands on the new clock date`() = runTest {
        val currentDateSource = FakeCurrentDateSource(TODAY)
        val viewModel = buildViewModel(
            currentDateSource = currentDateSource,
            entriesByDate = mapOf(TODAY to emptyList(), YESTERDAY to emptyList(), TOMORROW to emptyList()),
        )
        viewModel.uiState.first { it.rows.isNotEmpty() }

        viewModel.showPreviousDay()
        viewModel.uiState.first { it.date == YESTERDAY }

        currentDateSource.advanceTo(TOMORROW)
        viewModel.showToday()

        assertEquals(TOMORROW, viewModel.uiState.value.date)
    }

    /** today-past-day-correction, task 2.6 / design.md decision 1's sharp edge: [showNextDay]
     *  landing on the clock date MUST set `navigated = null`, never pin `navigated = clock`.
     *  Pinning would freeze the view across the NEXT midnight — the same bug re-entered through
     *  the forward door. A later tick moving the view is the only way to tell the two apart. */
    @Test
    fun `forward navigation onto today re-attaches, so a later tick still moves the view`() = runTest {
        val currentDateSource = FakeCurrentDateSource(TODAY)
        val viewModel = buildViewModel(
            currentDateSource = currentDateSource,
            entriesByDate = mapOf(TODAY to emptyList(), YESTERDAY to emptyList(), TOMORROW to emptyList()),
        )
        viewModel.uiState.first { it.rows.isNotEmpty() }

        viewModel.showPreviousDay()
        viewModel.uiState.first { it.date == YESTERDAY }
        viewModel.showNextDay()
        assertEquals(TODAY, viewModel.uiState.first { it.date == TODAY }.date)

        currentDateSource.advanceTo(TOMORROW)

        assertEquals(TOMORROW, viewModel.uiState.value.date)
    }

    /** today-past-day-correction, task 2.7 / design.md decision 1. The live edge is the forward
     *  boundary: no future date is ever reachable, and — since [TodayDate.viewed] does not
     *  actually change — the call must be a true no-op. */
    @Test
    fun `showNextDay at the live edge is a no-op`() = runTest {
        val viewModel = buildViewModel(entriesByDate = mapOf(TODAY to emptyList()))
        viewModel.uiState.first { it.rows.isNotEmpty() }

        viewModel.showNextDay()

        assertEquals(TODAY, viewModel.uiState.value.date)
        assertTrue(viewModel.uiState.value.isPastDay.not())
    }

    /** today-past-day-correction, task 2.8 / design.md decision 1. Backward navigation has no
     *  lower bound — N steps must reach exactly `clock - N`. */
    @Test
    fun `N backward steps reach clock minus N, unbounded`() = runTest {
        val viewModel = buildViewModel(
            entriesByDate = mapOf(
                TODAY to emptyList(),
                YESTERDAY to emptyList(),
                TWO_DAYS_AGO to emptyList(),
                THREE_DAYS_AGO to emptyList(),
            ),
        )
        viewModel.uiState.first { it.rows.isNotEmpty() }

        repeat(3) { viewModel.showPreviousDay() }

        assertEquals(THREE_DAYS_AGO, viewModel.uiState.first { it.date == THREE_DAYS_AGO }.date)
    }

    /** today-past-day-correction, task 2.9 / habit-entry-tracking: In-App Answer Date Attribution.
     *  [TodayViewModel.answer] must credit the NAVIGATED-TO date, not [TodayDate.clock], when the
     *  user is deliberately viewing a past day. */
    @Test
    fun `answer on a past day passes the viewed date to answerInApp`() = runTest {
        val entryWriter = mockk<EntryWriter>(relaxUnitFun = true)
        val viewModel = buildViewModel(
            entryWriter = entryWriter,
            entriesByDate = mapOf(TODAY to emptyList(), YESTERDAY to emptyList()),
        )
        viewModel.uiState.first { it.rows.isNotEmpty() }

        viewModel.showPreviousDay()
        val slot = viewModel.uiState.first { it.date == YESTERDAY }.rows.single().slots.first()
        viewModel.answer(HABIT_ID, slot, InAppEntryStatus.COMPLETED)

        coVerify(exactly = 1) {
            entryWriter.answerInApp(HABIT_ID, YESTERDAY, slot.slotId, InAppEntryStatus.COMPLETED, slot.occurrenceId)
        }
    }

    /** today-past-day-correction, task 2.10 / design.md decision 3. Populate both presented-state
     *  sets first, then navigate: a navigation that actually changes [TodayDate.viewed] must clear
     *  both. */
    @Test
    fun `both expansion sets are empty after a navigation that changes the viewed date`() = runTest {
        val viewModel = buildViewModel(entriesByDate = mapOf(TODAY to emptyList(), YESTERDAY to emptyList()))
        val onToday = viewModel.uiState.first { it.rows.isNotEmpty() }.rows.single()
        viewModel.toggleExpanded(HABIT_ID)
        viewModel.requestChange(onToday.slots.first().keyIn(HABIT_ID))
        viewModel.uiState.first { it.expandedHabitIds.isNotEmpty() && it.reopenedSlots.isNotEmpty() }

        viewModel.showPreviousDay()

        val onYesterday = viewModel.uiState.first { it.date == YESTERDAY }
        assertTrue(onYesterday.expandedHabitIds.isEmpty())
        assertTrue(onYesterday.reopenedSlots.isEmpty())
    }

    /** today-past-day-correction, task 2.11 / design.md decision 3. A clock tick while navigated
     *  away must neither clear the presented-state sets NOR cause a second subscription to
     *  [EntryDao.observeByDate] for the navigated-to date — [dateView]'s `distinctUntilChanged`
     *  must swallow the tick entirely, since it does not move [TodayDate.viewed]. */
    @Test
    fun `a tick while navigated away neither clears expansion state nor re-subscribes Room`() = runTest {
        val currentDateSource = FakeCurrentDateSource(TODAY)
        val entryDao = entryDaoStub(mapOf(TODAY to emptyList(), YESTERDAY to emptyList(), TOMORROW to emptyList()))
        val viewModel = buildViewModel(currentDateSource = currentDateSource, entryDao = entryDao)
        val onToday = viewModel.uiState.first { it.rows.isNotEmpty() }.rows.single()

        viewModel.showPreviousDay()
        viewModel.toggleExpanded(HABIT_ID)
        viewModel.requestChange(onToday.slots.first().keyIn(HABIT_ID))
        viewModel.uiState.first { it.date == YESTERDAY && it.expandedHabitIds.isNotEmpty() }

        currentDateSource.advanceTo(TOMORROW)

        val stillOnYesterday = viewModel.uiState.value
        assertEquals(YESTERDAY, stillOnYesterday.date)
        assertEquals(setOf(HABIT_ID), stillOnYesterday.expandedHabitIds)
        verify(exactly = 1) { entryDao.observeByDate(YESTERDAY.toString()) }
    }

    /** today-past-day-correction, task 2.12 / design.md decision 1, invariant 4. `isPastDay` is
     *  exactly `navigated != null`: true off the live edge, false at it. */
    @Test
    fun `isPastDay is true off the live edge, false at it`() = runTest {
        val viewModel = buildViewModel(entriesByDate = mapOf(TODAY to emptyList(), YESTERDAY to emptyList()))
        assertTrue(viewModel.uiState.first { it.rows.isNotEmpty() }.isPastDay.not())

        viewModel.showPreviousDay()
        assertTrue(viewModel.uiState.first { it.date == YESTERDAY }.isPastDay)

        viewModel.showToday()
        assertTrue(viewModel.uiState.first { it.date == TODAY }.isPastDay.not())
    }

    /** today-past-day-correction, task 2.13 — proves [toTodaySlot] is unchanged: it filters
     *  [TodaySnapshot.unresolvedOccurrences] by the snapshot's own date, so an occurrence scheduled
     *  for a DIFFERENT date (here, the live-edge [TODAY]) never attaches to a slot built for the
     *  navigated-to past date. */
    @Test
    fun `a past occurrence absent from observeUnresolved builds a slot with a null occurrenceId`() = runTest {
        val viewModel = buildViewModel(
            entriesByDate = mapOf(TODAY to emptyList(), YESTERDAY to emptyList()),
            unresolvedOccurrences = listOf(
                occurrence(OCCURRENCE_ID, MORNING_SLOT_ID, STATE_ARMED, null, scheduledDate = TODAY),
            ),
        )
        viewModel.uiState.first { it.rows.isNotEmpty() }

        viewModel.showPreviousDay()
        val slot = viewModel.uiState.first { it.date == YESTERDAY }.rows.single().slots.first()

        assertNull(slot.occurrenceId)
    }

    /** today-past-day-correction, task 2.15 / proposal.md's unrestricted-editing success
     *  criterion: nothing in [TodayViewModel.answer] restricts the transition, so a past slot must
     *  accept the full `COMPLETED -> MISSED -> SKIPPED` cycle, twice in a row, with every write
     *  landing. */
    @Test
    fun `a past slot is freely re-editable through every status, twice around the cycle`() = runTest {
        val entryWriter = mockk<EntryWriter>(relaxUnitFun = true)
        val viewModel = buildViewModel(
            entryWriter = entryWriter,
            entriesByDate = mapOf(TODAY to emptyList(), YESTERDAY to emptyList()),
        )
        viewModel.uiState.first { it.rows.isNotEmpty() }

        viewModel.showPreviousDay()
        val slot = viewModel.uiState.first { it.date == YESTERDAY }.rows.single().slots.first()

        val cycle = listOf(InAppEntryStatus.COMPLETED, InAppEntryStatus.MISSED, InAppEntryStatus.SKIPPED)
        repeat(2) {
            cycle.forEach { status -> viewModel.answer(HABIT_ID, slot, status) }
        }

        cycle.forEach { status ->
            coVerify(exactly = 2) {
                entryWriter.answerInApp(HABIT_ID, YESTERDAY, slot.slotId, status, slot.occurrenceId)
            }
        }
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
        // Overridable only so a test can keep a reference to the exact EntryDao mock and verify
        // how many times observeByDate was called (task 2.11) — every other test relies on the
        // default, built from entriesByDate exactly as before.
        entryDao: EntryDao = entryDaoStub(entriesByDate),
    ): TodayViewModel {
        val habitRepository = mockk<HabitRepository> {
            every { observeAll() } returns MutableStateFlow(listOf(habit()))
            coEvery { findScheduleFor(HABIT_ID) } returns Schedule.Daily()
            coEvery { findSlotsFor(HABIT_ID) } returns twoSlots()
        }
        val occurrenceDao = mockk<ReminderOccurrenceDao> {
            every { observeUnresolved() } returns MutableStateFlow(unresolvedOccurrences)
        }
        return TodayViewModel(
            habitRepository, entryDao, occurrenceDao, entryWriter, alarmScheduler,
            notificationPermission, reminderSettingsStore, currentDateSource,
        )
    }
}
