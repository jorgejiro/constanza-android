package com.jjrapps.constanza.scheduling

import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.data.mapper.toDomain
import com.jjrapps.constanza.core.di.ResolveDeadlineHours
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.domain.dueOn
import com.jjrapps.constanza.domain.model.Due
import com.jjrapps.constanza.domain.model.PeriodProgress
import com.jjrapps.constanza.domain.model.Schedule
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** design.md D4: every occurrence within the next 48h, expressed in whole calendar days so "now"
 *  landing at any minute of today still covers a full 48h forward. */
private const val HORIZON_DAYS = 2L

/** Bound on the beyond-horizon single-occurrence search, so a very sparse schedule (a large
 *  `EVERY_N_DAYS` interval) cannot loop unbounded. */
private const val LOOKAHEAD_CAP_DAYS = 400L

private const val SECONDS_PER_HOUR = 3600L
private const val STATE_ARMED = "ARMED"

/**
 * [dueOn]'s six branches either ignore [PeriodProgress] entirely, or — for `NTimesPerWeek` — always
 * return `Due.Candidate` regardless of its value (design D7/OA-3: the alarm is armed
 * UNCONDITIONALLY every day; quota suppression happens at fire time, in work unit 5, not here). A
 * constant zero is passed rather than computing real progress, which would be a needless query on
 * every replan for a value [dueOn] never actually branches on for the plan/skip decision.
 *
 * This is still true, and it is still the ONLY reason entries are absent from the *due* decision.
 * It is no longer a statement that the planner never reads entries at all — see [AnsweredSlots].
 */
private val ALWAYS_ZERO_PROGRESS = PeriodProgress(completedInWeek = 0, completedInMonth = 0)

/**
 * The five values that travel together through the planning recursion: which slot of which habit,
 * on what schedule, resolved in which zone. They are always passed as a unit and never varied
 * independently, so grouping them keeps the planning functions readable — which is what detekt's
 * `LongParameterList` was pointing at.
 */
private data class SlotPlan(
    val habitId: Long,
    val slotId: Long,
    val minuteOfDay: Int,
    val schedule: Schedule,
    val zone: ZoneId,
)

/**
 * One habit's `entries`, read lazily per date and memoised for the length of that habit's plan.
 *
 * [replanAll] runs on every app resume, so the answered-slot guard must not cost one query per
 * (habit x slot x date). A habit's slots all walk the same dates, so the memo collapses them onto
 * one read each, and [planDateIfDue] only ever asks about a date [dueOn] already said was due — it
 * returns before touching any DAO otherwise. That bounds this to at most one
 * `findByHabitAndDate` per habit per due date: three horizon days plus the single beyond-horizon
 * date, regardless of how many slots the habit has or how far the beyond-horizon search walked.
 * The read itself is served by `idx_entries_habit_date`.
 */
private class AnsweredSlots(private val entryDao: EntryDao, private val habitId: Long) {
    private val byDate = mutableMapOf<String, List<EntryEntity>>()

    suspend fun answers(slotId: Long, dateText: String): Boolean {
        val entries = byDate[dateText]
            ?: entryDao.findByHabitAndDate(habitId, dateText).also { byDate[dateText] = it }
        return entries.answerSlot(slotId)
    }
}

