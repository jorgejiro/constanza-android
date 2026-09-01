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
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.data.entity.ScheduleEntity
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

private const val HOUR_SECONDS = 3600L
private const val MINUTE_SECONDS = 60L
private const val RECONCILE_PERIOD_HOURS = 1L
private const val RESOLVE_DEADLINE_HOURS = 24L

/**
 * design.md D3 layers 2-3, §5.5 (reminder-delivery: Missed-Reminder Sweep; habit-entry-tracking:
 * Abandoned Snooze Resolution). Exercises [ReconcileWorker] via `TestListenableWorkerBuilder`
 * (task 4b.4), a real in-memory Room database, and a mocked [AlarmScheduler].
 */
@RunWith(AndroidJUnit4::class)
class ReconcileWorkerTest {

    private lateinit var database: AppDatabase
    private lateinit var alarmScheduler: AlarmScheduler
    private val now: Instant = Instant.parse("2026-09-02T01:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        alarmScheduler = mockk(relaxed = true)
    }

    @After
    fun tearDown() = database.close()

    private fun buildWorker(): ReconcileWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val daos = SchedulingDaos(
            database.habitDao(), database.scheduleDao(), database.reminderSlotDao(), database.reminderOccurrenceDao(),
        )
        val resolver = OccurrenceResolver(
            daos, database.entryDao(), alarmScheduler, RECONCILE_PERIOD_HOURS, RESOLVE_DEADLINE_HOURS,
        )
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                ReconcileWorker(appContext, workerParameters, resolver, FakeTimeProvider(now))
        }
        return TestListenableWorkerBuilder<ReconcileWorker>(context).setWorkerFactory(factory).build()
    }

    /**
     * Inserts a habit AND its schedule. The schedule matters: [OccurrenceResolver] consults it to
     * decide whether a dated `MISSED` is even permissible, so a habit without one would let these
     * tests pass without ever exercising that gate.
     */
    private suspend fun insertHabit(kind: String = "DAILY", timesPerWeek: Int? = null): Long {
        val habitId = database.habitDao().insert(
            HabitEntity(name = "Read", question = null, colorArgb = 0, notes = null, archivedAt = null, createdAt = now.toString()),
        )
        database.scheduleDao().upsert(
            ScheduleEntity(
                habitId = habitId, kind = kind, timesPerWeek = timesPerWeek, dayOfWeek = null,
                dayOfMonth = null, intervalDays = null, anchorDate = null, weekStart = 1,
            ),
        )
        return habitId
    }

    @Test
    fun aDroppedAlarmIsFiredLateInsteadOfLost() = runBlocking {
        val habitId = insertHabit()
        val occId = database.reminderOccurrenceDao().upsert(
            occurrence(habitId, "2026-09-02", now.minusSeconds(30 * MINUTE_SECONDS).toEpochMilli(), "ARMED"),
        )

        buildWorker().doWork()

        verify { alarmScheduler.schedule(occId, now.toEpochMilli()) }
        assertTrue(database.entryDao().findByHabitId(habitId).isEmpty())
        assertEquals("ARMED", database.reminderOccurrenceDao().findById(occId)?.state)
    }

    /**
     * design.md §5.5 ("Occurrence state is persisted, so 'which alarms should exist' is always
     * recomputable from the database | Recovery after any platform-initiated cancellation is a
     * query, not a guess") and §13.4 finding 3, task G.5.
     *
     * A `SCHEDULE_EXACT_ALARM` revoke makes the platform cancel every alarm this app owns, and no
     * broadcast announces it — `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` is
     * grant-only. The row is still `ARMED` and still in the future, so the reconcile pass must
     * re-arm it at its ORIGINAL instant instead of waiting for that instant to pass and then
     * delivering late. Before task G.5 this test failed: `reconcile()` re-armed past-due
     * occurrences only.
     */
    @Test
    fun aFutureArmedOccurrenceIsReArmedAtItsOriginalInstant() = runBlocking {
        val habitId = insertHabit()
        val scheduledAt = now.plusSeconds(3 * HOUR_SECONDS)
        every { alarmScheduler.schedule(any(), any()) } returns false
        val occId = database.reminderOccurrenceDao().upsert(
            occurrence(habitId, "2026-09-02", scheduledAt.toEpochMilli(), "ARMED"),
        )

        buildWorker().doWork()

        verify { alarmScheduler.schedule(occId, scheduledAt.toEpochMilli()) }
        verify(exactly = 0) { alarmScheduler.cancel(any()) }
        val stored = database.reminderOccurrenceDao().findById(occId)
        assertEquals("ARMED", stored?.state)
        // The re-arm records the mode it actually got, so a row cannot keep claiming `exact` after
        // the permission it depended on was revoked (the §13.4 finding 1 class of stale claim).
        assertEquals(false, stored?.exact)
        assertTrue(database.entryDao().findByHabitId(habitId).isEmpty())
    }

    /**
     * **The trap the re-arm above must never fall into.** design.md §8.2: the alarm
     * `PendingIntent` request code IS `occurrence.id`, with no second id scheme — so the
     * occurrence's own alarm and its snooze alarm share one request code. Re-arming a `SNOOZED` row
     * at its original `scheduledAt` would OVERWRITE the pending snooze alarm and fire the reminder
     * at the wrong time, silently undoing the user's snooze. The re-arm condition is
     * `state == ARMED && scheduledAt > now` and must stay exactly that; widening it to an `else`
     * branch fails here.
     */
    @Test
    fun aLiveSnoozeIsNeverReArmedAndItsSnoozeAlarmIsLeftIntact() = runBlocking {
        val habitId = insertHabit()
        val occ = occurrence(habitId, "2026-09-02", now.minusSeconds(HOUR_SECONDS).toEpochMilli(), "SNOOZED")
            .copy(snoozeUntilEpochMs = now.plusSeconds(20 * MINUTE_SECONDS).toEpochMilli(), snoozeCount = 1)
        val occId = database.reminderOccurrenceDao().upsert(occ)

        buildWorker().doWork()

        verify(exactly = 0) { alarmScheduler.schedule(any(), any()) }
        verify(exactly = 0) { alarmScheduler.cancel(any()) }
        val stored = database.reminderOccurrenceDao().findById(occId)
        assertEquals("SNOOZED", stored?.state)
        assertEquals(now.plusSeconds(20 * MINUTE_SECONDS).toEpochMilli(), stored?.snoozeUntilEpochMs)
        assertTrue(database.entryDao().findByHabitId(habitId).isEmpty())
    }

    /** A `FIRED` occurrence already reached the user and is waiting for an answer (design.md
     *  §9.1): re-arming it would post the same reminder twice, so it is left alone until it is
     *  answered, snoozed, or hits its resolve deadline. */
    @Test
    fun aFiredOccurrenceAwaitingAnAnswerIsNotReArmed() = runBlocking {
        val habitId = insertHabit()
        val occ = occurrence(habitId, "2026-09-02", now.minusSeconds(30 * MINUTE_SECONDS).toEpochMilli(), "FIRED")
            .copy(notifiedAtEpochMs = now.minusSeconds(30 * MINUTE_SECONDS).toEpochMilli())
        val occId = database.reminderOccurrenceDao().upsert(occ)

        buildWorker().doWork()

        verify(exactly = 0) { alarmScheduler.schedule(any(), any()) }
        verify(exactly = 0) { alarmScheduler.cancel(any()) }
        assertEquals("FIRED", database.reminderOccurrenceDao().findById(occId)?.state)
        assertTrue(database.entryDao().findByHabitId(habitId).isEmpty())
    }

    @Test
    fun graceExpiryForceResolvesAnAbandonedSnoozeToMissed() = runBlocking {
        val habitId = insertHabit()
        val occ = occurrence(habitId, "2026-09-01", now.minusSeconds(3 * HOUR_SECONDS).toEpochMilli(), "SNOOZED")
            .copy(snoozeUntilEpochMs = now.minusSeconds(2 * HOUR_SECONDS).toEpochMilli(), snoozeCount = 1)
        val occId = database.reminderOccurrenceDao().upsert(occ)

        buildWorker().doWork()

        verify { alarmScheduler.cancel(occId) }
        val entries = database.entryDao().findByHabitAndDate(habitId, "2026-09-01")
        assertEquals(1, entries.size)
        assertEquals("MISSED", entries.single().status)
        assertEquals("ABANDONED", database.reminderOccurrenceDao().findById(occId)?.state)
    }

    @Test
    fun hardResolveDeadlineForceResolvesRegardlessOfState() = runBlocking {
        val habitId = insertHabit()
        val occId = database.reminderOccurrenceDao().upsert(
            occurrence(habitId, "2026-08-31", now.minusSeconds(25 * HOUR_SECONDS).toEpochMilli(), "ARMED"),
        )

        buildWorker().doWork()

        verify { alarmScheduler.cancel(occId) }
        verify(exactly = 0) { alarmScheduler.schedule(any(), any()) }
        val entries = database.entryDao().findByHabitAndDate(habitId, "2026-08-31")
        assertEquals(1, entries.size)
        assertEquals("ABANDONED", database.reminderOccurrenceDao().findById(occId)?.state)
    }

    /** design.md D3: "NOT a snooze cap" — decision 11 stands. Many snoozes inside the 24h resolve
     *  window (a high `snoozeCount`) are all accepted; only elapsed calendar time is bounded. */
    @Test
    fun manySnoozesWithinTheResolveWindowAreAllAccepted() = runBlocking {
        val habitId = insertHabit()
        val occ = occurrence(habitId, "2026-09-02", now.minusSeconds(HOUR_SECONDS).toEpochMilli(), "SNOOZED")
            .copy(snoozeUntilEpochMs = now.plusSeconds(10 * MINUTE_SECONDS).toEpochMilli(), snoozeCount = 50)
        val occId = database.reminderOccurrenceDao().upsert(occ)

        buildWorker().doWork()

        verify(exactly = 0) { alarmScheduler.cancel(any()) }
        assertTrue(database.entryDao().findByHabitId(habitId).isEmpty())
        val stored = database.reminderOccurrenceDao().findById(occId)
        assertEquals("SNOOZED", stored?.state)
        assertEquals(50, stored?.snoozeCount)
    }

    @Test
    fun anAbandonedWeeklyHabitNeverReceivesADatedMissed() = runBlocking {
        // design.md D8: N_TIMES_PER_WEEK's unit of obligation is the week, so no date may carry a
        // MISSED for it. The midnight sweep already honoured that; abandonment did not, which let
        // the same phantom failure in through the other door.
        val habitId = insertHabit(kind = "N_TIMES_PER_WEEK", timesPerWeek = 3)
        val occId = database.reminderOccurrenceDao().upsert(
            occurrence(habitId, "2026-08-31", now.minusSeconds(25 * HOUR_SECONDS).toEpochMilli(), "ARMED"),
        )

        buildWorker().doWork()

        verify { alarmScheduler.cancel(occId) }
        assertTrue(
            "an abandoned weekly-quota occurrence must not fabricate a dated failure",
            database.entryDao().findByHabitAndDate(habitId, "2026-08-31").isEmpty(),
        )
        assertEquals("ABANDONED", database.reminderOccurrenceDao().findById(occId)?.state)
    }

    private fun occurrence(habitId: Long, date: String, scheduledAtEpochMs: Long, state: String) = ReminderOccurrenceEntity(
        habitId = habitId,
        scheduledDate = date,
        scheduledAtEpochMs = scheduledAtEpochMs,
        state = state,
        snoozeUntilEpochMs = null,
        snoozeCount = 0,
        notifiedAtEpochMs = null,
        resolveDeadlineMs = scheduledAtEpochMs + RESOLVE_DEADLINE_HOURS * HOUR_SECONDS * 1000,
    )
}
