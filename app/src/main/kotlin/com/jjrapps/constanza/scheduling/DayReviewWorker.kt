package com.jjrapps.constanza.scheduling

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.reminding.DayReviewNotificationPoster
import com.jjrapps.constanza.reminding.DayReviewSettingsStore
import com.jjrapps.constanza.tracking.DayHabitRowsAssembler
import com.jjrapps.constanza.tracking.DayReviewDecision
import com.jjrapps.constanza.tracking.decideDayReview
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import javax.inject.Inject

/** Bundles the three day-review-specific collaborators [DayReviewWorker] needs beyond
 *  [TimeProvider]/[WorkScheduler], keeping its constructor under detekt's `LongParameterList`
 *  threshold — same reasoning as [com.jjrapps.constanza.habit.HabitDaos]/[SchedulingDaos]. */
data class DayReviewComponents @Inject constructor(
    val settingsStore: DayReviewSettingsStore,
    val rowsAssembler: DayHabitRowsAssembler,
    val notificationPoster: DayReviewNotificationPoster,
)

/**
 * day-review, slice B (day-review-notification): the once-a-night "review your day" nudge —
 * [WorkScheduler] enrols this exactly the way it enrols [MidnightSweepWorker] (unique ONE-TIME work,
 * re-enqueuing its own successor via [WorkScheduler.scheduleNextDayReview] once this run is done),
 * anchored to [DayReviewSettingsStore.currentReviewTimeMinuteOfDay] instead of a fixed midnight. See
 * [WorkScheduler.scheduleDayReview] for the anchoring and the input-data contract.
 *
 * **The late-fire guard.** This app's own `WorkScheduler` KDoc documents a MEASURED history of
 * `WorkManager` anchor drift (task G.4): a cold start once recomputed the midnight sweep's delay
 * against the wrong instant and pushed it past the 00:00 boundary outright. If THIS job drifts past
 * midnight, [MidnightSweepWorker] has already run: every unanswered slot is now `MISSED` and the
 * date has rolled, so a notification built from a fresh read at that point would be reporting a day
 * that no longer exists in the shape it fired for — wrong, not merely late.
 *
 * [WorkScheduler.scheduleDayReview] embeds the LOCAL DATE this run targets
 * ([DAY_REVIEW_SCHEDULED_DATE_KEY], via [com.jjrapps.constanza.core.time.nextLocalDateForLocalTime])
 * as the request's input data. [doWork] compares that date against [TimeProvider.today] as read AT
 * RUN TIME: if they differ, this run woke up after its target date already ended, and it posts
 * nothing. Per this feature's own brief, that is a SUCCESS, never a failure — "a missed review is
 * not a failure, it is a review whose moment has passed" — so the chain continues exactly as if the
 * notification had posted, via the unconditional [WorkScheduler.scheduleNextDayReview] call below.
 *
 * **The "nothing due at all" rule.** [decideDayReview] treats an empty [DayHabitRowsAssembler.rowsFor]
 * result (no habit was due that day at all) as "nothing to review" in both
 * [DayReviewSettingsStore.currentReviewFiresEveryNight] modes, including the `true` "closing
 * ritual" default — without it, an owner whose habits are entirely weekday-only would get a review
 * notification every Saturday and Sunday with nothing to say. This is a product call flagged in
 * this slice's own report for the owner to confirm or veto, not one this worker was free to make
 * silently.
 */
@HiltWorker
class DayReviewWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val components: DayReviewComponents,
    private val timeProvider: TimeProvider,
    private val workScheduler: WorkScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scheduledDate = inputData.getString(DAY_REVIEW_SCHEDULED_DATE_KEY)?.let(LocalDate::parse)
            ?: timeProvider.today()
        // Reschedule FIRST and unconditionally, before the late-fire check below: a self-rescheduling
        // worker that returned early on the guarded path without enqueuing its successor would
        // silently end the chain the very first time it ever fired late — exactly the failure mode
        // MidnightSweepWorker's own KDoc chain-breakage warning is about, reached here on the
        // late-fire path specifically rather than a dropped link.
        workScheduler.scheduleNextDayReview()
        if (timeProvider.today() != scheduledDate) return Result.success()

        val rows = components.rowsAssembler.rowsFor(scheduledDate)
        val firesEveryNight = components.settingsStore.currentReviewFiresEveryNight()
        when (val decision = decideDayReview(rows, firesEveryNight)) {
            is DayReviewDecision.Post -> components.notificationPoster.postReview(decision.outstandingCount)
            DayReviewDecision.Skip -> Unit
        }
        return Result.success()
    }

    internal companion object {
        /** [WorkScheduler.scheduleDayReview]'s input-data key for the local date this run targets —
         *  read back here by [doWork]'s late-fire guard. */
        const val DAY_REVIEW_SCHEDULED_DATE_KEY = "scheduled_date"
    }
}
