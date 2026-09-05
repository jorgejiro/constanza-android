package com.jjrapps.constanza.habit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.entity.ReminderSlotEntity
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.scheduling.AlarmScheduler
import com.jjrapps.constanza.scheduling.FakeTimeProvider
import com.jjrapps.constanza.scheduling.OccurrencePlanner
import com.jjrapps.constanza.scheduling.ScheduleEditor
import com.jjrapps.constanza.scheduling.SchedulingDaos
import com.jjrapps.constanza.scheduling.insertHabitWithSchedule
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

private const val RESOLVE_DEADLINE_HOURS = 24L
private const val STATE_ARMED = "ARMED"
private const val MORNING_MINUTE_OF_DAY = 480

/** Bound on [HabitRepositoryTestFixture.close]'s scope drain. Generous, because it is never meant
 *  to be reached: a drain that runs out is a stuck coroutine worth investigating, not a bound worth
 *  widening. Bounded at all so that such a coroutine degrades to the old behaviour — close anyway —
 *  instead of hanging every remaining test on the matrix behind one `@After`. */
private const val SCOPE_DRAIN_TIMEOUT_MS = 5_000L

private val FIXED_INSTANT: Instant = Instant.parse("2026-09-01T08:00:00Z")

/**
 * Shared wiring for the work unit 6a instrumented tests: a real in-memory Room database and a
 * real [HabitRepository]/[ScheduleEditor]/[OccurrencePlanner] chain, with only [AlarmScheduler]
 * relaxed-mocked (arming a real system alarm is irrelevant to what these scenarios assert).
 *
 * It also owns **ViewModel lifetime**, which is not incidental convenience. See [close]: the
 * teardown ordering that `openspec/config.yaml`'s `compose-test-db-teardown-race` describes lives
 * here now, in one place, instead of being copy-pasted into each test class that happened to
 * remember it.
 */
class HabitRepositoryTestFixture(internal val context: Context) {
    val database: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val timeProvider: TimeProvider = FakeTimeProvider(FIXED_INSTANT)

    /** Shared by [occurrencePlanner] and [habitRepository] (tasks 1.1, 2.3), so a test can
     *  `verify { alarmScheduler.cancel(id) }` against whichever of the two ran the cancellation —
     *  a single mock instance is what makes that verification possible at all. Arming a real
     *  system alarm is irrelevant to what these scenarios assert. */
    val alarmScheduler: AlarmScheduler = mockk(relaxed = true)
    val occurrencePlanner: OccurrencePlanner
    val habitRepository: HabitRepository

    /** Every ViewModel handed out through [register], in creation order; [close] drains them in
     *  reverse. A plain list, not a set: identity order is what teardown needs. */
    private val viewModels = mutableListOf<ViewModel>()

    init {
        occurrencePlanner = OccurrencePlanner(
            SchedulingDaos(
                database.habitDao(),
                database.scheduleDao(),
                database.reminderSlotDao(),
                database.reminderOccurrenceDao(),
            ),
            alarmScheduler,
            timeProvider,
            RESOLVE_DEADLINE_HOURS,
        )
        val daos = HabitDaos(
            database.habitDao(),
            database.scheduleDao(),
            database.reminderSlotDao(),
            database.entryDao(),
            database.reminderOccurrenceDao(),
        )
        habitRepository = HabitRepository(
            daos,
            database,
            ScheduleEditor(database, database.scheduleDao(), occurrencePlanner),
            occurrencePlanner,
            timeProvider,
            alarmScheduler,
        )
    }

    suspend fun insertEnabledSlot(habitId: Long, minuteOfDay: Int = MORNING_MINUTE_OF_DAY): Long =
        database.reminderSlotDao().insert(
            ReminderSlotEntity(habitId = habitId, minuteOfDay = minuteOfDay, enabled = true),
        )

    /**
     * A habit, its daily schedule and one enabled slot — written in ONE transaction, and therefore
     * published to live observers as one state rather than three.
     *
     * **Use this, not `insertHabitWithSchedule` followed by [insertEnabledSlot], for any test that
     * puts a screen in front of the result.** The pair it replaces is what
     * `openspec/config.yaml`'s `today-slot-row-compose-test-timeout-flakiness` turned out to be.
     * Room's `InvalidationTracker` fires per commit, so writing the habit first publishes a habit
     * with no schedule and no slot. `TodayViewModel.uiState` observes `habits`, `entries` and
     * `reminder_occurrences`, but reads each habit's schedule and slots imperatively inside its
     * `combine` — so an observer that receives that first state drops the habit
     * (`findScheduleFor` is still null) and produces `rows = 0`. The `schedules` and
     * `reminder_slots` writes that follow invalidate tables nothing in that `combine` observes, so
     * there is no second emission and the screen stays empty **for good**.
     *
     * Measured rather than reasoned about. `MechanismProbeTest`, run on the api31 leg — the one
     * that had never been seen to flake — forced that interleaving and the screen was still at
     * `rows=0` four seconds later, while the same three writes inside one transaction reached it in
     * 17 ms. On the matrix it presented as a `ComposeTimeoutException` at exactly the wait bound,
     * with the ViewModel still holding zero rows: nothing was in flight, so no larger bound could
     * ever have helped.
     */
    suspend fun seedHabitWithEnabledSlot(
        name: String = "Meditate",
        minuteOfDay: Int = MORNING_MINUTE_OF_DAY,
    ): SeededHabit = database.withTransaction {
        val habitId = database.insertHabitWithSchedule(name = name)
        SeededHabit(habitId = habitId, slotId = insertEnabledSlot(habitId, minuteOfDay))
    }

