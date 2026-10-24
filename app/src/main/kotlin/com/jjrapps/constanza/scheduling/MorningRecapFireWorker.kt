package com.jjrapps.constanza.scheduling

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.recap.MorningRecapPublisher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/**
 * day-review-and-morning-recap (S4, S7): the morning recap's fire path. It recaps the day before the
 * one it was armed for, and carries [DayReviewFireWorker]'s late-fire guard: a run that lands on a
 * later date than [KEY_SCHEDULED_DATE] posts nothing, since "yesterday" would no longer be the day
 * the alarm was armed to recap. Either way it re-arms its successor last.
 */
@HiltWorker
class MorningRecapFireWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val publisher: MorningRecapPublisher,
    private val timeProvider: TimeProvider,
    private val alarmScheduler: MorningRecapAlarmScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scheduledDate = inputData.getString(KEY_SCHEDULED_DATE)?.let(LocalDate::parse)
            ?: timeProvider.today()
        if (timeProvider.today() == scheduledDate) publisher.publish(scheduledDate.minusDays(1))
        alarmScheduler.scheduleNext()
        return Result.success()
    }

    internal companion object {
        const val KEY_SCHEDULED_DATE = "scheduled_date"
    }
}
