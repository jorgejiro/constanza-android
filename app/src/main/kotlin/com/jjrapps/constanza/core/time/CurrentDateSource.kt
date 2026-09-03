package com.jjrapps.constanza.core.time

import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** today-midnight-rollover, design.md decision 1: the current local date as a live stream, kept
 *  off [TimeProvider] itself so that port stays a stateless synchronous clock — every
 *  `mockk<TimeProvider>` in the repo, including [com.jjrapps.constanza.scheduling.MidnightAnchorTest],
 *  would otherwise need to satisfy a `Flow` too. [zone] is exposed here rather than read from
 *  [TimeProvider] separately so [com.jjrapps.constanza.tracking.TodayViewModel] can replace its
 *  `TimeProvider` parameter with this one instead of adding a ninth constructor parameter. */
interface CurrentDateSource {
    /** The current local date, re-emitted every time local midnight is crossed. Never completes. */
    fun dates(): Flow<LocalDate>

    /** A synchronous read of the same current date [dates] streams, for seeding
     *  [com.jjrapps.constanza.tracking.TodayViewModel]'s `observedDate` at construction and for
     *  `refreshDate()`'s `ON_RESUME` correction — both need a value immediately, not a suspend
     *  collection off the stream. */
    fun today(): LocalDate

    fun zone(): ZoneId
}

/** design.md decision 5, the whole invariant this class exists to hold: every emission is
 *  [TimeProvider.today] read at emission time, and every delay is [millisUntilNextMidnight]
 *  recomputed at that same moment — never `previous.plusDays(1)`, never a fixed `delay(24h)`.
 *  `delay()` does not advance while the device sleeps, so a wake-up can be late; a late tick still
 *  emits the TRUE current date and re-anchors to the TRUE next midnight, so it is late, never
 *  wrong. Backgrounded sleep beyond that is corrected on `ON_RESUME` by
 *  [com.jjrapps.constanza.tracking.TodayViewModel.refreshDate], before anything renders.
 *
 *  [millisUntilNextMidnight] clamps to zero at exact midnight (`setInitialDelay` cannot take a
 *  negative delay); [MIN_DELAY_FLOOR_MS] is this class's own floor on top of that clamp, so a
 *  `delay(0)` never lets the loop re-emit the same date immediately, forever, inside one instant. */
class SelfReschedulingCurrentDateSource @Inject constructor(
    private val timeProvider: TimeProvider,
) : CurrentDateSource {

    override fun dates(): Flow<LocalDate> = flow {
        while (true) {
            emit(timeProvider.today())
            delay(timeProvider.millisUntilNextMidnight().coerceAtLeast(MIN_DELAY_FLOOR_MS))
        }
    }

    override fun today(): LocalDate = timeProvider.today()

    override fun zone(): ZoneId = timeProvider.zone()
}

/** Visible to [MidnightDateSourceTest][com.jjrapps.constanza.core.time.MidnightDateSourceTest]
 *  (`internal`, same Gradle module) so the exact-midnight test can assert the floor's boundary
 *  rather than guessing a value. One second: short enough that a real user never notices the
 *  extra tick, long enough that no test or production loop can spin hot on it. */
internal const val MIN_DELAY_FLOOR_MS = 1_000L
