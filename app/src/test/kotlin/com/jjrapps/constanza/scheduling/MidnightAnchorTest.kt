package com.jjrapps.constanza.scheduling

import com.jjrapps.constanza.core.time.TimeProvider
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

private val MADRID: ZoneId = ZoneId.of("Europe/Madrid")
private val SPRING_FORWARD = LocalDate.of(2026, 3, 29) // Europe/Madrid loses an hour at 02:00
private val FALL_BACK = LocalDate.of(2026, 10, 25) // Europe/Madrid gains an hour at 03:00

/**
 * Task G.4: [millisUntilNextMidnight] is the whole anchor. The midnight sweep is no longer periodic
 * work whose initial delay is fixed once and re-applied to a stale enqueue instant — it is one-time
 * work re-anchored by [MidnightSweepWorker] on every run — so this arithmetic is what decides
 * whether the sweep lands on the 00:00 boundary, and it is worth its own tests rather than being
 * covered only through `WorkManager`.
 *
 * DST is the reason the day length is asserted twice: a fixed 24h period cannot express a 23h or a
 * 25h local day, and recomputing per run is how the sweep absorbs both (design.md §4 keeps the
 * clock behind [TimeProvider], so both are tests rather than a wait until March).
 */
class MidnightAnchorTest {

    private fun timeProviderAt(instant: Instant, zone: ZoneId = MADRID): TimeProvider {
        val provider = mockk<TimeProvider>()
        every { provider.now() } returns instant
        every { provider.zone() } returns zone
        every { provider.today() } returns LocalDate.ofInstant(instant, zone)
        return provider
    }

    private fun localStartOfDay(date: LocalDate, zone: ZoneId = MADRID): Instant =
        date.atStartOfDay(zone).toInstant()

    @Test
    fun `a start just after midnight anchors on the next midnight, not the one already gone`() {
        val justAfterMidnight = LocalDate.of(2026, 9, 2).atTime(LocalTime.of(0, 5)).atZone(MADRID).toInstant()

        val delay = timeProviderAt(justAfterMidnight).millisUntilNextMidnight()

        assertEquals(Duration.ofHours(23).plusMinutes(55).toMillis(), delay)
    }

    /** The 00:04:55 process start of design.md §13.4 finding 2 is exactly this case: the boundary
     *  has just passed, so the correct anchor is a whole day out — never a few seconds back. */
    @Test
    fun `standing exactly on midnight anchors a full day ahead rather than zero`() {
        val delay = timeProviderAt(localStartOfDay(LocalDate.of(2026, 9, 2))).millisUntilNextMidnight()

        assertEquals(Duration.ofDays(1).toMillis(), delay)
    }

    @Test
    fun `the spring-forward day is 23 hours long and the anchor follows it`() {
        val delay = timeProviderAt(localStartOfDay(SPRING_FORWARD)).millisUntilNextMidnight()

        assertEquals(Duration.ofHours(23).toMillis(), delay)
    }

    @Test
    fun `the fall-back day is 25 hours long and the anchor follows it`() {
        val delay = timeProviderAt(localStartOfDay(FALL_BACK)).millisUntilNextMidnight()

        assertEquals(Duration.ofHours(25).toMillis(), delay)
    }

    /** `setInitialDelay` rejects a negative delay, so the clamp is load-bearing rather than
     *  defensive decoration: a provider whose `now()` has run past its own `today()` boundary must
     *  produce an immediate sweep. */
    @Test
    fun `a clock already past its own next boundary clamps to zero`() {
        val provider = mockk<TimeProvider>()
        every { provider.now() } returns localStartOfDay(LocalDate.of(2026, 9, 5))
        every { provider.zone() } returns MADRID
        every { provider.today() } returns LocalDate.of(2026, 9, 2)

        assertEquals(0L, provider.millisUntilNextMidnight())
    }
}
