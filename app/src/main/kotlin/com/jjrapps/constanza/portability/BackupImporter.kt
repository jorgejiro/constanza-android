package com.jjrapps.constanza.portability

import androidx.room.withTransaction
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import com.jjrapps.constanza.reminding.SnoozeDuration
import com.jjrapps.constanza.scheduling.AlarmScheduler
import com.jjrapps.constanza.scheduling.OccurrencePlanner
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject

private val IMPORT_JSON = Json { ignoreUnknownKeys = true }

private const val MALFORMED_MESSAGE = "The selected file is not a valid Constanza backup."

/** data-portability: Malformed file leaves data intact. Thrown by [BackupImporter.parseAndValidate]
 *  before any database write, whether the cause is unparseable JSON or a structurally invalid
 *  backup (e.g. an entry referencing a slot id absent from its own habit's [BackupHabit.slots]). */
class MalformedBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** design.md §8.4's Forward compatibility row: a newer [BackupFile.formatVersion] refuses the
 *  whole import instead of importing partially. */
class UnsupportedBackupVersionException(fileVersion: Int) :
    Exception("This backup was made by a newer version of Constanza (format $fileVersion) and can't be imported here.")

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
        throw MalformedBackupException(MALFORMED_MESSAGE, e)
    } catch (e: IllegalArgumentException) {
        throw MalformedBackupException(MALFORMED_MESSAGE, e)
    }

    private fun validateHabit(habit: BackupHabit) {
        val slotIds = habit.slots.map { it.id }.toSet()
        habit.entries.forEach { entry ->
            val slotId = entry.slotId
            if (slotId != null && slotId !in slotIds) {
                throw MalformedBackupException(
                    "Habit ${habit.id} has an entry referencing unknown slot $slotId.",
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

        database.withTransaction {
            // reminder_occurrences is deliberately excluded from the file (design.md §8.4) and
            // gets truncated here by the same cascade delete that wipes schedules/slots/entries —
            // the alarms it referenced are cancelled below, after the transaction commits, since
            // AlarmManager state is independent of the Room row it was armed from.
            daos.habitDao.deleteAll()
            backup.habits.forEach { insertHabit(it) }
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
