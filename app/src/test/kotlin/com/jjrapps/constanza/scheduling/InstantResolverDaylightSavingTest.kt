package com.jjrapps.constanza.scheduling

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Task 4a.7 — the DST resolution `:domain` cannot own, because `dueOn`/`rollupDay` never see a
 * `LocalTime` (`DueOnDaylightSavingTest` already pins `:domain`'s date-only immunity). Fixed zone
 * `Europe/Madrid`, injected explicitly — never the ambient default. Both transition dates and both
 * assertions below were verified directly against `java.time.zone.ZoneRules` before being written
 * (see the apply-progress record): the ambiguous fall-back local window for this zone/date is
 * 02:00–02:59, not 01:30 as a naive reading of the "at 3am, clocks go back to 2am" rule might
 * suggest for 01:30 — 01:30 has only one valid offset and is not ambiguous at all.
 */
private val MADRID: ZoneId = ZoneId.of("Europe/Madrid")
private const val MINUTE_02_30 = 150 // 2h30m
private const val MINUTE_08_00 = 480 // an ordinary, non-transition-day sanity slot

class InstantResolverDaylightSavingTest {

    @Test
    fun `spring forward shifts a gap local time forward by the gap length, one calendar day unaffected`() {
        // 2026-03-29: Europe/Madrid clocks jump 02:00 -> 03:00. 02:30 does not exist locally.
        val instant = resolveOccurrenceInstant(LocalDate.of(2026, 3, 29), MINUTE_02_30, MADRID)

        // Decision: fires at the equivalent 03:30+02:00 local time, one hour later, same date.
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), instant)
    }

    @Test
    fun `fall back resolves the ambiguous local time to the earlier offset and fires exactly once`() {
        // 2026-10-25: Europe/Madrid clocks fall back 03:00 -> 02:00. 02:30 occurs twice: once at
        // +02:00 (before the turnback) and once at +01:00 (after).
        val instant = resolveOccurrenceInstant(LocalDate.of(2026, 10, 25), MINUTE_02_30, MADRID)

        // Decision: resolves to the EARLIER (+02:00) offset — the first, not the second,
        // occurrence — so exactly one instant is produced, never two.
        val earlierOccurrence = Instant.parse("2026-10-25T00:30:00Z") // 02:30+02:00
        val laterOccurrence = Instant.parse("2026-10-25T01:30:00Z") // 02:30+01:00 — must NOT fire here
        assertEquals(earlierOccurrence, instant)
        assertNotEquals(laterOccurrence, instant)
    }

    @Test
    fun `transition-week guard around spring forward never throws and stays within one day of expectation`() {
        assertNoThrowAcrossWeek(LocalDate.of(2026, 3, 26))
    }

    @Test
    fun `transition-week guard around fall back never throws and stays within one day of expectation`() {
        assertNoThrowAcrossWeek(LocalDate.of(2026, 10, 22))
    }

    /** Walks a full week starting at [weekStart], asserting an ordinary slot resolves without
     *  throwing on every date, including the transition date itself, and that each day's instant
     *  differs from the previous day's by 24h +/- 1h (the only two valid deltas across a DST week). */
    private fun assertNoThrowAcrossWeek(weekStart: LocalDate) {
        var previous: Instant? = null
        var date = weekStart
        repeat(DAYS_IN_WEEK) {
            val instant = resolveOccurrenceInstant(date, MINUTE_08_00, MADRID)
            previous?.let { prior ->
                val deltaSeconds = instant.epochSecond - prior.epochSecond
                val validDelta = deltaSeconds == ONE_DAY_SECONDS ||
                    deltaSeconds == ONE_DAY_SECONDS + ONE_HOUR_SECONDS ||
                    deltaSeconds == ONE_DAY_SECONDS - ONE_HOUR_SECONDS
                kotlin.test.assertTrue(validDelta, "unexpected delta $deltaSeconds s on $date")
            }
            previous = instant
            date = date.plusDays(1)
        }
    }

    private companion object {
        const val DAYS_IN_WEEK = 7
        const val ONE_DAY_SECONDS = 24L * 60 * 60
        const val ONE_HOUR_SECONDS = 60L * 60
    }
}
