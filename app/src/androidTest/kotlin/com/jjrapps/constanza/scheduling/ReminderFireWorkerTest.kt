package com.jjrapps.constanza.scheduling

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
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.reminding.NotificationPoster
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** design.md §9.1's second half (task 5.9/5.7): the fire-time post path and the
 *  `N_TIMES_PER_WEEK` quota-met suppression. */
@RunWith(AndroidJUnit4::class)
class ReminderFireWorkerTest {

    private lateinit var database: AppDatabase
    private lateinit var notificationPoster: NotificationPoster

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        notificationPoster = mockk(relaxed = true)
    }

    @After
    fun tearDown() = database.close()

    private fun buildWorker(occId: Long, now: Instant): ReminderFireWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val daos = SchedulingDaos(
            database.habitDao(), database.scheduleDao(), database.reminderSlotDao(), database.reminderOccurrenceDao(),
        )
        val handler = ReminderFireHandler(daos, database.entryDao(), notificationPoster, FakeTimeProvider(now))
        val inputData = Data.Builder().putLong(ReminderFireWorker.KEY_OCCURRENCE_ID, occId).build()
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                ReminderFireWorker(appContext, workerParameters, handler)
        }
        return TestListenableWorkerBuilder<ReminderFireWorker>(context)
            .setInputData(inputData)
            .setWorkerFactory(factory)
            .build()
    }

    private suspend fun insertHabit(kind: String, timesPerWeek: Int? = null): Long =
        database.insertHabitWithSchedule(kind = kind, timesPerWeek = timesPerWeek, name = "Exercise", question = "Did you exercise?")

    private suspend fun insertArmedOccurrence(habitId: Long, scheduledDate: String, scheduledAt: Instant): Long =
        database.reminderOccurrenceDao().upsert(
            ReminderOccurrenceEntity(
                habitId = habitId, scheduledDate = scheduledDate, scheduledAtEpochMs = scheduledAt.toEpochMilli(),
                state = "ARMED", snoozeUntilEpochMs = null, snoozeCount = 0, notifiedAtEpochMs = null,
                resolveDeadlineMs = scheduledAt.toEpochMilli() + 24 * 3600 * 1000L,
            ),
        )

    @Test
    fun postsTheNotificationAndRecordsFiredState() = runBlocking {
        coEvery { notificationPoster.postReminder(any(), any(), any(), any()) } returns true
        val habitId = insertHabit(kind = "DAILY")
        val now = Instant.parse("2026-09-01T08:00:00Z")
        val occId = insertArmedOccurrence(habitId, "2026-09-01", now)
        buildWorker(occId, now).doWork()

        coVerify { notificationPoster.postReminder(occId, "Exercise", "Did you exercise?", 0) }
        val stored = database.reminderOccurrenceDao().findById(occId)
        assertEquals("FIRED", stored?.state)
        assertEquals(now.toEpochMilli(), stored?.notifiedAtEpochMs)
    }

    /**
     * design.md §13.4 finding 1 (task G.3). The gate said "no post", so the row must not claim a
     * delivery: `notifiedAtEpochMs` stays null. The state is still FIRED and deliberately NOT
     * SUPPRESSED — that one is D8's terminal quota exit which `findUnresolved()` excludes, whereas
     * a permission- or mute-suppressed occurrence has to stay unresolved so the reconcile net and
     * the Today screen still handle it, and so it never becomes a false `MISSED` (design.md §5.5,
     * §11). Before this branch existed the same run stored `notifiedAt = now`.
     */
    @Test
    fun aGatedPostRecordsNoNotifiedAtAndStillLandsOnFired() = runBlocking {
        coEvery { notificationPoster.postReminder(any(), any(), any(), any()) } returns false
        val habitId = insertHabit(kind = "DAILY")
        val now = Instant.parse("2026-09-01T08:00:00Z")
        val occId = insertArmedOccurrence(habitId, "2026-09-01", now)
        buildWorker(occId, now).doWork()

        val stored = database.reminderOccurrenceDao().findById(occId)
        assertEquals("FIRED", stored?.state)
        assertNull("a suppressed post must not record a notification", stored?.notifiedAtEpochMs)
        assertTrue(database.entryDao().findByHabitId(habitId).isEmpty())
    }

    /** design.md D7/D8/OA-3: the ONE place an already-met weekly quota is caught before nagging. */
    @Test
    fun nTimesPerWeekQuotaMetSuppressesTheNotification() = runBlocking {
        val habitId = insertHabit(kind = "N_TIMES_PER_WEEK", timesPerWeek = 2)
        database.entryDao().insert(completedEntry(habitId, "2026-08-31"))
        database.entryDao().insert(completedEntry(habitId, "2026-09-01"))
        val now = Instant.parse("2026-09-02T08:00:00Z")
        val occId = insertArmedOccurrence(habitId, "2026-09-02", now)
        buildWorker(occId, now).doWork()

        coVerify(exactly = 0) { notificationPoster.postReminder(any(), any(), any(), any()) }
        assertEquals("SUPPRESSED", database.reminderOccurrenceDao().findById(occId)?.state)
    }

    private fun completedEntry(habitId: Long, date: String) = EntryEntity(
        habitId = habitId, date = date, slotId = 0, status = "COMPLETED", value = null,
        answeredAt = "${date}T08:00:00Z", source = "NOTIFICATION",
    )
}
