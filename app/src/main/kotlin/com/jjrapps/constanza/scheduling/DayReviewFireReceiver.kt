package com.jjrapps.constanza.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf

private const val DAY_REVIEW_FIRE_WORK_NAME = "day-review-fire"

/**
 * day-review-exact-alarm: the [android.app.PendingIntent] target [DayReviewAlarmScheduler]'s alarm
 * fires into, mirroring [ReminderFireReceiver]'s own shape exactly — `exported = false` (design.md
 * §12), and no Room/DataStore access here either, for the identical reason: the ~10s `onReceive()`
 * budget is unreliable under OEM throttling, so the late-fire guard, the row read, the decision and
 * the post all happen in the expedited [DayReviewFireWorker] this enqueues, unique-named
 * [DAY_REVIEW_FIRE_WORK_NAME] (distinct from [ReminderFireReceiver]'s own `fire-<occurrenceId>` names
 * and from the now-deleted `WorkManager` day-review work name, so none of the three can ever collide
 * or be cancelled by one another).
 */
class DayReviewFireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduledDate = intent.getStringExtra(EXTRA_SCHEDULED_DATE) ?: return
        WorkManager.getInstance(context).enqueueUniqueWork(
            DAY_REVIEW_FIRE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DayReviewFireWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(DayReviewFireWorker.KEY_SCHEDULED_DATE to scheduledDate))
                .build(),
        )
    }

    companion object {
        /** [DayReviewAlarmScheduler.scheduleNext]'s intent-extra key for the target LOCAL DATE this
         *  alarm was armed for — read back by [DayReviewFireWorker]'s late-fire guard. */
        const val EXTRA_SCHEDULED_DATE = "scheduledDate"
    }
}
