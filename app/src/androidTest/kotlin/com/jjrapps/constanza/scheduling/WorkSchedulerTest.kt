package com.jjrapps.constanza.scheduling

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private const val RECONCILE_PERIOD_HOURS = 1L
private const val COLD_START_GAP_MINUTES = 17L
private const val ANCHOR_TOLERANCE_MS = 10_000L
private const val AWAIT_TIMEOUT_MS = 10_000L
private const val AWAIT_POLL_MS = 25L

/** 00:05 UTC, and the fake clock's zone is UTC: the next boundary is 23h55m out. Deliberately just
 *  past midnight, because that is the shape of the §13.4 finding-2 failure. */
private val FIRST_COLD_START: Instant = Instant.parse("2026-09-02T00:05:00Z")
private val SWEEP_FIRES_AT: Instant = Instant.parse("2026-09-03T00:00:00Z")

/**
 * Task G.4 (design.md §13.4 finding 2). Asserts the SCHEDULING itself, not worker behaviour: what
 * [WorkScheduler.scheduleAll] leaves in `WorkManager`, and whether a second cold start disturbs it.
 *
 * PR #14's lesson decides the technique. `TestListenableWorkerBuilder` bypasses both the worker
 * factory and the manifest, so it can never answer a question about enqueued state; every claim
 * below is read back through `WorkManager`'s own query APIs
 * ([WorkManager.getWorkInfosForUniqueWork], [WorkInfo.nextScheduleTimeMillis],
 * [WorkInfo.periodicityInfo]) — the same reading that measured the defect on the device.
 *
 * The anchor is asserted as a delay against the real clock rather than as an absolute instant:
 * `setInitialDelay` is relative to WorkManager's own enqueue time, so only the offset it produces is
 * under the fake clock's control.
 */
