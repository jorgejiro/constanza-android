package com.jjrapps.constanza.tracking

import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.data.mapper.toDomain
import com.jjrapps.constanza.domain.model.DayStatus
import com.jjrapps.constanza.domain.model.Entry
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.domain.rollupDay
import java.time.LocalDate

private const val STATE_SNOOZED = "SNOOZED"
private const val NO_SLOT = 0L

/** Task 6b.1 (OA-2, design.md §1's assumption table). One row per habit carrying [dayStatus] —
 *  produced by `:domain`'s [rollupDay], never reimplemented here — expandable to [slots], each
 *  independently answerable. A single-slot habit ([slots] of size 1) reads as one plain row; the
 *  UI decides that presentation, this state only carries the data. */
data class TodayHabitRow(
    val habitId: Long,
    val habitName: String,
    val dayStatus: DayStatus,
    val slots: List<TodaySlot>,
)

/** [occurrenceId] is the live/unresolved `reminder_occurrences` row for this slot, if any — the
 *  handle an in-app answer needs to credit the right origin date and cancel the right alarm
 *  (task 6b.2). [snoozedUntilEpochMs] is non-null only while that occurrence is `SNOOZED`
 *  (task 6b.3, design.md D3); `null` slotId means the habit has no distinguishable slot. */
data class TodaySlot(
    val slotId: Long?,
    val minuteOfDay: Int?,
    val status: EntryStatus,
    val occurrenceId: Long?,
    val snoozedUntilEpochMs: Long?,
)

/** Bundles the whole-database inputs [buildTodayHabitRow] needs, keeping its parameter count
 *  under detekt's `LongParameterList` threshold — same reasoning as `habit.HabitDaos`.
 *  [entriesToday]/[unresolvedOccurrences] are NOT pre-filtered to one habit; the caller passes
 *  the same today-wide snapshot for every habit it evaluates. */
data class TodaySnapshot(
    val entriesToday: List<EntryEntity>,
    val unresolvedOccurrences: List<ReminderOccurrenceEntity>,
    val today: LocalDate,
)

/** Pure join: `:domain`'s [rollupDay] decides whether/how the habit's day reads; this function
 *  only adds the per-slot identity (slotId, occurrenceId) the UI needs to answer independently
 *  (habit-entry-tracking: Day-Level Rollup and Per-Slot Display, Slot Independence). Returns
 *  `null` for a habit that is not due today, exactly mirroring [rollupDay]'s [DayStatus.NOT_DUE]. */
fun buildTodayHabitRow(
    habit: Habit,
    schedule: Schedule,
    slots: List<ReminderSlot>,
    snapshot: TodaySnapshot,
): TodayHabitRow? {
    val enabledSlots = slots.filter { it.enabled }
    val domainEntries = snapshot.entriesToday.filter { it.habitId == habit.id }.map { it.toDomain() }
    val dayStatus = rollupDay(schedule, snapshot.today, slots, domainEntries)
    if (dayStatus == DayStatus.NOT_DUE) return null

    val habitOccurrences = snapshot.unresolvedOccurrences.filter { it.habitId == habit.id }
    val slotRows = if (enabledSlots.isEmpty()) {
        listOf(toTodaySlot(slotId = null, minuteOfDay = null, entries = domainEntries, occurrences = habitOccurrences))
    } else {
        enabledSlots.map { slot ->
            toTodaySlot(slot.id, slot.minuteOfDay, domainEntries, habitOccurrences)
        }
    }
    return TodayHabitRow(habit.id, habit.name, dayStatus, slotRows)
}

private fun toTodaySlot(
    slotId: Long?,
    minuteOfDay: Int?,
    entries: List<Entry>,
    occurrences: List<ReminderOccurrenceEntity>,
): TodaySlot {
    val status = entries.firstOrNull { it.slotId == slotId }?.status ?: EntryStatus.UNKNOWN
    val storedSlotId = slotId ?: NO_SLOT
    val occurrence = occurrences.firstOrNull { it.slotId == storedSlotId }
    return TodaySlot(
        slotId = slotId,
        minuteOfDay = minuteOfDay,
        status = status,
        occurrenceId = occurrence?.id,
        snoozedUntilEpochMs = occurrence?.snoozeUntilEpochMs.takeIf { occurrence?.state == STATE_SNOOZED },
    )
}
