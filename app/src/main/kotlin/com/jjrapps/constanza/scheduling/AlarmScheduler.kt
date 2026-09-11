package com.jjrapps.constanza.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** design.md §11: the inexact degrade window is at least 10 minutes. */
private const val INEXACT_WINDOW_MS = 10 * 60 * 1000L

/**
 * design.md §9.1/§5.6/§11: the exact/inexact decision itself, factored out of [AlarmScheduler.schedule]
 * (day-review-exact-alarm) so a second caller with its own id scheme —
 * [DayReviewAlarmScheduler], which has no `reminder_occurrences` row and therefore no `occurrenceId`
 * to key on — arms its alarm through the identical decision instead of a second copy of it. This is
 * now the ONE place "which API survives Doze" is decided; [AlarmScheduler.schedule] and
 * [DayReviewAlarmScheduler.scheduleNext] both call it and neither re-implements the branch.
 *
 * `canScheduleExactAlarms()` is re-checked on EVERY call (reminder-delivery: Exact-Alarm Permission
 * States) — never cached, never checked only once at startup. `minSdk = 31` means
 * `SCHEDULE_EXACT_ALARM` always exists as a concept (design.md §5.6 consequence 2): this function has
 * exactly two modes, exact and inexact-window, and intentionally contains NO `Build.VERSION` branch
 * anywhere. `USE_EXACT_ALARM` is never used — it is Play-policy-restricted to alarm-clock/calendar
 * apps, which this is not.
 */
internal fun AlarmManager.scheduleExactOrInexact(pendingIntent: PendingIntent, atEpochMilli: Long): Boolean =
    if (canScheduleExactAlarms()) {
        setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMilli, pendingIntent)
        true
    } else {
        setWindow(AlarmManager.RTC_WAKEUP, atEpochMilli, INEXACT_WINDOW_MS, pendingIntent)
        false
    }

/**
 * design.md §9.1/§5.6/§11: exact-alarm scheduling with an inexact-window degrade path, for the
 * per-occurrence reminder pipeline. The exact/inexact decision itself now lives in
 * [scheduleExactOrInexact] above (day-review-exact-alarm) — this class's own public surface and
 * behaviour for its existing callers are unchanged by that extraction.
 */
class AlarmScheduler @Inject constructor(
    private val alarmManager: AlarmManager,
    @ApplicationContext private val context: Context,
) {
    /** Arms [occurrenceId]'s alarm for [atEpochMilli]. Returns `true` when armed exactly, `false`
     *  when degraded to the inexact window — the caller persists that flag as `exact` on the row. */
    fun schedule(occurrenceId: Long, atEpochMilli: Long): Boolean =
        alarmManager.scheduleExactOrInexact(pendingIntentFor(occurrenceId), atEpochMilli)

    /** Cancels [occurrenceId]'s alarm, if one is armed. No-op otherwise. */
    fun cancel(occurrenceId: Long) {
        alarmManager.cancel(pendingIntentFor(occurrenceId))
    }

    /** Task 6b.9 (design §12/§13.1): the same eligibility check [schedule] re-runs on every call,
     *  exposed here so the UI can explain a degraded (inexact) delivery mode instead of silently
     *  living with it. */
    fun canScheduleExactAlarms(): Boolean = alarmManager.canScheduleExactAlarms()

    /** design.md §8.2: `occurrence.id` IS the `PendingIntent` request code, with no second id
     *  scheme. `FLAG_IMMUTABLE` on every `PendingIntent` (design.md §12) is mandatory at API 31+
     *  and is never relaxed. */
    private fun pendingIntentFor(occurrenceId: Long): PendingIntent {
        // Not chained: Intent.putExtra's fluent return is a Java platform type, and chaining it
        // makes Kotlin insert a non-null assertion on that return value — which is unnecessary
        // here and actively breaks the AGP mockable-jar unit-test path (`returnDefaultValues`
        // makes stubbed reference-typed returns null, and the assertion then throws).
        val intent = Intent(context, ReminderFireReceiver::class.java)
        intent.putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
        return PendingIntent.getBroadcast(
            context,
            occurrenceId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val EXTRA_OCCURRENCE_ID = "occurrenceId"
    }
}
