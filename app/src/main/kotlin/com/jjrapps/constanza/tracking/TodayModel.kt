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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val STATE_SNOOZED = "SNOOZED"
private const val NO_SLOT = 0L

/** Task 6b.1 (OA-2, design.md §1's assumption table). One row per habit carrying [dayStatus] —
 *  produced by `:domain`'s [rollupDay], never reimplemented here — expandable to [slots], each
 *  independently answerable. A single-slot habit ([slots] of size 1) reads as one plain row; the
 *  UI decides that presentation, this state only carries the data.
 *
 *  [colorArgb] backs the habit-colour dot (design.md decision 6, work unit 4, correction C3) and
 *  deliberately has NO default value: [buildTodayHabitRow] must fill it from the [Habit] it
 *  already holds, and the compiler — not a silently-passing test — is what catches a forgotten
 *  mapping if that ever stops being true. */
data class TodayHabitRow(
    val habitId: Long,
    val habitName: String,
    val dayStatus: DayStatus,
    val colorArgb: Int,
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

    // Today's occurrences only. `observeUnresolved()` deliberately spans every unresolved date so it
    // agrees with what re-arming considers live, and `OccurrencePlanner` arms today, today+1 and
    // today+2 — so without this bound a slot picked whichever row the query happened to return
    // first, with no ORDER BY to make even that stable. Worse, once today's occurrence went
    // RESOLVED the slot surfaced TOMORROW's ARMED one: the answer buttons stayed on an
    // already-answered slot, and a correcting tap wrote the Entry against tomorrow's date and
    // cancelled tomorrow's alarm — an origin-date violation reachable by ordinary use. The date
    // bound belongs here, in the screen that shows one day, not in the DAO whose breadth is load
    // bearing for re-arming.
    val todayText = snapshot.today.toString()
    val habitOccurrences = snapshot.unresolvedOccurrences.filter {
        it.habitId == habit.id && it.scheduledDate == todayText
    }
    val slotRows = if (enabledSlots.isEmpty()) {
        listOf(toTodaySlot(slotId = null, minuteOfDay = null, entries = domainEntries, occurrences = habitOccurrences))
    } else {
        enabledSlots.map { slot ->
            toTodaySlot(slot.id, slot.minuteOfDay, domainEntries, habitOccurrences)
        }
    }
    return TodayHabitRow(habit.id, habit.name, dayStatus, habit.colorArgb, slotRows)
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

/** today-grouped-sections, design.md: the three ordered sections a grouped Today screen renders
 *  its rows into. [DONE] is the only kind the UI mutes — see `TodayScreen.kt`'s rendering rule
 *  that only the "Hecho" group recedes. */
enum class TodaySectionKind { NOW, LATER, DONE }

/** One non-empty run of [rows] under a single section header. [groupTodayRows] never emits a
 *  [TodaySection] with an empty [rows] — a group with nothing in it renders no header, no
 *  divider, no reserved space (design.md's explicit "zero rows renders nothing" rule). */
data class TodaySection(val kind: TodaySectionKind, val rows: List<TodayHabitRow>)

/**
 * Groups and orders [rows] into the three sections a grouped Today screen shows (today-grouped-
 * sections, design.md):
 * - [TodaySectionKind.NOW] ("Ahora"): habits with at least one unanswered slot that is actionable
 *   RIGHT NOW — the slot has no reminder time, or that time (a SNOOZED slot's own snooze time,
 *   never its original one) is already behind [now]. Within the section, habits with no timed
 *   now-actionable slot sort first, then the rest by that time ascending.
 * - [TodaySectionKind.LATER] ("Más tarde"): habits with no now-actionable slot but at least one
 *   unanswered slot still ahead of [now], ordered by that nearest future time ascending.
 * - [TodaySectionKind.DONE] ("Hecho"): habits whose slots are ALL answered, ordered by time.
 *
 * Grouping is per HABIT, never per slot: a multi-slot habit keeps its one expandable row, and is
 * placed by its single most actionable slot, in exactly this precedence — any unanswered slot
 * that is overdue or untimed beats any unanswered future slot beats "every slot already answered".
 * A section [groupTodayRows] would otherwise emit with no rows is omitted from the result
 * entirely, per the design's explicit "empty group renders nothing" rule — never returned as an
 * empty [TodaySection].
 */
fun groupTodayRows(
    rows: List<TodayHabitRow>,
    today: LocalDate,
    zone: ZoneId,
    now: Instant,
): List<TodaySection> {
    val placements = rows.map { row -> row to row.placement(today, zone, now) }
    val nowRows = placements.mapNotNull { (row, p) -> (p as? RowPlacement.Now)?.let { row to it.minuteOfDay } }
        .sortedWith(compareBy(nullsFirst<Int>()) { (_, minute) -> minute })
        .map { (row, _) -> row }
    val laterRows = placements.mapNotNull { (row, p) -> (p as? RowPlacement.Later)?.let { row to it.at } }
        .sortedBy { (_, at) -> at }
        .map { (row, _) -> row }
    val doneRows = placements.mapNotNull { (row, p) -> (p as? RowPlacement.Done)?.let { row to it.minuteOfDay } }
        .sortedWith(compareBy(nullsFirst<Int>()) { (_, minute) -> minute })
        .map { (row, _) -> row }
    return listOfNotNull(
        section(TodaySectionKind.NOW, nowRows),
        section(TodaySectionKind.LATER, laterRows),
        section(TodaySectionKind.DONE, doneRows),
    )
}

private fun section(kind: TodaySectionKind, rows: List<TodayHabitRow>): TodaySection? =
    TodaySection(kind, rows).takeIf { rows.isNotEmpty() }

/** [Now]/[Done] sort untimed ahead of timed (design.md: "habits with no time first, then by time
 *  ascending") via [minuteOfDay]'s natural `nullsFirst` ordering; [Later]'s [at] is always a real
 *  instant, since a slot with no future time cannot be "in the future" in the first place. */
private sealed interface RowPlacement {
    data class Now(val minuteOfDay: Int?) : RowPlacement
    data class Later(val at: Instant) : RowPlacement
    data class Done(val minuteOfDay: Int?) : RowPlacement
}

private fun TodayHabitRow.placement(today: LocalDate, zone: ZoneId, now: Instant): RowPlacement {
    val unanswered = slots.filterNot { it.isAnswered() }
    if (unanswered.isEmpty()) {
        return RowPlacement.Done(slots.mapNotNull { it.minuteOfDay }.minOrNull())
    }
    val withActionableTime = unanswered.map { it to it.actionableInstant(today, zone) }
    val overdueOrUntimed = withActionableTime.filter { (_, at) -> at == null || !at.isAfter(now) }
    return if (overdueOrUntimed.isNotEmpty()) {
        val untimed = overdueOrUntimed.any { (slot, _) -> slot.minuteOfDay == null }
        val minute = overdueOrUntimed.mapNotNull { (slot, _) -> slot.minuteOfDay }.minOrNull()
        RowPlacement.Now(minute.takeUnless { untimed })
    } else {
        // Every unanswered slot has an actionable time here (the `at == null` branch above is
        // exactly what "overdue or untimed" catches), so `mapNotNull` never drops a candidate and
        // `min()` is always defined.
        RowPlacement.Later(withActionableTime.mapNotNull { (_, at) -> at }.min())
    }
}

private fun TodaySlot.isAnswered(): Boolean = status != EntryStatus.UNKNOWN

/** `null` means "no reminder time" (always actionable now); a SNOOZED slot's stored
 *  [TodaySlot.snoozedUntilEpochMs] wins over its original [TodaySlot.minuteOfDay] — the design's
 *  explicit "a SNOOZED slot counts by its snooze time, not its original time" rule — mirroring the
 *  identical precedence `TodayScreen.kt`'s own snooze sentence gives it. */
private fun TodaySlot.actionableInstant(today: LocalDate, zone: ZoneId): Instant? = when {
    snoozedUntilEpochMs != null -> Instant.ofEpochMilli(snoozedUntilEpochMs)
    minuteOfDay == null -> null
    else -> today.atStartOfDay(zone).plusMinutes(minuteOfDay.toLong()).toInstant()
}
