package com.jjrapps.constanza.scheduling

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import com.jjrapps.constanza.habit.HabitDaos
import com.jjrapps.constanza.reminding.DayReviewNotificationPoster
import com.jjrapps.constanza.reminding.DayReviewSettingsStore
import com.jjrapps.constanza.tracking.DayHabitRowsAssembler
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.Instant

private const val OUTSTANDING_HABIT_COUNT = 1
private const val DAY_REVIEW_SETTINGS_TEST_FILE_NAME = "day_review_fire_worker_test_settings.preferences_pb"

/**
 * day-review-exact-alarm: [DayReviewFireWorker]'s behavioural coverage, ported from the deleted
 * `WorkManager`-based `DayReviewWorkerTest` per this change's own brief — its late-fire guard, the
 * outstanding-count case, the nothing-due-at-all case and the only-when-pending case, exercised via
 * `TestListenableWorkerBuilder` against a real in-memory Room database exactly as the deleted class
 * did. **The late-fire guard is still THE ONE RULE this class exists to prove**: see
 * [DayReviewFireWorker]'s own KDoc for why the guard remains load-bearing even though the exact-alarm
 * path (design.md:1158) is measured to survive Doze — the degraded `setWindow` fallback still has a
 * window, and OEM throttling can still push a fire late. [DayReviewNotificationPoster] and
 * [DayReviewAlarmScheduler] are both mocked (`relaxed`) here — the notification's own real-system
 * shape is [com.jjrapps.constanza.reminding.DayReviewNotificationPosterInstrumentedTest]'s job, and
 * the alarm's own exact/inexact arming is [AlarmSchedulerTest]'s — so this class asserts purely on
 * WHETHER/WHAT [DayReviewFireWorker.doWork] decides to post, and that it always re-arms its successor.
 *
 * The deleted worker's own `aRealEnqueueSurvivesItsOwnSelfReschedule` and
 * `onTimeFireAlsoReschedulesItsSuccessor` are NOT ported: both existed only to catch `WorkManager`'s
 * `REPLACE`-cancels-`RUNNING`-work self-cancellation bug (see the deleted `WorkScheduler`'s own KDoc
 * trail for that history), which has no equivalent under `AlarmManager`'s
 * `PendingIntent.FLAG_UPDATE_CURRENT` re-arm — there is no enqueue here that could cancel a `RUNNING`
 * coroutine. [DayReviewAlarmScheduler] is mocked, so the re-arm assertion below is simply that it was
 * called, not a real `WorkInfo`/`AlarmManager` read-back.
 */
@RunWith(AndroidJUnit4::class)
class DayReviewFireWorkerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var database: AppDatabase
    private lateinit var context: Context
    private lateinit var dayReviewSettingsStore: DayReviewSettingsStore
    private lateinit var notificationPoster: DayReviewNotificationPoster
    private lateinit var alarmScheduler: DayReviewAlarmScheduler

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
        alarmScheduler = mockk(relaxed = true)
    }

    @After
    fun tearDown() = database.close()

    private fun buildWorker(scheduledDate: String): DayReviewFireWorker {
        val daos = HabitDaos(
            database.habitDao(), database.scheduleDao(), database.reminderSlotDao(),
            database.entryDao(), database.reminderOccurrenceDao(),
        )
        val assembler = DayHabitRowsAssembler(daos)
        val components = DayReviewComponents(dayReviewSettingsStore, assembler, notificationPoster)
        val inputData = Data.Builder()
            .putString(DayReviewFireWorker.KEY_SCHEDULED_DATE, scheduledDate)
            .build()
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                DayReviewFireWorker(appContext, workerParameters, components, FakeTimeProvider(now), alarmScheduler)
        }
        return TestListenableWorkerBuilder<DayReviewFireWorker>(context)
            .setInputData(inputData)
            .setWorkerFactory(factory)
            .build()
    }

    @Test
    fun aLateFirePostsNothingButStillReschedulesItsSuccessor() = runBlocking {
        database.insertHabitWithSchedule(kind = "DAILY")

        buildWorker(scheduledDate = yesterday).doWork()

        coVerify(exactly = 0) { notificationPoster.postReview(any()) }
        coVerify(
            exactly = 1,
        ) { alarmScheduler.scheduleNext() }
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
}
