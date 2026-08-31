package com.jjrapps.constanza.reminding

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jjrapps.constanza.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val REMINDER_CHANNEL_ID = "reminders"
private const val NO_ACTION_ICON = 0

/**
 * design.md §9.1/§8.2 (reminder-response: Notification Actions). Every reminder notification is
 * posted, and only ever posted, through this class.
 *
 * [canPost] runs `areNotificationsEnabled()` **and** the channel's own importance check on every
 * call — never cached from a startup check — because a user can mute the channel without ever
 * touching the `POST_NOTIFICATIONS` permission (design.md §5.6 consequence 3, §11), and a
 * silently-suppressed notification must never masquerade as a delivered one that later becomes a
 * false `missed`.
 *
 * Uses standard [NotificationCompat] templates only, never `RemoteViews` (design.md §5.7 C2):
 * Android 17 enforces a hard `1.5 * screenWidth * screenHeight * 4` combined Bitmap/Icon memory
 * limit on a `RemoteViews` parcel and throws a **fatal**, process-crashing
 * `IllegalArgumentException` past it. The per-habit colour is applied as a
 * [NotificationCompat.Builder.setColor] tint over a vector small icon
 * ([R.drawable.ic_notification_reminder]), never as a bitmap.
 */
class NotificationPoster @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /**
     * design.md §8.2: [occurrenceId] is simultaneously the notification id and the request code
     * of every [PendingIntent] below — the same integer [com.jjrapps.constanza.scheduling.AlarmScheduler]
     * already uses for the alarm's own `PendingIntent`. No second id scheme exists anywhere in
     * this pipeline.
     */
    fun postReminder(occurrenceId: Long, habitName: String, question: String?, colorArgb: Int) {
        if (!canPost()) return
        manager.notify(occurrenceId.toInt(), buildNotification(occurrenceId, habitName, question, colorArgb))
    }

    /** design.md §9.1: cancels [occurrenceId]'s posted notification, a no-op when none is showing.
     *  [AnswerWorker]/[SnoozeWorker] call this ONLY after their own write has landed. */
    fun cancel(occurrenceId: Long) {
        manager.cancel(occurrenceId.toInt())
    }

    /** design.md §11's degradation table, restated as one predicate: not-enabled OR muted-channel
     *  both mean "no post", never a crash and never a silent lie about delivery. */
    fun canPost(): Boolean {
        ensureChannel()
        if (!manager.areNotificationsEnabled()) return false
        val importance = manager.getNotificationChannel(REMINDER_CHANNEL_ID)?.importance
            ?: NotificationManager.IMPORTANCE_DEFAULT
        return importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Idempotent: `createNotificationChannel` on an already-existing channel id is a no-op that
     *  never resets a user's own importance choice. Called from [canPost] itself, not once at
     *  startup, so every gate check is self-contained regardless of call order. */
    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL_ID,
                context.getString(R.string.notification_channel_reminders_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    private fun buildNotification(
        occurrenceId: Long,
        habitName: String,
        question: String?,
        colorArgb: Int,
    ): android.app.Notification =
        NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setColor(colorArgb)
            .setContentTitle(habitName)
            .setContentText(question ?: context.getString(R.string.notification_default_question))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .addAction(action(occurrenceId, ActionIntentContract.ACTION_YES, R.string.notification_action_yes))
            .addAction(action(occurrenceId, ActionIntentContract.ACTION_NO, R.string.notification_action_no))
            .addAction(action(occurrenceId, ActionIntentContract.ACTION_SNOOZE, R.string.notification_action_snooze))
            .build()

    private fun action(occurrenceId: Long, actionString: String, labelRes: Int): NotificationCompat.Action {
        // Not chained: same reason as AlarmScheduler.pendingIntentFor — Intent's fluent setters
        // return a Java platform type, and chaining inserts a Kotlin non-null assertion that
        // breaks under the AGP mockable-jar unit-test path (`isReturnDefaultValues` stubs a null
        // return, and the assertion then throws before the mock is even reached).
        val intent = Intent(actionString)
        intent.setClassName(context.packageName, ActionIntentContract.ACTION_RECEIVER_CLASS)
        intent.putExtra(ActionIntentContract.EXTRA_OCCURRENCE_ID, occurrenceId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            occurrenceId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action(NO_ACTION_ICON, context.getString(labelRes), pendingIntent)
    }
}
