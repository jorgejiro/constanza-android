package com.jjrapps.constanza.scheduling

import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.data.mapper.toDomain
import com.jjrapps.constanza.core.di.ReconcilePeriodHours
import com.jjrapps.constanza.core.di.ResolveDeadlineHours
import com.jjrapps.constanza.domain.dueOn
import com.jjrapps.constanza.domain.model.Due
import com.jjrapps.constanza.domain.model.PeriodProgress
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

private const val SECONDS_PER_HOUR = 3600L
private const val ENTRY_STATUS_MISSED = "MISSED"
private const val ENTRY_SOURCE_SWEEP = "SWEEP"
private const val STATE_ARMED = "ARMED"
private const val STATE_SNOOZED = "SNOOZED"
private const val STATE_RESOLVED = "RESOLVED"
private const val STATE_ABANDONED = "ABANDONED"

/** design.md D8: [dueOn]'s `Required`/`NotDue` branches never read [PeriodProgress] — only
 *  `NTimesPerWeek`'s `Candidate` branch does, and D8 excludes that kind regardless. Same rationale
 *  as [OccurrencePlanner]'s `ALWAYS_ZERO_PROGRESS`. */
private val ZERO_PROGRESS = PeriodProgress(completedInWeek = 0, completedInMonth = 0)

/**
 * design.md D3/D8/§9.2: work unit 4b's correctness core, shared by [ReconcileWorker] and
 * [MidnightSweepWorker] so the same idempotent logic runs from all three of §9.2's redundant
 * triggers. Every write below is a `(habitId, date, slotId)`-keyed upsert or a same-row state
 * transition, so running it more than once for the same occurrence is always safe.
 */
