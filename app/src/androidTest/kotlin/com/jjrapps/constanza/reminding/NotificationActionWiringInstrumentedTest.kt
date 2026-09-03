package com.jjrapps.constanza.reminding

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.di.ReminderSettingsDataStoreEntryPoint
import com.jjrapps.constanza.localization.AppLocaleController
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val YES_OCCURRENCE_ID = 9101L
private const val SNOOZE_OCCURRENCE_ID = 9102L
private const val HABIT_COLOR_ARGB = -14575885
private const val SEND_TIMEOUT_SECONDS = 5L
private const val POLL_TIMEOUT_MS = 5_000L
private const val POLL_INTERVAL_MS = 50L

/**
 * design.md §9.1/§12/§8.2 (reminder-response: Notification Actions). Proves the REAL
 * manifest-routed path end to end: posts a notification through [NotificationPoster], pulls one
 * action's actual [android.app.PendingIntent] off the posted [Notification], sends it exactly as
 * the system would on a tap, and asserts `ActionReceiver` (declared in `AndroidManifest.xml`,
 * `android:exported="false"`) enqueued the matching uniquely-named `WorkManager` request.
 * `AnswerWorkerTest`/`SnoozeWorkerTest` build their `Worker` directly via
 * `TestListenableWorkerBuilder` and never touch the manifest or the broadcast dispatch — they
 * cannot catch a missing `<receiver>` declaration. This class is the coverage that closes that gap.
 */
@RunWith(AndroidJUnit4::class)
class NotificationActionWiringInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settingsStore = ReminderSettingsStore(
        EntryPointAccessors.fromApplication(context, ReminderSettingsDataStoreEntryPoint::class.java)
            .reminderSettingsDataStore(),
    )
    private val poster = NotificationPoster(context, AppLocaleController(context, settingsStore))
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val workManager = WorkManager.getInstance(context)

    /**
     * Same rationale as [NotificationPosterInstrumentedTest]: a clean install starts with
     * `POST_NOTIFICATIONS` ungranted on API 33+, and this must not depend on ambient state.
     *
     * The grant is then **awaited**, because `grantRuntimePermission` returns before
     * `NotificationManager` reflects the new state. Without the wait these two tests fail on a
     * freshly installed APK — measured on a physical Pixel 10 under
     * `connectedDebugAndroidTest`, which reinstalls both APKs and so resets the permission, while
     * the same suite passed against an already-installed APK where the grant had settled. The
     * failure surfaced as `NoSuchElementException` from the action lookup, because
     * [NotificationPoster] correctly refuses to post while notifications read as disabled — the
     * product was right and the test was racing it.
     */
    @Before
    fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        while (!notificationManager.areNotificationsEnabled() &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(POLL_INTERVAL_MS)
        }
        assertTrue(
            "POST_NOTIFICATIONS was granted but NotificationManager still reports notifications " +
                "disabled after ${POLL_TIMEOUT_MS}ms; the post would be suppressed and the action " +
                "lookup would fail for a reason unrelated to the wiring under test",
            notificationManager.areNotificationsEnabled(),
        )
    }

    @Test
    fun tappingYesReachesActionReceiverAndEnqueuesAnswerWork() {
        val action = postAndFindAction(YES_OCCURRENCE_ID, R.string.notification_action_yes)

        send(action)

        assertEquals(1, awaitWorkInfos("answer-$YES_OCCURRENCE_ID").size)
    }

    @Test
    fun tappingSnoozeReachesActionReceiverAndEnqueuesSnoozeWork() {
        val action = postAndFindAction(SNOOZE_OCCURRENCE_ID, R.string.notification_action_snooze)

        send(action)

        assertEquals(1, awaitWorkInfos("snooze-$SNOOZE_OCCURRENCE_ID").size)
    }

    private fun postAndFindAction(occurrenceId: Long, labelRes: Int): Notification.Action {
        runBlocking { poster.postReminder(occurrenceId, "Meditate", "Did you meditate today?", HABIT_COLOR_ARGB) }
        val posted = awaitPosted(occurrenceId)
        val label = context.getString(labelRes)
        return posted.notification.actions.first { it.title == label }
    }

    /** `NotificationManager.notify` is a `oneway` Binder call, so a posted notification need not be
     *  visible in `activeNotifications` by the time `notify` returns. Same race, same fix as
     *  [NotificationPosterInstrumentedTest.awaitPosted]. */
    private fun awaitPosted(occurrenceId: Long): StatusBarNotification {
        val id = occurrenceId.toInt()
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        var found = notificationManager.activeNotifications.firstOrNull { it.id == id }
        while (found == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS)
            found = notificationManager.activeNotifications.firstOrNull { it.id == id }
        }
        return requireNotNull(found) {
            "No notification with id $id appeared within ${POLL_TIMEOUT_MS}ms of postReminder, " +
                "while areNotificationsEnabled() was true"
        }
    }

    /** Sends the action's real [android.app.PendingIntent] exactly as the system does on a tap,
     *  and blocks on [android.app.PendingIntent.OnFinished] so the assertion below only starts
     *  once `ActionReceiver.onReceive()` has run and returned — no arbitrary sleep. */
    private fun send(action: Notification.Action) {
        val latch = CountDownLatch(1)
        action.actionIntent.send(context, 0, null, { _, _, _, _, _ -> latch.countDown() }, null)
        assertTrue(
            "PendingIntent.OnFinished never fired — the broadcast was not delivered",
            latch.await(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
    }

    /** `WorkManager` makes a newly enqueued request visible to query APIs asynchronously even
     *  after `onReceive()` has returned, so this polls with a bound instead of asserting once. */
    private fun awaitWorkInfos(uniqueWorkName: String): List<WorkInfo> {
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        var infos = workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
        while (infos.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS)
            infos = workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
        }
        return infos
    }
}
