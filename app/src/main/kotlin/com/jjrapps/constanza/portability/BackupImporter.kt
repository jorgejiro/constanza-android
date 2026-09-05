package com.jjrapps.constanza.portability

import androidx.room.withTransaction
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.migration.HabitColorRemap
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import com.jjrapps.constanza.reminding.SnoozeDuration
import com.jjrapps.constanza.scheduling.AlarmScheduler
import com.jjrapps.constanza.scheduling.OccurrencePlanner
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject

private val IMPORT_JSON = Json { ignoreUnknownKeys = true }

/**
 * weekday-only-schedule design.md decision 2: the six values `ScheduleEntity.kind`/
 * `BackupSchedule.kind` may legitimately hold, duplicated here rather than importing
 * `core/data/mapper/Mappers.kt`'s (deliberately `private`) `SCHEDULE_KIND_*` constants — this file
 * already treats `kind` as a raw string throughout, matching `BackupSchedule.kind`'s own contract.
 * A legacy `kind = "WEEKLY"` row is the whole reason this set exists: unvalidated, it would import
 * cleanly and only crash later, at `ScheduleEntity.toDomain()`'s `else -> error(...)`.
 */
private val VALID_SCHEDULE_KINDS = setOf(
    "DAILY",
    "TIMES_PER_DAY",
    "N_TIMES_PER_WEEK",
    "DAYS_OF_WEEK",
    "MONTHLY",
    "EVERY_N_DAYS",
)

/**
 * app-localization: why an import was refused, as a value rather than as a sentence.
 *
 * This class and its exceptions are deliberately Android-free — no injected `Context`, testable
 * without the framework — so they cannot call `getString` and must not carry user-facing English.
 * Previously they did: the message travelled all the way to `DataPortabilityScreen`, which rendered
 * it verbatim, which meant a user who had chosen Spanish was told in English why their backup was
 * rejected. Carrying the *reason* plus its interpolation arguments lets the Compose layer, which
 * does have resources, pick the wording.
 *
 * Each case maps to exactly one `portability_import_error_*` string resource.
 */
sealed interface ImportFailure {
    /** The SAF read itself produced nothing — the file was never parsed. */
    data object UnreadableFile : ImportFailure

    /** Unparseable JSON, or JSON that is not a Constanza backup at all. */
    data object MalformedFile : ImportFailure

    /** design.md §8.4's Forward compatibility row. */
    data class UnsupportedVersion(val fileVersion: Int) : ImportFailure

    /** Structurally parseable, but an entry points at a slot absent from its own habit's slots. */
    data class UnknownSlotReference(val habitId: Long, val slotId: Long) : ImportFailure

    /** weekday-only-schedule design.md decision 2: [kind] is not one of the six current
     *  `ScheduleEntity.kind` values — a legacy `WEEKLY` file, most commonly. Rejected here, before
     *  any database write, rather than tolerated: an unvalidated `kind` reaches `ScheduleEntity.kind`
     *  unchanged and only crashes later, on the next read through `ScheduleEntity.toDomain()`. */
    data class UnsupportedScheduleKind(val habitId: Long, val kind: String) : ImportFailure
}

/** data-portability: Malformed file leaves data intact. Thrown by [BackupImporter.parseAndValidate]
 *  before any database write, whether the cause is unparseable JSON or a structurally invalid
 *  backup (e.g. an entry referencing a slot id absent from its own habit's [BackupHabit.slots]).
 *
 *  [failure] is the authoritative payload. The exception message exists for logs and crash reports
 *  and is deliberately technical: it is never shown to a user. */
class MalformedBackupException(val failure: ImportFailure, cause: Throwable? = null) :
    Exception("Backup rejected: $failure", cause)

/** design.md §8.4's Forward compatibility row: a newer [BackupFile.formatVersion] refuses the
 *  whole import instead of importing partially. */
class UnsupportedBackupVersionException(val fileVersion: Int) :
    Exception("Backup rejected: unsupported format version $fileVersion") {
    val failure: ImportFailure.UnsupportedVersion = ImportFailure.UnsupportedVersion(fileVersion)
}

/**
 * Task 2.8 (data-portability: Backup Schema Version Read On Import, Legacy Habit Colour Normalized
 * On Import). A pure, top-level function — not a [BackupImporter] method — so
 * `BackupImporterNormalizationTest` can assert its behaviour directly, without constructing a
 * [BackupImporter] and its five injected collaborators. [schemaVersion] `< CURRENT_SCHEMA_VERSION`
 * (a file exported before this palette change) has every habit's [BackupHabit.colorArgb] rewritten
 * through [HabitColorRemap.normalize] — the same one-to-one map `AppMigrations.MIGRATION_1_2`
 * applies to already-persisted data. `schemaVersion == CURRENT_SCHEMA_VERSION` returns [habits]
 * unchanged: colours are imported byte-identical (data-portability: Round-Trip Fidelity, "Current-
 * version round trip preserves colour exactly").
 */
