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
import com.jjrapps.constanza.scheduling.AlarmScheduler
import com.jjrapps.constanza.scheduling.FakeTimeProvider
import com.jjrapps.constanza.scheduling.insertHabitWithSchedule
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

private const val RESOLVE_DEADLINE_MS = 24 * 3600 * 1000L

/** reminder-response: Notification Actions, Origin-Date Crediting (task 5.4/5.7), via
 *  `TestListenableWorkerBuilder` against a real in-memory Room database. */
@RunWith(AndroidJUnit4::class)
class AnswerWorkerTest {

    private lateinit var database: AppDatabase
    private lateinit var alarmScheduler: AlarmScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        alarmScheduler = mockk(relaxed = true)
    }

    @After
    fun tearDown() = database.close()

    private fun buildWorker(occId: Long, status: String, now: Instant): AnswerWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val responder = AnswerResponder(
            database, database.entryDao(), database.reminderOccurrenceDao(),
            alarmScheduler, NotificationPoster(context), FakeTimeProvider(now),
        )
        val inputData = Data.Builder()
            .putLong(AnswerWorker.KEY_OCCURRENCE_ID, occId)
            .putString(AnswerWorker.KEY_STATUS, status)
            .build()
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                AnswerWorker(appContext, workerParameters, responder)
        }
        return TestListenableWorkerBuilder<AnswerWorker>(context)
            .setInputData(inputData)
            .setWorkerFactory(factory)
            .build()
    }

    private suspend fun insertOccurrence(scheduledDate: String): Pair<Long, Long> {
        val habitId = database.insertHabitWithSchedule()
        val scheduledAt = Instant.parse("${scheduledDate}T20:00:00Z").toEpochMilli()
        val occId = database.reminderOccurrenceDao().upsert(
            ReminderOccurrenceEntity(
                habitId = habitId, scheduledDate = scheduledDate, scheduledAtEpochMs = scheduledAt,
                state = "ARMED", snoozeUntilEpochMs = null, snoozeCount = 0, notifiedAtEpochMs = null,
                resolveDeadlineMs = scheduledAt + RESOLVE_DEADLINE_MS,
            ),
        )
        return habitId to occId
    }

    @Test
    fun idempotentUpsertOnRedelivery() = runBlocking {
        val (habitId, occId) = insertOccurrence("2026-09-01")
        buildWorker(occId, AnswerWorker.STATUS_COMPLETED, Instant.parse("2026-09-01T20:05:00Z")).doWork()
        buildWorker(occId, AnswerWorker.STATUS_COMPLETED, Instant.parse("2026-09-01T20:05:30Z")).doWork()

        val entries = database.entryDao().findByHabitAndDate(habitId, "2026-09-01")
        assertEquals(1, entries.size)
        assertEquals("COMPLETED", entries.single().status)
        assertEquals("RESOLVED", database.reminderOccurrenceDao().findById(occId)?.state)
        verify(exactly = 2) { alarmScheduler.cancel(occId) }
    }

    @Test
    fun answerGivenAfterMidnightCreditsTheOriginDate() = runBlocking {
        val (habitId, occId) = insertOccurrence("2026-09-01")
        buildWorker(occId, AnswerWorker.STATUS_COMPLETED, Instant.parse("2026-09-02T00:40:00Z")).doWork()

        assertEquals("COMPLETED", database.entryDao().findByHabitAndDate(habitId, "2026-09-01").single().status)
        assertTrue(database.entryDao().findByHabitAndDate(habitId, "2026-09-02").isEmpty())
    }
}
