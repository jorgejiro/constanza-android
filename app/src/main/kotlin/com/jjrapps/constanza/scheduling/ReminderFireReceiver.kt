package com.jjrapps.constanza.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf

private const val NO_OCCURRENCE_ID = -1L

/**
 * The [android.app.PendingIntent] target every [AlarmScheduler] alarm fires into (design.md §9.1).
 * `exported = false` (design.md §12). Task 5.9: validate-only, no Room access — the ~10s
 * `onReceive()` budget is unreliable under OEM throttling, so loading the occurrence,
 * re-evaluating `dueOn`, and posting all happen in the expedited [ReminderFireWorker] this
 * enqueues, unique-named `fire-<occurrenceId>` (design.md §8.2's naming pattern extended here).
 */
class ReminderFireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val occurrenceId = intent.getLongExtra(AlarmScheduler.EXTRA_OCCURRENCE_ID, NO_OCCURRENCE_ID)
        if (occurrenceId == NO_OCCURRENCE_ID) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            "fire-$occurrenceId",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ReminderFireWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(ReminderFireWorker.KEY_OCCURRENCE_ID to occurrenceId))
                .build(),
        )
    }
}
