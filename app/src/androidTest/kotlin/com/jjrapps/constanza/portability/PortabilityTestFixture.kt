package com.jjrapps.constanza.portability

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import com.jjrapps.constanza.scheduling.AlarmScheduler
import com.jjrapps.constanza.scheduling.FakeTimeProvider
import com.jjrapps.constanza.scheduling.OccurrencePlanner
import com.jjrapps.constanza.scheduling.SchedulingDaos
import io.mockk.mockk
import java.io.File
import java.time.Instant

private val FIXED_INSTANT: Instant = Instant.parse("2026-09-01T08:00:00Z")
private const val RESOLVE_DEADLINE_HOURS = 24L

/** Shared wiring for work unit 7's instrumented tests: a real in-memory Room database, a real
 *  temp-file-backed [ReminderSettingsStore] (proves settings actually round-trip, not just Room
 *  data), and only [AlarmScheduler] relaxed-mocked — arming a real system alarm is irrelevant to
 *  what tasks 7.6/7.7 assert. Mirrors `habit.HabitRepositoryTestFixture`'s established pattern. */
class PortabilityTestFixture(context: Context, dataStoreFile: File) {
    val database: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val timeProvider: TimeProvider = FakeTimeProvider(FIXED_INSTANT)
    val alarmScheduler = mockk<AlarmScheduler>(relaxed = true)
    val settingsStore = ReminderSettingsStore(PreferenceDataStoreFactory.create(produceFile = { dataStoreFile }))

    private val daos = PortabilityDaos(
        database.habitDao(),
        database.scheduleDao(),
        database.reminderSlotDao(),
        database.entryDao(),
        database.reminderOccurrenceDao(),
    )
    private val occurrencePlanner = OccurrencePlanner(
        SchedulingDaos(
            database.habitDao(),
            database.scheduleDao(),
            database.reminderSlotDao(),
            database.reminderOccurrenceDao(),
        ),
        alarmScheduler,
        timeProvider,
        RESOLVE_DEADLINE_HOURS,
    )
    val exporter = BackupExporter(daos, settingsStore, timeProvider)
    val importer = BackupImporter(daos, database, alarmScheduler, occurrencePlanner, settingsStore)

    fun close() = database.close()
}

fun habitEntity(name: String, archived: Boolean = false, archivedAt: String? = null): HabitEntity = HabitEntity(
    name = name,
    question = null,
    colorArgb = 0,
    notes = null,
    archived = archived,
    archivedAt = archivedAt,
    createdAt = "2026-01-01T00:00:00Z",
)
