package com.jjrapps.constanza.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.core.time.millisUntilNextLocalTime
import com.jjrapps.constanza.core.time.nextLocalDateForLocalTime
import com.jjrapps.constanza.recap.MorningRecapSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The third negative request code, after the day review's `-1` and the weekly recap's `-2` — see
 *  [DayReviewAlarmScheduler]'s own constant for why negatives never collide with `occurrenceId`. */
private const val MORNING_RECAP_ALARM_REQUEST_CODE = -3

/**
 * day-review-and-morning-recap (S4): arms the next morning recap at
 * [MorningRecapSettingsStore.currentTimeMinuteOfDay], exactly the way [DayReviewAlarmScheduler]
 * arms the day review — same exact/inexact decision ([scheduleExactOrInexact]), same DST-aware
 * [TimeProvider] resolution, and the target local date riding along for [MorningRecapFireWorker]'s
 * late-fire guard. Called from app start, every [RescheduleReceivers] trigger, the settings screen
 * when the time changes, and [MorningRecapFireWorker] re-arming its own successor.
 */
class MorningRecapAlarmScheduler @Inject constructor(
    private val alarmManager: AlarmManager,
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
    private val settingsStore: MorningRecapSettingsStore,
) {
    suspend fun scheduleNext() {
        val minuteOfDay = settingsStore.currentTimeMinuteOfDay()
        val targetDate = timeProvider.nextLocalDateForLocalTime(minuteOfDay)
        val atEpochMilli = timeProvider.now().toEpochMilli() + timeProvider.millisUntilNextLocalTime(minuteOfDay)
        val intent = Intent(context, MorningRecapFireReceiver::class.java)
        intent.putExtra(MorningRecapFireReceiver.EXTRA_SCHEDULED_DATE, targetDate.toString())
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            MORNING_RECAP_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarmManager.scheduleExactOrInexact(pendingIntent, atEpochMilli)
    }
}
