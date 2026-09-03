package com.jjrapps.constanza.localization

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.di.ReminderSettingsDataStoreEntryPoint
import com.jjrapps.constanza.e2e.CoreFlowTestFixture
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import com.jjrapps.constanza.scheduling.insertHabitWithSchedule
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

private const val REMINDER_CHANNEL_ID = "reminders" // mirrors NotificationPoster's private constant
private const val ONE_DAY_MS = 24 * 3600 * 1000L
private const val PERMISSION_POLL_TIMEOUT_MS = 5_000L
private const val PERMISSION_POLL_INTERVAL_MS = 50L

/**
 * app-localization: `Every User-Visible String Renders In The Resolved Language` /
 * `reminder-response`: Notification Actions (MODIFIED). The headline test for this whole change
 * (design.md's Testing Strategy table).
 *
 * Reuses [CoreFlowTestFixture]'s real, file-backed [com.jjrapps.constanza.core.data.AppDatabase]
 * and its [CoreFlowTestFixture.fireArmedAlarmFor] — the exact broadcast `AlarmManager` delivers,
 * routed through the manifest-declared `ReminderFireReceiver`, `WorkManager`'s real
 * `HiltWorkerFactory`, and the production `NotificationPoster` (see that method's own KDoc for why
 * `WorkManager.testing` cannot be used here without swapping out the wiring under test). No
 * `Activity` is ever created in this test class.
 *
 * **Honest limit**: this proves no Activity was ever created and the poster resolved Spanish from
 * the persisted/system tag — not literal post-process-death cold start, which stays a manual `adb`
 * check under `testing.instrumented.device_free_matrix.limits`.
 */
@RunWith(AndroidJUnit4::class)
class SpanishColdProcessNotificationInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = CoreFlowTestFixture(context)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val localeController = AppLocaleController(
        context,
        ReminderSettingsStore(
            EntryPointAccessors.fromApplication(context, ReminderSettingsDataStoreEntryPoint::class.java)
                .reminderSettingsDataStore(),
        ),
    )

    /** Same rationale and same fix as `NotificationPosterInstrumentedTest`: a clean install starts
     *  with `POST_NOTIFICATIONS` ungranted on API 33+, and the grant must be awaited because
     *  `grantRuntimePermission` returns before `NotificationManager` reflects the new state. */
    @Before
    fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val deadline = System.currentTimeMillis() + PERMISSION_POLL_TIMEOUT_MS
        while (!notificationManager.areNotificationsEnabled() && System.currentTimeMillis() < deadline) {
            Thread.sleep(PERMISSION_POLL_INTERVAL_MS)
        }
        assertTrue(
            "POST_NOTIFICATIONS was granted but notifications still read disabled after " +
                "${PERMISSION_POLL_TIMEOUT_MS}ms",
            notificationManager.areNotificationsEnabled(),
        )
    }

    @Before
    fun setUp() = runBlocking {
        fixture.reset()
        // Seeds through the exact production surface for this API level (design.md D1): below 33
        // this writes ReminderSettingsStore's persisted tag; on 33+ it sets the real
        // LocaleManager override — there is no third seeding path to keep in sync with production.
        localeController.set(AppLanguage.Spanish)
    }

    @After
    fun tearDown() = runBlocking {
        localeController.set(AppLanguage.SystemDefault)
        fixture.close()
    }

    @Test
    fun aReminderFiredWithNoActivityEverCreatedPostsInSpanish() = runBlocking {
        val habitId = fixture.database.insertHabitWithSchedule(kind = "DAILY", name = "Meditar", question = null)
        val now = Instant.now()
        val occurrenceId = fixture.database.reminderOccurrenceDao().upsert(
            ReminderOccurrenceEntity(
                habitId = habitId,
                scheduledDate = now.toString().take(10),
                scheduledAtEpochMs = now.toEpochMilli(),
                state = "ARMED",
                snoozeUntilEpochMs = null,
                snoozeCount = 0,
                notifiedAtEpochMs = null,
                resolveDeadlineMs = now.toEpochMilli() + ONE_DAY_MS,
            ),
        )
        val occurrence = fixture.occurrencesFor(habitId).first { it.id == occurrenceId }

        fixture.fireArmedAlarmFor(occurrence)

        val posted = fixture.awaitPostedNotification(occurrenceId.toInt())
        assertEquals(
            "¿Lo has hecho?",
            posted.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        val actionLabels = posted.notification.actions.map { it.title.toString() }
        assertEquals(listOf("Sí", "No", "Aplazar"), actionLabels)
        assertEquals(
            "Recordatorios de hábitos",
            notificationManager.getNotificationChannel(REMINDER_CHANNEL_ID)?.name,
        )
    }
}
