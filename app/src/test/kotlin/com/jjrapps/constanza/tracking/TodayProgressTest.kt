package com.jjrapps.constanza.tracking

import com.jjrapps.constanza.domain.model.DayStatus
import com.jjrapps.constanza.domain.model.EntryStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val COLOR_ARGB = 0xFF7D9CC4.toInt()

private fun slot(minuteOfDay: Int?, status: EntryStatus = EntryStatus.UNKNOWN) =
    TodaySlot(slotId = minuteOfDay?.toLong(), minuteOfDay = minuteOfDay, status = status, occurrenceId = null, snoozedUntilEpochMs = null)

private fun row(id: Long, vararg slots: TodaySlot) =
    TodayHabitRow(habitId = id, habitName = "Habit $id", dayStatus = DayStatus.PENDING, colorArgb = COLOR_ARGB, slots = slots.toList())

/**
 * Graphite Today: the header's "N of M" line and the multi-reminder "x of N · next HH:MM" line are
 * pure functions of the rows the screen already holds. M counts every habit shown for the day; N
 * counts only habits whose slots are ALL answered, whatever the answer — a skipped or missed slot is
 * answered, an unknown one is not.
 */
class TodayProgressTest {

    @Test
    fun `no habits reads zero of zero`() {
        assertEquals(TodayProgress(answered = 0, total = 0), todayProgress(emptyList()))
    }

    @Test
    fun `a habit counts as answered only when every slot is answered`() {
        val rows = listOf(
            row(1, slot(480, EntryStatus.COMPLETED)),
            row(2, slot(480, EntryStatus.COMPLETED), slot(720)),
            row(3, slot(480, EntryStatus.MISSED), slot(720, EntryStatus.SKIPPED)),
            row(4, slot(null)),
            row(5, slot(1140)),
        )

        assertEquals(TodayProgress(answered = 2, total = 5), todayProgress(rows))
    }

    @Test
    fun `the fraction is answered over total and zero when nothing is shown`() {
        assertEquals(0.4f, TodayProgress(answered = 2, total = 5).fraction)
        assertEquals(0f, TodayProgress(answered = 0, total = 0).fraction)
    }

    @Test
    fun `reminder progress counts answered slots and names the earliest unanswered time`() {
        val progress = reminderProgress(
            row(1, slot(480, EntryStatus.COMPLETED), slot(1140), slot(780)),
        )

        assertEquals(1, progress.answered)
        assertEquals(3, progress.total)
        assertEquals(780, progress.nextMinuteOfDay)
    }

    @Test
    fun `reminder progress has no next time once every slot is answered`() {
        val progress = reminderProgress(
            row(1, slot(480, EntryStatus.COMPLETED), slot(1140, EntryStatus.MISSED)),
        )

        assertEquals(2, progress.answered)
        assertNull(progress.nextMinuteOfDay)
    }
}
