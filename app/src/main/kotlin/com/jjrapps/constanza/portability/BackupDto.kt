package com.jjrapps.constanza.portability

import kotlinx.serialization.Serializable

/**
 * design.md §8.4 — the export/import file format. `:app`-only (task 7.1): no `:domain` type is
 * ever `@Serializable`, matching the same translation-boundary discipline `core/data/mapper`
 * already applies for Room entities.
 *
 * `formatVersion` gates the whole file: a value higher than [CURRENT_BACKUP_FORMAT_VERSION]
 * refuses the entire import rather than importing what it understands (data-portability spec,
 * §8.4's Forward compatibility row). Unknown JSON fields are ignored on decode instead
 * (`Json { ignoreUnknownKeys = true }` in [com.jjrapps.constanza.portability.BackupImporter]) —
 * a deliberately different behaviour from the version gate, not a relaxation of it.
 */
const val CURRENT_BACKUP_FORMAT_VERSION = 1

private const val BACKUP_FORMAT_NAME = "constanza.backup"
private const val CURRENT_SCHEMA_VERSION = 1

@Serializable
data class BackupFile(
    val format: String = BACKUP_FORMAT_NAME,
    val formatVersion: Int = CURRENT_BACKUP_FORMAT_VERSION,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAt: String,
    val exportedAtZone: String,
    val settings: BackupSettings,
    val habits: List<BackupHabit>,
)

/**
 * Only [defaultSnoozeMinutes] round-trips here — the app's only real global setting
 * ([com.jjrapps.constanza.reminding.ReminderSettingsStore]). `weekStart` is deliberately NOT
 * modelled at this level: design.md's own domain contracts (§10) and the persisted
 * `schedules.weekStart` column make week start a PER-SCHEDULE value (design D7), never a global
 * app setting — there is no such DataStore entry to export. Each habit's own
 * [BackupSchedule.weekStart] is the authoritative field for that value.
 */
@Serializable
data class BackupSettings(val defaultSnoozeMinutes: Int)

@Serializable
data class BackupHabit(
    val id: Long,
    val name: String,
    val question: String?,
    val colorArgb: Int,
    val notes: String?,
    val archived: Boolean,
    val archivedAt: String?,
    val createdAt: String,
    val sortOrder: Int,
    val schedule: BackupSchedule,
    val slots: List<BackupSlot>,
    val entries: List<BackupEntry>,
)

/** Mirrors `ScheduleEntity` (design.md §8.1) rather than `:domain`'s `Schedule` sealed hierarchy,
 *  so a malformed `kind` fails validation instead of failing to deserialize at all. */
@Serializable
data class BackupSchedule(
    val kind: String,
    val weekStart: String,
    val timesPerWeek: Int? = null,
    val dayOfWeek: String? = null,
    val dayOfMonth: Int? = null,
    val intervalDays: Int? = null,
    val anchorDate: String? = null,
)

@Serializable
data class BackupSlot(val id: Long, val minuteOfDay: Int, val enabled: Boolean)

/** [slotId] is `null` for a habit saved with no reminder time (D7/OA-3) — the same optionality
 *  `EntryEntity.slotId`'s `0` sentinel encodes at the Room layer, translated back to a real
 *  nullable field here since the backup file has no sentinel convention of its own. [value] is
 *  present and `null` from day one (design.md §8.3/§8.4) even though the MVP has no numeric
 *  habits yet. */
@Serializable
data class BackupEntry(
    val date: String,
    val slotId: Long?,
    val status: String,
    val value: Int? = null,
    val answeredAt: String,
    val source: String,
)
