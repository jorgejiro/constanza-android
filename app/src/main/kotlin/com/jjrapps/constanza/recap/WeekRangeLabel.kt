package com.jjrapps.constanza.recap

import android.content.Context
import android.text.format.DateUtils
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Formatter
import java.util.Locale

private const val LAST_DAY_OFFSET = 6L

/**
 * weekly-recap: "12–18 de octubre" / "October 12–18", and "28 sept – 4 oct" across a month, from the
 * platform's own range formatter rather than a hand-built pattern per language. Both ends are taken
 * at noon UTC and formatted in UTC, so neither the device zone nor DST can shift a day. The
 * [Formatter] carries [locale] explicitly: below API 33 the app's chosen language is not the JVM
 * default, so the screen passes its composition locale and the notification its localized context's.
 */
fun weekRangeLabel(context: Context, weekStart: LocalDate, locale: Locale): String {
    val noon = LocalTime.NOON
    val start = weekStart.atTime(noon).toInstant(ZoneOffset.UTC).toEpochMilli()
    val end = weekStart.plusDays(LAST_DAY_OFFSET).atTime(noon).toInstant(ZoneOffset.UTC).toEpochMilli()
    val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR
    return DateUtils.formatDateRange(context, Formatter(StringBuilder(), locale), start, end, flags, "UTC").toString()
}
