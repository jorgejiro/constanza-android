package com.jjrapps.constanza.tracking

import com.jjrapps.constanza.core.data.mapper.toDomain
import com.jjrapps.constanza.habit.HabitDaos
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * day-review, slice B (day-review-notification): the one-shot twin of the per-habit join
 * [TodayViewModel.uiState] assembles reactively for the Today screen. A `CoroutineWorker`
 * ([com.jjrapps.constanza.scheduling.DayReviewFireWorker]) has no `ViewModel` and no `combine` chain to
 * attach to — it runs once for one date and is gone — so this class re-reads the exact same
 * [HabitDaos] bundle [com.jjrapps.constanza.habit.HabitRepository] already reads, through one-shot
 * suspend calls in the same style [com.jjrapps.constanza.scheduling.OccurrencePlanner] and
 * [com.jjrapps.constanza.portability.BackupExporter] already use for one-shot work
 * ([com.jjrapps.constanza.core.data.dao.HabitDao.findAllSnapshot]), and joins them through the exact
 * same [buildTodayHabitRow] the screen uses — never re-deriving the day-status/slot-answered logic
 * that function already owns.
 *
 * Archived habits are filtered out here exactly as [TodayViewModel.uiState] filters them
 * (`habits.filterNot { it.archived }`), for the same reason: an archived habit is never due and
 * must never contribute to [countHabitsWithUnansweredSlots]'s count.
 *
 * [rowsFor] otherwise mirrors [TodayViewModel.uiState]'s per-habit loop verbatim — one
 * `findScheduleFor`-equivalent and one `findSlotsFor`-equivalent read per habit — which is the same
 * N+1 query shape that screen already accepts for a UI-sized habit list; this is a once-a-night
 * background read, not a hot path, so the same shape is not worth optimizing away here either.
 */
class DayHabitRowsAssembler @Inject constructor(
    private val daos: HabitDaos,
) {
    suspend fun rowsFor(date: LocalDate): List<TodayHabitRow> {
        // .first(), not a dedicated suspend query: EntryDao was already at detekt's TooManyFunctions
        // interface threshold, and Room's Flow always emits the current result immediately on
        // collection (then again on invalidation), so this is a correct one-shot read of
        // observeByDate — see that function's own KDoc.
        val entries = daos.entryDao.observeByDate(date.toString()).first()
        val unresolved = daos.reminderOccurrenceDao.findUnresolved()
        val snapshot = TodaySnapshot(entriesToday = entries, unresolvedOccurrences = unresolved, today = date)
        return daos.habitDao.findAllSnapshot()
            .filterNot { it.archived }
            .mapNotNull { entity ->
                val habit = entity.toDomain()
                val schedule = daos.scheduleDao.findByHabitId(habit.id)?.toDomain() ?: return@mapNotNull null
                val slots = daos.reminderSlotDao.findByHabitId(habit.id).map { it.toDomain() }
                buildTodayHabitRow(habit, schedule, slots, snapshot)
            }
    }
}
