package com.jjrapps.constanza.tracking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.data.mapper.toDomain
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.habit.HabitRepositoryTestFixture
import com.jjrapps.constanza.reminding.AnswerResponder
import com.jjrapps.constanza.reminding.AnswerWorker
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val ORIGIN_DATE = "2026-08-31"
private const val PAST_DATE = "2026-08-20"
private const val EVENING_MINUTE = 20 * 60
private const val AWAIT_TIMEOUT_MS = 5_000L
private const val RESOLVE_DEADLINE_MS = 24 * 3600 * 1000L
private const val STATE_ARMED = "ARMED"
private const val STATE_RESOLVED = "RESOLVED"
private const val ENTRY_SOURCE_IN_APP = "IN_APP"
private const val BOTH_ROUTES = 2

/**
 * Task 6b.7 debt item 2 — the comparative assertion task 6b.2 could only make implicitly, by having
 * the two routes share [EntryWriter]. Given equivalent inputs, the notification action and the
 * in-app answer must land the *same* `Entry`, and both must credit the occurrence's own
 * `scheduledDate` rather than today (habit-entry-tracking: Origin-Date Crediting) — which is why the
 * occurrences below are staged on yesterday, with the fixture's clock held on today.
 *
 * Asserted end-to-end against a real Room database, never as a captured call argument: this
 * repository has already shipped a bug a capture-style test would have accepted. `EntryEntity.source`
 * is the one field that legitimately differs (`NOTIFICATION` vs `IN_APP`); it has no `:domain`
 * counterpart, so comparing the mapped [com.jjrapps.constanza.domain.model.Entry] values compares
 * exactly what the two routes are supposed to agree on.
 *
 * The habits are inserted straight through the DAOs rather than via `HabitRepository.create`, so
 * `OccurrencePlanner` never runs and each slot has exactly one occurrence — the one this test staged.
 */
@RunWith(AndroidJUnit4::class)
class EntryWriteParityTest {

    private lateinit var fixture: HabitRepositoryTestFixture
    private lateinit var entryWriter: EntryWriter

    @Before
    fun setUp() {
        fixture = HabitRepositoryTestFixture(ApplicationProvider.getApplicationContext<Context>())
        entryWriter = fixture.entryWriter()
    }

    /**
     * No [TodayViewModel] is built here, and that is a deliberate removal rather than an oversight.
     *
     * One used to be, and by the time `fix/compose-teardown-race` reached this file the only thing
     * still referencing it was the teardown that cancelled its scope — so the field was already
     * vestigial and the teardown was masking it. It became vestigial when the in-app route moved
     * off `TodayViewModel.uiState` and onto [EntryWriter.answerInApp] directly, for the reason the
     * comment on that call sets out. Constructing one anyway would leave an eager `stateIn`
     * collector querying this fixture's database throughout a test that measures writes, which is
     * noise this comparison does not want.
     *
     * Teardown ordering therefore has nothing to cancel here, and still lives in
     * [HabitRepositoryTestFixture.close] for every test that does.
     */
    @After
    fun tearDown() = fixture.close()

    @Test
    fun bothRoutesWriteTheSameEntryCreditingTheOccurrenceOriginDate() = runBlocking {
        val (notifyHabit, notifySlot, notifyOcc) = armHabit("Notified")
        val (inAppHabit, inAppSlot, inAppOcc) = armHabit("Answered in app")

        // Notification route, through the same adapter ActionReceiver's worker uses.
        AnswerResponder(entryWriter).answer(notifyOcc, AnswerWorker.STATUS_COMPLETED)

        // In-app route, driven at EntryWriter's own entry point rather than through
        // TodayViewModel.uiState. Both routes are what this test compares, and going through the
        // screen made it depend on the screen surfacing an occurrence dated other than today — which
        // it deliberately no longer does, since a slot adopting another day's occurrence was the bug
        // `TodayViewModelTest` now guards.
        //
        // The `date` passed here is deliberately TODAY while the occurrence's own scheduledDate is
        // ORIGIN_DATE, so the assertion below proves the occurrence's origin date wins over whatever
        // the caller supplied. That is a stronger claim than the previous wiring could make.
        entryWriter.answerInApp(
            habitId = inAppHabit,
            date = fixture.timeProvider.today(),
            slotId = inAppSlot,
            status = InAppEntryStatus.COMPLETED,
            occurrenceId = inAppOcc,
        )

        val entries = withTimeout(AWAIT_TIMEOUT_MS) {
            fixture.database.entryDao().observeByDate(ORIGIN_DATE).first { it.size == BOTH_ROUTES }
        }
        val notifyEntry = entries.single { it.habitId == notifyHabit }
        val inAppEntry = entries.single { it.habitId == inAppHabit }
        assertEquals(notifySlot, notifyEntry.slotId)
        assertEquals(inAppSlot, inAppEntry.slotId)

        // Same status, same credited date, same answeredAt — the whole domain Entry, field by field.
        assertEquals(
            notifyEntry.toDomain().copy(habitId = 0, slotId = null),
            inAppEntry.toDomain().copy(habitId = 0, slotId = null),
        )
        val today = fixture.timeProvider.today().toString()
        assertTrue(fixture.database.entryDao().findByHabitAndDate(notifyHabit, today).isEmpty())
        assertTrue(fixture.database.entryDao().findByHabitAndDate(inAppHabit, today).isEmpty())
        assertEquals(STATE_RESOLVED, fixture.database.reminderOccurrenceDao().findById(notifyOcc)?.state)
        assertEquals(STATE_RESOLVED, fixture.database.reminderOccurrenceDao().findById(inAppOcc)?.state)
    }

