package com.jjrapps.constanza.core.ui

import java.time.LocalTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The point of extracting [TimeOfDayFormat] out of `tracking.TodayScreen` and
 * `habit.ReminderTimeField` was that a rendering rule this small should be provable without an
 * emulator. These are the cases the three deleted copies could only be checked against by looking
 * at a screen.
 *
 * Every case pins an explicit [Locale], never the JVM default: the 12-hour output carries a
 * day-period marker whose text is locale data, so a test that inherited the machine's locale would
 * assert something different on someone else's laptop.
 */
class TimeOfDayFormatTest {

    private fun format24(minuteOfDay: Int) = TimeOfDayFormat(is24Hour = true, locale = Locale.US).format(minuteOfDay)

    private fun format12(minuteOfDay: Int) = TimeOfDayFormat(is24Hour = false, locale = Locale.US).format(minuteOfDay)

    // ------------------------------------------------------------------------------------------
    // 24-hour cycle.
    // ------------------------------------------------------------------------------------------

    @Test
    fun `a 24-hour evening time keeps both halves two digits`() {
        assertEquals("21:05", format24(21 * MINUTES_PER_HOUR + 5))
    }

    /** The exact shape of the defect the old `"%02d:%02d"` existed to prevent, kept as a test. */
    @Test
    fun `a 24-hour time pads a single-digit minute`() {
        assertEquals("00:05", format24(5))
    }

    @Test
    fun `a 24-hour time pads a single-digit hour`() {
        assertEquals("08:00", format24(8 * MINUTES_PER_HOUR))
    }

    @Test
    fun `midnight is 00 00 on a 24-hour clock, not 24 00`() {
        assertEquals("00:00", format24(0))
    }

    @Test
    fun `noon is 12 00 on a 24-hour clock`() {
        assertEquals("12:00", format24(12 * MINUTES_PER_HOUR))
    }

    @Test
    fun `the last minute of the day is 23 59`() {
        assertEquals("23:59", format24(LAST_MINUTE_OF_DAY))
    }

    // ------------------------------------------------------------------------------------------
    // 12-hour cycle. The same instants, so the pairs above and below are directly comparable.
    // ------------------------------------------------------------------------------------------

    @Test
    fun `a 12-hour evening time wraps past noon and keeps the minute padded`() {
        assertEquals("9:05 PM", format12(21 * MINUTES_PER_HOUR + 5))
    }

    /**
     * Midnight is the case a naive `hour % 12` gets wrong: it yields `0`, and there is no `0 AM`.
     * A 12-hour clock counts `12, 1, 2 … 11`, so five past midnight is `12:05 AM`.
     */
    @Test
    fun `midnight is 12 05 AM, never 0 05 AM`() {
        assertEquals("12:05 AM", format12(5))
    }

    @Test
    fun `midnight on the hour is 12 00 AM`() {
        assertEquals("12:00 AM", format12(0))
    }

    /** The other end of the same trap: noon is `12:00 PM`, not `0:00 PM` and not `12:00 AM`. */
    @Test
    fun `noon is 12 00 PM`() {
        assertEquals("12:00 PM", format12(12 * MINUTES_PER_HOUR))
    }

    @Test
    fun `one minute before noon is still AM`() {
        assertEquals("11:59 AM", format12(12 * MINUTES_PER_HOUR - 1))
    }

    @Test
    fun `the last minute of the day is 11 59 PM`() {
        assertEquals("11:59 PM", format12(LAST_MINUTE_OF_DAY))
    }

    @Test
    fun `a 12-hour morning hour is not zero-padded`() {
        assertEquals("8:00 AM", format12(8 * MINUTES_PER_HOUR))
    }

    // ------------------------------------------------------------------------------------------
    // What follows the device and what follows the locale.
    // ------------------------------------------------------------------------------------------

