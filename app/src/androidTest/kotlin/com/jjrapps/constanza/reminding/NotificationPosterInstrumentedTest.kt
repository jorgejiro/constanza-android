package com.jjrapps.constanza.reminding

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
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
    }

    @Test
    fun postsAVisibleNotificationWithAllThreeActionsWhenEnabled() {
        assertTrue(
            "POST_NOTIFICATIONS was granted in @Before, so notifications must be enabled here",
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )

        poster.postReminder(OCCURRENCE_ID, "Meditate", "Did you meditate today?", HABIT_COLOR_ARGB)

        val posted = manager.activeNotifications.first { it.id == OCCURRENCE_ID.toInt() }
        assertEquals(EXPECTED_ACTION_COUNT, posted.notification.actions?.size)
    }
}
