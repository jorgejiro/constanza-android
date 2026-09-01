package com.jjrapps.constanza.domain

import com.jjrapps.constanza.domain.model.DayStatus
import com.jjrapps.constanza.domain.model.Due
import com.jjrapps.constanza.domain.model.Entry
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.PeriodProgress
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import java.time.LocalDate

/**
 * Mandated function 2 (design.md §10): collapses a multi-slot day into one [DayStatus],
 * independent of any UI (habit-entry-tracking: Day-Level Rollup and Per-Slot Display).
 *
 * **OA-2 was ratified 2026-09-01** (design.md §1): one row per habit carrying this rollup,
 * expandable to per-slot rows.
 *
 * **Task 6b.10, ratified 2026-09-01: `PARTIAL` leads with the progress.** `ANY_MISSED` is reserved
 * for a day with NO completion at all; a day with at least one completed slot and at least one
 * missed slot reads `PARTIAL`, not `ANY_MISSED`. The collapsed row is the single word a user reads
 * for the whole day, the per-slot detail is one tap away, and [com.jjrapps.constanza.domain.ComplianceCalculator]
 * already carries the real numbers — the row need not act as judge of a day that is not yet a total
 * loss. This SUPERSEDES the earlier assumption (missed outranks partial), recorded here rather than
 * silently overwritten so the history is visible.
 */
fun rollupDay(
    schedule: Schedule,
    date: LocalDate,
    slots: List<ReminderSlot>,
    entries: List<Entry>,
): DayStatus {
    val alwaysZero = PeriodProgress(completedInWeek = 0, completedInMonth = 0)
    if (dueOn(schedule, date, alwaysZero) is Due.NotDue) return DayStatus.NOT_DUE

    val statuses = statusesForDate(date, slots, entries)
    return when {
        statuses.all { it == EntryStatus.UNKNOWN } -> DayStatus.PENDING
        statuses.all { it == EntryStatus.COMPLETED } -> DayStatus.ALL_COMPLETED
        statuses.all { it == EntryStatus.SKIPPED } -> DayStatus.ALL_SKIPPED
        statuses.none { it == EntryStatus.COMPLETED } && statuses.any { it == EntryStatus.MISSED } ->
            DayStatus.ANY_MISSED
        else -> DayStatus.PARTIAL
    }
}

private fun statusesForDate(
    date: LocalDate,
    slots: List<ReminderSlot>,
    entries: List<Entry>,
): List<EntryStatus> {
    val entriesForDate = entries.filter { it.date == date }
    if (slots.isEmpty()) {
        return if (entriesForDate.isEmpty()) {
            listOf(EntryStatus.UNKNOWN)
        } else {
            entriesForDate.map { it.status }
        }
    }
    return slots.filter { it.enabled }.map { slot ->
        entriesForDate.firstOrNull { it.slotId == slot.id }?.status ?: EntryStatus.UNKNOWN
    }
}
