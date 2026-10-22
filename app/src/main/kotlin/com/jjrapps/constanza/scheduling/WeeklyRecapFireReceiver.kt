package com.jjrapps.constanza.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf

private const val WEEKLY_RECAP_FIRE_WORK_NAME = "weekly-recap-fire"

/** weekly-recap: [WeeklyRecapAlarmScheduler]'s alarm target, shaped like [DayReviewFireReceiver] —
 *  not exported, no database work in `onReceive()`, only an expedited [WeeklyRecapFireWorker]. */
class WeeklyRecapFireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduledDate = intent.getStringExtra(EXTRA_SCHEDULED_DATE) ?: return
        WorkManager.getInstance(context).enqueueUniqueWork(
            WEEKLY_RECAP_FIRE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WeeklyRecapFireWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(WeeklyRecapFireWorker.KEY_SCHEDULED_DATE to scheduledDate))
                .build(),
        )
    }

    companion object {
        /** The Monday this alarm was armed for, as an ISO date. */
        const val EXTRA_SCHEDULED_DATE = "scheduledDate"
    }
}
