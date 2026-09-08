package com.jjrapps.constanza.scheduling

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.habit.HabitDaos
import com.jjrapps.constanza.reminding.DayReviewNotificationPoster
import com.jjrapps.constanza.reminding.DayReviewSettingsStore
import com.jjrapps.constanza.tracking.DayHabitRowsAssembler
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.Instant

private const val RECONCILE_PERIOD_HOURS = 1L
private const val OUTSTANDING_HABIT_COUNT = 1
private const val DAY_REVIEW_SETTINGS_TEST_FILE_NAME = "day_review_worker_test_settings.preferences_pb"

/**
 * day-review, slice B (day-review-notification): exercises [DayReviewWorker] via
 * `TestListenableWorkerBuilder` against a real in-memory Room database, mirroring
 * [MidnightSweepWorkerTest]'s own technique — **the late-fire guard is THE ONE RULE this class
 * exists to prove**: a run whose embedded [DayReviewWorker.DAY_REVIEW_SCHEDULED_DATE_KEY] no longer
 * matches [now][FakeTimeProvider]'s date posts nothing, yet still reschedules its successor.
 * [DayReviewNotificationPoster] is mocked (`relaxed`) here — the notification's own real-system
 * shape is [DayReviewNotificationPosterInstrumentedTest]'s job, not this class's — so this class can
 * assert purely on WHETHER/WHAT it was asked to post.
 */
@RunWith(AndroidJUnit4::class)
class DayReviewWorkerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var database: AppDatabase
    private lateinit var context: Context
    private lateinit var dayReviewSettingsStore: DayReviewSettingsStore
    private lateinit var notificationPoster: DayReviewNotificationPoster

    private val now: Instant = Instant.parse("2026-09-02T22:00:00Z")
    private val today = "2026-09-02"
    private val yesterday = "2026-09-01"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dayReviewSettingsStore = DayReviewSettingsStore(
            PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile(DAY_REVIEW_SETTINGS_TEST_FILE_NAME) }),
        )
        notificationPoster = mockk(relaxed = true)
    }

    @After
    fun tearDown() = database.close()

    private fun buildWorker(scheduledDate: String): DayReviewWorker {
        val daos = HabitDaos(
            database.habitDao(), database.scheduleDao(), database.reminderSlotDao(),
            database.entryDao(), database.reminderOccurrenceDao(),
        )
        val assembler = DayHabitRowsAssembler(daos)
        // The real WorkScheduler, not a mock, for the identical reason MidnightSweepWorkerTest uses
        // one: the worker re-enqueues its own successor, and that enqueue must land in a real
        // WorkManager for the "still reschedules" half of the late-fire assertion to mean anything.
        val workScheduler = WorkScheduler(context, FakeTimeProvider(now), RECONCILE_PERIOD_HOURS, dayReviewSettingsStore)
        val components = DayReviewComponents(dayReviewSettingsStore, assembler, notificationPoster)
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                DayReviewWorker(appContext, workerParameters, components, FakeTimeProvider(now), workScheduler)
        }
        val inputData = Data.Builder()
            .putString(DayReviewWorker.DAY_REVIEW_SCHEDULED_DATE_KEY, scheduledDate)
            .build()
        return TestListenableWorkerBuilder<DayReviewWorker>(context)
            .setInputData(inputData)
            .setWorkerFactory(factory)
            .build()
    }

    private fun pendingDayReview(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(DAY_REVIEW_WORK_NAME).get()

    @Test
    fun aLateFirePostsNothingButStillReschedulesItsSuccessor() = runBlocking {
        database.insertHabitWithSchedule(kind = "DAILY")

        buildWorker(scheduledDate = yesterday).doWork()

        coVerify(exactly = 0) { notificationPoster.postReview(any()) }
        assertTrue(
            "a late-fire run must still enqueue its successor — a missed review is not a failure",
            pendingDayReview().any { !it.state.isFinished },
        )
    }

    @Test
    fun anOnTimeFireWithOutstandingHabitsPostsTheOutstandingCount() = runBlocking {
        database.insertHabitWithSchedule(kind = "DAILY")

        buildWorker(scheduledDate = today).doWork()

        coVerify { notificationPoster.postReview(OUTSTANDING_HABIT_COUNT) }
    }

    @Test
    fun anOnTimeFireWithNothingDueAtAllPostsNothing() = runBlocking {
        // No habit inserted at all: nothing is due today, in either review mode.
        buildWorker(scheduledDate = today).doWork()

        coVerify(exactly = 0) { notificationPoster.postReview(any()) }
    }

    @Test
    fun onlyWhenPendingWithNothingOutstandingPostsNothing() = runBlocking {
        val habitId = database.insertHabitWithSchedule(kind = "DAILY")
        database.entryDao().insert(
            EntryEntity(
                habitId = habitId, date = today, slotId = 0, status = "COMPLETED", value = null,
                answeredAt = "${today}T09:00:00Z", source = "IN_APP",
            ),
        )
        dayReviewSettingsStore.setReviewFiresEveryNight(false)

        buildWorker(scheduledDate = today).doWork()

        coVerify(exactly = 0) { notificationPoster.postReview(any()) }
    }

    @Test
    fun onTimeFireAlsoReschedulesItsSuccessor() = runBlocking {
        buildWorker(scheduledDate = today).doWork()

        assertTrue(pendingDayReview().any { !it.state.isFinished })
    }
}
