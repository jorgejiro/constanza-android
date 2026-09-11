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

/** Bundles the three day-review-specific collaborators [DayReviewFireWorker] needs beyond
 *  [TimeProvider]/[DayReviewAlarmScheduler], keeping its constructor under detekt's
 *  `LongParameterList` threshold — same reasoning as
 *  [com.jjrapps.constanza.habit.HabitDaos]/[SchedulingDaos]. Carried over verbatim (day-review-exact-alarm)
 *  from the deleted `WorkManager`-based `DayReviewWorker`; only its scheduling and fire path moved,
 *  not this bundle's shape. */
data class DayReviewComponents @Inject constructor(
    val settingsStore: DayReviewSettingsStore,
    val rowsAssembler: DayHabitRowsAssembler,
    val notificationPoster: DayReviewNotificationPoster,
)

/**
 * day-review-exact-alarm: the once-a-night "review your day" nudge's fire path, enqueued by
 * [DayReviewFireReceiver] off [DayReviewAlarmScheduler]'s exact/inexact alarm — replacing the
 * anchored `WorkManager` one-time work the now-deleted `DayReviewWorker` used, for the reason
 * [DayReviewAlarmScheduler]'s own KDoc records: a wall-clock `WorkManager` anchor has no equivalent
 * Doze-survival guarantee, so the review could slip past midnight and be silently lost.
 *
 * **The late-fire guard is still load-bearing**, carried over from `DayReviewWorker` unchanged. The
 * exact-alarm path (design.md:1158) is measured to survive Doze, but the degraded `setWindow` path
 * ([AlarmScheduler.scheduleExactOrInexact]) still has a 10-minute window, and OEM background-kill or
 * throttling can still push even an exact alarm's `WorkManager` follow-up past midnight. If THIS run
 * fires after its target date, [MidnightSweepWorker] has already turned every unanswered slot
 * `MISSED` and rolled the date, so a fresh read at that point would report a day that no longer
 * exists in the shape it fired for — wrong, not merely late. [DayReviewAlarmScheduler.scheduleNext]
 * embeds the target LOCAL DATE as [DayReviewFireReceiver.EXTRA_SCHEDULED_DATE], carried through
 * [KEY_SCHEDULED_DATE] as this worker's own input data; [doWork] compares it against
 * [TimeProvider.today] read AT RUN TIME, and posts nothing when they differ. Per this feature's own
 * brief, that is a SUCCESS, never a failure — "a missed review is not a failure, it is a review whose
 * moment has passed" — so the chain continues exactly as if the notification had posted, via the
 * unconditional [DayReviewAlarmScheduler.scheduleNext] call below.
 *
 * **The "nothing due at all" rule**, also carried over unchanged: [decideDayReview] treats an empty
 * [DayHabitRowsAssembler.rowsFor] result as "nothing to review" in both
 * [DayReviewSettingsStore.currentReviewFiresEveryNight] modes.
 *
 * Re-arming the next alarm LAST (rather than first) mirrors `DayReviewWorker`'s own ordering, though
 * for a narrower reason here: `AlarmManager`'s `PendingIntent.FLAG_UPDATE_CURRENT` re-arm carries no
 * `WorkManager`-style self-cancellation risk (there is no `REPLACE`-of-a-`RUNNING`-request to trigger
 * it — see the deleted `WorkScheduler.scheduleNextDayReview` KDoc for that history), but the guard
 * must still run before the reschedule reads a settings/time snapshot for the FOLLOWING night, not
 * this one.
 */
@HiltWorker
class DayReviewFireWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val components: DayReviewComponents,
    private val timeProvider: TimeProvider,
    private val alarmScheduler: DayReviewAlarmScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scheduledDate = inputData.getString(KEY_SCHEDULED_DATE)?.let(LocalDate::parse)
            ?: timeProvider.today()
        if (timeProvider.today() == scheduledDate) {
            val rows = components.rowsAssembler.rowsFor(scheduledDate)
            val firesEveryNight = components.settingsStore.currentReviewFiresEveryNight()
            when (val decision = decideDayReview(rows, firesEveryNight)) {
                is DayReviewDecision.Post -> components.notificationPoster.postReview(decision.outstandingCount)
                DayReviewDecision.Skip -> Unit
            }
        }
        alarmScheduler.scheduleNext()
        return Result.success()
    }

    internal companion object {
        /** [DayReviewFireReceiver]'s input-data key for the local date this run targets — read back
         *  here by [doWork]'s late-fire guard. */
        const val KEY_SCHEDULED_DATE = "scheduled_date"
    }
}
