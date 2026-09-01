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
 * **OA-2 was ratified 2026-09-01** (design.md §1), but it settled the *screen shape* — one row per
 * habit carrying this rollup, expandable to per-slot rows — and **not** the precedence below.
 *
 * **Still this implementation's own assumption, now visible to users (task 6b.10):** a missed slot
 * outranks partial completion, which outranks a still-fully-pending day. No spec mandates that
 * order. It used to be invisible; with the ratified collapsed row it is the single word a user reads
 * for the whole day, so a three-slot day with two completions and one miss reads `ANY_MISSED` rather
 * than `PARTIAL`. That is a product choice about whether the collapsed row leads with the failure or
 * with the progress, and it deserves a deliberate answer rather than inheriting one.
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
