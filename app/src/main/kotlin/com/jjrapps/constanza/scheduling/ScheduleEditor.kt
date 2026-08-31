package com.jjrapps.constanza.scheduling

import androidx.room.withTransaction
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.dao.ScheduleDao
import com.jjrapps.constanza.core.data.mapper.toEntity
import com.jjrapps.constanza.domain.model.Schedule
import javax.inject.Inject

/**
 * Task 4a.5: wires an in-app schedule edit to [OccurrencePlanner.replanAll] inside the SAME Room
 * transaction as the write (habit-management: Editing the schedule reschedules reminders).
 * Replan is part of the edit, not a follow-up call site (design.md §9.3) — a crash between a
 * standalone write and a separate replan call would leave stale alarms armed against the old
 * schedule. No dedicated automated test exists for this wiring (tasks.md carries no `[Unit]`/
 * `[Instrumented]` tag on 4a.5); it is exercised by unit 6a's schedule-edit UI once that lands.
 */
class ScheduleEditor @Inject constructor(
    private val database: AppDatabase,
    private val scheduleDao: ScheduleDao,
    private val occurrencePlanner: OccurrencePlanner,
) {
    suspend fun updateSchedule(habitId: Long, schedule: Schedule) {
        database.withTransaction {
            scheduleDao.upsert(schedule.toEntity(habitId))
            occurrencePlanner.replanAll()
        }
    }
}
