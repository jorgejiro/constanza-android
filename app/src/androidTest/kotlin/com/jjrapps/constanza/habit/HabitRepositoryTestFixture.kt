package com.jjrapps.constanza.habit

import android.content.Context
import androidx.room.Room
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.scheduling.AlarmScheduler
import com.jjrapps.constanza.scheduling.FakeTimeProvider
import com.jjrapps.constanza.scheduling.OccurrencePlanner
import com.jjrapps.constanza.scheduling.ScheduleEditor
import com.jjrapps.constanza.scheduling.SchedulingDaos
import io.mockk.mockk
import java.time.Instant

private const val RESOLVE_DEADLINE_HOURS = 24L
private val FIXED_INSTANT: Instant = Instant.parse("2026-09-01T08:00:00Z")

/**
 * Shared wiring for the work unit 6a instrumented tests: a real in-memory Room database and a
 * real [HabitRepository]/[ScheduleEditor]/[OccurrencePlanner] chain, with only [AlarmScheduler]
 * relaxed-mocked (arming a real system alarm is irrelevant to what these scenarios assert).
 */
class HabitRepositoryTestFixture(context: Context) {
    val database: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val timeProvider: TimeProvider = FakeTimeProvider(FIXED_INSTANT)
    val habitRepository: HabitRepository

    init {
        val occurrencePlanner = OccurrencePlanner(
            SchedulingDaos(
                database.habitDao(),
                database.scheduleDao(),
                database.reminderSlotDao(),
                database.reminderOccurrenceDao(),
            ),
            mockk<AlarmScheduler>(relaxed = true),
            timeProvider,
            RESOLVE_DEADLINE_HOURS,
        )
        val daos = HabitDaos(
            database.habitDao(),
            database.scheduleDao(),
            database.reminderSlotDao(),
            database.entryDao(),
        )
        habitRepository = HabitRepository(
            daos,
            database,
            ScheduleEditor(database, database.scheduleDao(), occurrencePlanner),
            occurrencePlanner,
            timeProvider,
        )
    }

    fun close() = database.close()
}
