package com.jjrapps.constanza.tracking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.habit.HabitRepositoryTestFixture
import com.jjrapps.constanza.scheduling.AlarmScheduler
import com.jjrapps.constanza.scheduling.OccurrenceResolver
import com.jjrapps.constanza.scheduling.SchedulingDaos
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val MORNING_MINUTE = 8 * 60
private const val RECONCILE_PERIOD_HOURS = 1L
private const val RESOLVE_DEADLINE_HOURS = 24L
private const val RESOLVE_DEADLINE_MS = RESOLVE_DEADLINE_HOURS * 3600 * 1000L
private const val STATE_ARMED = "ARMED"
private const val STATE_FIRED = "FIRED"
private const val STATE_RESOLVED = "RESOLVED"
private const val ENTRY_STATUS_MISSED = "MISSED"
private const val ENTRY_SOURCE_SWEEP = "SWEEP"

/**
 * today-clear-answer: **THE ONE RULE this feature must not break**, proved against a real
 * [OccurrenceResolver] rather than a stand-in — the owner's own words: "si ahora te llega una
 * notificación y no le das que sí o no, se queda sin responder y al final del día eso se cuenta
 * como un no". An occurrence answered and then cleared through [EntryWriter.clearAnswer] must
 * still reach [OccurrenceResolver.sweepMidnight] the following day and become `MISSED` — exactly
 * as if it had never been answered at all.
 *
 * The trap this class exists to catch: [resolveOccurrenceAndWrite]'s "answer" leaves an occurrence
 * `RESOLVED`, which [ReminderOccurrenceDao.findUnresolved] permanently excludes. A clear that only
 * deleted the `Entry` and left the occurrence `RESOLVED` would make the sweep never see the slot
 * again — [aClearedSlotIsFoundAndMissedByTheFollowingMidnightSweep] is the test that would fail if
 * [EntryWriter.clearAnswer] regressed to that shape.
 *
 * [aClearedSlotIsNeitherReArmedNorForceResolvedBeforeMidnight] proves the other half of the brief's
 * investigation: reopening to `FIRED` (never `ARMED`) is what keeps
 * [OccurrenceResolver.reconcile]'s hourly pass from treating a same-day clear as a lost alarm and
 * reposting the reminder — [AlarmScheduler.schedule] is verified never called.
 */
@RunWith(AndroidJUnit4::class)
class ClearAnswerSweepTest {

    private lateinit var fixture: HabitRepositoryTestFixture
    private lateinit var entryWriter: EntryWriter

    @Before
    fun setUp() {
        fixture = HabitRepositoryTestFixture(ApplicationProvider.getApplicationContext<Context>())
        entryWriter = fixture.entryWriter()
    }

    @After
    fun tearDown() = fixture.close()

    /** A real [OccurrenceResolver] over this fixture's own database — the same construction
     *  [MidnightSweepWorkerTest] uses, built fresh per call since [resolver] itself carries no
     *  state worth reusing. */
    private fun resolver(alarmScheduler: AlarmScheduler = mockk(relaxed = true)): OccurrenceResolver {
        val daos = SchedulingDaos(
            fixture.database.habitDao(),
            fixture.database.scheduleDao(),
            fixture.database.reminderSlotDao(),
            fixture.database.reminderOccurrenceDao(),
        )
        return OccurrenceResolver(
            daos, fixture.database.entryDao(), alarmScheduler, RECONCILE_PERIOD_HOURS, RESOLVE_DEADLINE_HOURS,
        )
    }

    /** A daily habit (always `dueOn(...) == Required`), one enabled morning slot, and one `ARMED`
     *  occurrence scheduled for it today — the shape an in-app answer resolves before this test
     *  clears it again. */
    private suspend fun armTodayOccurrence(): Triple<Long, Long, Long> {
        val seeded = fixture.seedHabitWithEnabledSlot(name = "Meditate", minuteOfDay = MORNING_MINUTE)
        val today = fixture.timeProvider.today()
        val scheduledAt = today.atStartOfDay(fixture.timeProvider.zone())
            .plusMinutes(MORNING_MINUTE.toLong()).toInstant()
        val occId = fixture.database.reminderOccurrenceDao().upsert(
            ReminderOccurrenceEntity(
                habitId = seeded.habitId,
                slotId = seeded.slotId,
                scheduledDate = today.toString(),
                scheduledAtEpochMs = scheduledAt.toEpochMilli(),
                state = STATE_ARMED,
                snoozeUntilEpochMs = null,
                snoozeCount = 0,
                notifiedAtEpochMs = null,
                resolveDeadlineMs = scheduledAt.toEpochMilli() + RESOLVE_DEADLINE_MS,
            ),
        )
        return Triple(seeded.habitId, seeded.slotId, occId)
    }

