package com.jjrapps.constanza.reminding

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jjrapps.constanza.tracking.EntryWriter
import com.jjrapps.constanza.tracking.NotificationEntryStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject

/**
 * design.md §9.1/§8.2 (reminder-response: Notification Actions, Origin-Date Crediting;
 * habit-entry-tracking: Provisional-Missed Correction). Thin adapter over [EntryWriter] (task
 * 6b.2): the actual write, the origin-date crediting, and the idempotent-upsert/cancel sequence
 * all now live in [EntryWriter.answerOccurrence], shared with the in-app Today screen route.
 */
class AnswerResponder @Inject constructor(private val entryWriter: EntryWriter) {
    /** [status] is [AnswerWorker.STATUS_COMPLETED] ("Yes") or [AnswerWorker.STATUS_MISSED] ("No"). */
    suspend fun answer(occurrenceId: Long, status: String) {
        val notificationStatus = when (status) {
            AnswerWorker.STATUS_COMPLETED -> NotificationEntryStatus.COMPLETED
            AnswerWorker.STATUS_MISSED -> NotificationEntryStatus.MISSED
            else -> return
        }
        entryWriter.answerOccurrence(occurrenceId, notificationStatus)
    }
}

/** Task 5.4: thin `WorkManager` adapter [ActionReceiver] enqueues for "Yes"/"No"; logic in [AnswerResponder]. */
@HiltWorker
class AnswerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val responder: AnswerResponder,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val occurrenceId = inputData.getLong(KEY_OCCURRENCE_ID, NO_OCCURRENCE_ID)
        val status = inputData.getString(KEY_STATUS)
        if (occurrenceId == NO_OCCURRENCE_ID || status == null) return Result.failure()
        responder.answer(occurrenceId, status)
        return Result.success()
    }

    companion object {
        const val KEY_OCCURRENCE_ID = "occurrenceId"
        const val KEY_STATUS = "status"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_MISSED = "MISSED"
        private const val NO_OCCURRENCE_ID = -1L
    }
}
