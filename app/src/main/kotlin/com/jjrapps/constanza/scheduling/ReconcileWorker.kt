package com.jjrapps.constanza.scheduling

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jjrapps.constanza.core.time.TimeProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * design.md §5.5: "the hourly reconcile worker is the correctness net, not an optimisation" — the
 * whole design's answer to OEM throttling and any API 37 alarm-delivery behaviour that could not be
 * verified offline (§5.3). Delegates all decision logic to [OccurrenceResolver] (task 4b.1/4b.3) so
 * this class stays a thin `WorkManager` adapter, matching [BootReceiver]/[TimeChangeReceiver]'s
 * existing thin-wrapper pattern. Also runs [OccurrenceResolver.sweepMidnight] on every pass — the
 * third of design.md §9.2's three redundant midnight triggers (the other two are
 * [MidnightSweepWorker]'s own periodic schedule and `TimeChangeReceiver`'s `ACTION_DATE_CHANGED`).
 *
 * `minSdk = 31` means this expedited periodic worker maps to an expedited `JobScheduler` job, never
 * a foreground service (design.md §5.5/§5.6).
 */
@HiltWorker
class ReconcileWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val occurrenceResolver: OccurrenceResolver,
    private val timeProvider: TimeProvider,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val now = timeProvider.now()
        occurrenceResolver.reconcile(now)
        occurrenceResolver.sweepMidnight(timeProvider.today(), now)
        return Result.success()
    }
}
