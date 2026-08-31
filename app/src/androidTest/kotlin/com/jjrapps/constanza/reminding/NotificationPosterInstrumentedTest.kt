package com.jjrapps.constanza.reminding

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun postsAVisibleNotificationWithAllThreeActionsWhenEnabled() {
        assertTrue(
            "This device/emulator must have notifications enabled for this test to be meaningful",
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )

        poster.postReminder(OCCURRENCE_ID, "Meditate", "Did you meditate today?", HABIT_COLOR_ARGB)

        val posted = manager.activeNotifications.first { it.id == OCCURRENCE_ID.toInt() }
        assertEquals(EXPECTED_ACTION_COUNT, posted.notification.actions?.size)
    }
}