    @Test
    fun aClearedSlotIsFoundAndMissedByTheFollowingMidnightSweep(): Unit = runBlocking {
        val (habitId, slotId, occId) = armTodayOccurrence()
        val today = fixture.timeProvider.today()

        entryWriter.answerInApp(habitId, today, slotId, InAppEntryStatus.COMPLETED, occId)
        assertEquals(1, fixture.database.entryDao().findByHabitAndDate(habitId, today.toString()).size)
        assertEquals(STATE_RESOLVED, fixture.database.reminderOccurrenceDao().findById(occId)?.state)

        entryWriter.clearAnswer(habitId, today, slotId, occId)

        // Cleared slot reads as pending on Today: no entry row survives the clear.
        assertTrue(
            "clearing must delete the entry row, never write a sentinel status",
            fixture.database.entryDao().findByHabitAndDate(habitId, today.toString()).isEmpty(),
        )
        assertEquals(STATE_FIRED, fixture.database.reminderOccurrenceDao().findById(occId)?.state)

        // The real sweep, the following day — not a stand-in.
        val tomorrow = today.plusDays(1)
        val justAfterMidnight = tomorrow.atStartOfDay(fixture.timeProvider.zone()).plusMinutes(5).toInstant()
        resolver().sweepMidnight(tomorrow, justAfterMidnight)

        val entries = fixture.database.entryDao().findByHabitAndDate(habitId, today.toString())
        assertEquals(1, entries.size)
        assertEquals(
            "an unanswered slot must still become MISSED at day's end, even after being cleared",
            ENTRY_STATUS_MISSED,
            entries.single().status,
        )
        assertEquals(ENTRY_SOURCE_SWEEP, entries.single().source)
        assertEquals(STATE_RESOLVED, fixture.database.reminderOccurrenceDao().findById(occId)?.state)
    }

    @Test
    fun aClearedSlotIsNeitherReArmedNorForceResolvedBeforeMidnight(): Unit = runBlocking {
        val (habitId, slotId, occId) = armTodayOccurrence()
        val today = fixture.timeProvider.today()
        entryWriter.answerInApp(habitId, today, slotId, InAppEntryStatus.COMPLETED, occId)
        entryWriter.clearAnswer(habitId, today, slotId, occId)

        // The hourly pass, called the same day the clear happened — never at midnight.
        val alarmScheduler = mockk<AlarmScheduler>(relaxed = true)
        val laterToday = fixture.timeProvider.now().plusSeconds(3600)
        resolver(alarmScheduler).reconcile(laterToday)

        verify(exactly = 0) { alarmScheduler.schedule(any(), any()) }
        verify(exactly = 0) { alarmScheduler.cancel(any()) }
        assertEquals(
            "reconcile must leave a same-day cleared occurrence exactly as clearAnswer left it",
            STATE_FIRED,
            fixture.database.reminderOccurrenceDao().findById(occId)?.state,
        )
        assertTrue(
            "reconcile must not resurrect the cleared entry",
            fixture.database.entryDao().findByHabitAndDate(habitId, today.toString()).isEmpty(),
        )
    }

    @Test
    fun reAnsweringAfterClearingWritesTheNewAnswerNormally(): Unit = runBlocking {
        val (habitId, slotId, occId) = armTodayOccurrence()
        val today = fixture.timeProvider.today()
        entryWriter.answerInApp(habitId, today, slotId, InAppEntryStatus.MISSED, occId)
        entryWriter.clearAnswer(habitId, today, slotId, occId)

        entryWriter.answerInApp(habitId, today, slotId, InAppEntryStatus.COMPLETED, occId)

        val entry = fixture.database.entryDao().findByHabitAndDate(habitId, today.toString()).single()
        assertEquals(EntryStatus.COMPLETED.name, entry.status)
        assertEquals(STATE_RESOLVED, fixture.database.reminderOccurrenceDao().findById(occId)?.state)
    }
}
