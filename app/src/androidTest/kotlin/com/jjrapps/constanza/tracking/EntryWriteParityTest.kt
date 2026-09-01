package com.jjrapps.constanza.tracking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.data.mapper.toDomain
import com.jjrapps.constanza.habit.HabitRepositoryTestFixture
import com.jjrapps.constanza.reminding.AnswerResponder
import com.jjrapps.constanza.reminding.AnswerWorker
import com.jjrapps.constanza.reminding.NotificationPoster
import com.jjrapps.constanza.scheduling.AlarmScheduler
import com.jjrapps.constanza.scheduling.insertHabitWithSchedule
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

private const val ORIGIN_DATE = "2026-08-31"
private const val EVENING_MINUTE = 20 * 60
private const val AWAIT_TIMEOUT_MS = 5_000L
private const val RESOLVE_DEADLINE_MS = 24 * 3600 * 1000L
private const val STATE_ARMED = "ARMED"
private const val STATE_RESOLVED = "RESOLVED"
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
    private lateinit var viewModel: TodayViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fixture = HabitRepositoryTestFixture(context)
        entryWriter = EntryWriter(
            fixture.database, fixture.database.entryDao(), fixture.database.reminderOccurrenceDao(),
            mockk<AlarmScheduler>(relaxed = true), NotificationPoster(context), fixture.timeProvider,
        )
        viewModel = TodayViewModel(
            fixture.habitRepository, fixture.database.entryDao(), fixture.database.reminderOccurrenceDao(),
            entryWriter, fixture.timeProvider,
        )
    }

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

    /** A daily habit with one enabled slot and one still-unresolved occurrence dated [ORIGIN_DATE] —
     *  the live-past-midnight shape both routes have to credit identically. */
    private suspend fun armHabit(name: String): Triple<Long, Long, Long> {
        val habitId = fixture.database.insertHabitWithSchedule(name = name)
        val slotId = fixture.insertEnabledSlot(habitId, EVENING_MINUTE)
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
