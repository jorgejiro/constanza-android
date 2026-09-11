package com.jjrapps.constanza.scheduling

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DATE_CHANGED_SWEEP_WORK_NAME = "midnight-sweep-on-date-changed"

/** [replanStep]'s logcat tag — the file's receivers, not any one of them, since all four share it. */
private const val TAG = "RescheduleReceivers"

/**
 * design.md §9.3: shared `goAsync()` + coroutine boilerplate for every reschedule-trigger receiver
 * below. All five triggers (task 4a.4's three plus [ExactAlarmPermissionReceiver]) converge on the
 * same two idempotent entry points, [OccurrencePlanner.replanAll] and
 * [DayReviewAlarmScheduler.scheduleNext] (day-review-exact-alarm) — `goAsync()` keeps the broadcast
 * alive past `onReceive()`'s ~10s budget while both suspend calls run.
 *
 * [dayReviewAlarmScheduler] joined this function, rather than each receiver pasting a second call
 * into its own body, for the reason the same `AlarmManager` non-survival applies to BOTH alarms this
 * app arms: `AlarmManager` alarms do not survive a reboot or a package replace, and a wall-clock
 * `RTC_WAKEUP` target must be recomputed after a timezone/date/time change exactly as
 * [OccurrencePlanner.replanAll]'s own per-occurrence alarms do — the day review is not a special
 * case among these four triggers, it is the same case with a different [AlarmManager] target.
 *
 * The two run as INDEPENDENT steps ([replanStep]), never as two statements sharing one `try`. They
 * arm different alarms for different features and have no data dependency on each other, so one
 * failing must not decide the other's fate: sequenced in a single `try`, a throw from
 * [OccurrencePlanner.replanAll] would skip [DayReviewAlarmScheduler.scheduleNext] entirely and leave
 * the review with no armed alarm until the next cold start re-armed it. That matters most on
 * exactly the trigger that matters most — `BOOT_COMPLETED` reaches Room moments after first unlock,
 * which is the least calm moment this app ever reads it.
 */
private fun BroadcastReceiver.replanAsync(
    planner: OccurrencePlanner,
    dayReviewAlarmScheduler: DayReviewAlarmScheduler,
) {
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            replanStep("reminder occurrences") { planner.replanAll() }
            replanStep("day review alarm") { dayReviewAlarmScheduler.scheduleNext() }
        } finally {
            pendingResult.finish()
        }
    }
}

/**
 * One isolated [replanAsync] step: runs [step], and degrades to a logged warning rather than letting
 * a failure escape. Escaping is not a neutral outcome here — an exception leaving this coroutine
 * reaches the default uncaught handler and takes the receiver's process down, on a broadcast the
 * owner never sees, so it would cost the OTHER step as well as its own.
 *
 * `catch (expectedFailure: Exception)` follows this codebase's existing precedent verbatim
 * ([com.jjrapps.constanza.core.data.migration.PreMigrationSnapshotWriter]'s own catch, which
 * documents it): detekt's `TooGenericExceptionCaught` is satisfied through its configured
 * `allowedExceptionNameRegex` escape, never a `@Suppress`. `Exception` and not `Throwable`, for that
 * same precedent's reason — everything anticipated here (a `SQLiteException` from a database not yet
 * readable, a stray `RuntimeException` from the alarm APIs) is recoverable on the next trigger, and
 * the next trigger always comes: [replanAsync]'s own KDoc lists four, plus the cold-start arm in
 * `ConstanzaApplication.onCreate`.
 *
 * It also catches `CancellationException`, which is an [Exception]. That is inert rather than
 * overlooked: [replanAsync] launches into a bare `CoroutineScope` whose [kotlinx.coroutines.Job] no
 * caller keeps a reference to, so nothing in this app ever cancels it.
 */