class OccurrenceResolver @Inject constructor(
    private val daos: SchedulingDaos,
    private val entryDao: EntryDao,
    private val alarmScheduler: AlarmScheduler,
    @ReconcilePeriodHours private val reconcilePeriodHours: Long,
    @ResolveDeadlineHours private val resolveDeadlineHours: Long,
) {
    /**
     * design.md §5.5/§11 (reminder-delivery: Missed-Reminder Sweep) and D3's abandonment layers 2-3
     * (habit-entry-tracking: Abandoned Snooze Resolution): the hard `resolveDeadline` wins first and
     * force-resolves regardless of state; otherwise a `SNOOZED` chain past grace force-resolves;
     * otherwise an `ARMED` occurrence whose alarm should already have fired is re-armed for now;
     * otherwise an `ARMED` occurrence still in the future is re-armed at its own instant.
     *
     * That last branch is what makes §5.5's "occurrence state is persisted, so 'which alarms should
     * exist' is always recomputable from the database — recovery after any platform-initiated
     * cancellation is a query, not a guess" true rather than aspirational (§13.4 finding 3, task
     * G.5). Revoking `SCHEDULE_EXACT_ALARM` makes the platform cancel every alarm this app owns and
     * stop its process, and `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` is documented as
     * firing on **grant only**, so nothing tells the app it happened. Without this branch every
     * future occurrence stayed `ARMED` with no alarm behind it and was delivered only once the
     * branch above noticed it was overdue — late, not lost, but late every day until some other
     * trigger happened to re-plan.
     *
     * **The condition is deliberately `ARMED` AND future, and must stay exactly that.** design.md
     * §8.2: `occurrence.id` IS the `PendingIntent` request code, shared by the occurrence's own
     * alarm and its snooze alarm, so re-arming a `SNOOZED` row at its original
     * `scheduledAtEpochMs` would overwrite the pending snooze and fire the reminder at the wrong
     * time. A `FIRED` row already reached the user and is waiting for an answer, so re-arming it
     * would post the same reminder twice. `findUnresolved()` returns all three states; exactly one
     * of them may be re-armed.
     */
    suspend fun reconcile(now: Instant) {
        for (occ in daos.reminderOccurrenceDao.findUnresolved()) {
            val scheduledAt = Instant.ofEpochMilli(occ.scheduledAtEpochMs)
            val hardDeadline = scheduledAt.plusSeconds(resolveDeadlineHours * SECONDS_PER_HOUR)
            when {
                !hardDeadline.isAfter(now) -> forceResolve(occ, now)
                isGraceExpired(occ, now) -> forceResolve(occ, now)
                occ.state == STATE_ARMED && scheduledAt.isBefore(now) -> reArm(occ, now.toEpochMilli())
                occ.state == STATE_ARMED -> reArm(occ, occ.scheduledAtEpochMs)
                else -> Unit
            }
        }
    }

    /**
     * **THE ONE RULE this class exists to enforce** (design.md D3/D8, habit-entry-tracking:
     * Midnight Transition): a dated `MISSED` row is written only where `dueOn(...) == Required`
     * AND NOT `(state = SNOOZED AND snoozeUntil > now)`. A live snooze is skipped — no row, no
     * state change (design.md §8.1). Everything else past its date, including a kind that never
     * returns `Required` (`N_TIMES_PER_WEEK`, D8), is marked `RESOLVED` so it stops being rescanned.
     */
    suspend fun sweepMidnight(today: LocalDate, now: Instant) {
        for (occ in daos.reminderOccurrenceDao.findUnresolved()) {
            val scheduledDate = LocalDate.parse(occ.scheduledDate)
            if (shouldSkipSweep(occ, scheduledDate, today, now)) continue
            if (carriesDatedObligation(occ, scheduledDate)) writeMissed(occ, now)
            daos.reminderOccurrenceDao.upsert(occ.copy(state = STATE_RESOLVED))
        }
    }

    /** Not yet past its own local midnight, or a live snooze (D3's rule). */
    private fun shouldSkipSweep(
        occ: ReminderOccurrenceEntity,
        scheduledDate: LocalDate,
        today: LocalDate,
        now: Instant,
    ): Boolean = !scheduledDate.isBefore(today) || isLiveSnooze(occ, now)

    private fun isLiveSnooze(occ: ReminderOccurrenceEntity, now: Instant): Boolean =
        occ.state == STATE_SNOOZED && occ.snoozeUntilEpochMs != null && occ.snoozeUntilEpochMs > now.toEpochMilli()

    private fun isGraceExpired(occ: ReminderOccurrenceEntity, now: Instant): Boolean {
        if (occ.state != STATE_SNOOZED || occ.snoozeUntilEpochMs == null) return false
        val grace = now.minusSeconds(reconcilePeriodHours * SECONDS_PER_HOUR)
        return Instant.ofEpochMilli(occ.snoozeUntilEpochMs).isBefore(grace)
    }

    /** Arms [occ]'s alarm for [atEpochMilli] and persists the mode it actually got. Recording the
     *  degrade is the point: after an exact-alarm revoke the row would otherwise keep claiming
     *  `exact = 1` for an alarm that is now an inexact window (design.md §11, §13.4 finding 3). */
    private suspend fun reArm(occ: ReminderOccurrenceEntity, atEpochMilli: Long) {
        val exact = alarmScheduler.schedule(occ.id, atEpochMilli)
        daos.reminderOccurrenceDao.updateExact(occ.id, exact)
    }

    private suspend fun forceResolve(occ: ReminderOccurrenceEntity, now: Instant) {
        alarmScheduler.cancel(occ.id)
        if (carriesDatedObligation(occ, LocalDate.parse(occ.scheduledDate))) writeMissed(occ, now)
        daos.reminderOccurrenceDao.upsert(occ.copy(state = STATE_ABANDONED))
    }

    /**
     * design.md D8: only a determinate per-date obligation may carry a dated `MISSED`.
     * `N_TIMES_PER_WEEK` returns [Due.Candidate], never [Due.Required], because its unit of
     * obligation is the week — a dated row there would fabricate up to seven failures out of one
     * unmet weekly quota and corrupt compliance.
     *
     * Deliberately shared by BOTH write paths. The midnight sweep applied this gate and
     * abandonment did not, so an `N_TIMES_PER_WEEK` occurrence that was snoozed and then abandoned
     * still produced the phantom failure D8 exists to prevent — the same defect arriving by the
     * other door. One gate with two callers makes it impossible to fix one and forget the other.
     */
    private suspend fun carriesDatedObligation(
        occ: ReminderOccurrenceEntity,
        scheduledDate: LocalDate,
    ): Boolean {
        val schedule = daos.scheduleDao.findByHabitId(occ.habitId)?.toDomain() ?: return false
        return dueOn(schedule, scheduledDate, ZERO_PROGRESS) == Due.Required
    }

    private suspend fun writeMissed(occ: ReminderOccurrenceEntity, now: Instant) {
        entryDao.upsert(
            EntryEntity(
                habitId = occ.habitId,
                date = occ.scheduledDate,
                slotId = occ.slotId,
                status = ENTRY_STATUS_MISSED,
                value = null,
                answeredAt = now.toString(),
                source = ENTRY_SOURCE_SWEEP,
            ),
        )
    }
}
