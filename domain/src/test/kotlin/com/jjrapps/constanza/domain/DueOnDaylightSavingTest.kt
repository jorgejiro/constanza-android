package com.jjrapps.constanza.domain

import com.jjrapps.constanza.domain.model.Due
import com.jjrapps.constanza.domain.model.PeriodProgress
import com.jjrapps.constanza.domain.model.Schedule
import java.time.LocalDate
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression guard for tasks 2a.9 / 2a.10.
 *
 * [dueOn] takes only [LocalDate], so a daylight-saving transition cannot skip or duplicate a
 * calendar day: DST shifts wall-clock times of day, never the sequence of dates. These tests
 * therefore do not drive new behaviour — they are characterization tests that pin that property,
 * so that introducing `Instant`, `ZonedDateTime`, or any ambient time zone into the predicate
 * fails loudly here rather than silently misplacing a day in a user's history.
 *
 * The substantive DST question — which instant a 02:30 reminder slot maps to on a spring-forward
 * day, when that local time does not exist, and what happens to a 01:30 slot on a fall-back day,
 * when it occurs twice — belongs to the alarm scheduler in `:app` (work unit 4a). It cannot be
 * answered here because a time of day never enters this module.
 */
class DueOnDaylightSavingTest {

    private val originalZone: TimeZone = TimeZone.getDefault()

    @AfterTest
    fun restoreDefaultZone() {
        TimeZone.setDefault(originalZone)
    }

    @Test
    fun `every n days keeps strict calendar cadence across a spring forward transition`() {
        // Europe/Madrid springs forward on 2026-03-29, which is itself a due date here.
        val schedule = Schedule.EveryNDays(n = 2, anchor = date(2026, 3, 27))

        val results = dueDatesIn(schedule, date(2026, 3, 27), date(2026, 3, 31))

        assertEquals(
            listOf(date(2026, 3, 27), date(2026, 3, 29), date(2026, 3, 31)),
            results,
            "the two-day cadence must neither skip nor duplicate the transition day",
        )
    }

    @Test
    fun `every n days keeps strict calendar cadence across a fall back transition`() {
        // Europe/Madrid falls back on 2026-10-25, again landing on a due date.
        val schedule = Schedule.EveryNDays(n = 2, anchor = date(2026, 10, 23))

        val results = dueDatesIn(schedule, date(2026, 10, 23), date(2026, 10, 27))

        assertEquals(
            listOf(date(2026, 10, 23), date(2026, 10, 25), date(2026, 10, 27)),
            results,
            "an extra wall-clock hour must not shift the cadence by a day",
        )
    }

    @Test
    fun `monthly is required on a transition date like any other date`() {
        val schedule = Schedule.Monthly(dayOfMonth = 29)

        assertEquals(Due.Required, dueOn(schedule, date(2026, 3, 29), noProgress))
        assertEquals(Due.Required, dueOn(schedule, date(2026, 10, 29), noProgress))
    }

    @Test
    fun `results are identical under default time zones with different dst rules`() {
        val schedule = Schedule.EveryNDays(n = 3, anchor = date(2026, 3, 1))
        val from = date(2026, 3, 1)
        val to = date(2026, 11, 30)

        // Northern DST, southern DST, and a zone with no DST at all.
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"))
        val northern = dueDatesIn(schedule, from, to)

        TimeZone.setDefault(TimeZone.getTimeZone("America/Santiago"))
        val southern = dueDatesIn(schedule, from, to)

        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
        val noDst = dueDatesIn(schedule, from, to)

        assertEquals(northern, southern, "dueOn must not read the ambient time zone")
        assertEquals(northern, noDst, "dueOn must not read the ambient time zone")
    }

    @Test
    fun `daily is required on every calendar day across a transition, with no gap or repeat`() {
        val from = date(2026, 3, 27)
        val to = date(2026, 3, 31)

        val results = dueDatesIn(Schedule.Daily(), from, to)

        assertEquals(
            listOf(
                date(2026, 3, 27),
                date(2026, 3, 28),
                date(2026, 3, 29),
                date(2026, 3, 30),
                date(2026, 3, 31),
            ),
            results,
            "a spring-forward day is still exactly one calendar day",
        )
    }

    private val noProgress = PeriodProgress(completedInWeek = 0, completedInMonth = 0)

    private fun date(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

    /** Every date in `[from, to]` for which the schedule reports [Due.Required]. */
    private fun dueDatesIn(schedule: Schedule, from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { previous -> previous.plusDays(1).takeIf { !it.isAfter(to) } }
            .filter { dueOn(schedule, it, noProgress) == Due.Required }
            .toList()
}
