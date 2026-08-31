package com.jjrapps.constanza.scheduling

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jjrapps.constanza.core.time.TimeProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * design.md D3/D8/§9.2 (habit-entry-tracking: Midnight Transition): the primary, dedicated trigger
 * for the midnight sweep. [WorkScheduler] enrolls this as periodic `WorkManager` work with an
 * initial delay targeting the next local midnight, re-firing every 24h after that — one of §9.2's
 * three redundant triggers (the other two are `TimeChangeReceiver`'s `ACTION_DATE_CHANGED` and
 * [ReconcileWorker]'s hourly pass). All decision logic lives in [OccurrenceResolver] (task 4b.2),
 * which this class only invokes.
 */
@HiltWorker
class MidnightSweepWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val occurrenceResolver: OccurrenceResolver,
    private val timeProvider: TimeProvider,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        occurrenceResolver.sweepMidnight(timeProvider.today(), timeProvider.now())
        return Result.success()
    }
}
