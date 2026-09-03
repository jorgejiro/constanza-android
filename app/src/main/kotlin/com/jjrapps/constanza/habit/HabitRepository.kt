package com.jjrapps.constanza.habit

import androidx.room.withTransaction
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.dao.HabitDao
import com.jjrapps.constanza.core.data.dao.ReminderOccurrenceDao
import com.jjrapps.constanza.core.data.dao.ReminderSlotDao
import com.jjrapps.constanza.core.data.dao.ScheduleDao
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.data.mapper.toDomain
import com.jjrapps.constanza.core.data.mapper.toEntity
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.scheduling.AlarmScheduler
import com.jjrapps.constanza.scheduling.OccurrencePlanner
import com.jjrapps.constanza.scheduling.ScheduleEditor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Bundles the five DAOs [HabitRepository] needs, keeping its constructor under detekt's
 *  `LongParameterList` threshold — same reasoning as `scheduling.SchedulingDaos`.
 *  [reminderOccurrenceDao] (task 2.2, habit-management: Habit Deletion) is [delete]'s only
 *  caller so far — snapshotting armed occurrence ids before the cascade removes them. */
data class HabitDaos @Inject constructor(
    val habitDao: HabitDao,
    val scheduleDao: ScheduleDao,
    val reminderSlotDao: ReminderSlotDao,
    val entryDao: EntryDao,
    val reminderOccurrenceDao: ReminderOccurrenceDao,
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
 * [create]/[update]'s `slots` parameter is task 6a.1's `TIMES_PER_DAY` slot editor surface, and
 * (task 6a.8) the single reminder time the other five kinds also round-trip through the same list,
 * capped at one entry. [syncSlots] runs BEFORE [ScheduleEditor.updateSchedule] rather than after —
 * that ordering matters, not just style: `updateSchedule` calls [OccurrencePlanner.replanAll]
 * synchronously inside its own (composed) transaction, and `replanAll` reads `reminder_slots`
 * directly off the database. Syncing the slots first means that read sees the just-written rows in
 * the same write; syncing them after (this method's original order) left a brand-new habit's first
 * replan seeing zero slots regardless of what [slots] contained, arming nothing until some later,
 * unrelated write happened to trigger a second replan. A habit switching away from `TIMES_PER_DAY`
 * naturally arrives here with an empty list, which correctly tears down its now-orphaned slots
 * either way, since teardown order does not depend on replan visibility.
 *
 * [delete] (task 2.3, habit-management: Habit Deletion, design.md D1) follows
 * `BackupImporter.replaceAll`'s ordering, not [setArchived]'s: snapshot armed occurrence ids,
 * cascade the habit row inside a transaction, cancel those alarms after commit. No
 * [OccurrencePlanner.replanAll] — no other habit's plan is affected, and by the time a replan
 * could run, the cascade has already removed the occurrence rows [OccurrencePlanner.cancelAllFor]
 * would need to read.
 */
class HabitRepository @Inject constructor(
    private val daos: HabitDaos,
    private val database: AppDatabase,
    private val scheduleEditor: ScheduleEditor,
    private val occurrencePlanner: OccurrencePlanner,
    private val timeProvider: TimeProvider,
    private val alarmScheduler: AlarmScheduler,
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
            syncSlots(id, slots)
            scheduleEditor.updateSchedule(id, schedule)
            id
        }

    /** habit-management: Habit Editing / Editing the schedule reschedules reminders. */
    suspend fun update(habit: Habit, schedule: Schedule, slots: List<ReminderSlot> = emptyList()) {
        database.withTransaction {
            daos.habitDao.update(habit.toEntity())
            syncSlots(habit.id, slots)
            scheduleEditor.updateSchedule(habit.id, schedule)
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

    /** habit-management: Habit Deletion (design.md D1). Irreversible: the habit, its schedule, its
     *  reminder slots, its entries, and its reminder occurrences are all gone once this returns —
     *  the four `ForeignKey.CASCADE` declarations on [HabitEntity]'s children do the removal, this
     *  method only orders the alarm cancellation around it correctly. See design.md D1 for why
     *  cancellation runs after the transaction commits rather than inside it or through
     *  [OccurrencePlanner.replanAll]. */
    suspend fun delete(habitId: Long) {
        val armedIds = daos.reminderOccurrenceDao.findByHabitId(habitId).map { it.id }
        database.withTransaction { daos.habitDao.deleteById(habitId) }
        armedIds.forEach { alarmScheduler.cancel(it) }
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
