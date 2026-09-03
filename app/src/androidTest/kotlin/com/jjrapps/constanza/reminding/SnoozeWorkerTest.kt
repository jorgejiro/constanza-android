package com.jjrapps.constanza.reminding

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.localization.AppLocaleController
import com.jjrapps.constanza.scheduling.AlarmScheduler
import com.jjrapps.constanza.scheduling.FakeTimeProvider
import com.jjrapps.constanza.scheduling.insertHabitWithSchedule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

private const val HOUR_MS = 3600 * 1000L
private const val MINUTE_MS = 60 * 1000L

/** reminder-response: Snooze Configuration and Re-arm (task 5.5/5.7); the "unlimited snoozing"
 *  bound (decision 11). */
@RunWith(AndroidJUnit4::class)
class SnoozeWorkerTest {

    private lateinit var database: AppDatabase
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var settingsStore: ReminderSettingsStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        alarmScheduler = mockk(relaxed = true)
        every { alarmScheduler.schedule(any(), any()) } returns true
        settingsStore = mockk()
    }

    @After
    fun tearDown() = database.close()

    private fun buildWorker(occId: Long, now: Instant): SnoozeWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // localization: never touched by this path — SnoozeResponder only ever calls
        // NotificationPoster.cancel(), which needs no locale, so a relaxed mock is enough.
        val notificationPoster = NotificationPoster(context, mockk<AppLocaleController>(relaxed = true))
        val responder = SnoozeResponder(
            database.reminderOccurrenceDao(), alarmScheduler, notificationPoster, settingsStore, FakeTimeProvider(now),
        )
        val inputData = Data.Builder().putLong(SnoozeWorker.KEY_OCCURRENCE_ID, occId).build()
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                SnoozeWorker(appContext, workerParameters, responder)
        }
        return TestListenableWorkerBuilder<SnoozeWorker>(context)
            .setInputData(inputData)
            .setWorkerFactory(factory)
            .build()
    }

    private suspend fun insertOccurrence(scheduledAt: Instant, resolveDeadline: Instant, snoozeCount: Int = 0): Long {
        val habitId = database.insertHabitWithSchedule()
        return database.reminderOccurrenceDao().upsert(
            ReminderOccurrenceEntity(
                habitId = habitId, scheduledDate = "2026-09-01", scheduledAtEpochMs = scheduledAt.toEpochMilli(),
                state = "FIRED", snoozeUntilEpochMs = null, snoozeCount = snoozeCount, notifiedAtEpochMs = null,
                resolveDeadlineMs = resolveDeadline.toEpochMilli(),
            ),
        )
    }

    @Test
    fun snoozeRearmsWithTheConfiguredDuration() = runBlocking {
        coEvery { settingsStore.currentSnoozeDuration() } returns SnoozeDuration.TWENTY_MINUTES
        val now = Instant.parse("2026-09-01T22:00:00Z")
        val occId = insertOccurrence(now, now.plusMillis(24 * HOUR_MS))
        buildWorker(occId, now).doWork()

        val stored = database.reminderOccurrenceDao().findById(occId)
        assertEquals("SNOOZED", stored?.state)
        assertEquals(now.plusMillis(20 * MINUTE_MS).toEpochMilli(), stored?.snoozeUntilEpochMs)
        assertEquals(1, stored?.snoozeCount)
    }

    @Test
    fun snoozeUntilClampsToTheResolveDeadline() = runBlocking {
        coEvery { settingsStore.currentSnoozeDuration() } returns SnoozeDuration.FOUR_HOURS
        val now = Instant.parse("2026-09-01T22:00:00Z")
        val deadline = now.plusMillis(30 * MINUTE_MS)
        val occId = insertOccurrence(now, deadline)
        buildWorker(occId, now).doWork()

        assertEquals(deadline.toEpochMilli(), database.reminderOccurrenceDao().findById(occId)?.snoozeUntilEpochMs)
    }

    @Test
    fun unlimitedSnoozingIsAllAccepted() = runBlocking {
        coEvery { settingsStore.currentSnoozeDuration() } returns SnoozeDuration.TEN_MINUTES
        var now = Instant.parse("2026-09-01T22:00:00Z")
        val occId = insertOccurrence(now, now.plusMillis(24 * HOUR_MS), snoozeCount = 5)
        repeat(10) {
            buildWorker(occId, now).doWork()
            now = database.reminderOccurrenceDao().findById(occId)!!.snoozeUntilEpochMs!!.let(Instant::ofEpochMilli)
        }

        val stored = database.reminderOccurrenceDao().findById(occId)
        assertEquals(15, stored?.snoozeCount)
        assertTrue("snoozeCount is diagnostic only, never a cap", stored?.state == "SNOOZED")
    }
}