    /** The dates [OccurrencePlanner] currently holds armed for [habitId] — how a replan is observed
     *  from outside, since `reminder_occurrences` is the scheduling source of truth (design.md D4). */
    suspend fun armedOccurrenceDates(habitId: Long): List<String> =
        database.reminderOccurrenceDao().findByHabitId(habitId)
            .filter { it.state == STATE_ARMED }
            .map { it.scheduledDate }

    /**
     * Records [viewModel] so [close] can stop it before the database goes, and returns it unchanged
     * so a factory can be written as a one-liner.
     *
     * This exists because these tests build ViewModels by bare constructor rather than through a
     * `ViewModelProvider`, so nothing ever calls `onCleared` and nothing ever cancels
     * `viewModelScope`. A ViewModel that never reaches this method is invisible to teardown: its
     * eager `stateIn` collector keeps querying a database [close] has already shut, and the
     * resulting failure lands on an unrelated test. Prefer one of the factories below (or
     * `fixture.todayViewModel()` in `TodayViewModelTestFactory.kt`); reach for `register` directly
     * only for a ViewModel that has no factory here yet.
     */
    fun <T : ViewModel> register(viewModel: T): T {
        viewModels += viewModel
        return viewModel
    }

    /** The habit list's ViewModel, registered for teardown. See [register]. */
    fun habitListViewModel(): HabitListViewModel =
        register(HabitListViewModel(habitRepository, database.entryDao()))

    /** The habit editor's ViewModel, registered for teardown. See [register]. */
    fun habitEditorViewModel(): HabitEditorViewModel =
        register(HabitEditorViewModel(habitRepository, timeProvider))

    /**
     * Stops every registered ViewModel, then closes the database — and that order is the whole
     * point of this method.
     *
     * `openspec/config.yaml`'s `compose-test-db-teardown-race`. `TodayViewModel.uiState` and its
     * siblings are `stateIn(viewModelScope, SharingStarted.Eagerly, …)` over a Room `Flow`. Built
     * by bare constructor, as every instrumented test here builds them, nothing ever clears them,
     * so that collector outlives the test body. Close the database first and the collector's next
     * query hits a shut connection pool; the `SQLiteConnectionPool` "connection pool has been
     * closed" throw then surfaces **asynchronously** in the shared instrumentation process and is
     * attributed to whichever test happens to be running at that moment — occasionally killing the
     * process outright. It has fired for real on the matrix. Cancelling first removes the race
     * rather than shrinking it.
     *
     * **`cancelAndJoin`, not `cancel`.** `cancel` returns as soon as the job is marked cancelled,
     * before its children have actually stopped, so a query already handed to Room's query executor
     * would still land on a closing database. Joining waits for that query to finish. Reverse
     * creation order for the usual teardown reason: a later ViewModel may have been built on an
     * earlier one's state.
     *
     * **The join is bounded** by [SCOPE_DRAIN_TIMEOUT_MS]. A coroutine that refuses to finish then
     * degrades to the old behaviour — proceed and close anyway — instead of hanging the whole
     * matrix behind one `@After`. [withTimeoutOrNull] returning null is deliberately not asserted
     * on: `close()` runs in `@After`, where throwing would replace a genuine test failure with a
     * teardown failure and misattribute the result, which is exactly the failure mode this change
     * removes. A drain that times out is a real defect to investigate, not a bound to widen.
     *
     * **Its limit, stated plainly.** This is deterministic for ViewModels the fixture was told
     * about, via a factory or [register], and for nothing else. One built by bare constructor
     * remains invisible here — which is why `ViewModelTeardownCallSiteTest` in `src/test` fails the
     * build on that call shape rather than trusting each new test to remember.
     */
    fun close() {
        runBlocking {
            withTimeoutOrNull(SCOPE_DRAIN_TIMEOUT_MS) {
                viewModels.asReversed().forEach { viewModel ->
                    viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
                }
            }
        }
        viewModels.clear()
        database.close()
    }
}

/** What [HabitRepositoryTestFixture.seedHabitWithEnabledSlot] wrote, for the tests that go on to
 *  assert against the slot as well as the habit. */
data class SeededHabit(val habitId: Long, val slotId: Long)

/** A brand-new, unarchived [Habit] carrying the `id = 0` sentinel [HabitRepository.create] expects. */
fun newHabit(name: String = "Read"): Habit = Habit(
    id = 0,
    name = name,
    colorArgb = 0,
    notes = null,
    archived = false,
    archivedAt = null,
    createdAt = FIXED_INSTANT,
    sortOrder = 0,
)
