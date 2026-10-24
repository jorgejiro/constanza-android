package com.jjrapps.constanza.recap

import com.jjrapps.constanza.domain.model.DayStatus
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.tracking.DayHabitRowsAssembler
import com.jjrapps.constanza.tracking.TodayHabitRow
import com.jjrapps.constanza.tracking.TodaySlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test

class MorningRecapPublisherTest {

    private val day = LocalDate.parse("2026-10-23")
    private val settings = mockk<MorningRecapSettingsStore>()
    private val rowsAssembler = mockk<DayHabitRowsAssembler>()
    private val poster = mockk<MorningRecapNotificationPoster>(relaxed = true)
    private val publisher = MorningRecapPublisher(settings, rowsAssembler, poster)

    private fun row(id: Long, status: EntryStatus) = TodayHabitRow(
        habitId = id,
        habitName = "Habit $id",
        dayStatus = DayStatus.PENDING,
        colorArgb = 0,
        slots = listOf(TodaySlot(null, null, status, null, null)),
    )

    @Test
    fun `an enabled recap of a day with something to count is posted`() = runTest {
        coEvery { settings.currentEnabled() } returns true
        coEvery { rowsAssembler.rowsFor(day) } returns listOf(row(1, EntryStatus.COMPLETED), row(2, EntryStatus.MISSED))

        publisher.publish(day)

        coVerify(exactly = 1) { poster.postRecap(DaySummary(day, done = 1, total = 2)) }
    }

    @Test
    fun `a disabled recap is neither read nor posted`() = runTest {
        coEvery { settings.currentEnabled() } returns false

        publisher.publish(day)

        coVerify(exactly = 0) { rowsAssembler.rowsFor(any()) }
        coVerify(exactly = 0) { poster.postRecap(any()) }
    }

    @Test
    fun `a day with nothing to count is not posted`() = runTest {
        coEvery { settings.currentEnabled() } returns true
        coEvery { rowsAssembler.rowsFor(day) } returns emptyList()

        publisher.publish(day)

        coVerify(exactly = 0) { poster.postRecap(any()) }
    }
}
