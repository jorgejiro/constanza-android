package com.jjrapps.constanza.reminding

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val OCCURRENCE_ID = 9001L
private const val EXPECTED_ACTION_COUNT = 3
private const val HABIT_COLOR_ARGB = -14575885
private const val GRANT_TIMEOUT_MS = 5_000L
private const val GRANT_POLL_INTERVAL_MS = 50L

/**
 * reminder-response: Notification Actions (task 5.1). Posts a REAL notification through
 * [NotificationPoster] on the connected device — no mocks anywhere in this class — so
 * `adb shell dumpsys notification --noredact` can inspect exactly what ships. Deliberately does
 * NOT cancel the notification in teardown: it is left in the drawer for the manual on-device
 * check this task's verification requires.
 */
@RunWith(AndroidJUnit4::class)
class NotificationPosterInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val poster = NotificationPoster(context)
    private val manager = context.getSystemService(NotificationManager::class.java)

    /**
     * Grants `POST_NOTIFICATIONS` to the app under test rather than requiring a hand-prepared
     * device. `connectedDebugAndroidTest` uninstalls both APKs when it finishes, so a grant made
     * manually before a run is gone by the next one — this test previously passed only on a
     * machine where someone had just granted it, and failed on any clean install of the very app
     * it tests. A test that depends on invisible ambient state is not a test.
     *
     * Skipped below API 33, where the permission does not exist and notifications are enabled by
     * default; granting it there would throw.
     */
    @Before
    fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        // `grantRuntimePermission` returns before `NotificationManager` reflects the new state, so
        // the grant is awaited rather than assumed. Granting without waiting is what made this
        // class fail intermittently on a freshly installed APK — roughly one run in three on a
        // physical Pixel 10 — while passing whenever the grant had already settled.
        val deadline = System.currentTimeMillis() + GRANT_TIMEOUT_MS
        while (!manager.areNotificationsEnabled() && System.currentTimeMillis() < deadline) {
            Thread.sleep(GRANT_POLL_INTERVAL_MS)
        }
    }

    @Test
    fun postsAVisibleNotificationWithAllThreeActionsWhenEnabled() {
        assertTrue(
            "POST_NOTIFICATIONS was granted in @Before, so notifications must be enabled here",
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )

        // The return value is the fire path's only evidence of delivery (design.md §13.4 finding 1,
        // task G.3), and the real notification build is only exercisable here, not in the
        // mockable-jar unit test — so `true` is asserted on the same call whose visibility is awaited.
        assertTrue(
            "postReminder must report a real post when notifications are enabled",
            poster.postReminder(OCCURRENCE_ID, "Meditate", "Did you meditate today?", HABIT_COLOR_ARGB),
        )

        val posted = awaitPosted(OCCURRENCE_ID)
        assertEquals(EXPECTED_ACTION_COUNT, posted.notification.actions?.size)
    }

    /**
     * `NotificationManager.notify` is a `oneway` Binder call, so a posted notification is not
     * guaranteed to appear in `activeNotifications` by the time `notify` returns. Reading it
     * immediately is what made this class fail intermittently on a physical Pixel 10 —
     * `NoSuchElementException` from the id lookup, with notifications verifiably enabled — so the
     * post's visibility is awaited rather than assumed.
     */
    private fun awaitPosted(occurrenceId: Long): StatusBarNotification {
        val id = occurrenceId.toInt()
        val deadline = System.currentTimeMillis() + GRANT_TIMEOUT_MS
        var found = manager.activeNotifications.firstOrNull { it.id == id }
        while (found == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(GRANT_POLL_INTERVAL_MS)
            found = manager.activeNotifications.firstOrNull { it.id == id }
        }
        return requireNotNull(found) {
            "No notification with id $id appeared within ${GRANT_TIMEOUT_MS}ms of postReminder, " +
                "while areNotificationsEnabled() was true"
        }
    }
}
