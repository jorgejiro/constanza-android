package com.jjrapps.constanza.habit

import androidx.room.withTransaction
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.dao.HabitDao
import com.jjrapps.constanza.core.data.dao.ReminderSlotDao
import com.jjrapps.constanza.core.data.dao.ScheduleDao
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.data.mapper.toDomain
import com.jjrapps.constanza.core.data.mapper.toEntity
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.scheduling.OccurrencePlanner
import com.jjrapps.constanza.scheduling.ScheduleEditor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Bundles the four DAOs [HabitRepository] needs, keeping its constructor under detekt's
 *  `LongParameterList` threshold — same reasoning as `scheduling.SchedulingDaos`. */
data class HabitDaos @Inject constructor(
    val habitDao: HabitDao,
    val scheduleDao: ScheduleDao,
    val reminderSlotDao: ReminderSlotDao,
    val entryDao: EntryDao,
)

/**
 * Task 6a.2/6a.3/6a.4 (habit-management: Habit Creation, Habit Editing, Habit Archiving). [create]
 * and [update] delegate the schedule write to [ScheduleEditor.updateSchedule], reusing its
 * existing `replanAll()`-in-transaction wiring (task 4a.5) rather than duplicating it — 6a.3's own
 * text names "the `HabitRepository` transaction that triggers `replanAll()`", but that transaction
 * is [ScheduleEditor]'s; `database.withTransaction` composes when already inside one, so the outer
 * transaction here and [ScheduleEditor]'s inner one collapse into a single write.
 *
 * [setArchived] calls [OccurrencePlanner.replanAll] directly rather than through [ScheduleEditor],
 * since archiving does not change the persisted `Schedule` row. [OccurrencePlanner.replanAll] only
 * ever plans from "today" forward (design.md D4), so un-archiving naturally resumes reminders from
 * now onward without back-filling the archived window (Un-archiving does not back-fill).
 *
 * [deleteSlot] pays D11's cost of dropping `entries.slotId`'s foreign key by hand, inside one
 * transaction; entries under the deleted slot are removed rather than reassigned to the `0`
 * sentinel, which would risk colliding with `UNIQUE(habitId, date, slotId)`.
 *
 * [create]/[update]'s `slots` parameter is task 6a.1's (slice ii-a) `TIMES_PER_DAY` slot editor
 * surface, reconciled by [syncSlots] inside the same transaction as the schedule write — a habit
 * switching away from `TIMES_PER_DAY` naturally arrives here with an empty list, which correctly
 * tears down its now-orphaned slots (habit-scheduling: Reminder Slots for TIMES_PER_DAY, "Every
 * other frequency kind MUST have exactly one configurable reminder time" — that single-slot editor
 * for the other five kinds is NOT built by this slice; see the apply report).
 */
class HabitRepository @Inject constructor(
    private val daos: HabitDaos,
    private val database: AppDatabase,
    private val scheduleEditor: ScheduleEditor,
    private val occurrencePlanner: OccurrencePlanner,
    private val timeProvider: TimeProvider,
) {
    /** habit-management: the habit list, filterable by [Habit.archived] in the presentation layer. */
    fun observeAll(): Flow<List<Habit>> = daos.habitDao.observeAll().map(::toDomainHabits)

    suspend fun findById(habitId: Long): Habit? = daos.habitDao.findById(habitId)?.toDomain()

    suspend fun findScheduleFor(habitId: Long): Schedule? = daos.scheduleDao.findByHabitId(habitId)?.toDomain()

    /** Task 6a.1 (slice ii-a): the persisted [ReminderSlot]s the editor loads back when editing a
     *  `TIMES_PER_DAY` habit. */
    suspend fun findSlotsFor(habitId: Long): List<ReminderSlot> =
        daos.reminderSlotDao.findByHabitId(habitId).map { it.toDomain() }

    /** habit-management: Habit Creation. [habit] carries a sentinel `id = 0`; returns the real id. */
    suspend fun create(habit: Habit, schedule: Schedule, slots: List<ReminderSlot> = emptyList()): Long =
        database.withTransaction {
            val id = daos.habitDao.insert(habit.toEntity())
            scheduleEditor.updateSchedule(id, schedule)
            syncSlots(id, slots)
            id
        }

    /** habit-management: Habit Editing / Editing the schedule reschedules reminders. */
    suspend fun update(habit: Habit, schedule: Schedule, slots: List<ReminderSlot> = emptyList()) {
        database.withTransaction {
            daos.habitDao.update(habit.toEntity())
            scheduleEditor.updateSchedule(habit.id, schedule)
            syncSlots(habit.id, slots)
        }
    }

    /** habit-management: Habit Archiving / Un-archiving does not back-fill. */
    suspend fun setArchived(habitId: Long, archived: Boolean) {
        database.withTransaction {
            val existing = requireNotNull(daos.habitDao.findById(habitId)) { "Habit $habitId not found" }
            daos.habitDao.update(
                existing.copy(
                    archived = archived,
                    archivedAt = if (archived) timeProvider.today().toString() else null,
                ),
            )
            occurrencePlanner.replanAll()
        }
    }

    suspend fun deleteSlot(habitId: Long, slotId: Long) {
        database.withTransaction {
            daos.entryDao.deleteBySlot(habitId, slotId)
            daos.reminderSlotDao.deleteById(slotId)
        }
    }

    /** Reconciles [habitId]'s persisted slots against the editor's [slots]: a slot missing from
     *  [slots] is removed through [deleteSlot] (so its entries drop with it, D11), a slot carrying
     *  the `id = 0` sentinel (same convention as [Habit.id]) is a new, unsaved slot and is
     *  inserted, and every other slot is updated in place. */
    private suspend fun syncSlots(habitId: Long, slots: List<ReminderSlot>) {
        val existingIds = daos.reminderSlotDao.findByHabitId(habitId).map { it.id }.toSet()
        val keptIds = slots.mapNotNull { it.id.takeIf { id -> id != 0L } }.toSet()
        (existingIds - keptIds).forEach { deleteSlot(habitId, it) }
        slots.forEach { slot ->
            val withHabit = slot.copy(habitId = habitId)
            if (slot.id == 0L) {
                daos.reminderSlotDao.insert(withHabit.toEntity())
            } else {
                daos.reminderSlotDao.update(withHabit.toEntity())
            }
        }
    }
}

private fun toDomainHabits(entities: List<HabitEntity>): List<Habit> = entities.map { it.toDomain() }
