package com.jjrapps.constanza.scheduling

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.data.entity.ScheduleEntity
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

private const val RECONCILE_PERIOD_HOURS = 1L
private const val RESOLVE_DEADLINE_HOURS = 24L

/**
 * habit-entry-tracking: Midnight Transition (design.md D3/D8). Exercises [MidnightSweepWorker] via
 * `TestListenableWorkerBuilder` (task 4b.4) against a real in-memory Room database — **THE ONE
 * RULE this unit exists to enforce**: a dated `MISSED` row is written only where
 * `dueOn(...) == Required` and NOT `(state = SNOOZED AND snoozeUntil > now)`.
 */
@RunWith(AndroidJUnit4::class)
class MidnightSweepWorkerTest {

    private lateinit var database: AppDatabase
    private val now: Instant = Instant.parse("2026-09-02T00:05:00Z")
    private val yesterday = "2026-09-01"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = database.close()

    private fun buildWorker(): MidnightSweepWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val daos = SchedulingDaos(
            database.habitDao(), database.scheduleDao(), database.reminderSlotDao(), database.reminderOccurrenceDao(),
        )
        val resolver = OccurrenceResolver(
            daos, database.entryDao(), mockk(relaxed = true), RECONCILE_PERIOD_HOURS, RESOLVE_DEADLINE_HOURS,
        )
        // The real WorkScheduler, not a mock: the worker re-enqueues its own successor (task G.4),
        // and that enqueue lands harmlessly in this test's WorkManager. What the successor's anchor
        // must be is asserted in WorkSchedulerTest, through WorkManager's own query APIs.
        // day-review-exact-alarm: WorkScheduler no longer takes a DayReviewSettingsStore.
        val workScheduler = WorkScheduler(context, FakeTimeProvider(now), RECONCILE_PERIOD_HOURS)
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                MidnightSweepWorker(appContext, workerParameters, resolver, FakeTimeProvider(now), workScheduler)
        }
        return TestListenableWorkerBuilder<MidnightSweepWorker>(context).setWorkerFactory(factory).build()
    }

    private suspend fun insertHabitWithSchedule(kind: String, timesPerWeek: Int? = null): Long {
        val habitId = database.habitDao().insert(
            HabitEntity(name = "Read", colorArgb = 0, notes = null, archivedAt = null, createdAt = now.toString()),
        )
        database.scheduleDao().upsert(
            ScheduleEntity(
                habitId = habitId, kind = kind, timesPerWeek = timesPerWeek, dayOfWeek = null,
                dayOfMonth = null, intervalDays = null, anchorDate = null, weekStart = 1,
            ),
        )
        return habitId
    }

    private fun occurrence(habitId: Long, state: String, snoozeUntilEpochMs: Long? = null) = ReminderOccurrenceEntity(
        habitId = habitId,
        scheduledDate = yesterday,
        scheduledAtEpochMs = Instant.parse("2026-09-01T20:00:00Z").toEpochMilli(),
        state = state,
        snoozeUntilEpochMs = snoozeUntilEpochMs,
        snoozeCount = if (snoozeUntilEpochMs != null) 1 else 0,
        notifiedAtEpochMs = null,
        resolveDeadlineMs = Instant.parse("2026-09-02T20:00:00Z").toEpochMilli(),
    )

    @Test
    fun writesMissedForARequiredOccurrenceWithNoLiveSnooze() = runBlocking {
        val habitId = insertHabitWithSchedule(kind = "DAILY")
        val occId = database.reminderOccurrenceDao().upsert(occurrence(habitId, "ARMED"))

        buildWorker().doWork()

        val entries = database.entryDao().findByHabitAndDate(habitId, yesterday)
        assertEquals(1, entries.size)
        assertEquals("MISSED", entries.single().status)
        assertEquals("SWEEP", entries.single().source)
        assertEquals("RESOLVED", database.reminderOccurrenceDao().findById(occId)?.state)
    }

    /** Task 4b.4's dedicated D3 test: a live snooze at midnight leaves NO `entries` row — the
     *  pending occurrence costs zero storage (design.md §8.1), never a hidden or flickering row. */
    @Test
    fun liveSnoozeAtMidnightLeavesNoEntriesRow() = runBlocking {
        val habitId = insertHabitWithSchedule(kind = "DAILY")
        val liveSnoozeUntil = now.plusSeconds(600).toEpochMilli()
        val occId = database.reminderOccurrenceDao().upsert(occurrence(habitId, "SNOOZED", liveSnoozeUntil))

        buildWorker().doWork()

        assertTrue(database.entryDao().findByHabitAndDate(habitId, yesterday).isEmpty())
        assertEquals("SNOOZED", database.reminderOccurrenceDao().findById(occId)?.state)
    }

    /**
     * **The severe half of the answered-slot defect, and the reason it deserved its own test.**
     * A stale `ARMED` occurrence sitting beside an already-answered `Entry` did not merely produce
     * an unwanted notification: `writeMissed` upserts with `OnConflictStrategy.REPLACE` against
     * `UNIQUE(habitId, date, slotId)`, so the sweep REPLACED the answer with `MISSED`. A restore
     * silently rewrote history.
     *
     * habit-entry-tracking: Midnight Transition scopes the transition to an `Entry` "still
     * `UNKNOWN`", and Provisional-Missed Correction is one-way (`MISSED -> COMPLETED`) — nothing
     * ratifies the reverse. The occurrence is still resolved so it stops being rescanned; only the
     * dated row is withheld, exactly as the `N_TIMES_PER_WEEK` exception withholds it.
     */
    @Test
    fun aCompletedEntryIsNeverOverwrittenByTheSweep() = runBlocking {
        val habitId = insertHabitWithSchedule(kind = "DAILY")
        database.entryDao().insert(
            EntryEntity(
                habitId = habitId, date = yesterday, slotId = 0, status = "COMPLETED", value = null,
                answeredAt = "${yesterday}T09:00:00Z", source = "IN_APP",
            ),
        )
        val occId = database.reminderOccurrenceDao().upsert(occurrence(habitId, "ARMED"))

        buildWorker().doWork()

        val entry = database.entryDao().findByHabitAndDate(habitId, yesterday).single()
        assertEquals("COMPLETED", entry.status)
        assertEquals("IN_APP", entry.source)
        assertEquals("RESOLVED", database.reminderOccurrenceDao().findById(occId)?.state)
    }

    /** The same guard must not close Provisional-Missed Correction's import route: a `MISSED` row
     *  is a provisional verdict awaiting an answer, so re-sweeping it stays idempotent. */
    @Test
    fun anExistingMissedEntryIsStillRewrittenBySweep() = runBlocking {
        val habitId = insertHabitWithSchedule(kind = "DAILY")
        database.entryDao().insert(
            EntryEntity(
                habitId = habitId, date = yesterday, slotId = 0, status = "MISSED", value = null,
                answeredAt = "${yesterday}T09:00:00Z", source = "IN_APP",
            ),
        )
        database.reminderOccurrenceDao().upsert(occurrence(habitId, "ARMED"))

        buildWorker().doWork()

        val entry = database.entryDao().findByHabitAndDate(habitId, yesterday).single()
        assertEquals("MISSED", entry.status)
        assertEquals("SWEEP", entry.source)
    }

    /** design.md D8: `dueOn` never returns `Required` for `N_TIMES_PER_WEEK`, so the midnight
     *  transition MUST NOT fabricate a dated `MISSED` from one unmet weekly quota. */
    @Test
    fun nTimesPerWeekNeverReceivesADatedMissed() = runBlocking {
        val habitId = insertHabitWithSchedule(kind = "N_TIMES_PER_WEEK", timesPerWeek = 3)
        val occId = database.reminderOccurrenceDao().upsert(occurrence(habitId, "ARMED"))

        buildWorker().doWork()

        assertTrue(database.entryDao().findByHabitAndDate(habitId, yesterday).isEmpty())
        assertEquals("RESOLVED", database.reminderOccurrenceDao().findById(occId)?.state)
    }
}
