package com.jjrapps.constanza.scheduling

import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.data.entity.ScheduleEntity

/** Shared habit+schedule fixture for `AnswerWorkerTest`/`SnoozeWorkerTest`/[ReminderFireWorkerTest]. */
suspend fun AppDatabase.insertHabitWithSchedule(
    kind: String = "DAILY",
    timesPerWeek: Int? = null,
    name: String = "Meditate",
    question: String? = null,
): Long {
    val habitId = habitDao().insert(
        HabitEntity(name = name, question = question, colorArgb = 0, notes = null, archivedAt = null, createdAt = "2026-01-01T00:00:00Z"),
    )
    scheduleDao().upsert(
        ScheduleEntity(
            habitId = habitId, kind = kind, timesPerWeek = timesPerWeek, dayOfWeek = null,
            dayOfMonth = null, intervalDays = null, anchorDate = null, weekStart = 1,
        ),
    )
    return habitId
}
