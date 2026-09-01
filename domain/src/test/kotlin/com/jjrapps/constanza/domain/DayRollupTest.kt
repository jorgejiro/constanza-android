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
 * **OA-2 ratified 2026-09-01** (design.md §1): the rollup function and the per-slot UI are both
 * required and non-overlapping.
 *
 * **Task 6b.10, ratified 2026-09-01: `PARTIAL` leads with the progress.** `ANY_MISSED` is now
 * reserved for a day with no completion at all; a day with at least one completed slot and at
 * least one missed slot reads `PARTIAL`. The scenario below that used to assert the opposite
 * precedence is updated, not deleted — it now proves the corrected rule.
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
    fun `a missed slot alongside a completed one rolls up to PARTIAL, not ANY_MISSED`() {
        // Updated for task 6b.10: PARTIAL now leads with the progress. This scenario used to assert
        // ANY_MISSED under the old precedence (a missed slot outranked partial completion); it now
        // proves the corrected rule instead of being silently deleted.
        val entries = listOf(
            entry(1, EntryStatus.COMPLETED),
            entry(2, EntryStatus.MISSED),
            entry(3, EntryStatus.COMPLETED),
        )
        assertEquals(DayStatus.PARTIAL, rollupDay(Schedule.TimesPerDay(), date, slots, entries))
    }

    @Test
    fun `all slots missed with no completion at all rolls up to ANY_MISSED`() {
        // The day that did NOT change meaning under task 6b.10: with zero completions, ANY_MISSED
        // still applies.
        val entries = slots.map { entry(it.id, EntryStatus.MISSED) }
        assertEquals(DayStatus.ANY_MISSED, rollupDay(Schedule.TimesPerDay(), date, slots, entries))
    }

    @Test
    fun `a missed slot alongside only unknown and skipped slots rolls up to ANY_MISSED`() {
        // No completion anywhere in the day, so ANY_MISSED still applies even though not every
        // slot is MISSED.
        val entries = listOf(
            entry(1, EntryStatus.MISSED),
            entry(2, EntryStatus.SKIPPED),
            entry(3, EntryStatus.UNKNOWN),
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
