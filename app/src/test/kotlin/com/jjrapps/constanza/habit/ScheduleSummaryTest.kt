package com.jjrapps.constanza.habit

import com.jjrapps.constanza.core.ui.TimeOfDayFormat
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

private const val NINE_THIRTY = 9 * 60 + 30
private const val SEVEN_PM = 19 * 60
private const val SEVEN_THIRTY = 7 * 60 + 30

/** English copy, mirroring `values/strings.xml`. */
private object EnglishStrings : ScheduleSummaryStrings {
    override val daily = "Daily"
    override val timesPerDay = "Several times a day"
    override fun reminderCount(count: Int) = if (count == 1) "1 reminder" else "$count reminders"
    override fun timesPerWeek(times: Int) = if (times == 1) "1 time a week" else "$times times a week"
    override fun monthlyOnDay(dayOfMonth: Int) = "Monthly on day $dayOfMonth"
    override fun everyNDays(days: Int) = if (days == 1) "Every day" else "Every $days days"
}

/** Spanish copy, mirroring `values-es/strings.xml`. */
private object SpanishStrings : ScheduleSummaryStrings {
    override val daily = "Diaria"
    override val timesPerDay = "Varias veces al día"
    override fun reminderCount(count: Int) = if (count == 1) "1 recordatorio" else "$count recordatorios"
    override fun timesPerWeek(times: Int) = if (times == 1) "1 vez por semana" else "$times veces por semana"
    override fun monthlyOnDay(dayOfMonth: Int) = "Mensual, el día $dayOfMonth"
    override fun everyNDays(days: Int) = if (days == 1) "Cada día" else "Cada $days días"
}

/** habit-management: the habit list row's schedule subtitle (graphite redesign). */
class ScheduleSummaryTest {

    private val english12h = TimeOfDayFormat(is24Hour = false, locale = Locale.US)
    private val spanish24h = TimeOfDayFormat(is24Hour = true, locale = SPANISH)

    private fun slot(minuteOfDay: Int, enabled: Boolean = true, id: Long = minuteOfDay.toLong()) =
        ReminderSlot(id = id, habitId = 1, minuteOfDay = minuteOfDay, enabled = enabled)

    private fun english(schedule: Schedule, vararg slots: ReminderSlot) =
        scheduleSummary(schedule, slots.toList(), EnglishStrings, english12h, Locale.US)

    private fun spanish(schedule: Schedule, vararg slots: ReminderSlot) =
        scheduleSummary(schedule, slots.toList(), SpanishStrings, spanish24h, SPANISH)

    @Test
    fun `a daily habit shows its reminder time`() {
        assertEquals("Daily · 9:30 AM", english(Schedule.Daily(), slot(NINE_THIRTY)))
        assertEquals("Diaria · 09:30", spanish(Schedule.Daily(), slot(NINE_THIRTY)))
    }

    @Test
    fun `the time part is omitted when no reminder is enabled`() {
        assertEquals("Daily", english(Schedule.Daily()))
        assertEquals("Daily", english(Schedule.Daily(), slot(NINE_THIRTY, enabled = false)))
    }

    @Test
    fun `several times a day counts the enabled reminders instead of naming a time`() {
        val schedule = Schedule.TimesPerDay()
        val slots = arrayOf(slot(NINE_THIRTY), slot(SEVEN_PM), slot(SEVEN_THIRTY), slot(600, enabled = false))
        assertEquals("Several times a day · 3 reminders", english(schedule, *slots))
        assertEquals("Varias veces al día · 3 recordatorios", spanish(schedule, *slots))
        assertEquals("Several times a day · 1 reminder", english(schedule, slot(NINE_THIRTY)))
        assertEquals("Several times a day", english(schedule))
    }

    @Test
    fun `days of the week are abbreviated in week order from the week start`() {
        val days = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        assertEquals("Mon, Wed, Fri · 7:00 PM", english(Schedule.DaysOfWeek(days), slot(SEVEN_PM)))
        assertEquals("Lun, mié, vie · 19:00", spanish(Schedule.DaysOfWeek(days), slot(SEVEN_PM)))
        val sundayFirst = Schedule.DaysOfWeek(setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), weekStart = DayOfWeek.SUNDAY)
        assertEquals("Sun, Mon", english(sundayFirst))
    }

    @Test
    fun `times per week is plural aware`() {
        assertEquals("3 times a week", english(Schedule.NTimesPerWeek(3)))
        assertEquals("1 time a week · 7:00 PM", english(Schedule.NTimesPerWeek(1), slot(SEVEN_PM)))
        assertEquals("3 veces por semana", spanish(Schedule.NTimesPerWeek(3)))
    }

    @Test
    fun `monthly names the day of the month`() {
        assertEquals("Monthly on day 5", english(Schedule.Monthly(5)))
        assertEquals("Mensual, el día 5 · 19:00", spanish(Schedule.Monthly(5), slot(SEVEN_PM)))
    }

    @Test
    fun `every n days names the interval and the time`() {
        val schedule = Schedule.EveryNDays(2, anchor = LocalDate.of(2026, 9, 1))
        assertEquals("Every 2 days · 7:30 AM", english(schedule, slot(SEVEN_THIRTY)))
        assertEquals("Cada 2 días · 07:30", spanish(schedule, slot(SEVEN_THIRTY)))
    }

    @Test
    fun `the earliest enabled reminder is the one shown`() {
        assertEquals(
            "Daily · 7:30 AM",
            english(Schedule.Daily(), slot(SEVEN_PM), slot(SEVEN_THIRTY), slot(5 * 60, enabled = false)),
        )
    }

    private companion object {
        val SPANISH: Locale = Locale.forLanguageTag("es-ES")
    }
}
