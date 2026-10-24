package com.jjrapps.constanza.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf

private const val MORNING_RECAP_FIRE_WORK_NAME = "morning-recap-fire"

/** [MorningRecapAlarmScheduler]'s alarm target, shaped exactly like [DayReviewFireReceiver]: no
 *  Room or DataStore work inside `onReceive()`, only an expedited [MorningRecapFireWorker]. */
class MorningRecapFireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduledDate = intent.getStringExtra(EXTRA_SCHEDULED_DATE) ?: return
        WorkManager.getInstance(context).enqueueUniqueWork(
            MORNING_RECAP_FIRE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<MorningRecapFireWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(MorningRecapFireWorker.KEY_SCHEDULED_DATE to scheduledDate))
                .build(),
        )
    }

    companion object {
        const val EXTRA_SCHEDULED_DATE = "scheduledDate"
    }
}
