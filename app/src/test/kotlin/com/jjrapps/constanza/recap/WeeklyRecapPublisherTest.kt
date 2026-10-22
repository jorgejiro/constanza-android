package com.jjrapps.constanza.recap

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test

/** weekly-recap S3/S8: what the Monday fire posts, and when it posts nothing. */
class WeeklyRecapPublisherTest {

    private val weekStart = LocalDate.parse("2026-10-12")
    private val settings = mockk<WeeklyRecapSettingsStore>()
    private val loader = mockk<WeeklyRecapLoader>()
    private val poster = mockk<WeeklyRecapNotificationPoster>(relaxed = true)
    private val publisher = WeeklyRecapPublisher(settings, loader, poster)

    private fun recap(done: Int, total: Int) =
        LoadedWeeklyRecap(weekStart, rows = emptyList(), done = done, total = total, percent = 0)

    @Test
    fun `an enabled recap with something to count is posted`() = runTest {
        val week = recap(done = 3, total = 4)
        coEvery { settings.currentEnabled() } returns true
        coEvery { loader.load(weekStart) } returns week

        publisher.publish(weekStart)

        coVerify(exactly = 1) { poster.postRecap(week) }
    }

    @Test
    fun `a disabled recap is neither loaded nor posted`() = runTest {
        coEvery { settings.currentEnabled() } returns false

        publisher.publish(weekStart)

        coVerify(exactly = 0) { loader.load(any()) }
        coVerify(exactly = 0) { poster.postRecap(any()) }
    }

    @Test
    fun `a week with nothing to count is not posted`() = runTest {
        coEvery { settings.currentEnabled() } returns true
        coEvery { loader.load(weekStart) } returns recap(done = 0, total = 0)

        publisher.publish(weekStart)

        coVerify(exactly = 0) { poster.postRecap(any()) }
    }
}
