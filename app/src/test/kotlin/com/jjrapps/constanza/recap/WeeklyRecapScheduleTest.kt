package com.jjrapps.constanza.recap

import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.core.time.epochMilliAtLocalTime
import com.jjrapps.constanza.core.time.nextLocalDateForWeeklyTime
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val NINE_AM = 9 * 60

/** weekly-recap S2: the recap fires on Monday at 09:00 local time and covers the week before. */
class WeeklyRecapScheduleTest {

    private val madrid = ZoneId.of("Europe/Madrid")

    private fun clockAt(local: String) = object : TimeProvider {
        private val instant = java.time.LocalDateTime.parse(local).atZone(madrid).toInstant()
        override fun now(): Instant = instant
        override fun today(): LocalDate = instant.atZone(madrid).toLocalDate()
        override fun zone(): ZoneId = madrid
    }

    @Test
    fun `mid-week the next fire is the coming Monday`() {
        assertEquals(
            LocalDate.parse("2026-10-26"),
            clockAt("2026-10-22T15:00").nextLocalDateForWeeklyTime(DayOfWeek.MONDAY, NINE_AM),
        )
    }

    @Test
    fun `on Monday before nine the next fire is today`() {
        assertEquals(
            LocalDate.parse("2026-10-19"),
            clockAt("2026-10-19T08:59").nextLocalDateForWeeklyTime(DayOfWeek.MONDAY, NINE_AM),
        )
    }

    @Test
    fun `on Monday at or after nine the next fire is a week later`() {
        assertEquals(
            LocalDate.parse("2026-10-26"),
            clockAt("2026-10-19T09:00").nextLocalDateForWeeklyTime(DayOfWeek.MONDAY, NINE_AM),
        )
    }

    @Test
    fun `the fire instant is nine o'clock local time, across the October DST change`() {
        val clock = clockAt("2026-10-22T15:00")
        assertEquals(
            Instant.parse("2026-10-26T08:00:00Z"),
            Instant.ofEpochMilli(clock.epochMilliAtLocalTime(LocalDate.parse("2026-10-26"), NINE_AM)),
        )
    }

    @Test
    fun `an on-time or late fire in the same week recaps the previous week`() {
        val monday = LocalDate.parse("2026-10-19")
        assertEquals(LocalDate.parse("2026-10-12"), recapWeekFor(monday, today = monday))
        assertEquals(LocalDate.parse("2026-10-12"), recapWeekFor(monday, today = LocalDate.parse("2026-10-25")))
    }

    @Test
    fun `a fire that slipped into the following week posts nothing`() {
        assertNull(recapWeekFor(LocalDate.parse("2026-10-19"), today = LocalDate.parse("2026-10-26")))
    }
}
