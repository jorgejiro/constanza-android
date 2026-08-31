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
 * **Provisional (OA-2, unconfirmed)**: the classification precedence below — a missed slot
 * outranks partial completion, which outranks a still-fully-pending day — is this
 * implementation's own assumption, not a spec-mandated priority. A reviewer may want a different
 * tie-break once OA-2 is confirmed.
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
        statuses.any { it == EntryStatus.MISSED } -> DayStatus.ANY_MISSED
        statuses.all { it == EntryStatus.COMPLETED } -> DayStatus.ALL_COMPLETED
        statuses.all { it == EntryStatus.SKIPPED } -> DayStatus.ALL_SKIPPED
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