    /**
     * today-past-day-correction, task 5.5: the manual in-app edit path
     * ([TodayViewModel.answer] calling [EntryWriter.answerInApp] with `occurrenceId = null`) is
     * exactly what a force-resolved slot produces — [TodayModel.toTodaySlot] only ever reads
     * `unresolvedOccurrences`, so a `RESOLVED` occurrence's slot always carries a `null` handle.
     * This proves that null-handle write upserts on the PAST date the caller supplied and never
     * touches the already-`RESOLVED` occurrence row at all — "resurrecting" it would mean flipping
     * it back to armed/unresolved, which [resolveOccurrenceAndWrite] is never even reached to do.
     */
    @Test
    fun answerInAppWithNoOccurrenceHandleUpsertsThePastDateAndLeavesTheResolvedOccurrenceUntouched() = runBlocking {
        val (habitId, slotId) = fixture.seedHabitWithEnabledSlot(name = "Corrected", minuteOfDay = EVENING_MINUTE)
        val scheduledAt = Instant.parse("${PAST_DATE}T20:00:00Z").toEpochMilli()
        val occurrenceId = fixture.database.reminderOccurrenceDao().upsert(
            ReminderOccurrenceEntity(
                habitId = habitId, slotId = slotId, scheduledDate = PAST_DATE,
                scheduledAtEpochMs = scheduledAt, state = STATE_RESOLVED, snoozeUntilEpochMs = null,
                snoozeCount = 0, notifiedAtEpochMs = null, resolveDeadlineMs = scheduledAt + RESOLVE_DEADLINE_MS,
            ),
        )
        fixture.database.entryDao().upsert(
            EntryEntity(
                habitId = habitId, date = PAST_DATE, slotId = slotId, status = EntryStatus.MISSED.name,
                value = null, answeredAt = fixture.timeProvider.now().toString(), source = ENTRY_SOURCE_IN_APP,
            ),
        )

        entryWriter.answerInApp(
            habitId = habitId,
            date = LocalDate.parse(PAST_DATE),
            slotId = slotId,
            status = InAppEntryStatus.COMPLETED,
            occurrenceId = null,
        )

        val entries = fixture.database.entryDao().findByHabitAndDate(habitId, PAST_DATE)
        assertEquals(1, entries.size)
        assertEquals(EntryStatus.COMPLETED.name, entries.single().status)
        assertEquals(
            "a null occurrence handle must never resurrect the already-resolved occurrence",
            STATE_RESOLVED,
            fixture.database.reminderOccurrenceDao().findById(occurrenceId)?.state,
        )
    }

    /** A daily habit with one enabled slot and one still-unresolved occurrence dated [ORIGIN_DATE] —
     *  the live-past-midnight shape both routes have to credit identically. */
    private suspend fun armHabit(name: String): Triple<Long, Long, Long> {
        val (habitId, slotId) = fixture.seedHabitWithEnabledSlot(name = name, minuteOfDay = EVENING_MINUTE)
        val scheduledAt = Instant.parse("${ORIGIN_DATE}T20:00:00Z").toEpochMilli()
        val occId = fixture.database.reminderOccurrenceDao().upsert(
            ReminderOccurrenceEntity(
                habitId = habitId, slotId = slotId, scheduledDate = ORIGIN_DATE,
                scheduledAtEpochMs = scheduledAt, state = STATE_ARMED, snoozeUntilEpochMs = null,
                snoozeCount = 0, notifiedAtEpochMs = null, resolveDeadlineMs = scheduledAt + RESOLVE_DEADLINE_MS,
            ),
        )
        return Triple(habitId, slotId, occId)
    }
}
