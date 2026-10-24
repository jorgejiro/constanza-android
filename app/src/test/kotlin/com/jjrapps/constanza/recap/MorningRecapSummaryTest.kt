package com.jjrapps.constanza.recap

import com.jjrapps.constanza.domain.model.DayStatus
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.tracking.TodayHabitRow
import com.jjrapps.constanza.tracking.TodaySlot
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

private val YESTERDAY: LocalDate = LocalDate.parse("2026-10-23")

private fun row(id: Long, vararg statuses: EntryStatus) = TodayHabitRow(
    habitId = id,
    habitName = "Habit $id",
    dayStatus = DayStatus.PENDING,
    colorArgb = 0,
    slots = statuses.map { TodaySlot(slotId = null, minuteOfDay = null, status = it, occurrenceId = null, snoozedUntilEpochMs = null) },
)

class MorningRecapSummaryTest {

    @Test
    fun `a habit counts as done only when every slot it answered is completed`() {
        val summary = summarizeDay(
            YESTERDAY,
            listOf(
                row(1, EntryStatus.COMPLETED),
                row(2, EntryStatus.MISSED),
                row(3, EntryStatus.COMPLETED, EntryStatus.MISSED),
                row(4, EntryStatus.COMPLETED, EntryStatus.COMPLETED),
            ),
        )

        assertEquals(DaySummary(YESTERDAY, done = 2, total = 4), summary)
        assertEquals(50, summary.percent)
    }

    @Test
    fun `an unanswered slot leaves the habit not done`() {
        val summary = summarizeDay(YESTERDAY, listOf(row(1, EntryStatus.COMPLETED, EntryStatus.UNKNOWN)))

        assertEquals(DaySummary(YESTERDAY, done = 0, total = 1), summary)
    }

    @Test
    fun `skipped slots are ignored and an all-skipped habit is left out`() {
        val summary = summarizeDay(
            YESTERDAY,
            listOf(
                row(1, EntryStatus.COMPLETED, EntryStatus.SKIPPED),
                row(2, EntryStatus.SKIPPED),
                row(3, EntryStatus.MISSED),
            ),
        )

        assertEquals(DaySummary(YESTERDAY, done = 1, total = 2), summary)
    }

    @Test
    fun `a day with nothing to count has a zero total and a zero percent`() {
        val summary = summarizeDay(YESTERDAY, listOf(row(1, EntryStatus.SKIPPED)))

        assertEquals(0, summary.total)
        assertEquals(0, summary.percent)
    }

    @Test
    fun `the percent rounds to the nearest whole number`() {
        val summary = summarizeDay(
            YESTERDAY,
            listOf(row(1, EntryStatus.COMPLETED), row(2, EntryStatus.COMPLETED), row(3, EntryStatus.MISSED)),
        )

        assertEquals(67, summary.percent)
    }
}