    /**
     * The whole reason this class takes an explicit pattern instead of
     * `DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)`: `ofLocalizedTime` derives the hour
     * cycle from the locale, so `en-US` would come back 12-hour no matter what the device setting
     * said. Here the setting wins and the locale is not consulted about the cycle at all.
     */
    @Test
    fun `the hour cycle follows the setting and not the locale`() {
        val evening = 21 * MINUTES_PER_HOUR + 5
        assertEquals("21:05", TimeOfDayFormat(is24Hour = true, locale = Locale.US).format(evening))
        assertEquals("9:05 PM", TimeOfDayFormat(is24Hour = false, locale = Locale.US).format(evening))
    }

    /**
     * `en-GB` writes the marker lower case. Pinned as its own case because it is the cheapest
     * possible reminder that the day-period text is data and not a constant: an earlier draft of
     * this file asserted `9:05 PM` for [Locale.UK] and failed on exactly this.
     */
    @Test
    fun `British English writes the day period marker in lower case`() {
        val british = TimeOfDayFormat(is24Hour = false, locale = Locale.UK)
        assertEquals("9:05 pm", british.format(21 * MINUTES_PER_HOUR + 5))
    }

    /** 24-hour output is pure digits, so it is byte-identical in every locale. */
    @Test
    fun `a 24-hour time is the same string in every locale`() {
        val evening = 21 * MINUTES_PER_HOUR + 5
        assertEquals("21:05", TimeOfDayFormat(is24Hour = true, locale = Locale.US).format(evening))
        assertEquals("21:05", TimeOfDayFormat(is24Hour = true, locale = Locale.FRANCE).format(evening))
        assertEquals("21:05", TimeOfDayFormat(is24Hour = true, locale = SPAIN).format(evening))
    }

    /**
     * The locale's one job here. The digits and the separator are this app's; the day-period marker
     * is the locale's, and it is genuinely not "PM" outside English — asserted loosely (it differs,
     * and it is not the English marker) rather than against a CLDR string, because the exact
     * Spanish spelling has changed between CLDR releases and pinning it would make this test a
     * statement about the JDK's bundled data rather than about this class.
     */
    @Test
    fun `the day period marker comes from the locale`() {
        val evening = 21 * MINUTES_PER_HOUR + 5
        val spanish = TimeOfDayFormat(is24Hour = false, locale = SPAIN).format(evening)
        assertTrue(spanish.startsWith("9:05 "), "expected the Spanish time to start \"9:05 \", got \"$spanish\"")
        assertTrue(spanish != "9:05 PM", "expected a non-English day-period marker, got \"$spanish\"")
    }

    // ------------------------------------------------------------------------------------------
    // The LocalTime overload — the shape TodayScreen's snoozed-until text needs, since that time
    // arrives as an Instant in a zone rather than as a minute-of-day.
    // ------------------------------------------------------------------------------------------

    @Test
    fun `the LocalTime overload agrees with the minute-of-day overload`() {
        val format = TimeOfDayFormat(is24Hour = true, locale = Locale.US)
        assertEquals(format.format(21 * MINUTES_PER_HOUR + 5), format.format(LocalTime.of(21, 5)))
    }

    /** Seconds are not part of a time-of-day the user picked, and must not leak into the string. */
    @Test
    fun `seconds on the LocalTime are dropped rather than rendered`() {
        val format = TimeOfDayFormat(is24Hour = true, locale = Locale.US)
        assertEquals("21:05", format.format(LocalTime.of(21, 5, 45)))
    }

    /** [TimeOfDayFormat.is24Hour] is read by `habit.ReminderTimeField` to seed `TimePickerState`. */
    @Test
    fun `the hour cycle is readable back off the format`() {
        assertTrue(TimeOfDayFormat(is24Hour = true, locale = Locale.US).is24Hour)
        assertTrue(!TimeOfDayFormat(is24Hour = false, locale = Locale.US).is24Hour)
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val LAST_MINUTE_OF_DAY = 23 * MINUTES_PER_HOUR + 59
        val SPAIN: Locale = Locale.forLanguageTag("es-ES")
    }
}
