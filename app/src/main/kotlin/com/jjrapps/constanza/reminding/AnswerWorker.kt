package com.jjrapps.constanza.reminding

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.dao.ReminderOccurrenceDao
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.scheduling.AlarmScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject

private const val ENTRY_SOURCE_NOTIFICATION = "NOTIFICATION"
private const val STATE_RESOLVED = "RESOLVED"

/**
 * design.md §9.1/§8.2 (reminder-response: Notification Actions, Origin-Date Crediting;
 * habit-entry-tracking: Provisional-Missed Correction). `date = occ.scheduledDate`, never
 * `today()`, is the one line implementing Origin-Date Crediting. The `UNIQUE(habitId, date,
 * slotId)` replace plus no-op re-cancels make a redelivered broadcast or retried worker converge
 * on one row. The notification is cancelled ONLY after the transaction commits.
 */
class AnswerResponder @Inject constructor(
    private val database: AppDatabase,
    private val entryDao: EntryDao,
    private val reminderOccurrenceDao: ReminderOccurrenceDao,
    private val alarmScheduler: AlarmScheduler,
    private val notificationPoster: NotificationPoster,
    private val timeProvider: TimeProvider,
) {
    /** [status] is [AnswerWorker.STATUS_COMPLETED] ("Yes") or [AnswerWorker.STATUS_MISSED] ("No"). */
    suspend fun answer(occurrenceId: Long, status: String) {
        val occ = reminderOccurrenceDao.findById(occurrenceId) ?: return
        database.withTransaction {
            entryDao.upsert(
                EntryEntity(
                    habitId = occ.habitId,
                    date = occ.scheduledDate,
                    slotId = occ.slotId,
                    status = status,
                    value = null,
                    answeredAt = timeProvider.now().toString(),
                    source = ENTRY_SOURCE_NOTIFICATION,
                ),
            )
            reminderOccurrenceDao.upsert(occ.copy(state = STATE_RESOLVED))
        }
        alarmScheduler.cancel(occurrenceId)
        notificationPoster.cancel(occurrenceId)
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
