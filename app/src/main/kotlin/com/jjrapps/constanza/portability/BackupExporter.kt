package com.jjrapps.constanza.portability

import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import kotlinx.serialization.json.Json
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val EXPORT_JSON = Json { prettyPrint = true }
private val FILENAME_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

/**
 * Task 7.2 (data-portability: Export). [buildBackup] reads Room plus the DataStore settings and
 * assembles the whole in-memory [BackupFile]; the caller (task 7.2's SAF `ACTION_CREATE_DOCUMENT`
 * flow) is responsible for writing [serialize]'s bytes to the user-chosen `Uri` — this class knows
 * nothing about `ContentResolver`, keeping it directly unit-testable without Android.
 *
 * `exportedAtZone` and the filename's timestamp both go through [TimeProvider] (never
 * `ZoneId.systemDefault()` directly), matching `detekt.yml`'s `ForbiddenMethodCall` ban.
 */
class BackupExporter @Inject constructor(
    private val daos: PortabilityDaos,
    private val settingsStore: ReminderSettingsStore,
    private val timeProvider: TimeProvider,
) {
    suspend fun buildBackup(): BackupFile {
        val snooze = settingsStore.currentSnoozeDuration()
        val habits = daos.habitDao.findAllSnapshot().map { habit ->
            val schedule = requireNotNull(daos.scheduleDao.findByHabitId(habit.id)) {
                "Habit ${habit.id} has no schedule row; every habit gets one on create/update."
            }
            val slots = daos.reminderSlotDao.findByHabitId(habit.id)
            val entries = daos.entryDao.findByHabitId(habit.id)
            habit.toBackup(
                schedule = schedule.toBackup(),
                slots = slots.map { it.toBackup() },
                entries = entries.map { it.toBackup() },
            )
        }
        return BackupFile(
            exportedAt = timeProvider.now().toString(),
            exportedAtZone = timeProvider.zone().id,
            settings = BackupSettings(defaultSnoozeMinutes = snooze.minutes),
            habits = habits,
        )
    }

    fun serialize(backup: BackupFile): String = EXPORT_JSON.encodeToString(BackupFile.serializer(), backup)

    /** design.md §8.4: `constanza-backup-<yyyyMMdd-HHmmss>.json`, suggested as the `ACTION_CREATE_
     *  DOCUMENT` initial name — the user may still rename it in the system picker. */
    fun fileName(): String {
        val at = ZonedDateTime.ofInstant(timeProvider.now(), timeProvider.zone())
        return "constanza-backup-${FILENAME_TIMESTAMP.format(at)}.json"
    }
}
