package com.jjrapps.constanza.domain

import com.jjrapps.constanza.domain.model.Entry
import com.jjrapps.constanza.domain.model.EntryStatus
import java.time.LocalDate

/**
 * Collapses every [Entry] recorded for a single date into one [EntryStatus], shared by
 * [StreakCalculator] and [ComplianceCalculator]. A date with no matching entry is
 * [EntryStatus.UNKNOWN] (design.md §8: absence of a row for a due occurrence IS unknown — the
 * pending-snooze case, D3). A date with any [EntryStatus.MISSED] entry resolves to `MISSED` (the
 * worst case). A date where every entry is [EntryStatus.COMPLETED] resolves to `COMPLETED`. A date
 * with an explicit [EntryStatus.UNKNOWN] entry (and no `MISSED`/all-`COMPLETED`) resolves to
 * `UNKNOWN`; anything else (a mix, or all `SKIPPED`) resolves to `SKIPPED`. Both callers treat
 * `SKIPPED` and `UNKNOWN` identically as pass-through, so this precedence never changes behaviour.
 */
internal fun resolvedStatus(date: LocalDate, entries: List<Entry>): EntryStatus {
    val statuses = entries.filter { it.date == date }.map { it.status }
    return when {
        statuses.isEmpty() -> EntryStatus.UNKNOWN
        statuses.any { it == EntryStatus.MISSED } -> EntryStatus.MISSED
        statuses.all { it == EntryStatus.COMPLETED } -> EntryStatus.COMPLETED
        statuses.any { it == EntryStatus.UNKNOWN } -> EntryStatus.UNKNOWN
        else -> EntryStatus.SKIPPED
    }
}
