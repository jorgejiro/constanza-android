package com.jjrapps.constanza.habit

import android.content.Context
import androidx.room.Room
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.entity.ReminderSlotEntity
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.scheduling.AlarmScheduler
import com.jjrapps.constanza.scheduling.FakeTimeProvider
import com.jjrapps.constanza.scheduling.OccurrencePlanner
import com.jjrapps.constanza.scheduling.ScheduleEditor
import com.jjrapps.constanza.scheduling.SchedulingDaos
import io.mockk.mockk
import java.time.Instant

private const val RESOLVE_DEADLINE_HOURS = 24L
private const val STATE_ARMED = "ARMED"
private const val MORNING_MINUTE_OF_DAY = 480
private val FIXED_INSTANT: Instant = Instant.parse("2026-09-01T08:00:00Z")

/**
 * Shared wiring for the work unit 6a instrumented tests: a real in-memory Room database and a
 * real [HabitRepository]/[ScheduleEditor]/[OccurrencePlanner] chain, with only [AlarmScheduler]
 * relaxed-mocked (arming a real system alarm is irrelevant to what these scenarios assert).
 */
class HabitRepositoryTestFixture(context: Context) {
    val database: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val timeProvider: TimeProvider = FakeTimeProvider(FIXED_INSTANT)
    val occurrencePlanner: OccurrencePlanner
    val habitRepository: HabitRepository

    init {
        occurrencePlanner = OccurrencePlanner(
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

    suspend fun insertEnabledSlot(habitId: Long, minuteOfDay: Int = MORNING_MINUTE_OF_DAY): Long =
        database.reminderSlotDao().insert(
            ReminderSlotEntity(habitId = habitId, minuteOfDay = minuteOfDay, enabled = true),
        )

    /** The dates [OccurrencePlanner] currently holds armed for [habitId] — how a replan is observed
     *  from outside, since `reminder_occurrences` is the scheduling source of truth (design.md D4). */
    suspend fun armedOccurrenceDates(habitId: Long): List<String> =
        database.reminderOccurrenceDao().findByHabitId(habitId)
            .filter { it.state == STATE_ARMED }
            .map { it.scheduledDate }

    fun close() = database.close()
}

/** A brand-new, unarchived [Habit] carrying the `id = 0` sentinel [HabitRepository.create] expects. */
fun newHabit(name: String = "Read"): Habit = Habit(
    id = 0,
    name = name,
    question = null,
    colorArgb = 0,
    notes = null,
    archived = false,
    archivedAt = null,
    createdAt = FIXED_INSTANT,
    sortOrder = 0,
)
