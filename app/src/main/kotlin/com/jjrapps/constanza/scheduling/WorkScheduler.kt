package com.jjrapps.constanza.scheduling

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.jjrapps.constanza.core.di.ReconcilePeriodHours
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.core.time.millisUntilNextLocalTime
import com.jjrapps.constanza.core.time.millisUntilNextMidnight
import com.jjrapps.constanza.core.time.nextLocalDateForLocalTime
import com.jjrapps.constanza.reminding.DayReviewSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal const val RECONCILE_WORK_NAME = "reconcile-worker"
internal const val MIDNIGHT_SWEEP_WORK_NAME = "midnight-sweep-worker"

/** day-review, slice B (day-review-notification): [DayReviewWorker]'s own unique work name — never
 *  [MIDNIGHT_SWEEP_WORK_NAME], per this feature's own brief: two different jobs, two different
 *  names, so [ExistingWorkPolicy.KEEP]/`REPLACE` on one can never touch the other's pending request. */
internal const val DAY_REVIEW_WORK_NAME = "day-review-worker"

/**
 * design.md §9.2: enrolls work unit 4b's two workers as persistent `WorkManager` work, called once
 * from `ConstanzaApplication.onCreate`. The two are enrolled differently on purpose, and task G.4
 * (design.md §13.4 finding 2) is why:
 *
 * - [ReconcileWorker] stays periodic under [ExistingPeriodicWorkPolicy.UPDATE], so tuning
 *   [ReconcilePeriodHours] reaches an existing install and not just a fresh one. It carries no
 *   initial delay and its request shape is identical on every launch, so the repeated `UPDATE` a
 *   cold start performs leaves its next run time exactly where it was — measured on the Pixel 10
 *   against this project's WorkManager 2.11.2, not assumed from the docs, which do not say.
 * - The midnight sweep is unique ONE-TIME work anchored to the next local midnight and re-enqueued
 *   by [MidnightSweepWorker] itself (see [scheduleNextMidnightSweep]). It cannot be periodic: the
 *   same measurement showed `UPDATE` applying a new initial delay to the ORIGINAL enqueue instant
 *   rather than to now, so a delay recomputed on every cold start produced an anchor of
 *   `firstEnqueueTime + millisUntilNextMidnight(now)` — not midnight, and drifting further with
 *   every launch. A process start at 00:04:55 left the sweep reading `Delay=+23h29m59s`, having
 *   skipped the 00:00 boundary entirely. [ExistingWorkPolicy.KEEP] is what makes a cold start
 *   harmless here: a sweep that is already pending is left where it is.
 */
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
    @ReconcilePeriodHours private val reconcilePeriodHours: Long,
    private val dayReviewSettingsStore: DayReviewSettingsStore,
) {
    /** day-review, slice B: deliberately does NOT also enqueue [DayReviewWorker] — see
     *  [scheduleDayReview]'s own KDoc for why that startup enqueue is a separate, asynchronous call
     *  made from [com.jjrapps.constanza.ConstanzaApplication.onCreate] instead of folded in here. */
    fun scheduleAll() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RECONCILE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReconcileWorker>(reconcilePeriodHours, TimeUnit.HOURS).build(),
        )
        enqueueMidnightSweep(ExistingWorkPolicy.KEEP)
    }

    /**
     * Anchors the NEXT sweep, called by [MidnightSweepWorker] at the end of a successful run. This
     * is what makes the anchor self-correcting: it is recomputed once a day from the clock as it
     * stands then, never derived from a stale first-enqueue instant, so a DST transition or a
     * timezone change is absorbed on the following boundary for free.
     *
     * [ExistingWorkPolicy.REPLACE], not `KEEP`: the run doing the calling still holds
     * [MIDNIGHT_SWEEP_WORK_NAME], and `KEEP` counts work that is `RUNNING` as pending, so it would
     * silently drop the successor and end the chain. `REPLACE` also holds the invariant that at most
     * one sweep is ever pending under this name, which matters because `TimeChangeReceiver` runs the
     * same worker under a name of its own on `ACTION_DATE_CHANGED` and re-anchors this one through
     * here. Measured on the Pixel 10 rather than assumed (`WorkSchedulerTest`): after a run the name
     * holds exactly one `WorkInfo`, the `ENQUEUED` successor, with the finished run's own record
     * replaced away — the sweep's writes are committed before this call, so nothing is lost with it.
     */
    fun scheduleNextMidnightSweep() = enqueueMidnightSweep(ExistingWorkPolicy.REPLACE)

    private fun enqueueMidnightSweep(policy: ExistingWorkPolicy) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            MIDNIGHT_SWEEP_WORK_NAME,
            policy,
            OneTimeWorkRequestBuilder<MidnightSweepWorker>()
                .setInitialDelay(timeProvider.millisUntilNextMidnight(), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    /**
     * day-review, slice B (day-review-notification): [DayReviewWorker]'s sibling of
     * [scheduleNextMidnightSweep] — anchored to [DayReviewSettingsStore.currentReviewTimeMinuteOfDay]
     * via [millisUntilNextLocalTime], [MidnightSweepWorker]'s own pattern generalised from a fixed
     * midnight to an arbitrary configured time. `suspend`, unlike every other method here: the
     * anchor depends on a `DataStore` read, which the midnight sweep's fixed boundary never needed.
     *
     * [DayReviewWorker.DAY_REVIEW_SCHEDULED_DATE_KEY] carries the LOCAL DATE this run targets
     * ([nextLocalDateForLocalTime]) as the request's input data — [DayReviewWorker]'s late-fire
     * guard reads it back and compares it against [TimeProvider.today] at run time, per this
     * feature's own brief: a run that wakes up after its target date has ended must post nothing,
     * because the midnight sweep has already turned every unanswered slot it would have reported
     * into `MISSED` and rolled the date.
     *
     * [policy] defaults to [ExistingWorkPolicy.KEEP], for [scheduleAll]'s cold-start call — an
     * already-pending review must not be re-anchored on every launch, mirroring
     * [enqueueMidnightSweep]'s own `KEEP` reasoning exactly. [scheduleNextDayReview] passes
     * `REPLACE` instead, for the identical reason [scheduleNextMidnightSweep] does: the run doing
     * the calling still holds [DAY_REVIEW_WORK_NAME], and `KEEP` treats a `RUNNING` request as
     * pending, which would silently drop the successor and end the self-rescheduling chain.
     *
     * This is also the method a future settings screen must call after writing a new
     * [DayReviewSettingsStore.setReviewTimeMinuteOfDay] — no such screen exists yet in this slice
     * (day-review-data only added the store; nothing in `:app` writes to it yet), so today this is
     * reachable only from [scheduleAll]'s startup call and from [scheduleNextDayReview]'s own
     * self-reschedule.
     */
    suspend fun scheduleDayReview(policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val minuteOfDay = dayReviewSettingsStore.currentReviewTimeMinuteOfDay()
        val targetDate = timeProvider.nextLocalDateForLocalTime(minuteOfDay)
        WorkManager.getInstance(context).enqueueUniqueWork(
            DAY_REVIEW_WORK_NAME,
            policy,
            OneTimeWorkRequestBuilder<DayReviewWorker>()
                .setInitialDelay(timeProvider.millisUntilNextLocalTime(minuteOfDay), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(DayReviewWorker.DAY_REVIEW_SCHEDULED_DATE_KEY to targetDate.toString()))
                .build(),
        )
    }

    /** Anchors the NEXT day-review run, called by [DayReviewWorker] at the end of every one of its
     *  own runs — including a late-fire, guarded run, per this feature's own brief: "a missed review
     *  is not a failure, it is a review whose moment has passed", so the chain must continue exactly
     *  as if it had posted. See [scheduleDayReview]'s KDoc for why `REPLACE` is required here. */
    suspend fun scheduleNextDayReview() = scheduleDayReview(ExistingWorkPolicy.REPLACE)
}
