package com.jjrapps.constanza.reminding

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jjrapps.constanza.core.data.dao.ReminderOccurrenceDao
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.scheduling.AlarmScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import javax.inject.Inject

private const val STATE_SNOOZED = "SNOOZED"
private const val SECONDS_PER_MINUTE = 60L

/**
 * design.md D3/§9.2 (reminder-response: Snooze Configuration and Re-arm). `snoozeCount++` is
 * diagnostic ONLY — snoozing is unlimited (decision 11), never capped. The one bound is
 * `resolveDeadlineMs`, fixed at plan time: clamping `snoozeUntil` to it bounds the CALENDAR DATE,
 * never the snooze count. Re-arms on the SAME `reqCode`.
 */
class SnoozeResponder @Inject constructor(
    private val reminderOccurrenceDao: ReminderOccurrenceDao,
    private val alarmScheduler: AlarmScheduler,
    private val notificationPoster: NotificationPoster,
    private val reminderSettingsStore: ReminderSettingsStore,
    private val timeProvider: TimeProvider,
) {
    suspend fun snooze(occurrenceId: Long) {
        val occ = reminderOccurrenceDao.findById(occurrenceId) ?: return
        val duration = reminderSettingsStore.currentSnoozeDuration()
        val requested = timeProvider.now().plusSeconds(duration.minutes * SECONDS_PER_MINUTE)
        val deadline = Instant.ofEpochMilli(occ.resolveDeadlineMs)
        val snoozeUntil = if (requested.isAfter(deadline)) deadline else requested

        val exact = alarmScheduler.schedule(occurrenceId, snoozeUntil.toEpochMilli())
        reminderOccurrenceDao.upsert(
            occ.copy(
                state = STATE_SNOOZED,
                snoozeCount = occ.snoozeCount + 1,
                snoozeUntilEpochMs = snoozeUntil.toEpochMilli(),
                exact = exact,
            ),
        )
        notificationPoster.cancel(occurrenceId)
    }
}

/** Task 5.5: thin `WorkManager` adapter [ActionReceiver] enqueues for "Snooze"; logic in [SnoozeResponder]. */
@HiltWorker
class SnoozeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val responder: SnoozeResponder,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val occurrenceId = inputData.getLong(KEY_OCCURRENCE_ID, NO_OCCURRENCE_ID)
        if (occurrenceId == NO_OCCURRENCE_ID) return Result.failure()
        responder.snooze(occurrenceId)
        return Result.success()
    }

    companion object {
        const val KEY_OCCURRENCE_ID = "occurrenceId"
        private const val NO_OCCURRENCE_ID = -1L
    }
}
