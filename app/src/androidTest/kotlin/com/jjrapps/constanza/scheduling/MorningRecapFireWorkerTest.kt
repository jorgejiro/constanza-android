package com.jjrapps.constanza.scheduling

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.jjrapps.constanza.recap.MorningRecapPublisher
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/** day-review-and-morning-recap: the morning recap recaps the day before the one it was armed for,
 *  posts nothing when it fires late, and always re-arms its successor. */
@RunWith(AndroidJUnit4::class)
class MorningRecapFireWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val publisher = mockk<MorningRecapPublisher>(relaxed = true)
    private val alarmScheduler = mockk<MorningRecapAlarmScheduler>(relaxed = true)

    private val now: Instant = Instant.parse("2026-10-24T05:00:00Z")
    private val today = "2026-10-24"

    private fun buildWorker(scheduledDate: String): MorningRecapFireWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                MorningRecapFireWorker(appContext, workerParameters, publisher, FakeTimeProvider(now), alarmScheduler)
        }
        return TestListenableWorkerBuilder<MorningRecapFireWorker>(context)
            .setInputData(Data.Builder().putString(MorningRecapFireWorker.KEY_SCHEDULED_DATE, scheduledDate).build())
            .setWorkerFactory(factory)
            .build()
    }

    @Test
    fun anOnTimeFireRecapsThePreviousDayAndReschedules() = runBlocking {
        buildWorker(scheduledDate = today).doWork()

        coVerify(exactly = 1) { publisher.publish(LocalDate.parse("2026-10-23")) }
        coVerify(exactly = 1) { alarmScheduler.scheduleNext() }
    }

    @Test
    fun aLateFirePublishesNothingButStillReschedules() = runBlocking {
        buildWorker(scheduledDate = "2026-10-23").doWork()

        coVerify(exactly = 0) { publisher.publish(any()) }
        coVerify(exactly = 1) { alarmScheduler.scheduleNext() }
    }
}
