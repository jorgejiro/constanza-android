package com.jjrapps.constanza.seed

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "ConstanzaAnchorProbe"

/** A unique name of its own, so the probe never touches `reconcile-worker` or
 *  `midnight-sweep-worker`. Cancelled at the end of the run. */
private const val PROBE_WORK_NAME = "anchor-probe-worker"

/** Long enough that the probe's work can never actually run during the measurement. */
private const val FIRST_DELAY_MINUTES = 30L
private const val SECOND_DELAY_MINUTES = 50L
private const val PERIOD_HOURS = 24L
private const val SETTLE_MS = 1_500L

/**
 * Answers one question the official documentation does not: **when
 * `enqueueUniquePeriodicWork` is called again with [ExistingPeriodicWorkPolicy.UPDATE] on work that
 * already exists, does the next run time move?**
 *
 * `WorkScheduler.scheduleAll()` runs on every `Application.onCreate` with `UPDATE`, and the API 37
 * delivery matrix (design §13.4, finding 2 / task G.4) measured the midnight sweep skipping the
 * 00:00 boundary after a process start. That symptom has two possible causes — `UPDATE` re-anchoring
 * the schedule, or something else entirely — and the fix differs depending on which it is. Neither
 * `ExistingPeriodicWorkPolicy`'s reference page nor the "update work" guide documents the timing
 * behaviour, so it is measured here against the exact WorkManager version this project ships.
 *
 * Reads `WorkInfo.nextScheduleTimeMillis` (WorkManager 2.9+; this project is on 2.11.2) before and
 * after each re-enqueue. Asserts nothing — it is a measurement, and the numbers go to logcat under
 * [TAG] for a human to read.
 *
 * **The answer, measured 2026-09-01 on the Pixel 10 (Android 17, WorkManager 2.11.2):** re-enqueuing
 * with `UPDATE` and the SAME initial delay left the next run time unchanged; with a DIFFERENT initial
 * delay (30m → 50m) it shifted by exactly the delta — `+1200000ms`, `08:52:17.615` →
 * `09:12:17.615`, the milliseconds identical on both sides; `KEEP` left it unchanged. So **`UPDATE`
 * applies the new initial delay to the ORIGINAL enqueue instant, not to now.**
 *
 * That acquits `ReconcileWorker`, which is enqueued with no initial delay and an unchanging request
 * shape, and convicts the midnight sweep alone, whose delay was recomputed on every cold start and
 * then applied to the first-ever enqueue instant. Task G.4 accordingly left the reconcile worker's
 * `UPDATE` untouched and moved the sweep to self-rescheduled one-time work. This fixture is kept
 * rather than deleted because that timing rule appears in neither of the two documentation pages
 * above, so the only record of it is this measurement.
 */
@SeedOnly
@RunWith(AndroidJUnit4::class)
class PeriodicAnchorProbe {

    /** Never runs: every enqueue below is delayed well past the probe's own lifetime. */
    class NoopWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): ListenableWorker.Result = ListenableWorker.Result.success()
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workManager = WorkManager.getInstance(context)
    private val zone: ZoneId = ZoneId.systemDefault()

    @Test
    fun measureWhetherUpdateMovesTheNextRunTime() {
        log("===== ANCHOR PROBE BEGIN =====")
        log("workManagerConfiguration minimumLoggingLevel=${Configuration.Builder().build().minimumLoggingLevel}")

        workManager.cancelUniqueWork(PROBE_WORK_NAME).result.get()
        Thread.sleep(SETTLE_MS)

        enqueue(ExistingPeriodicWorkPolicy.UPDATE, FIRST_DELAY_MINUTES)
        val first = readNextRun("after first enqueue (UPDATE, delay=${FIRST_DELAY_MINUTES}m)")

        // Exactly what a cold start does today: same policy, same shape, delay recomputed.
        enqueue(ExistingPeriodicWorkPolicy.UPDATE, FIRST_DELAY_MINUTES)
        val sameDelay = readNextRun("after re-enqueue UPDATE with the SAME delay")

        enqueue(ExistingPeriodicWorkPolicy.UPDATE, SECOND_DELAY_MINUTES)
        val changedDelay = readNextRun("after re-enqueue UPDATE with a DIFFERENT delay (${SECOND_DELAY_MINUTES}m)")

        enqueue(ExistingPeriodicWorkPolicy.KEEP, FIRST_DELAY_MINUTES)
        val afterKeep = readNextRun("after re-enqueue KEEP with the ORIGINAL delay")

        log("VERDICT sameDelayUpdateMovedAnchor=${moved(first, sameDelay)}")
        log("VERDICT changedDelayUpdateMovedAnchor=${moved(sameDelay, changedDelay)}")
        log("VERDICT keepMovedAnchor=${moved(changedDelay, afterKeep)}")

        workManager.cancelUniqueWork(PROBE_WORK_NAME).result.get()
        log("probe work cancelled")
        log("===== ANCHOR PROBE END =====")
    }

    private fun enqueue(policy: ExistingPeriodicWorkPolicy, delayMinutes: Long) {
        workManager.enqueueUniquePeriodicWork(
            PROBE_WORK_NAME,
            policy,
            PeriodicWorkRequestBuilder<NoopWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build(),
        ).result.get()
        Thread.sleep(SETTLE_MS)
    }

    private fun readNextRun(label: String): Long {
        val infos = workManager.getWorkInfosForUniqueWork(PROBE_WORK_NAME).get()
        if (infos.isEmpty()) {
            log("$label -> NO WORKINFO")
            return -1
        }
        val next = infos.first().nextScheduleTimeMillis
        val rendered =
            if (next == Long.MAX_VALUE) "MAX_VALUE (not scheduled)"
            else Instant.ofEpochMilli(next).atZone(zone).toString()
        log("$label -> state=${infos.first().state} nextScheduleTimeMillis=$next ($rendered)")
        return next
    }

    private fun moved(before: Long, after: Long): String =
        if (before == after) "false (unchanged: $before)" else "true (${after - before}ms shift)"

    private fun log(message: String) = Log.i(TAG, message)
}
