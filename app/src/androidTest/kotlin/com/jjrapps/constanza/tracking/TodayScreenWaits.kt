package com.jjrapps.constanza.tracking

import com.jjrapps.constanza.domain.model.EntryStatus
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * Safety bound on Room's first emission reaching [TodayViewModel.uiState] — deliberately NOT
 * headroom.
 *
 * The four Today Compose classes used to keep a `WAIT_TIMEOUT_MS` each, raised in lockstep from 5s
 * to 15s the last time this went wrong, and the flake came back anyway.
 * `openspec/config.yaml`'s `today-slot-row-compose-test-timeout-flakiness` records the measurement
 * that ended that argument: across a full api37 suite run the eleven waits that *did* satisfy took
 * 2, 5, 15, 18, 24, 24, 27, 28, 69, 87 and 88 ms — a 24 ms median and an 88 ms worst case, against
 * a 15,000 ms bound. The one that failed was not slow; it was stalled, with the ViewModel still
 * holding zero rows after fifteen seconds. Nothing was in flight, so no bound would have caught it.
 *
 * 5s is therefore ~57x the measured worst case: generous enough that load can never reach it, and
 * small enough that a genuine stall reports quickly instead of costing fifteen seconds per
 * occurrence. If this bound is ever hit, the answer is the diagnostic below, never a bigger number.
 */
private const val ROOM_EMISSION_TIMEOUT_MS = 5_000L

/**
 * Suspends until [TodayViewModel.uiState] satisfies [predicate], and returns that state.
 *
 * **Why the state and not a composed node.** [TodayComposeTest]'s own KDoc has said for two
 * changes that "an idle composition is not one that has received Room's first emission" — and then
 * every class waited on a node anyway, which conflates two different failures behind one message.
 * A node that never appears might mean the row is missing, or the screen is wrong, or Room never
 * emitted; `ComposeTimeoutException: Condition still not satisfied after 15000 ms` cannot tell you
 * which, which is exactly why the last four occurrences were re-run rather than investigated.
 * Waiting here on the ViewModel's own state separates them: this fails with the state it actually
 * saw, and anything that gets past it is a real UI defect.
 *
 * Call it BEFORE `setContent` wherever the data is seeded up front. The composition then renders
 * from a state that already holds the rows, on its first frame, and there is no race left for a
 * node wait to lose.
 */
internal suspend fun TodayViewModel.awaitState(
    expectation: String,
    predicate: (TodayUiState) -> Boolean,
): TodayUiState =
    try {
        withTimeout(ROOM_EMISSION_TIMEOUT_MS) { uiState.first(predicate) }
    } catch (timeout: TimeoutCancellationException) {
        throw AssertionError(
            "Today's state never became $expectation within ${ROOM_EMISSION_TIMEOUT_MS}ms. It is " +
                "still ${uiState.value.describe()}. This is Room's emission, not the composition: " +
                "if the state is empty, the seed published a habit the screen cannot build a row " +
                "from — see HabitRepositoryTestFixture.seedHabitWithEnabledSlot. Raising this " +
                "bound has been tried and measured useless.",
            timeout,
        )
    }

/** Waits for exactly [rows] habit rows to reach the screen's state. */
internal suspend fun TodayViewModel.awaitRows(rows: Int): TodayUiState =
    awaitState("$rows habit row(s)") { it.rows.size == rows }

/** Waits for one habit row carrying [slots] slots — the multi-slot shape, where the row exists
 *  before every slot is attached to it. */
internal suspend fun TodayViewModel.awaitOneRowWithSlots(slots: Int): TodayUiState =
    awaitState("one habit row with $slots slots") { it.rows.singleOrNull()?.slots?.size == slots }

/** Waits for the answer written by the tap under test to come back through Room. */
internal suspend fun TodayViewModel.awaitSlotStatus(
    slotIndex: Int,
    status: EntryStatus,
): TodayUiState = awaitState("slot $slotIndex reading $status") { state ->
    state.rows.singleOrNull()?.slots?.getOrNull(slotIndex)?.status == status
}

/** The failure text every wait above shares: enough to tell "Room never emitted" from "Room
 *  emitted something else" without attaching a debugger to an emulator. */
private fun TodayUiState.describe(): String =
    if (rows.isEmpty()) {
        "empty (no habit row has ever reached it)"
    } else {
        rows.joinToString(prefix = "rows=[", postfix = "]") { row ->
            "${row.habitName}:${row.slots.map { it.status }}"
        }
    }