internal fun normalizeHabitColors(habits: List<BackupHabit>, schemaVersion: Int): List<BackupHabit> =
    if (schemaVersion < CURRENT_SCHEMA_VERSION) {
        habits.map { it.copy(colorArgb = HabitColorRemap.normalize(it.colorArgb)) }
    } else {
        habits
    }

/**
 * Task 7.3 (data-portability: Import, Round-Trip Fidelity). Split in two on purpose:
 * [parseAndValidate] does no database I/O at all, so a caller can run it, show the destructive
 * confirmation (task 7.4) ONLY once it has succeeded, and call [replaceAll] only after the user
 * confirms — declining after a successful parse still leaves the dataset untouched, satisfying
 * "Declined confirmation changes nothing" even though parsing already happened.
 */
class BackupImporter @Inject constructor(
    private val daos: PortabilityDaos,
    private val database: AppDatabase,
    private val alarmScheduler: AlarmScheduler,
    private val occurrencePlanner: OccurrencePlanner,
    private val settingsStore: ReminderSettingsStore,
) {
    /** Throws [MalformedBackupException] or [UnsupportedBackupVersionException]; never writes. */
    fun parseAndValidate(json: String): BackupFile {
        val backup = decode(json)
        if (backup.formatVersion > CURRENT_BACKUP_FORMAT_VERSION) {
            throw UnsupportedBackupVersionException(backup.formatVersion)
        }
        backup.habits.forEach(::validateHabit)
        return backup
    }

    private fun decode(json: String): BackupFile = try {
        IMPORT_JSON.decodeFromString(BackupFile.serializer(), json)
    } catch (e: SerializationException) {
        throw MalformedBackupException(ImportFailure.MalformedFile, e)
    } catch (e: IllegalArgumentException) {
        throw MalformedBackupException(ImportFailure.MalformedFile, e)
    }

    private fun validateHabit(habit: BackupHabit) {
        if (habit.schedule.kind !in VALID_SCHEDULE_KINDS) {
            throw MalformedBackupException(
                ImportFailure.UnsupportedScheduleKind(habitId = habit.id, kind = habit.schedule.kind),
            )
        }
        val slotIds = habit.slots.map { it.id }.toSet()
        habit.entries.forEach { entry ->
            val slotId = entry.slotId
            if (slotId != null && slotId !in slotIds) {
                throw MalformedBackupException(
                    ImportFailure.UnknownSlotReference(habitId = habit.id, slotId = slotId),
                )
            }
        }
    }

    /** Replace-all in one Room transaction with ID remapping (data-portability: Import — Replace-
     *  all). Callers MUST pass a [backup] already returned by [parseAndValidate] — this assumes it
     *  is valid and does no further validation of its own. */
    suspend fun replaceAll(backup: BackupFile) {
        val armedOccurrenceIds = daos.habitDao.findAllSnapshot()
            .flatMap { daos.reminderOccurrenceDao.findByHabitId(it.id) }
            .map { it.id }
        val habits = normalizeHabitColors(backup.habits, backup.schemaVersion)

        database.withTransaction {
            // reminder_occurrences is deliberately excluded from the file (design.md §8.4) and
            // gets truncated here by the same cascade delete that wipes schedules/slots/entries —
            // the alarms it referenced are cancelled below, after the transaction commits, since
            // AlarmManager state is independent of the Room row it was armed from.
            daos.habitDao.deleteAll()
            habits.forEach { insertHabit(it) }
        }
        settingsStore.setSnoozeDuration(SnoozeDuration.fromMinutes(backup.settings.defaultSnoozeMinutes))

        armedOccurrenceIds.forEach { alarmScheduler.cancel(it) }
        occurrencePlanner.replanAll()
    }

    private suspend fun insertHabit(habit: BackupHabit) {
        val newHabitId = daos.habitDao.insert(habit.toEntity())
        daos.scheduleDao.upsert(habit.schedule.toEntity(newHabitId))
        val slotIdMap: Map<Long, Long> = habit.slots.associate { slot ->
            slot.id to daos.reminderSlotDao.insert(slot.toEntity(newHabitId))
        }
        habit.entries.forEach { entry ->
            daos.entryDao.insert(entry.toEntity(newHabitId, remapEntrySlotId(entry.slotId, slotIdMap)))
        }
    }
}
