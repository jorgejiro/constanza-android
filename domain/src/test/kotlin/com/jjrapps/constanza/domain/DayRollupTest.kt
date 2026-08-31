package com.jjrapps.constanza.domain

import com.jjrapps.constanza.domain.model.DayStatus
import com.jjrapps.constanza.domain.model.Entry
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Test

/**
 * habit-entry-tracking: Day-Level Rollup and Per-Slot Display. RED for tasks 2a.7/2a.8.
 * **Provisional — OA-2, unconfirmed**: both the rollup function and per-slot UI are treated as
 * required and non-overlapping.
 */
class DayRollupTest {

    private val date = LocalDate.of(2026, 9, 1)
    private val slots = listOf(
        ReminderSlot(id = 1, habitId = 1, minuteOfDay = 480, enabled = true),
        ReminderSlot(id = 2, habitId = 1, minuteOfDay = 840, enabled = true),
        ReminderSlot(id = 3, habitId = 1, minuteOfDay = 1200, enabled = true),
    )

    private fun entry(slotId: Long, status: EntryStatus) =
        Entry(habitId = 1, date = date, slotId = slotId, status = status)

    @Test
    fun `a 3-slot day with 2 completed and 1 unknown reports partial completion`() {
        val entries = listOf(
            entry(1, EntryStatus.COMPLETED),
            entry(2, EntryStatus.COMPLETED),
            entry(3, EntryStatus.UNKNOWN),
        )
        assertEquals(DayStatus.PARTIAL, rollupDay(Schedule.TimesPerDay(), date, slots, entries))
    }

    @Test
    fun `all slots completed rolls up to ALL_COMPLETED`() {
        val entries = slots.map { entry(it.id, EntryStatus.COMPLETED) }
        assertEquals(DayStatus.ALL_COMPLETED, rollupDay(Schedule.TimesPerDay(), date, slots, entries))
    }

    @Test
    fun `any missed slot rolls up to ANY_MISSED even when others completed`() {
        val entries = listOf(
            entry(1, EntryStatus.COMPLETED),
            entry(2, EntryStatus.MISSED),
            entry(3, EntryStatus.COMPLETED),
        )
        assertEquals(DayStatus.ANY_MISSED, rollupDay(Schedule.TimesPerDay(), date, slots, entries))
    }

    @Test
    fun `all slots skipped rolls up to ALL_SKIPPED`() {
        val entries = slots.map { entry(it.id, EntryStatus.SKIPPED) }
        assertEquals(DayStatus.ALL_SKIPPED, rollupDay(Schedule.TimesPerDay(), date, slots, entries))
    }

    @Test
    fun `no entries at all rolls up to PENDING`() {
        assertEquals(DayStatus.PENDING, rollupDay(Schedule.TimesPerDay(), date, slots, emptyList()))
    }

    @Test
    fun `a date the schedule does not require rolls up to NOT_DUE`() {
        val weekly = Schedule.Weekly(dayOfWeek = java.time.DayOfWeek.MONDAY)
        // 2026-09-01 is a Tuesday, so a MONDAY-only weekly habit is not due that day.
        assertEquals(DayStatus.NOT_DUE, rollupDay(weekly, date, emptyList(), emptyList()))
    }

    @Test
    fun `a single non-slotted occurrence with no entry rolls up to PENDING, not NOT_DUE`() {
        val daily = Schedule.Daily()
        assertEquals(DayStatus.PENDING, rollupDay(daily, date, emptyList(), emptyList()))
    }
}
