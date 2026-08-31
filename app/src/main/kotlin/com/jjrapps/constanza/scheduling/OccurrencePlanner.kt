package com.jjrapps.constanza.scheduling

import com.jjrapps.constanza.core.data.dao.HabitDao
import com.jjrapps.constanza.core.data.dao.ReminderOccurrenceDao
import com.jjrapps.constanza.core.data.dao.ReminderSlotDao
import com.jjrapps.constanza.core.data.dao.ScheduleDao
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.data.mapper.toDomain
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

/** design.md D3/§9.1: `resolveDeadline = scheduledAt + 24h`. Written here because the column is
 *  `NOT NULL`; the deadline is *consumed* by work unit 4b's `ReconcileWorker`/`MidnightSweepWorker`
 *  (task 4b.3), not by this unit. */
private const val RESOLVE_DEADLINE_HOURS = 24L
private const val SECONDS_PER_HOUR = 3600L
private const val STATE_ARMED = "ARMED"

/**
 * [dueOn]'s six branches either ignore [PeriodProgress] entirely, or — for `NTimesPerWeek` — always
 * return `Due.Candidate` regardless of its value (design D7/OA-3: the alarm is armed
 * UNCONDITIONALLY every day; quota suppression happens at fire time, in work unit 5, not here). A
 * constant zero is passed rather than reading real entries to compute it, which would be a needless
 * query on every replan for a value [dueOn] never actually branches on for the plan/skip decision.
 */
private val ALWAYS_ZERO_PROGRESS = PeriodProgress(completedInWeek = 0, completedInMonth = 0)

/**
 * design.md D4/§9.1/§9.3: the single idempotent entry point every one of the five reschedule
 * triggers (task 4a.4, [ExactAlarmPermissionReceiver]) and the schedule-edit path
 * ([ScheduleEditor], task 4a.5) converge on. Reads `habits`/`schedules`/`reminder_slots`, upserts
 * `reminder_occurrences` — the scheduling source of truth (D4) — and arms/cancels the matching
 * [AlarmScheduler] alarm. Uses [dueOn] as the ONLY due-authority; see [ALWAYS_ZERO_PROGRESS] for
 * why no second, entries-based due-check is written here.
 *
 * Deliberately does not open its own `AppDatabase.withTransaction`: Room's `withTransaction`
 * composes when a caller (task 4a.5's [ScheduleEditor]) already holds one, and the
 * broadcast-triggered callers need no cross-row atomicity beyond what each upsert already
 * provides on its own.
 */
class OccurrencePlanner @Inject constructor(
    private val habitDao: HabitDao,
    private val scheduleDao: ScheduleDao,
    private val reminderSlotDao: ReminderSlotDao,
    private val reminderOccurrenceDao: ReminderOccurrenceDao,
    private val alarmScheduler: AlarmScheduler,
    private val timeProvider: TimeProvider,
) {
    suspend fun replanAll() {
        val today = timeProvider.today()
        val zone = timeProvider.zone()
        val horizonEnd = today.plusDays(HORIZON_DAYS)
        for (habit in habitDao.findAllSnapshot()) {
            if (habit.archived) {
                cancelAllFor(habit.id)
                continue
            }
            val schedule = scheduleDao.findByHabitId(habit.id)?.toDomain()
            val enabledSlots = reminderSlotDao.findByHabitId(habit.id).filter { it.enabled }
            cancelStaleArmedOccurrences(habit.id, enabledSlots.map { it.id }.toSet())
            if (schedule == null || enabledSlots.isEmpty()) continue // no reminder time set: no occurrence planned (D7/OA-3)
            for (slot in enabledSlots) {
                planSlot(habit.id, slot.id, slot.minuteOfDay, schedule, today, horizonEnd, zone)
            }
        }
    }

    private suspend fun planSlot(
        habitId: Long,
        slotId: Long,
        minuteOfDay: Int,
        schedule: Schedule,
        today: LocalDate,
        horizonEnd: LocalDate,
        zone: ZoneId,
    ) {
        var date = today
        while (!date.isAfter(horizonEnd)) {
            planDateIfDue(habitId, slotId, minuteOfDay, schedule, date, zone)
            date = date.plusDays(1)
        }
        var beyond = horizonEnd.plusDays(1)
        val cutoff = horizonEnd.plusDays(LOOKAHEAD_CAP_DAYS)
        while (!beyond.isAfter(cutoff)) {
            if (planDateIfDue(habitId, slotId, minuteOfDay, schedule, beyond, zone)) break
            beyond = beyond.plusDays(1)
        }
    }

    /** Returns whether [date] is due for [schedule], regardless of whether a new row was written —
     *  the beyond-horizon search above uses this to know when to stop looking. */
    private suspend fun planDateIfDue(
        habitId: Long,
        slotId: Long,
        minuteOfDay: Int,
        schedule: Schedule,
        date: LocalDate,
        zone: ZoneId,
    ): Boolean {
        if (dueOn(schedule, date, ALWAYS_ZERO_PROGRESS) == Due.NotDue) return false
        val dateText = date.toString()
        val existing = reminderOccurrenceDao.findByHabitSlotDate(habitId, slotId, dateText)
        if (existing != null && existing.state != STATE_ARMED) return true // in-flight/resolved: leave it alone
        val scheduledAt = resolveOccurrenceInstant(date, minuteOfDay, zone)
        val entity = (existing ?: emptyArmedOccurrence(habitId, slotId, dateText)).copy(
            scheduledAtEpochMs = scheduledAt.toEpochMilli(),
            state = STATE_ARMED,
            resolveDeadlineMs = scheduledAt.plusSeconds(RESOLVE_DEADLINE_HOURS * SECONDS_PER_HOUR).toEpochMilli(),
        )
        val id = reminderOccurrenceDao.upsert(entity)
        val exact = alarmScheduler.schedule(id, scheduledAt.toEpochMilli())
        reminderOccurrenceDao.updateExact(id, exact)
        return true
    }

    private suspend fun cancelAllFor(habitId: Long) {
        reminderOccurrenceDao.findByHabitId(habitId).forEach { alarmScheduler.cancel(it.id) }
        reminderOccurrenceDao.deleteByHabitId(habitId)
    }

    /** Cancels an armed occurrence whose slot is no longer enabled or no longer exists — the
     *  "cancels what should not exist" half of §9.3's replan contract. */
    private suspend fun cancelStaleArmedOccurrences(habitId: Long, enabledSlotIds: Set<Long>) {
        reminderOccurrenceDao.findByHabitId(habitId)
            .filter { it.state == STATE_ARMED && it.slotId !in enabledSlotIds }
            .forEach {
                alarmScheduler.cancel(it.id)
                reminderOccurrenceDao.deleteById(it.id)
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
