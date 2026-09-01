package com.jjrapps.constanza.portability

import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.data.entity.ReminderSlotEntity
import com.jjrapps.constanza.core.data.entity.ScheduleEntity
import java.time.DayOfWeek

/**
 * Room entity <-> backup-DTO translation (task 7.1), the export/import analogue of
 * `core/data/mapper/Mappers.kt`. Deliberately bypasses `:domain`'s `Schedule`/`Entry` types on
 * both directions: export/import round-trips exactly what Room persists, not what `:domain`
 * models, and `EntryStatus.UNKNOWN` is never persisted in the first place (design.md §8.1), so
 * there is nothing here to guard against that the entity layer does not already guard against.
 */

private const val ENTRY_SLOT_SENTINEL = 0L

fun HabitEntity.toBackup(schedule: BackupSchedule, slots: List<BackupSlot>, entries: List<BackupEntry>) =
    BackupHabit(
        id = id,
        name = name,
        question = question,
        colorArgb = colorArgb,
        notes = notes,
        archived = archived,
        archivedAt = archivedAt,
        createdAt = createdAt,
        sortOrder = sortOrder,
        schedule = schedule,
        slots = slots,
        entries = entries,
    )

/** `id = 0` is Room's autogenerate sentinel (matches every other `toEntity()` in this codebase
 *  that produces a brand-new row) — import always inserts, never reuses the file's own ids. */
fun BackupHabit.toEntity(): HabitEntity = HabitEntity(
    id = 0,
    name = name,
    question = question,
    colorArgb = colorArgb,
    notes = notes,
    archived = archived,
    archivedAt = archivedAt,
    createdAt = createdAt,
    sortOrder = sortOrder,
)

fun ScheduleEntity.toBackup(): BackupSchedule = BackupSchedule(
    kind = kind,
    weekStart = DayOfWeek.of(weekStart).name,
    timesPerWeek = timesPerWeek,
    dayOfWeek = dayOfWeek?.let { DayOfWeek.of(it).name },
    dayOfMonth = dayOfMonth,
    intervalDays = intervalDays,
    anchorDate = anchorDate,
)

fun BackupSchedule.toEntity(habitId: Long): ScheduleEntity = ScheduleEntity(
    habitId = habitId,
    kind = kind,
    timesPerWeek = timesPerWeek,
    dayOfWeek = dayOfWeek?.let { DayOfWeek.valueOf(it).value },
    dayOfMonth = dayOfMonth,
    intervalDays = intervalDays,
    anchorDate = anchorDate,
    weekStart = DayOfWeek.valueOf(weekStart).value,
)

fun ReminderSlotEntity.toBackup(): BackupSlot = BackupSlot(id = id, minuteOfDay = minuteOfDay, enabled = enabled)

fun BackupSlot.toEntity(habitId: Long): ReminderSlotEntity =
    ReminderSlotEntity(id = 0, habitId = habitId, minuteOfDay = minuteOfDay, enabled = enabled)

/** Converts the `0` sentinel to a real `null` (D11) — the backup file has no sentinel of its own. */
fun EntryEntity.toBackup(): BackupEntry = BackupEntry(
    date = date,
    slotId = if (slotId == ENTRY_SLOT_SENTINEL) null else slotId,
    status = status,
    value = value,
    answeredAt = answeredAt,
    source = source,
)

/** [newSlotId] is the remapped id from [BackupImporter]'s old->new slot map ([remapEntrySlotId]),
 *  already resolved to the `0` sentinel by the caller when [BackupEntry.slotId] was `null`. */
fun BackupEntry.toEntity(habitId: Long, newSlotId: Long): EntryEntity = EntryEntity(
    id = 0,
    habitId = habitId,
    date = date,
    slotId = newSlotId,
    status = status,
    value = value,
    answeredAt = answeredAt,
    source = source,
)

/**
 * Task 7.3's slotId remap (data-portability: Round-Trip Fidelity), extracted as a pure function so
 * it is unit-testable without Room: resolves an entry's OLD slot id through [slotIdMap] (built
 * from the just-inserted slots' returned new ids) to its NEW one, or the `0` sentinel when the
 * entry had no slot at all (D7/OA-3, a habit saved with no reminder time). [parseAndValidate]
 * already guaranteed every non-null [oldSlotId] has a key in [slotIdMap] by the time this runs
 * (`BackupImporter`'s per-habit slot-reference check) — the `requireNotNull` here is a defensive
 * invariant, not the primary validation path.
 */
fun remapEntrySlotId(oldSlotId: Long?, slotIdMap: Map<Long, Long>): Long =
    oldSlotId?.let { requireNotNull(slotIdMap[it]) { "No remapped id for slot $it" } } ?: ENTRY_SLOT_SENTINEL
