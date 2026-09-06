package com.jjrapps.constanza.reminding

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
class NotificationPoster @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLocaleController: AppLocaleController,
) {

    private val manager = NotificationManagerCompat.from(context)

    /**
     * design.md §8.2: [occurrenceId] is simultaneously the notification id and the request code
     * of every [PendingIntent] below — the same integer [com.jjrapps.constanza.scheduling.AlarmScheduler]
     * already uses for the alarm's own `PendingIntent`. No second id scheme exists anywhere in
     * this pipeline.
     *
     * reminder-notification-tap-opens-today extends that same single scheme to the notification's
     * content [PendingIntent] ([contentIntent]) rather than inventing a parallel offset: it reuses
     * [occurrenceId] as its request code too. That is safe, not merely convenient — `PendingIntent`
     * identity keys on the creating factory ([PendingIntent.getActivity] vs.
     * [PendingIntent.getBroadcast], an internal "operation type" the platform tracks alongside the
     * request code) as well as `Intent.filterEquals` (action, data, component, categories), and
     * [contentIntent] both calls a different factory AND targets a different component
     * ([MainActivity], never [ActionIntentContract.ACTION_RECEIVER_CLASS]) than the three actions
     * below. Four `PendingIntent`s now share one request code and stay four distinct system
     * entries on two independent grounds at once.
     *
     * That is why the KDoc's "no second id scheme" claim still holds after this notification grew
     * a fourth `PendingIntent`.
     *
     * Returns whether the notification actually reached the system: `false` means [canPost] gated
     * it and nothing was posted. Callers that persist delivery MUST branch on this instead of
     * assuming a post happened — [canPost] promises "no post, never a silent lie about delivery",
     * and only the caller can keep the second half of that promise in the database (design.md
     * §13.4 finding 1, task G.3).
     *
     * app-localization (design.md D4): resolves one localized [Context] per post via
     * [AppLocaleController.localizedApplicationContext] and uses it for the channel name, the
     * title, and all three action labels — the whole reason this function is now `suspend`.
     * `Every User-Visible String Renders In The Resolved Language`, including a notification fired
     * by a cold process with no Activity ever created. The body is [habitName] itself, the user's
     * own content, and is deliberately NOT resolved through [localizedContext] or any string
     * resource (reminder-response: the notification body is never localized).
     */
    suspend fun postReminder(occurrenceId: Long, habitName: String, colorArgb: Int): Boolean {
        val localizedContext = appLocaleController.localizedApplicationContext()
        if (!canPost(localizedContext)) return false
        postToSystem(occurrenceId, buildNotification(localizedContext, occurrenceId, habitName, colorArgb))
        return true
    }

    /**
     * Named `postToSystem`, not `notify`, only to stay clearly distinct from `Object.notify()`.
     *
     * The suppression is scoped to this one-line function rather than to [postReminder] so that it
     * covers exactly the guarded call and nothing else: `@SuppressLint` cannot target a statement,
     * and a wider scope would silence a future unguarded call added to the same body.
     *
     * `POST_NOTIFICATIONS` is declared in the manifest, and the real guard is [canPost], which
     * [postReminder] evaluates immediately before this call: its `areNotificationsEnabled()` check
     * returns `false` from API 33 on whenever the permission is denied — denial blocks every
     * channel, and `areNotificationsEnabled()` is the platform's own documented pre-send check.
     * Lint cannot follow that guard one call deep into a helper, so it reports a false positive.
     *
     * The rejected alternative was an inline permission check lint could see. That would duplicate
     * the SDK-branched logic that [NotificationPermission] already owns as this app's single source
     * of truth — including its API 31-32 branch, where the permission does not exist and must never
     * be checked. Two owners of a subtle version-branched check is a worse correctness risk than
     * the lint warning it would silence, and drift between them would be invisible.
     *
     * Deliberately not `@RequiresPermission`: that would push the requirement onto the
     * [postReminder] callers, which are `Worker`s designed to gate rather than crash and can do
     * nothing useful with it.
     */
    @SuppressLint("MissingPermission")
    private fun postToSystem(occurrenceId: Long, notification: Notification) {
        manager.notify(occurrenceId.toInt(), notification)
    }

    /** design.md §9.1: cancels [occurrenceId]'s posted notification, a no-op when none is showing.
     *  [AnswerWorker]/[SnoozeWorker] call this ONLY after their own write has landed. */
    fun cancel(occurrenceId: Long) {
        manager.cancel(occurrenceId.toInt())
    }

    /** design.md §11's degradation table, restated as one predicate: not-enabled OR muted-channel
     *  both mean "no post", never a crash and never a silent lie about delivery. Public signature
     *  stays non-suspend (design.md D4) — this app's base, non-localized [context] is enough for a
     *  gate check that never renders user-visible text itself. */
    fun canPost(): Boolean = canPost(context)

    private fun canPost(ctx: Context): Boolean {
        ensureChannel(ctx)
        if (!manager.areNotificationsEnabled()) return false
        val importance = manager.getNotificationChannel(REMINDER_CHANNEL_ID)?.importance
            ?: NotificationManager.IMPORTANCE_DEFAULT
        return importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Idempotent: `createNotificationChannel` on an already-existing channel id is a no-op that
     *  never resets a user's own importance choice. Called from [canPost] itself, not once at
     *  startup, so every gate check is self-contained regardless of call order. Re-invoked on
     *  every post with [ctx] — design.md D4's "channel-name re-localization comes free": the
     *  channel name follows a language change on the very next post. */
    private fun ensureChannel(ctx: Context) {
        manager.createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL_ID,
                ctx.getString(R.string.notification_channel_reminders_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    private fun buildNotification(
        ctx: Context,
        occurrenceId: Long,
        habitName: String,
        colorArgb: Int,
    ): Notification =
        NotificationCompat.Builder(ctx, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setColor(colorArgb)
            .setContentTitle(ctx.getString(R.string.notification_reminder_title))
            .setContentText(habitName)
            .setStyle(NotificationCompat.BigTextStyle().bigText(habitName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setContentIntent(contentIntent(ctx, occurrenceId))
            .addAction(action(ctx, occurrenceId, ActionIntentContract.ACTION_YES, R.string.notification_action_yes))
            .addAction(action(ctx, occurrenceId, ActionIntentContract.ACTION_NO, R.string.notification_action_no))
            .addAction(
                action(ctx, occurrenceId, ActionIntentContract.ACTION_SNOOZE, R.string.notification_action_snooze),
            )
            .build()

    /**
     * reminder-notification-tap-opens-today: tapping the notification body must open the app on
     * Today WITHOUT dismissing the notification ([setAutoCancel] above already stays `false` for
     * that) and without stacking a second [MainActivity] instance on top of a running one.
     *
     * `FLAG_ACTIVITY_SINGLE_TOP`, not a manifest `android:launchMode`, is set directly on this one
     * `Intent` (see [MainActivity]'s class KDoc for why that scoping choice was made): a warm tap
     * reuses the running instance and calls its `onNewIntent` instead of creating a new one, while
     * a cold tap (no task exists yet) launches normally — `singleTop` only changes behaviour when
     * an instance is already at the top, which this single-Activity app's task always is once
     * running. [MainActivity.EXTRA_FROM_REMINDER_NOTIFICATION] is how `onNewIntent` tells this tap
     * apart from any other way a new `Intent` could reach that Activity.
     */
    private fun contentIntent(ctx: Context, occurrenceId: Long): PendingIntent =
        PendingIntent.getActivity(
            ctx,
            occurrenceId.toInt(),
            reminderTapIntent(ctx),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun action(
        ctx: Context,
        occurrenceId: Long,
        actionString: String,
        labelRes: Int,
    ): NotificationCompat.Action {
        // Not chained: same reason as AlarmScheduler.pendingIntentFor — Intent's fluent setters
        // return a Java platform type, and chaining inserts a Kotlin non-null assertion that
        // breaks under the AGP mockable-jar unit-test path (`isReturnDefaultValues` stubs a null
        // return, and the assertion then throws before the mock is even reached).
        val intent = Intent(actionString)
        intent.setClassName(ctx.packageName, ActionIntentContract.ACTION_RECEIVER_CLASS)
        intent.putExtra(ActionIntentContract.EXTRA_OCCURRENCE_ID, occurrenceId)
        val pendingIntent = PendingIntent.getBroadcast(
            ctx,
            occurrenceId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action(NO_ACTION_ICON, ctx.getString(labelRes), pendingIntent)
    }
}

/**
 * reminder-notification-tap-opens-today: the `Intent` [NotificationPoster.contentIntent] wraps in
 * a `PendingIntent`, pulled out to its own top-level `internal` function purely so
 * [NotificationPosterTest] can assert on the target component, `FLAG_ACTIVITY_SINGLE_TOP`, and
 * [MainActivity.EXTRA_FROM_REMINDER_NOTIFICATION] directly — without going through
 * `PendingIntent.getActivity` (a real system call the mockable `android.jar` cannot answer
 * meaningfully) or a real `Notification` build (deliberately kept out of this unit-test class, see
 * [NotificationPosterTest]'s own class KDoc).
 */
internal fun reminderTapIntent(ctx: Context): Intent {
    val intent = Intent(ctx, MainActivity::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
    intent.putExtra(MainActivity.EXTRA_FROM_REMINDER_NOTIFICATION, true)
    return intent
}