private suspend fun replanStep(what: String, step: suspend () -> Unit) {
    try {
        step()
    } catch (expectedFailure: Exception) {
        Log.w(TAG, "$what could not be re-armed on this trigger", expectedFailure)
    }
}

/** `BOOT_COMPLETED`, never `LOCKED_BOOT_COMPLETED`: Room lives in credential-encrypted storage and
 *  is unreadable before first unlock (design.md §9.3). AlarmManager alarms do not survive a reboot
 *  at all, so this receiver is the only thing keeping reminders — and, since day-review-exact-alarm,
 *  the day review — correct after one. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var occurrencePlanner: OccurrencePlanner

    @Inject lateinit var dayReviewAlarmScheduler: DayReviewAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        replanAsync(occurrencePlanner, dayReviewAlarmScheduler)
    }
}

/** Alarms do not survive this app's own update either (design.md §9.3) — day-review-exact-alarm's
 *  alarm included. */
@AndroidEntryPoint
class PackageReplacedReceiver : BroadcastReceiver() {
    @Inject lateinit var occurrencePlanner: OccurrencePlanner

    @Inject lateinit var dayReviewAlarmScheduler: DayReviewAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        replanAsync(occurrencePlanner, dayReviewAlarmScheduler)
    }
}

/** Wall-clock `RTC_WAKEUP` targets must be recomputed after a timezone, date, or time change —
 *  including the DST transition itself (design.md §9.3, task 4a.7) — and that now includes the
 *  day-review alarm (day-review-exact-alarm), not only the per-occurrence ones
 *  [OccurrencePlanner.replanAll] re-arms. `ACTION_DATE_CHANGED` is also one of design.md §9.2's three
 *  redundant midnight-sweep triggers for work unit 4b: it enqueues an immediate one-shot
 *  [MidnightSweepWorker] run under a work name of its own rather than waiting for the sweep's own
 *  midnight-anchored run. */
@AndroidEntryPoint
class TimeChangeReceiver : BroadcastReceiver() {
    @Inject lateinit var occurrencePlanner: OccurrencePlanner

    @Inject lateinit var dayReviewAlarmScheduler: DayReviewAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ALLOWED_ACTIONS) return
        replanAsync(occurrencePlanner, dayReviewAlarmScheduler)
        if (intent.action == Intent.ACTION_DATE_CHANGED) enqueueMidnightSweep(context)
    }

    private fun enqueueMidnightSweep(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            DATE_CHANGED_SWEEP_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<MidnightSweepWorker>().build(),
        )
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
 * Exact-Alarm Permission States): upgrades armed inexact alarms to exact **on grant**, without
 * waiting for the next app launch. [AlarmScheduler.schedule]/[DayReviewAlarmScheduler.scheduleNext]
 * both re-check `canScheduleExactAlarms()` on every call (through the shared
 * [scheduleExactOrInexact]), so simply re-running [OccurrencePlanner.replanAll] and
 * [DayReviewAlarmScheduler.scheduleNext] upgrades every armed alarm — the day review included, since
 * day-review-exact-alarm — for free; no separate branch is needed here, and none is written.
 *
 * **This receiver never sees a revoke.** The platform sends
 * `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` on grant only, and on revoke it instead
 * cancels every one of the app's alarms and stops its process. Measured on the Pixel 10 at API 37
 * and matched to the exact-alarm guide (design.md §13.4 finding 3); an earlier version of this KDoc
 * claimed it downgraded on revoke, which was never possible. The revoke path is
 * `OccurrenceResolver.reconcile()`'s re-arm plus the `onResume()` re-check instead (task G.5) — and,
 * for the day review, the next of this app's own re-arm points listed on [replanAsync]'s KDoc.
 */
@AndroidEntryPoint
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    @Inject lateinit var occurrencePlanner: OccurrencePlanner

    @Inject lateinit var dayReviewAlarmScheduler: DayReviewAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        replanAsync(occurrencePlanner, dayReviewAlarmScheduler)
    }
}
