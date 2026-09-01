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
 * for the midnight sweep — one of §9.2's three redundant triggers (the other two are
 * `TimeChangeReceiver`'s `ACTION_DATE_CHANGED` and [ReconcileWorker]'s hourly pass). All decision
 * logic lives in [OccurrenceResolver] (task 4b.2), which this class only invokes.
 *
 * [WorkScheduler] enrols this as unique ONE-TIME work delayed to the next local midnight, and this
 * worker enqueues its own successor for the following midnight once the sweep has succeeded (task
 * G.4, design.md §13.4 finding 2). Recomputing the delay per run is the point: the periodic form it
 * replaces had its anchor re-derived from the first-ever enqueue instant on every cold start, which
 * made it skip the 00:00 boundary outright.
 *
 * A worker that re-enqueues itself would be a poor pattern with nothing behind it, so note that
 * nothing here is load-bearing alone: [ReconcileWorker.doWork] already calls
 * [OccurrenceResolver.sweepMidnight] on every hourly pass, and `TimeChangeReceiver` enqueues an
 * immediate one-shot sweep on `ACTION_DATE_CHANGED`. If a link in this chain were ever lost, the
 * hourly worker still sweeps and the next cold start re-enqueues the chain, because
 * [WorkScheduler.scheduleAll]'s `KEEP` only defers to a sweep that is still pending.
 */
@HiltWorker
class MidnightSweepWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val occurrenceResolver: OccurrenceResolver,
    private val timeProvider: TimeProvider,
    private val workScheduler: WorkScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        occurrenceResolver.sweepMidnight(timeProvider.today(), timeProvider.now())
        workScheduler.scheduleNextMidnightSweep()
        return Result.success()
    }
}
