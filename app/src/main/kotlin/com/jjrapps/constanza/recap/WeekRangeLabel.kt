package com.jjrapps.constanza.recap

import android.text.format.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val LAST_DAY_OFFSET = 6L
private const val RANGE_SEPARATOR = " – "

/** The first run of `d` outside a quoted literal in a date pattern. */
private val DAY_FIELD = Regex("""d+(?=(?:[^']*'[^']*')*[^']*$)""")

/**
 * weekly-recap: "12 – 18 de octubre" / "October 12 – 18" within one month, and
 * "28 de septiembre – 4 de octubre" across two. The day-and-month pattern is the platform's best
 * pattern for [locale], the same source `TodayHeader` uses for its date, and [locale] is always
 * explicit: below API 33 the app's chosen language is not the JVM default, so anything that reads
 * the default (as `DateUtils.formatDateRange` does for month names) would print the system's
 * language instead. Within one month the start day is spliced in front of the end date's own day
 * field, so each language keeps its own order around the range.
 */
fun weekRangeLabel(weekStart: LocalDate, locale: Locale): String {
    val weekEnd = weekStart.plusDays(LAST_DAY_OFFSET)
    val dayMonth = DateFormat.getBestDateTimePattern(locale, "dMMMM")
    if (weekStart.month != weekEnd.month) {
        val formatter = DateTimeFormatter.ofPattern(dayMonth, locale)
        return formatter.format(weekStart) + RANGE_SEPARATOR + formatter.format(weekEnd)
    }
    val spliced = DAY_FIELD.find(dayMonth)?.let { day ->
        dayMonth.replaceRange(day.range, "'${weekStart.dayOfMonth}$RANGE_SEPARATOR'${day.value}")
    } ?: dayMonth
    return DateTimeFormatter.ofPattern(spliced, locale).format(weekEnd)
}