/**
 * design.md D4/§9.1/§9.3: the single idempotent entry point every one of the five reschedule
 * triggers (task 4a.4, [ExactAlarmPermissionReceiver]) and the schedule-edit path
 * ([ScheduleEditor], task 4a.5) converge on. Reads `habits`/`schedules`/`reminder_slots`, upserts
 * `reminder_occurrences` — the scheduling source of truth (D4) — and arms/cancels the matching
 * [AlarmScheduler] alarm. Uses [dueOn] as the ONLY due-authority; see [ALWAYS_ZERO_PROGRESS] for
 * why no second, entries-based due-check is written here.
 *
 * **It does read `entries`, and that is a correction, not a drift.** The original design note said
 * the planner never reads them at all. That note was answering one question — "is this date due?"
 * — where [dueOn] is still the sole authority. It silently also answered a second, different
 * question the planner had never been asked: "has the user already settled this slot for this
 * date?". Nothing here could answer that, so a `(habit, date, slot)` carrying a `COMPLETED` entry
 * but no occurrence row was armed as if it were unanswered. The only "leave it alone" gate was
 * `existing != null && existing.state != ARMED`, keyed on the very row an import
 * (`BackupImporter.replaceAll` wipes `reminder_occurrences` by cascade and replans) or an
 * archive/unarchive cycle had just deleted — so the gate always saw `null` and always armed.
 * A past reminder time armed that way fires immediately on `setExactAndAllowWhileIdle`, and the
 * stale `ARMED` row it leaves behind is then swept at midnight into a `MISSED` that REPLACES the
 * restored `COMPLETED` (`entryDao.upsert` is `REPLACE` on `UNIQUE(habitId, date, slotId)`).
 * The notification was the symptom; rewritten history was the defect. Reading entries here is what
 * stops the unresolved row from ever existing, which is what the sweep keys on. See
 * [AnsweredSlots] for the query shape and `AnsweredEntries.kt` for why `MISSED` is excluded.
 *
 * Deliberately does not open its own `AppDatabase.withTransaction`: Room's `withTransaction`
 * composes when a caller (task 4a.5's [ScheduleEditor]) already holds one, and the
 * broadcast-triggered callers need no cross-row atomicity beyond what each upsert already
 * provides on its own.
 */
