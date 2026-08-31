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
     * otherwise an `ARMED` occurrence whose alarm should already have fired is re-armed for now.
     */
    suspend fun reconcile(now: Instant) {
        for (occ in daos.reminderOccurrenceDao.findUnresolved()) {
            val scheduledAt = Instant.ofEpochMilli(occ.scheduledAtEpochMs)
            val hardDeadline = scheduledAt.plusSeconds(resolveDeadlineHours * SECONDS_PER_HOUR)
            when {
                !hardDeadline.isAfter(now) -> forceResolve(occ, now)
                isGraceExpired(occ, now) -> forceResolve(occ, now)
                occ.state == STATE_ARMED && scheduledAt.isBefore(now) ->
                    alarmScheduler.schedule(occ.id, now.toEpochMilli())
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
