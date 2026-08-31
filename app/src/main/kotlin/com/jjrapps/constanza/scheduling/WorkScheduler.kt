package com.jjrapps.constanza.scheduling

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jjrapps.constanza.core.di.ReconcilePeriodHours
import com.jjrapps.constanza.core.time.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val HOURS_PER_DAY = 24L
private const val RECONCILE_WORK_NAME = "reconcile-worker"
private const val MIDNIGHT_SWEEP_WORK_NAME = "midnight-sweep-worker"

/** design.md §9.2: enrolls work unit 4b's two workers as persistent periodic `WorkManager` work,
 *  called once from `ConstanzaApplication.onCreate`. `UPDATE` (not `KEEP`) so tuning
 *  [ReconcilePeriodHours] takes effect on an existing install, not just a fresh one. */
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
    @ReconcilePeriodHours private val reconcilePeriodHours: Long,
) {
    fun scheduleAll() {
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            RECONCILE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReconcileWorker>(reconcilePeriodHours, TimeUnit.HOURS).build(),
        )
        workManager.enqueueUniquePeriodicWork(
            MIDNIGHT_SWEEP_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<MidnightSweepWorker>(HOURS_PER_DAY, TimeUnit.HOURS)
                .setInitialDelay(millisUntilNextMidnight(), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    private fun millisUntilNextMidnight(): Long {
        val nextMidnight = timeProvider.today().plusDays(1).atStartOfDay(timeProvider.zone())
        return Duration.between(timeProvider.now(), nextMidnight.toInstant()).toMillis().coerceAtLeast(0)
    }
}