class OccurrencePlanner @Inject constructor(
    private val daos: SchedulingDaos,
    private val entryDao: EntryDao,
    private val alarmScheduler: AlarmScheduler,
    private val timeProvider: TimeProvider,
    @ResolveDeadlineHours private val resolveDeadlineHours: Long,
) {
    suspend fun replanAll() {
        val today = timeProvider.today()
        val zone = timeProvider.zone()
        val horizonEnd = today.plusDays(HORIZON_DAYS)
        for (habit in daos.habitDao.findAllSnapshot()) {
            planHabit(habit, today, horizonEnd, zone)
        }
    }

    private suspend fun planHabit(
        habit: HabitEntity,
        today: LocalDate,
        horizonEnd: LocalDate,
        zone: ZoneId,
    ) {
        if (habit.archived) {
            cancelAllFor(habit.id)
            return
        }
        val schedule = daos.scheduleDao.findByHabitId(habit.id)?.toDomain()
        val enabledSlots = daos.reminderSlotDao.findByHabitId(habit.id).filter { it.enabled }
        cancelStaleArmedOccurrences(habit.id, enabledSlots.map { it.id }.toSet())
        // No reminder time set means no occurrence is planned (D7/OA-3).
        if (schedule == null || enabledSlots.isEmpty()) return
        val answered = AnsweredSlots(entryDao, habit.id)
        for (slot in enabledSlots) {
            planSlot(SlotPlan(habit.id, slot.id, slot.minuteOfDay, schedule, zone), today, horizonEnd, answered)
        }
    }

    private suspend fun planSlot(
        plan: SlotPlan,
        today: LocalDate,
        horizonEnd: LocalDate,
        answered: AnsweredSlots,
    ) {
        var date = today
        while (!date.isAfter(horizonEnd)) {
            planDateIfDue(plan, date, answered)
            date = date.plusDays(1)
        }
        var beyond = horizonEnd.plusDays(1)
        val cutoff = horizonEnd.plusDays(LOOKAHEAD_CAP_DAYS)
        while (!beyond.isAfter(cutoff)) {
            if (planDateIfDue(plan, beyond, answered)) break
            beyond = beyond.plusDays(1)
        }
    }

    /** Returns whether [date] is due for [schedule], regardless of whether a new row was written —
     *  the beyond-horizon search above uses this to know when to stop looking. An answered slot is
     *  still *due*; it just must not be armed, so the answered-slot guard returns `true` and the
     *  search stops on it exactly as it would on a date it did arm. */
    private suspend fun planDateIfDue(plan: SlotPlan, date: LocalDate, answered: AnsweredSlots): Boolean {
        if (dueOn(plan.schedule, date, ALWAYS_ZERO_PROGRESS) == Due.NotDue) return false
        val dateText = date.toString()
        val existing = daos.reminderOccurrenceDao.findByHabitSlotDate(plan.habitId, plan.slotId, dateText)
        if (!shouldArm(plan, dateText, existing, answered)) return true
        val scheduledAt = resolveOccurrenceInstant(date, plan.minuteOfDay, plan.zone)
        val entity = (existing ?: emptyArmedOccurrence(plan.habitId, plan.slotId, dateText)).copy(
            scheduledAtEpochMs = scheduledAt.toEpochMilli(),
            state = STATE_ARMED,
            resolveDeadlineMs = scheduledAt.plusSeconds(resolveDeadlineHours * SECONDS_PER_HOUR).toEpochMilli(),
        )
        val id = daos.reminderOccurrenceDao.upsert(entity)
        val exact = alarmScheduler.schedule(id, scheduledAt.toEpochMilli())
        daos.reminderOccurrenceDao.updateExact(id, exact)
        return true
    }

    /**
     * The two reasons a due date is deliberately left unarmed, kept together because they are the
     * same judgement made from two different starting points.
     *
     * An existing row that is not `ARMED` is already in flight or resolved: leave it alone. An
     * existing row that IS `ARMED` must be updated in place and keep its identity — design.md §8.2
     * makes `occurrence.id` the `PendingIntent` request code, so dropping and recreating it would
     * orphan the alarm it already owns. That is why the answered-slot check applies only when
     * there is no row at all: nothing can answer a slot without resolving its occurrence in the
     * same transaction ([com.jjrapps.constanza.tracking.EntryWriter]), so an `ARMED` row sitting
     * beside an answered entry means the answer was written with no occurrence to resolve — the
     * import and archive/unarchive doors, where there is no row to preserve anyway.
     * [ReminderFireHandler] is the net for the remaining case.
     */
    private suspend fun shouldArm(
        plan: SlotPlan,
        dateText: String,
        existing: ReminderOccurrenceEntity?,
        answered: AnsweredSlots,
    ): Boolean =
        if (existing != null) existing.state == STATE_ARMED else !answered.answers(plan.slotId, dateText)

    private suspend fun cancelAllFor(habitId: Long) {
        daos.reminderOccurrenceDao.findByHabitId(habitId).forEach { alarmScheduler.cancel(it.id) }
        daos.reminderOccurrenceDao.deleteByHabitId(habitId)
    }

    /** Cancels an armed occurrence whose slot is no longer enabled or no longer exists — the
     *  "cancels what should not exist" half of §9.3's replan contract. */
    private suspend fun cancelStaleArmedOccurrences(habitId: Long, enabledSlotIds: Set<Long>) {
        daos.reminderOccurrenceDao.findByHabitId(habitId)
            .filter { it.state == STATE_ARMED && it.slotId !in enabledSlotIds }
            .forEach {
                alarmScheduler.cancel(it.id)
                daos.reminderOccurrenceDao.deleteById(it.id)
            }
    }

    private fun emptyArmedOccurrence(habitId: Long, slotId: Long, dateText: String) = ReminderOccurrenceEntity(
        habitId = habitId,
        slotId = slotId,
        scheduledDate = dateText,
        scheduledAtEpochMs = 0,
        state = STATE_ARMED,
        snoozeUntilEpochMs = null,
        snoozeCount = 0,
        notifiedAtEpochMs = null,
        resolveDeadlineMs = 0,
    )
}