@RunWith(AndroidJUnit4::class)
class WorkSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val timeProvider = FakeTimeProvider(FIRST_COLD_START)
    private val occurrenceResolver = mockk<OccurrenceResolver>(relaxed = true)
    private val scheduler = WorkScheduler(context, timeProvider, RECONCILE_PERIOD_HOURS)
    private lateinit var workManager: WorkManager

    /** Stands in for `HiltWorkerFactory`, which an instrumented test cannot reach — without it the
     *  `@AssistedInject`-only workers would fail to instantiate and every state read below would be
     *  measuring a failed job instead of a schedule (task 5.9's discovery, from the other side). */
    private val workerFactory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
            when (workerClassName) {
                MidnightSweepWorker::class.java.name ->
                    MidnightSweepWorker(appContext, workerParameters, occurrenceResolver, timeProvider, scheduler)
                ReconcileWorker::class.java.name ->
                    ReconcileWorker(appContext, workerParameters, occurrenceResolver, timeProvider)
                else -> null
            }
    }

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(workerFactory).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun theFirstMidnightSweepIsOneTimeWorkAnchoredToTheNextLocalMidnight() {
        val enqueuedAt = System.currentTimeMillis()

        scheduler.scheduleAll()

        val sweep = pendingSweep()
        assertNull(
            "the sweep must not be periodic: UPDATE applies a new initial delay to the ORIGINAL " +
                "enqueue instant, which is what pushed it past the 00:00 boundary",
            sweep.periodicityInfo,
        )
        assertEquals(WorkInfo.State.ENQUEUED, sweep.state)
        assertAnchoredAt(Duration.ofHours(23).plusMinutes(55).toMillis(), enqueuedAt, sweep)
    }

    /**
     * The regression this task fixes. A first enqueue alone would have looked correct under the old
     * scheduling too; only the SECOND `scheduleAll()` — one more cold start, minutes later, with a
     * freshly recomputed delay — exposed it, moving the anchor by exactly the delta between the two
     * delays instead of leaving the pending sweep alone.
     */
    @Test
    fun aSecondColdStartDoesNotMoveThePendingMidnightSweep() {
        scheduler.scheduleAll()
        val first = pendingSweep()

        timeProvider.instant = FIRST_COLD_START.plus(Duration.ofMinutes(COLD_START_GAP_MINUTES))
        scheduler.scheduleAll()

        val afterSecondColdStart = pendingSweep()
        assertEquals(
            "a cold start must not re-anchor a sweep that is already pending",
            first.nextScheduleTimeMillis,
            afterSecondColdStart.nextScheduleTimeMillis,
        )
        assertEquals("KEEP must leave the pending request itself in place", first.id, afterSecondColdStart.id)
        assertEquals("a cold start must not stack a second sweep", 1, sweepInfos().size)
    }

    /** The other half of the fix: the chain is what makes the anchor self-correcting, so a run that
     *  does not leave a successor behind would silently reduce the sweep to a one-off. */
    @Test
    fun aCompletedSweepEnqueuesItsSuccessorForTheFollowingMidnight() {
        scheduler.scheduleAll()
        val firstId = pendingSweep().id

        timeProvider.instant = SWEEP_FIRES_AT
        val ranAt = System.currentTimeMillis()
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(firstId)

        val successor = awaitPendingSweepOtherThan(firstId)
        coVerify { occurrenceResolver.sweepMidnight(any(), any()) }
        assertNull("the successor is one-time work too", successor.periodicityInfo)
        assertAnchoredAt(Duration.ofDays(1).toMillis(), ranAt, successor)
        assertEquals(
            "REPLACE must leave exactly one sweep under the unique name, never a growing chain",
            1,
            sweepInfos().size,
        )
    }

    /**
     * §13.4 finding 2 named both periodic workers; the measurement behind this task showed only the
     * sweep was affected. [ReconcileWorker] carries no initial delay and an unchanging request
     * shape, so `UPDATE` has nothing to re-anchor — and `UPDATE` is what lets a tuned
     * `ReconcilePeriodHours` reach an existing install, so it stays exactly as it was.
     */
    @Test
    fun reconcileWorkStaysPeriodicOnItsConfiguredPeriodAcrossColdStarts() {
        val expectedPeriodMs = TimeUnit.HOURS.toMillis(RECONCILE_PERIOD_HOURS)

        scheduler.scheduleAll()
        assertEquals(expectedPeriodMs, reconcileInfo().periodicityInfo?.repeatIntervalMillis)

        timeProvider.instant = FIRST_COLD_START.plus(Duration.ofMinutes(COLD_START_GAP_MINUTES))
        scheduler.scheduleAll()

        val reconcile = reconcileInfo()
        assertEquals(expectedPeriodMs, reconcile.periodicityInfo?.repeatIntervalMillis)
        assertNotEquals(
            "the hourly correctness net must never be left cancelled",
            WorkInfo.State.CANCELLED,
            reconcile.state,
        )
        assertNotEquals("the hourly correctness net must never be left failed", WorkInfo.State.FAILED, reconcile.state)
    }

    private fun sweepInfos(): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(MIDNIGHT_SWEEP_WORK_NAME).get()

    private fun pendingSweep(): WorkInfo = sweepInfos().single { !it.state.isFinished }

    private fun reconcileInfo(): WorkInfo =
        workManager.getWorkInfosForUniqueWork(RECONCILE_WORK_NAME).get().single()

    /** [MidnightSweepWorker] is a `CoroutineWorker`, so it runs off the test's thread however the
     *  executor is configured; polling is the honest way to wait for its successor. */
    private fun awaitPendingSweepOtherThan(previousId: UUID): WorkInfo {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val candidate = sweepInfos().firstOrNull { !it.state.isFinished && it.id != previousId }
            if (candidate != null) return candidate
            Thread.sleep(AWAIT_POLL_MS)
        }
        error("no successor sweep was enqueued within ${AWAIT_TIMEOUT_MS}ms; states=${sweepInfos().map { it.state }}")
    }

    private fun assertAnchoredAt(expectedDelayMs: Long, enqueuedAtMillis: Long, info: WorkInfo) {
        val measuredDelayMs = info.nextScheduleTimeMillis - enqueuedAtMillis
        assertTrue(
            "expected an anchor ${expectedDelayMs}ms out, measured ${measuredDelayMs}ms",
            abs(measuredDelayMs - expectedDelayMs) <= ANCHOR_TOLERANCE_MS,
        )
    }
}
