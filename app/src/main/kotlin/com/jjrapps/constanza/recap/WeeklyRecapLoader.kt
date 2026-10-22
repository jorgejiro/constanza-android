package com.jjrapps.constanza.recap

import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.mapper.toDomain
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.domain.HabitWeekInput
import com.jjrapps.constanza.domain.RecapMark
import com.jjrapps.constanza.domain.WeeklyRecapCalculator
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.habit.HabitRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private const val LAST_DAY_OFFSET = 6L

/** One habit's line on the recap screen: [marks] runs Monday to Sunday. */
data class WeeklyRecapRow(
    val habitId: Long,
    val name: String,
    val colorArgb: Int,
    val marks: List<RecapMark>,
    val done: Int,
    val total: Int,
)

/** A whole Monday–Sunday week, ready to render or to summarise in the notification. */
data class LoadedWeeklyRecap(
    val weekStart: LocalDate,
    val rows: List<WeeklyRecapRow>,
    val done: Int,
    val total: Int,
    val percent: Int,
)

/**
 * weekly-recap: reads one week for every habit that existed during it and hands the counting to
 * [WeeklyRecapCalculator]. The archive boundary is applied here, as [com.jjrapps.constanza.progress.ProgressViewModel]
 * applies it for Progress: a habit archived before the week, or created after it, is left out, and
 * entries on or after a habit's archive date are dropped before the calculator sees them.
 */
class WeeklyRecapLoader @Inject constructor(
    private val habitRepository: HabitRepository,
    private val entryDao: EntryDao,
    private val timeProvider: TimeProvider,
) {
    suspend fun load(weekStart: LocalDate): LoadedWeeklyRecap {
        val weekEnd = weekStart.plusDays(LAST_DAY_OFFSET)
        val habits = habitRepository.observeAll().first().filter { it.existedDuring(weekStart, weekEnd) }
        val schedules = habitRepository.schedules.first()
        val inputs = habits.mapNotNull { habit ->
            val schedule = schedules[habit.id]?.schedule ?: return@mapNotNull null
            val archivedAt = habit.archivedAt
            val entries = entryDao.findByHabitIdBetweenDates(habit.id, weekStart.toString(), weekEnd.toString())
                .map { it.toDomain() }
                .filter { archivedAt == null || it.date.isBefore(archivedAt) }
            HabitWeekInput(habit.id, schedule, entries)
        }
        val recap = WeeklyRecapCalculator.recap(weekStart, inputs)
        val byId = habits.associateBy { it.id }
        return LoadedWeeklyRecap(
            weekStart = weekStart,
            rows = recap.habits.map { week ->
                val habit = byId.getValue(week.habitId)
                WeeklyRecapRow(habit.id, habit.name, habit.colorArgb, week.marks, week.done, week.total)
            },
            done = recap.done,
            total = recap.total,
            percent = recap.percent,
        )
    }

    private fun Habit.existedDuring(weekStart: LocalDate, weekEnd: LocalDate): Boolean {
        val createdOn = createdAt.atZone(timeProvider.zone()).toLocalDate()
        val archivedOn = archivedAt
        return !createdOn.isAfter(weekEnd) && (archivedOn == null || archivedOn.isAfter(weekStart))
    }
}
