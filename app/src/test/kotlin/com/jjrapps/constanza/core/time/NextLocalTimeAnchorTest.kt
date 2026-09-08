package com.jjrapps.constanza.core.time

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
private const val REVIEW_MINUTE_OF_DAY = 23 * 60 + 30 // 23:30, day-review's own default

/**
 * day-review, slice A (day-review-data): [millisUntilNextLocalTime] is [millisUntilNextMidnight]'s
 * sibling for an arbitrary local time rather than only midnight — mirrors its shape and, per the
 * same reasoning, is worth its own tests rather than being covered only through a future worker.
 *
 * DST is exercised the same way [MidnightAnchorTest][com.jjrapps.constanza.scheduling.MidnightAnchorTest]
 * exercises it for midnight: [TimeProvider] is a plain interface here, so a `mockk` can stand on any
 * instant/zone/date it likes, including a real transition day, with no need for a phone or a wait
 * until March.
 */
class NextLocalTimeAnchorTest {

    private fun timeProviderAt(instant: Instant, zone: ZoneId = MADRID): TimeProvider {
        val provider = mockk<TimeProvider>()
        every { provider.now() } returns instant
        every { provider.zone() } returns zone
        every { provider.today() } returns LocalDate.ofInstant(instant, zone)
        return provider
    }

    private fun localInstant(date: LocalDate, time: LocalTime, zone: ZoneId = MADRID): Instant =
        date.atTime(time).atZone(zone).toInstant()

    @Test
    fun `a time still ahead today anchors on today, not tomorrow`() {
        val now = localInstant(LocalDate.of(2026, 9, 2), LocalTime.of(10, 0))

        val delay = timeProviderAt(now).millisUntilNextLocalTime(REVIEW_MINUTE_OF_DAY)

        assertEquals(Duration.ofHours(13).plusMinutes(30).toMillis(), delay)
    }

    @Test
    fun `a time already passed today anchors on tomorrow, not the one already gone`() {
        val now = localInstant(LocalDate.of(2026, 9, 2), LocalTime.of(23, 35))

        val delay = timeProviderAt(now).millisUntilNextLocalTime(REVIEW_MINUTE_OF_DAY)

        assertEquals(Duration.ofHours(23).plusMinutes(55).toMillis(), delay)
    }

    /** Mirrors [MidnightAnchorTest][com.jjrapps.constanza.scheduling.MidnightAnchorTest]'s "standing
     *  exactly on midnight anchors a full day ahead, not zero" — the equivalent boundary for an
     *  arbitrary local time. */
    @Test
    fun `standing exactly on the target time anchors a full day ahead rather than zero`() {
        val now = localInstant(LocalDate.of(2026, 9, 2), LocalTime.of(23, 30))

        val delay = timeProviderAt(now).millisUntilNextLocalTime(REVIEW_MINUTE_OF_DAY)

        assertEquals(Duration.ofDays(1).toMillis(), delay)
    }

    /** The spring-forward day is 23 hours long; 23:30 falls after the 02:00-03:00 gap, so the real
     *  elapsed time from 00:00 to 23:30 local on that date is 22h30m, not the naive 23h30m a
     *  duration-based anchor (adding elapsed minutes to a zoned start-of-day) would compute. */
    @Test
    fun `the spring-forward day is 23 hours long and the anchor follows it`() {
        val now = localInstant(SPRING_FORWARD, LocalTime.MIDNIGHT)

        val delay = timeProviderAt(now).millisUntilNextLocalTime(REVIEW_MINUTE_OF_DAY)

        assertEquals(Duration.ofHours(22).plusMinutes(30).toMillis(), delay)
    }

    /** The fall-back day is 25 hours long; the repeated 02:00-03:00 hour makes the real elapsed time
     *  from 00:00 to 23:30 local on that date 24h30m, not the naive 23h30m. */
    @Test
    fun `the fall-back day is 25 hours long and the anchor follows it`() {
        val now = localInstant(FALL_BACK, LocalTime.MIDNIGHT)

        val delay = timeProviderAt(now).millisUntilNextLocalTime(REVIEW_MINUTE_OF_DAY)

        assertEquals(Duration.ofHours(24).plusMinutes(30).toMillis(), delay)
    }

    /** `setInitialDelay` rejects a negative delay, so the clamp is load-bearing rather than
     *  defensive decoration — same reasoning as [millisUntilNextMidnight]'s own clamp. */
    @Test
    fun `a clock already past its own next boundary clamps to zero`() {
        val provider = mockk<TimeProvider>()
        every { provider.now() } returns localInstant(LocalDate.of(2026, 9, 5), LocalTime.of(23, 31))
        every { provider.zone() } returns MADRID
        every { provider.today() } returns LocalDate.of(2026, 9, 2)

        assertEquals(0L, provider.millisUntilNextLocalTime(REVIEW_MINUTE_OF_DAY))
    }
}
