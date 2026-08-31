package com.jjrapps.constanza.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

private const val NO_OCCURRENCE_ID = -1L

/**
 * The [android.app.PendingIntent] target every [AlarmScheduler] alarm fires into (design.md §9.1).
 * `exported = false`: only this app's own alarms are allowed to deliver here (design.md §12), the
 * same trust posture as `ActionReceiver`.
 *
 * Posting the actual notification is work unit 5's job (`NotificationPoster`, the re-evaluated
 * `dueOn` suppression check, etc. — design.md §9.1's second half). This unit's scope ends at
 * scheduling: [onReceive] only validates that a real occurrence id extra is present, so the
 * `PendingIntent` this class is the target of is never dangling.
 */
class ReminderFireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val occurrenceId = intent.getLongExtra(AlarmScheduler.EXTRA_OCCURRENCE_ID, NO_OCCURRENCE_ID)
        if (occurrenceId == NO_OCCURRENCE_ID) return
        // Work unit 5 wires: load the occurrence, re-evaluate dueOn, post via NotificationPoster.
    }
}
