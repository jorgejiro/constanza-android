package com.jjrapps.constanza.scheduling

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.recap.WeeklyRecapLoader
import com.jjrapps.constanza.recap.WeeklyRecapNotificationPoster
import com.jjrapps.constanza.recap.recapWeekFor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/**
 * weekly-recap: the Monday fire path. [recapWeekFor] decides which week the fire is about (or that
 * it came too late to be about any); a week with nothing to count posts nothing. The next Monday is
 * armed last and unconditionally, so a skipped or failed post never ends the chain — the same order
 * and reasoning as [DayReviewFireWorker].
 */
@HiltWorker
class WeeklyRecapFireWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val loader: WeeklyRecapLoader,
    private val notificationPoster: WeeklyRecapNotificationPoster,
    private val timeProvider: TimeProvider,
    private val alarmScheduler: WeeklyRecapAlarmScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scheduledDate = inputData.getString(KEY_SCHEDULED_DATE)?.let(LocalDate::parse)
        val weekStart = scheduledDate?.let { recapWeekFor(it, timeProvider.today()) }
        if (weekStart != null) {
            val recap = loader.load(weekStart)
            if (recap.total > 0) notificationPoster.postRecap(recap)
        }
        alarmScheduler.scheduleNext()
        return Result.success()
    }

    internal companion object {
        const val KEY_SCHEDULED_DATE = "scheduled_date"
    }
}
