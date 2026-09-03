package com.jjrapps.constanza.core.time

import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private val ZONE: ZoneId = ZoneId.of("UTC")

/**
 * today-midnight-rollover, design.md decision 5. [SelfReschedulingCurrentDateSource]'s whole
 * contract is: every emission reads [TimeProvider.today] at emission time, and every delay is
 * [millisUntilNextMidnight] recomputed at that same moment — never `previous.plusDays(1)`, never a
 * fixed `delay(24h)`. These tests drive that arithmetic with virtual time via [runTest] rather than
 * waiting on the real clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MidnightDateSourceTest {

    /** A [TimeProvider] whose [now]/[today] both read one mutable [instant], kept in step with the
     *  test's virtual time by the test itself — [SelfReschedulingCurrentDateSource] never learns
     *  the difference between this and [SystemTimeProvider]. */
    private class FakeTimeProvider(var instant: Instant, private val zone: ZoneId) : TimeProvider {
        override fun now(): Instant = instant
        override fun today(): LocalDate = LocalDate.ofInstant(instant, zone)
        override fun zone(): ZoneId = zone
    }

    @Test
    fun `each emission re-reads today and re-anchors the delay, rather than counting a fixed 24h`() = runTest {
        val day1Start = LocalDate.of(2026, 9, 1).atStartOfDay(ZONE).toInstant()
        val provider = FakeTimeProvider(day1Start, ZONE)
        val source = SelfReschedulingCurrentDateSource(provider)
        val emissions = mutableListOf<LocalDate>()
        val job = launch { source.dates().collect { emissions.add(it) } }
        runCurrent()

        assertEquals(listOf(LocalDate.of(2026, 9, 1)), emissions)

        // The wall clock keeps pace with virtual time, one boundary at a time, so the timer must
        // recompute its delay from the fresh instant rather than replaying the first one.
        provider.instant = LocalDate.of(2026, 9, 2).atStartOfDay(ZONE).toInstant()
        advanceTimeBy(Duration.ofDays(1).toMillis())
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2)), emissions)

        provider.instant = LocalDate.of(2026, 9, 3).atStartOfDay(ZONE).toInstant()
        advanceTimeBy(Duration.ofDays(1).toMillis())
        runCurrent()
        assertEquals(
            listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 3)),
            emissions,
        )

        job.cancel()
    }

    @Test
    fun `a late wake-up after simulated sleep emits the true current date once, never plusDays(1)`() = runTest {
        val day1Start = LocalDate.of(2026, 9, 1).atStartOfDay(ZONE).toInstant()
        val provider = FakeTimeProvider(day1Start, ZONE)
        val source = SelfReschedulingCurrentDateSource(provider)
        val emissions = mutableListOf<LocalDate>()
        val job = launch { source.dates().collect { emissions.add(it) } }
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 9, 1)), emissions)

        // The process "sleeps" straight through 2026-09-02's boundary: delay() itself does not
        // advance while asleep, so the loop is still waiting on the delay it computed from
        // day1Start when it wakes up two days and change later.
        provider.instant = day1Start.plus(Duration.ofDays(2)).plusSeconds(300)
        advanceTimeBy(Duration.ofDays(1).toMillis())
        runCurrent()

        assertEquals(
            listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3)),
            emissions,
            "the late tick must emit the true current date directly, never an assumed plusDays(1)" +
                " nor a duplicate/missing emission for the skipped day",
        )

        job.cancel()
    }

    @Test
    fun `a delay clamped to zero at exact midnight applies a positive floor instead of spinning`() = runTest {
        // Mirrors MidnightAnchorTest's "clock already past its own next boundary" case: now() has
        // run past today()'s own midnight boundary, so millisUntilNextMidnight() clamps to zero.
        // Without a positive floor, delay(0) would let the loop re-emit the same date immediately,
        // forever, inside the same virtual instant.
        val provider = mockk<TimeProvider>()
        every { provider.zone() } returns ZONE
        every { provider.today() } returns LocalDate.of(2026, 9, 2)
        every { provider.now() } returns LocalDate.of(2026, 9, 5).atStartOfDay(ZONE).toInstant()
        val source = SelfReschedulingCurrentDateSource(provider)
        val emissions = mutableListOf<LocalDate>()
        val job = launch { source.dates().collect { emissions.add(it) } }
        runCurrent()
        assertEquals(1, emissions.size)

        // One millisecond short of the floor: no second emission yet.
        advanceTimeBy(MIN_DELAY_FLOOR_MS - 1)
        runCurrent()
        assertEquals(1, emissions.size, "the floor must gate the next emission, not delay(0)")

        // Crossing the floor releases exactly the next emission.
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, emissions.size)

        job.cancel()
    }
}
