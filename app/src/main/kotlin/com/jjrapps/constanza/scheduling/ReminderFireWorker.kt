package com.jjrapps.constanza.scheduling

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.mapper.toDomain
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.domain.dueOn
import com.jjrapps.constanza.domain.model.Due
import com.jjrapps.constanza.domain.model.PeriodProgress
import com.jjrapps.constanza.domain.startOfWeek
import com.jjrapps.constanza.reminding.NotificationPoster
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

private const val STATE_ARMED = "ARMED"
private const val STATE_FIRED = "FIRED"
private const val STATE_SUPPRESSED = "SUPPRESSED"
private const val ENTRY_STATUS_COMPLETED = "COMPLETED"

/**
 * design.md §9.1's second half (task 5.9). **The re-evaluation is the point, not the plumbing.**
 * [OccurrencePlanner] arms an alarm every day for `N_TIMES_PER_WEEK` because D7/D8 defer quota
 * suppression to fire time — this is the one place that suppression happens, so unlike the
 * planner's own always-zero progress, [currentWeekProgress] reads REAL entries for the week.
 */
class ReminderFireHandler @Inject constructor(
    private val daos: SchedulingDaos,
    private val entryDao: EntryDao,
    private val notificationPoster: NotificationPoster,
    private val timeProvider: TimeProvider,
) {
    suspend fun fire(occurrenceId: Long) {
        val occ = daos.reminderOccurrenceDao.findById(occurrenceId) ?: return
        if (occ.state != STATE_ARMED) return // already answered/snoozed/resolved
        val habit = daos.habitDao.findById(occ.habitId) ?: return
        val schedule = daos.scheduleDao.findByHabitId(occ.habitId)?.toDomain() ?: return
        val scheduledDate = LocalDate.parse(occ.scheduledDate)
        val progress = currentWeekProgress(occ.habitId, scheduledDate, schedule.weekStart)

        val decision = dueOn(schedule, scheduledDate, progress)
        if (decision is Due.Candidate && decision.quotaRemaining <= 0) {
            daos.reminderOccurrenceDao.upsert(occ.copy(state = STATE_SUPPRESSED))
            return
        }

        val posted = notificationPoster.postReminder(occ.id, habit.name, habit.colorArgb)
        // design.md §13.4 finding 1 (task G.3): `notifiedAtEpochMs` records that the user was
        // actually told, so a gated post leaves it null rather than claiming a delivery that never
        // happened. The state still becomes FIRED and NOT STATE_SUPPRESSED — that one is D8's quota
        // exit above and is terminal, excluded by `findUnresolved()`, whereas a permission- or
        // mute-suppressed occurrence must stay unresolved so the reconcile net and the Today screen
        // still handle it and it never becomes a false `MISSED` (design.md §5.5, §11). The null is
        // what separates "fired but not notified" from "fired and notified"; no new state is needed.
        val notifiedAt = if (posted) timeProvider.now().toEpochMilli() else null
        daos.reminderOccurrenceDao.upsert(occ.copy(state = STATE_FIRED, notifiedAtEpochMs = notifiedAt))
    }

    private suspend fun currentWeekProgress(habitId: Long, date: LocalDate, weekStart: DayOfWeek): PeriodProgress {
        val weekBegin = startOfWeek(date, weekStart)
        val completedInWeek = entryDao.findByHabitIdBetweenDates(habitId, weekBegin.toString(), date.toString())
            .count { it.status == ENTRY_STATUS_COMPLETED }
        return PeriodProgress(completedInWeek = completedInWeek, completedInMonth = 0)
    }
}

/** Task 5.9: thin `WorkManager` adapter [ReminderFireReceiver] enqueues; logic in [ReminderFireHandler]. */
@HiltWorker
class ReminderFireWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val handler: ReminderFireHandler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val occurrenceId = inputData.getLong(KEY_OCCURRENCE_ID, NO_OCCURRENCE_ID)
        if (occurrenceId == NO_OCCURRENCE_ID) return Result.failure()
        handler.fire(occurrenceId)
        return Result.success()
    }

    companion object {
        const val KEY_OCCURRENCE_ID = "occurrenceId"
        private const val NO_OCCURRENCE_ID = -1L
    }
}
