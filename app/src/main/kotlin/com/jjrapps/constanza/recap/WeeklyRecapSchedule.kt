package com.jjrapps.constanza.recap

import com.jjrapps.constanza.domain.startOfWeek
import java.time.DayOfWeek
import java.time.LocalDate

/** weekly-recap S2: the recap is due on Monday at 09:00 local time. */
val WEEKLY_RECAP_DAY: DayOfWeek = DayOfWeek.MONDAY
const val WEEKLY_RECAP_MINUTE_OF_DAY = 9 * 60

/**
 * The Monday of the week a recap armed for [scheduledDate] should summarise, or `null` when the fire
 * came too late to be about the right week. Unlike the day review, a late recap is still true — last
 * week's answers stop changing once the midnight sweep has closed Sunday — so a fire delayed by Doze,
 * a throttled worker or a phone that was off still posts any time during [scheduledDate]'s own week.
 * Once a new week has started it would name a week that is no longer "last week", so it posts nothing.
 */
fun recapWeekFor(scheduledDate: LocalDate, today: LocalDate): LocalDate? =
    if (startOfWeek(today, WEEKLY_RECAP_DAY) == scheduledDate) scheduledDate.minusWeeks(1) else null
