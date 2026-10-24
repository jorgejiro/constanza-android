package com.jjrapps.constanza.recap

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.MainActivity
import com.jjrapps.constanza.localization.AppLocaleController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Its own channel, so the owner can mute the morning recap from system settings without muting
 *  reminders, the day review or the weekly recap. */
private const val MORNING_RECAP_CHANNEL_ID = "morning_recap"

/** Notification id and `PendingIntent` request code: the next free negative constant after the day
 *  review's `-1` and the weekly recap's `-2`, out of reach of every positive `occurrenceId`. */
private const val MORNING_RECAP_NOTIFICATION_ID = -3

/**
 * day-review-and-morning-recap (S4, S5): posts the morning recap. The title carries the percent, the
 * body the count, and a tap opens Today on [DaySummary.date] through
 * [MainActivity.EXTRA_DAY_RECAP_DATE] — the date the recap is about, not "yesterday" relative to
 * whenever the tap happens.
 */
class MorningRecapNotificationPoster @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLocaleController: AppLocaleController,
) {
    private val manager = NotificationManagerCompat.from(context)

    suspend fun postRecap(summary: DaySummary): Boolean {
        val localizedContext = appLocaleController.localizedApplicationContext()
        if (!canPost(localizedContext)) return false
        postToSystem(buildNotification(localizedContext, summary))
        return true
    }

    @SuppressLint("MissingPermission")
    private fun postToSystem(notification: Notification) {
        manager.notify(MORNING_RECAP_NOTIFICATION_ID, notification)
    }

    private fun canPost(ctx: Context): Boolean {
        manager.createNotificationChannel(
            NotificationChannel(
                MORNING_RECAP_CHANNEL_ID,
                ctx.getString(R.string.notification_channel_morning_recap_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        if (!manager.areNotificationsEnabled()) return false
        val importance = manager.getNotificationChannel(MORNING_RECAP_CHANNEL_ID)?.importance
            ?: NotificationManager.IMPORTANCE_DEFAULT
        return importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun buildNotification(ctx: Context, summary: DaySummary): Notification {
        val text = ctx.getString(R.string.notification_morning_recap_text, summary.done, summary.total)
        return NotificationCompat.Builder(ctx, MORNING_RECAP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(ctx.getString(R.string.notification_morning_recap_title, summary.percent))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(ctx, summary))
            .build()
    }

    private fun contentIntent(ctx: Context, summary: DaySummary): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        intent.putExtra(MainActivity.EXTRA_DAY_RECAP_DATE, summary.date.toString())
        return PendingIntent.getActivity(
            ctx,
            MORNING_RECAP_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
