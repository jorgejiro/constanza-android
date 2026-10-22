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

private const val WEEKLY_RECAP_CHANNEL_ID = "weekly_recap"

/** A third fixed negative id beside the day review's `-1`; per-habit reminders use positive ids. */
private const val WEEKLY_RECAP_NOTIFICATION_ID = -2

/**
 * weekly-recap S3: posts the Monday recap on its own channel, so the owner can mute it in system
 * settings without muting reminders or the day review — the one-class-per-channel rule
 * [com.jjrapps.constanza.reminding.DayReviewNotificationPoster] follows. `IMPORTANCE_DEFAULT`, for
 * that class's reason: a summary to read at leisure, not a time-critical heads-up.
 *
 * Collapsed it reads "Your week: 64% done / 18 of 28 · tap to see each habit"; expanded it adds the
 * week's range and, when the week has at least two habits that scored differently, the best one
 * and the one that needs work. Tapping opens that week's recap screen.
 */
class WeeklyRecapNotificationPoster @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLocaleController: AppLocaleController,
) {
    private val manager = NotificationManagerCompat.from(context)

    /** `false` when notifications or this channel are off and nothing reached the system. */
    suspend fun postRecap(recap: LoadedWeeklyRecap): Boolean {
        val localizedContext = appLocaleController.localizedApplicationContext()
        if (!canPost(localizedContext)) return false
        postToSystem(buildNotification(localizedContext, recap))
        return true
    }

    @SuppressLint("MissingPermission")
    private fun postToSystem(notification: Notification) {
        manager.notify(WEEKLY_RECAP_NOTIFICATION_ID, notification)
    }

    private fun canPost(ctx: Context): Boolean {
        manager.createNotificationChannel(
            NotificationChannel(
                WEEKLY_RECAP_CHANNEL_ID,
                ctx.getString(R.string.notification_channel_weekly_recap_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        if (!manager.areNotificationsEnabled()) return false
        val importance = manager.getNotificationChannel(WEEKLY_RECAP_CHANNEL_ID)?.importance
            ?: NotificationManager.IMPORTANCE_DEFAULT
        return importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun buildNotification(ctx: Context, recap: LoadedWeeklyRecap): Notification {
        val text = ctx.getString(R.string.notification_weekly_recap_text, recap.done, recap.total)
        return NotificationCompat.Builder(ctx, WEEKLY_RECAP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(ctx.getString(R.string.notification_weekly_recap_title, recap.percent))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText(ctx, recap)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(ctx, recap))
            .build()
    }

    private fun expandedText(ctx: Context, recap: LoadedWeeklyRecap): String {
        val locale = ctx.resources.configuration.locales[0]
        val range = weekRangeLabel(recap.weekStart, locale)
        val lines = mutableListOf(
            ctx.getString(R.string.notification_weekly_recap_range, range, recap.done, recap.total),
        )
        val ranked = recap.rows.sortedByDescending { it.done.toDouble() / it.total }
        val best = ranked.firstOrNull()
        val worst = ranked.lastOrNull()
        if (best != null && worst != null && best.done * worst.total != worst.done * best.total) {
            lines += ctx.getString(R.string.notification_weekly_recap_best, best.name, best.done, best.total)
            lines += ctx.getString(R.string.notification_weekly_recap_worst, worst.name, worst.done, worst.total)
        }
        return lines.joinToString("\n")
    }

    /** Opens [MainActivity] on this week's recap: `SINGLE_TOP` reaches `onNewIntent` when the app is
     *  already open, and a cold start reads the same extra in `onCreate`. */
    private fun contentIntent(ctx: Context, recap: LoadedWeeklyRecap): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        intent.putExtra(MainActivity.EXTRA_WEEKLY_RECAP_WEEK_START, recap.weekStart.toString())
        return PendingIntent.getActivity(
            ctx,
            WEEKLY_RECAP_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
