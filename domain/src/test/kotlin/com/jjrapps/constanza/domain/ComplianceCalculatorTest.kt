package com.jjrapps.constanza.domain

import com.jjrapps.constanza.domain.model.Entry
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.Schedule
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Test

/**
 * habit-progress: Compliance Calculation. RED for tasks 2b.3/2b.4 — `ComplianceCalculator` does
 * not exist yet.
 */
class ComplianceCalculatorTest {

    private val daily = Schedule.Daily()

    private fun entry(date: LocalDate, status: EntryStatus) =
        Entry(habitId = 1L, date = date, slotId = null, status = status)

    /**
     * 15 completed, 5 missed, 5 skipped, 5 more completed — 30 days total, matching the spec's
     * own numbers (20 completed, 5 missed, 5 skipped) while giving the 7-day-window test below an
     * independently interesting subset.
     */
    private fun thirtyDayHistory(today: LocalDate): List<Entry> {
        val start = today.minusDays(29)
        val completedFirst = (0..14).map { entry(start.plusDays(it.toLong()), EntryStatus.COMPLETED) }
        val missed = (15..19).map { entry(start.plusDays(it.toLong()), EntryStatus.MISSED) }
        val skipped = (20..24).map { entry(start.plusDays(it.toLong()), EntryStatus.SKIPPED) }
        val completedLast = (25..29).map { entry(start.plusDays(it.toLong()), EntryStatus.COMPLETED) }
        return completedFirst + missed + skipped + completedLast
    }

    @Test
    fun `30-day window excludes skipped days from both sides of the ratio`() {
        val today = LocalDate.of(2026, 3, 30)
        val entries = thirtyDayHistory(today)

        assertEquals(20.0 / 25.0, ComplianceCalculator.ratio(daily, entries, today, windowDays = 30))
    }

    @Test
    fun `a different window length is independently correct from the 30-day result`() {
        val today = LocalDate.of(2026, 3, 30)
        val entries = thirtyDayHistory(today)

        // Last 7 days (03-24..03-30): two skipped, five completed — no missed at all.
        assertEquals(1.0, ComplianceCalculator.ratio(daily, entries, today, windowDays = 7))
    }

    @Test
    fun `unknown days from absent entries are excluded from both sides`() {
        val today = LocalDate.of(2026, 5, 5)
        val entries = listOf(
            entry(today.minusDays(4), EntryStatus.COMPLETED),
            entry(today.minusDays(3), EntryStatus.MISSED),
            entry(today.minusDays(2), EntryStatus.SKIPPED),
            // today.minusDays(1) has no Entry at all: an unknown, still-pending day.
            entry(today, EntryStatus.COMPLETED),
        )

        assertEquals(2.0 / 3.0, ComplianceCalculator.ratio(daily, entries, today, windowDays = 5))
    }

    @Test
    fun `an entry exactly at the window edge is included, one day earlier is excluded`() {
        val today = LocalDate.of(2026, 6, 15)
        val entries = listOf(
            entry(today.minusDays(7), EntryStatus.COMPLETED), // one day before the 7-day window
            entry(today.minusDays(6), EntryStatus.MISSED), // exactly the window's earliest day
        )

        assertEquals(0.0, ComplianceCalculator.ratio(daily, entries, today, windowDays = 7))
    }

    @Test
    fun `an empty entry list yields zero compliance`() {
        val today = LocalDate.of(2026, 7, 1)

        assertEquals(0.0, ComplianceCalculator.ratio(daily, emptyList(), today, windowDays = 30))
    }
}
