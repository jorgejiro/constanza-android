package com.jjrapps.constanza.scheduling

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * design.md §9.3: shared `goAsync()` + coroutine boilerplate for every reschedule-trigger receiver
 * below. All five triggers (task 4a.4's three plus [ExactAlarmPermissionReceiver]) converge on the
 * same idempotent entry point, [OccurrencePlanner.replanAll] — `goAsync()` keeps the broadcast
 * alive past `onReceive()`'s ~10s budget while the suspend planner runs.
 */
private fun BroadcastReceiver.replanAsync(planner: OccurrencePlanner) {
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            planner.replanAll()
        } finally {
            pendingResult.finish()
        }
    }
}

/** `BOOT_COMPLETED`, never `LOCKED_BOOT_COMPLETED`: Room lives in credential-encrypted storage and
 *  is unreadable before first unlock (design.md §9.3). AlarmManager alarms do not survive a reboot
 *  at all, so this receiver is the only thing keeping reminders correct after one. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var occurrencePlanner: OccurrencePlanner

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        replanAsync(occurrencePlanner)
    }
}

/** Alarms do not survive this app's own update either (design.md §9.3). */
@AndroidEntryPoint
class PackageReplacedReceiver : BroadcastReceiver() {
    @Inject lateinit var occurrencePlanner: OccurrencePlanner

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        replanAsync(occurrencePlanner)
    }
}

/** Wall-clock `RTC_WAKEUP` targets must be recomputed after a timezone, date, or time change —
 *  including the DST transition itself (design.md §9.3, task 4a.7). Also a redundant
 *  midnight-sweep trigger for work unit 4b. */
@AndroidEntryPoint
class TimeChangeReceiver : BroadcastReceiver() {
    @Inject lateinit var occurrencePlanner: OccurrencePlanner

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ALLOWED_ACTIONS) return
        replanAsync(occurrencePlanner)
    }

    companion object {
        val ALLOWED_ACTIONS: Set<String> = setOf(
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
        )
    }
}

/**
 * Not one of the five mandatory triggers, but mandatory anyway (design.md §9.3, reminder-delivery:
 * Exact-Alarm Permission States): upgrades armed inexact alarms to exact on grant, downgrades on
 * revoke, without waiting for the next app launch. [AlarmScheduler.schedule] re-checks
 * `canScheduleExactAlarms()` on every call, so simply re-running [OccurrencePlanner.replanAll]
 * upgrades or downgrades every armed occurrence for free — no separate upgrade/downgrade branch is
 * needed here, and none is written.
 */
@AndroidEntryPoint
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    @Inject lateinit var occurrencePlanner: OccurrencePlanner

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        replanAsync(occurrencePlanner)
    }
}
