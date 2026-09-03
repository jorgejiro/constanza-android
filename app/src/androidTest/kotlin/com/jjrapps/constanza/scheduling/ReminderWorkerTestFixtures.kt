package com.jjrapps.constanza.scheduling

import androidx.room.withTransaction
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.data.entity.ScheduleEntity

/**
 * Shared habit+schedule fixture for `AnswerWorkerTest`/`SnoozeWorkerTest`/[ReminderFireWorkerTest].
 *
 * **One transaction, not two, and that is load-bearing rather than tidy.** Room's
 * `InvalidationTracker` fires per committed write, so two bare inserts publish two states to every
 * live observer: a habit with no schedule, and then a habit with one. `TodayViewModel.uiState`
 * observes `habits` but reads `schedules` imperatively inside its `combine`, so an observer that
 * sees the first state builds no row for the habit — and the `schedules` write that would correct
 * it invalidates a table nothing in that `combine` observes, so no second emission ever comes. The
 * screen stays empty permanently. That is measured, not argued: see
 * `openspec/config.yaml`'s `today-slot-row-compose-test-timeout-flakiness`. Wrapping the writes
 * means the tracker fires once, after commit, and the only state an observer can ever see is the
 * finished habit.
 */
suspend fun AppDatabase.insertHabitWithSchedule(
    kind: String = "DAILY",
    timesPerWeek: Int? = null,
    name: String = "Meditate",
    question: String? = null,
): Long = withTransaction {
    val habitId = habitDao().insert(
        HabitEntity(name = name, question = question, colorArgb = 0, notes = null, archivedAt = null, createdAt = "2026-01-01T00:00:00Z"),
    )
    scheduleDao().upsert(
        ScheduleEntity(
            habitId = habitId, kind = kind, timesPerWeek = timesPerWeek, dayOfWeek = null,
            dayOfMonth = null, intervalDays = null, anchorDate = null, weekStart = 1,
        ),
    )
    habitId
}
