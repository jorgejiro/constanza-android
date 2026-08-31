package com.jjrapps.constanza.reminding

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf

private const val NO_OCCURRENCE_ID = -1L

/** design.md §9.1/§8.2 (reminder-response: Notification Actions). `exported = false` (§12).
 *  Validate-only, no Room access — the ~10s `onReceive()` budget is unreliable under OEM
 *  throttling, so this only reads [ActionIntentContract]'s two extras and enqueues expedited
 *  unique work per §8.2's naming (`answer-<occurrenceId>` / `snooze-<occurrenceId>`). */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val occurrenceId = intent.getLongExtra(ActionIntentContract.EXTRA_OCCURRENCE_ID, NO_OCCURRENCE_ID)
        if (occurrenceId == NO_OCCURRENCE_ID) return
        val (workName, request) = when (intent.action) {
            ActionIntentContract.ACTION_YES ->
                "answer-$occurrenceId" to answerRequest(occurrenceId, AnswerWorker.STATUS_COMPLETED)
            ActionIntentContract.ACTION_NO ->
                "answer-$occurrenceId" to answerRequest(occurrenceId, AnswerWorker.STATUS_MISSED)
            ActionIntentContract.ACTION_SNOOZE -> "snooze-$occurrenceId" to snoozeRequest(occurrenceId)
            else -> return
        }
        WorkManager.getInstance(context).enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
    }

    private fun answerRequest(occurrenceId: Long, status: String): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<AnswerWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(AnswerWorker.KEY_OCCURRENCE_ID to occurrenceId, AnswerWorker.KEY_STATUS to status))
            .build()

    private fun snoozeRequest(occurrenceId: Long): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<SnoozeWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(SnoozeWorker.KEY_OCCURRENCE_ID to occurrenceId))
            .build()
}
