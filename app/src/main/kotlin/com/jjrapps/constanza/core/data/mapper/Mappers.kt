package com.jjrapps.constanza.core.data.mapper

import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.data.entity.ReminderSlotEntity
import com.jjrapps.constanza.core.data.entity.ScheduleEntity
import com.jjrapps.constanza.domain.model.Entry
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * `:app`-only translation between Room entities and `:domain` types (design.md §4, §14). No
 * `:domain` type ever gains a Room annotation; no Room entity ever leaks past this package.
 */

private const val ENTRY_STATUS_UNKNOWN_MESSAGE =
    "EntryStatus.UNKNOWN must never be persisted — absence of a row IS unknown (design.md §8.1)."

private const val SCHEDULE_KIND_DAILY = "DAILY"
private const val SCHEDULE_KIND_TIMES_PER_DAY = "TIMES_PER_DAY"
private const val SCHEDULE_KIND_N_TIMES_PER_WEEK = "N_TIMES_PER_WEEK"
private const val SCHEDULE_KIND_WEEKLY = "WEEKLY"
private const val SCHEDULE_KIND_MONTHLY = "MONTHLY"
private const val SCHEDULE_KIND_EVERY_N_DAYS = "EVERY_N_DAYS"

fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    name = name,
    question = question,
    colorArgb = colorArgb,
    notes = notes,
    archived = archived,
    archivedAt = archivedAt?.let(LocalDate::parse),
    createdAt = Instant.parse(createdAt),
    sortOrder = sortOrder,
)

fun Habit.toEntity(): HabitEntity = HabitEntity(
    id = id,
    name = name,
    question = question,
    colorArgb = colorArgb,
    notes = notes,
    archived = archived,
    archivedAt = archivedAt?.toString(),
    createdAt = createdAt.toString(),
    sortOrder = sortOrder,
)

fun ScheduleEntity.toDomain(): Schedule {
    val weekStartDay = DayOfWeek.of(weekStart)
    return when (kind) {
        SCHEDULE_KIND_DAILY -> Schedule.Daily(weekStartDay)
        SCHEDULE_KIND_TIMES_PER_DAY -> Schedule.TimesPerDay(weekStartDay)
        SCHEDULE_KIND_N_TIMES_PER_WEEK ->
            Schedule.NTimesPerWeek(times = requireNotNull(timesPerWeek), weekStart = weekStartDay)

        SCHEDULE_KIND_WEEKLY ->
            Schedule.Weekly(dayOfWeek = DayOfWeek.of(requireNotNull(dayOfWeek)), weekStart = weekStartDay)

        SCHEDULE_KIND_MONTHLY ->
            Schedule.Monthly(dayOfMonth = requireNotNull(dayOfMonth), weekStart = weekStartDay)

        SCHEDULE_KIND_EVERY_N_DAYS ->
            Schedule.EveryNDays(
                n = requireNotNull(intervalDays),
                anchor = LocalDate.parse(requireNotNull(anchorDate)),
                weekStart = weekStartDay,
            )

        else -> error("Unknown schedule kind persisted: $kind")
    }
}

fun Schedule.toEntity(habitId: Long): ScheduleEntity = when (this) {
    is Schedule.Daily -> emptyScheduleEntity(habitId, SCHEDULE_KIND_DAILY, weekStart)
    is Schedule.TimesPerDay -> emptyScheduleEntity(habitId, SCHEDULE_KIND_TIMES_PER_DAY, weekStart)
    is Schedule.NTimesPerWeek ->
        emptyScheduleEntity(habitId, SCHEDULE_KIND_N_TIMES_PER_WEEK, weekStart).copy(timesPerWeek = times)

    is Schedule.Weekly ->
        emptyScheduleEntity(habitId, SCHEDULE_KIND_WEEKLY, weekStart).copy(dayOfWeek = dayOfWeek.value)

    is Schedule.Monthly ->
        emptyScheduleEntity(habitId, SCHEDULE_KIND_MONTHLY, weekStart).copy(dayOfMonth = dayOfMonth)

    is Schedule.EveryNDays ->
        emptyScheduleEntity(habitId, SCHEDULE_KIND_EVERY_N_DAYS, weekStart)
            .copy(intervalDays = n, anchorDate = anchor.toString())
}

private fun emptyScheduleEntity(habitId: Long, kind: String, weekStart: DayOfWeek) = ScheduleEntity(
    habitId = habitId,
    kind = kind,
    timesPerWeek = null,
    dayOfWeek = null,
    dayOfMonth = null,
    intervalDays = null,
    anchorDate = null,
    weekStart = weekStart.value,
)

fun ReminderSlotEntity.toDomain(): ReminderSlot =
    ReminderSlot(id = id, habitId = habitId, minuteOfDay = minuteOfDay, enabled = enabled)

fun ReminderSlot.toEntity(): ReminderSlotEntity =
    ReminderSlotEntity(id = id, habitId = habitId, minuteOfDay = minuteOfDay, enabled = enabled)

/** Converts the `0` sentinel back to `null` (D11) — `:domain` never sees the sentinel. */
fun EntryEntity.toDomain(): Entry {
    val status = EntryStatus.valueOf(status)
    require(status != EntryStatus.UNKNOWN) { ENTRY_STATUS_UNKNOWN_MESSAGE }
    return Entry(
        habitId = habitId,
        date = LocalDate.parse(date),
        slotId = if (slotId == 0L) null else slotId,
        status = status,
        value = value,
        answeredAt = Instant.parse(answeredAt),
    )
}

/**
 * Converts `slotId = null` to the `0` sentinel (D11) and rejects [EntryStatus.UNKNOWN] before it
 * can reach the database — no `INSERT` may ever write it (design.md §8.1).
 */
fun Entry.toEntity(source: String): EntryEntity {
    require(status != EntryStatus.UNKNOWN) { ENTRY_STATUS_UNKNOWN_MESSAGE }
    return EntryEntity(
        habitId = habitId,
        date = date.toString(),
        slotId = slotId ?: 0L,
        status = status.name,
        value = value,
        answeredAt = requireNotNull(answeredAt) { "A persisted Entry must carry answeredAt." }.toString(),
        source = source,
    )
}
