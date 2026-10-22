package com.jjrapps.constanza.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.core.time.epochMilliAtLocalTime
import com.jjrapps.constanza.core.time.nextLocalDateForWeeklyTime
import com.jjrapps.constanza.recap.WEEKLY_RECAP_DAY
import com.jjrapps.constanza.recap.WEEKLY_RECAP_MINUTE_OF_DAY
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** weekly-recap: a third fixed negative request code, beside the day review's `-1`, for the reason
 *  [DayReviewAlarmScheduler] gives — occurrence ids are always positive, so it never collides. */
private const val WEEKLY_RECAP_ALARM_REQUEST_CODE = -2

/**
 * weekly-recap: arms the Monday 09:00 recap as an exact (or degraded inexact) `AlarmManager` alarm,
 * the same Doze-surviving path [DayReviewAlarmScheduler] uses, through the shared
 * [scheduleExactOrInexact]. The Monday it targets rides along as
 * [WeeklyRecapFireReceiver.EXTRA_SCHEDULED_DATE] so [WeeklyRecapFireWorker] can tell which week to
 * summarise. Re-armed by the worker after every fire, by every [RescheduleReceivers] trigger and at
 * app start, because `AlarmManager` alarms survive none of reboot, package replace or a clock change.
 */
class WeeklyRecapAlarmScheduler @Inject constructor(
    private val alarmManager: AlarmManager,
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
) {
    fun scheduleNext() {
        val targetDate = timeProvider.nextLocalDateForWeeklyTime(WEEKLY_RECAP_DAY, WEEKLY_RECAP_MINUTE_OF_DAY)
        val intent = Intent(context, WeeklyRecapFireReceiver::class.java)
        intent.putExtra(WeeklyRecapFireReceiver.EXTRA_SCHEDULED_DATE, targetDate.toString())
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            WEEKLY_RECAP_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarmManager.scheduleExactOrInexact(
            pendingIntent,
            timeProvider.epochMilliAtLocalTime(targetDate, WEEKLY_RECAP_MINUTE_OF_DAY),
        )
    }
}
