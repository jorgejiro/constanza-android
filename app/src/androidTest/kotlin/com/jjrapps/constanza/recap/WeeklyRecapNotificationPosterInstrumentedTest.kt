package com.jjrapps.constanza.recap

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.di.ReminderSettingsDataStoreEntryPoint
import com.jjrapps.constanza.domain.RecapMark
import com.jjrapps.constanza.localization.AppLocaleController
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val GRANT_TIMEOUT_MS = 5_000L
private const val POLL_INTERVAL_MS = 50L
private const val RECAP_NOTIFICATION_ID = -2

/** weekly-recap S3/S4: the recap posts on its own channel with the % in its title, the best and the
 *  weakest habit when expanded, and a tap target. */
@RunWith(AndroidJUnit4::class)
class WeeklyRecapNotificationPosterInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settingsStore = ReminderSettingsStore(
        EntryPointAccessors.fromApplication(context, ReminderSettingsDataStoreEntryPoint::class.java)
            .reminderSettingsDataStore(),
    )
    private val appLocaleController = AppLocaleController(context, settingsStore)
    private val poster = WeeklyRecapNotificationPoster(context, appLocaleController)
    private val manager = context.getSystemService(NotificationManager::class.java)

    private val recap = LoadedWeeklyRecap(
        weekStart = LocalDate.parse("2026-10-12"),
        rows = listOf(
            row(1L, "Sun", done = 6),
            row(2L, "Lunch", done = 5),
            row(3L, "Dinner", done = 3),
        ),
        done = 14,
        total = 21,
        percent = 67,
    )

    private fun row(id: Long, name: String, done: Int) =
        WeeklyRecapRow(id, name, colorArgb = 0, marks = List(7) { RecapMark.NONE }, done = done, total = 7)

    @Before
    fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val deadline = System.currentTimeMillis() + GRANT_TIMEOUT_MS
        while (!manager.areNotificationsEnabled() && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    @After
    fun cancel() {
        manager.cancel(RECAP_NOTIFICATION_ID)
    }

    @Test
    fun postsTheWeekPercentWithBestAndWeakestHabitAndATapTarget() = runBlocking {
        assertTrue(poster.postRecap(recap))

        val posted = awaitPosted()
        val ctx = appLocaleController.localizedApplicationContext()
        val extras = posted.notification.extras
        assertEquals("weekly_recap", posted.notification.channelId)
        assertEquals(
            ctx.getString(R.string.notification_weekly_recap_title, 67),
            extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
        assertEquals(
            ctx.getString(R.string.notification_weekly_recap_text, 14, 21),
            extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertTrue(big, big.contains(ctx.getString(R.string.notification_weekly_recap_best, "Sun", 6, 7)))
        assertTrue(big, big.contains(ctx.getString(R.string.notification_weekly_recap_worst, "Dinner", 3, 7)))
        assertNotNull(posted.notification.contentIntent)
    }

    @Test
    fun aSingleHabitWeekNamesNoBestOrWeakest() = runBlocking {
        assertTrue(poster.postRecap(recap.copy(rows = recap.rows.take(1), done = 6, total = 7, percent = 86)))

        val big = awaitPosted().notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertEquals(1, big.lines().size)
    }

    private fun awaitPosted(): StatusBarNotification {
        val deadline = System.currentTimeMillis() + GRANT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            manager.activeNotifications.firstOrNull { it.id == RECAP_NOTIFICATION_ID }?.let { return it }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        error("the weekly recap notification never appeared")
    }
}
