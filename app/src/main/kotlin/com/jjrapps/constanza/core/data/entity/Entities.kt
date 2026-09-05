package com.jjrapps.constanza.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room mirror of design.md §8.1's `habits` table. `:domain`'s [com.jjrapps.constanza.domain.model.Habit]
 * has no Room annotations at all (design.md §4) — mapping happens in `core/data/mapper`.
 */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val notes: String?,
    val archived: Boolean = false,
    val archivedAt: String?,
    val createdAt: String,
    val sortOrder: Int = 0,
)

/** Exactly one row per habit — design.md §8.1's `schedules` table. */
@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ScheduleEntity(
    @PrimaryKey val habitId: Long,
    val kind: String,
    val timesPerWeek: Int?,
    /**
     * Dead since v4 (weekday-only-schedule design D1) — left declared, never written, so Room's
     * `identityHash` keeps matching the v4/v5 schema without a table rebuild. The day set now
     * lives in [daysOfWeekMask]; a `WEEKLY` row's `dayOfWeek` is only ever read by `migration3To4`.
     */
    val dayOfWeek: Int?,
    val dayOfMonth: Int?,
    val intervalDays: Int?,
    val anchorDate: String?,
    val weekStart: Int = 1,
    /** Bit `n` = `DayOfWeek.value - 1`; e.g. Mon–Fri = `0b0011111` = 31. Added in v4. */
    val daysOfWeekMask: Int? = null,
)

/** design.md §8.1's `reminder_slots` table. `UNIQUE(habitId, minuteOfDay)` as written. */
@Entity(
    tableName = "reminder_slots",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["habitId", "minuteOfDay"], unique = true)],
)
data class ReminderSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val minuteOfDay: Int,
    val enabled: Boolean = true,
)

/**
 * design.md §8.1's `entries` table and D11's load-bearing decision: `slotId` is `NOT NULL DEFAULT 0`
 * with **no** foreign key to `reminder_slots`, because `UNIQUE(habitId, date, slotId)` cannot
 * constrain a nullable column — SQLite treats every `NULL` as distinct from every other `NULL`.
 * The `0` sentinel makes the uniqueness constraint enforceable; the mapper converts `0 ↔ null`
 * so `:domain` never sees it.
 */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["habitId", "date", "slotId"], unique = true),
        Index(value = ["habitId", "date"], name = "idx_entries_habit_date"),
    ],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: String,
    @ColumnInfo(defaultValue = "0") val slotId: Long = 0,
    val status: String,
    val value: Int?,
    val answeredAt: String,
    val source: String,
)

/**
 * design.md §8.1's `reminder_occurrences` table — transient scheduling state with no `:domain`
 * counterpart (design D4); wired by work unit 4a's [com.jjrapps.constanza.scheduling] package.
 * `id` doubles as the notification id and the `PendingIntent` request code (design.md §8.2).
 */
@Entity(
    tableName = "reminder_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["habitId", "slotId", "scheduledDate"], unique = true),
        Index(value = ["state", "snoozeUntilEpochMs"], name = "idx_occ_state_snooze"),
        Index(value = ["resolveDeadlineMs"], name = "idx_occ_deadline"),
    ],
)
data class ReminderOccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    @ColumnInfo(defaultValue = "0") val slotId: Long = 0,
    val scheduledDate: String,
    val scheduledAtEpochMs: Long,
    val state: String,
    @ColumnInfo(defaultValue = "1") val exact: Boolean = true,
    val snoozeUntilEpochMs: Long?,
    val snoozeCount: Int = 0,
    val notifiedAtEpochMs: Long?,
    val resolveDeadlineMs: Long,
)
