package com.jjrapps.constanza.domain

import com.jjrapps.constanza.domain.model.Due
import com.jjrapps.constanza.domain.model.Entry
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.PeriodProgress
import com.jjrapps.constanza.domain.model.Schedule
import java.time.LocalDate
import kotlin.math.roundToInt

private const val DAYS_PER_WEEK = 7
private const val PERCENT = 100

/** One day of a habit's week in the recap: answered yes, answered no, or nothing to show. */
enum class RecapMark { DONE, MISSED, NONE }

/** What the recap needs about one habit: its schedule and its entries (any range; the week is
 *  cut out here). */
data class HabitWeekInput(val habitId: Long, val schedule: Schedule, val entries: List<Entry>)

/** One habit's week: [marks] holds seven days from the week's first day, [done] of [total]. */
data class HabitWeekRecap(val habitId: Long, val marks: List<RecapMark>, val done: Int, val total: Int)

data class WeeklyRecap(val weekStart: LocalDate, val habits: List<HabitWeekRecap>) {
    val done: Int = habits.sumOf { it.done }
    val total: Int = habits.sumOf { it.total }
    val hasAnythingToCount: Boolean get() = total > 0
    val percent: Int = if (total == 0) 0 else (done * PERCENT.toDouble() / total).roundToInt()
}

/**
 * weekly-recap: one seven-day week counted exactly as [ComplianceCalculator] counts a window, so the
 * Monday recap and the Progress screen never disagree about what "done" means. Per due day the
 * entries collapse through [resolvedStatus]; COMPLETED is done, MISSED is not done, SKIPPED and
 * UNKNOWN count on neither side. An [Schedule.NTimesPerWeek] habit has no per-day obligation, so its
 * total is its quota and its done is `min(completed, times)`, as in the calculator's weekly ratio.
 * A habit whose week has nothing to count (not due, or nothing answered) is left out of the recap.
 */
object WeeklyRecapCalculator {
    fun recap(weekStart: LocalDate, inputs: List<HabitWeekInput>): WeeklyRecap = WeeklyRecap(
        weekStart = weekStart,
        habits = inputs.map { habitWeek(it.schedule, it.entries, weekStart, it.habitId) }.filter { it.total > 0 },
    )

    fun habitWeek(schedule: Schedule, entries: List<Entry>, weekStart: LocalDate, habitId: Long = 0L): HabitWeekRecap {
        val days = (0 until DAYS_PER_WEEK).map { weekStart.plusDays(it.toLong()) }
        val noProgress = PeriodProgress(completedInWeek = 0, completedInMonth = 0)
        val marks = days.map { date ->
            if (dueOn(schedule, date, noProgress) == Due.NotDue) {
                RecapMark.NONE
            } else {
                when (resolvedStatus(date, entries)) {
                    EntryStatus.COMPLETED -> RecapMark.DONE
                    EntryStatus.MISSED -> RecapMark.MISSED
                    EntryStatus.SKIPPED, EntryStatus.UNKNOWN -> RecapMark.NONE
                }
            }
        }
        val completed = marks.count { it == RecapMark.DONE }
        return if (schedule is Schedule.NTimesPerWeek) {
            HabitWeekRecap(habitId, marks, done = minOf(completed, schedule.times), total = schedule.times)
        } else {
            HabitWeekRecap(habitId, marks, done = completed, total = completed + marks.count { it == RecapMark.MISSED })
        }
    }
}
