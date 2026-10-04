package com.jjrapps.constanza.habit

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.TimeOfDayFormat
import com.jjrapps.constanza.core.ui.rememberTimeOfDayFormat
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private const val PART_SEPARATOR = " · "
private const val DAY_SEPARATOR = ", "
private const val DAYS_PER_WEEK = 7

/**
 * The copy [scheduleSummary] assembles a habit-list subtitle from. An interface rather than a
 * `Resources` parameter so the assembly stays a plain function a JVM test can drive: the composable
 * edge backs it with string and plural resources ([rememberScheduleSummaryStrings]), a test with
 * literals.
 */
interface ScheduleSummaryStrings {
    val daily: String
    val timesPerDay: String
    fun reminderCount(count: Int): String
    fun timesPerWeek(times: Int): String
    fun monthlyOnDay(dayOfMonth: Int): String
    fun everyNDays(days: Int): String
}

/**
 * habit-management: the habit list row's subtitle — the schedule, then ` · ` and its reminder
 * ("Daily · 9:30 AM", "Mon, Wed, Fri · 7:00 PM"). A several-times-a-day habit names how many
 * reminders it has instead of one time ("Several times a day · 3 reminders"), since no single time
 * describes it. Only enabled slots count, and with none the reminder part is left out entirely —
 * the habit is still tracked in the app, it just does not ring.
 *
 * Every other kind carries at most one slot (the editor caps it), but the earliest enabled one is
 * taken rather than assuming that cap holds. Day abbreviations come from
 * [DayOfWeek.getDisplayName] in [locale], the same source the editor's day chips use, ordered from
 * the schedule's own week start; times go through the app's one [TimeOfDayFormat].
 */
fun scheduleSummary(
    schedule: Schedule,
    slots: List<ReminderSlot>,
    strings: ScheduleSummaryStrings,
    timeFormat: TimeOfDayFormat,
    locale: Locale,
): String {
    val enabledMinutes = slots.filter { it.enabled }.map { it.minuteOfDay }.sorted()
    val frequency = when (schedule) {
        is Schedule.Daily -> strings.daily
        is Schedule.TimesPerDay -> strings.timesPerDay
        is Schedule.NTimesPerWeek -> strings.timesPerWeek(schedule.times)
        is Schedule.DaysOfWeek -> dayList(schedule.days, schedule.weekStart, locale)
        is Schedule.Monthly -> strings.monthlyOnDay(schedule.dayOfMonth)
        is Schedule.EveryNDays -> strings.everyNDays(schedule.n)
    }
    val reminder = if (schedule is Schedule.TimesPerDay) {
        enabledMinutes.size.takeIf { it > 0 }?.let(strings::reminderCount)
    } else {
        enabledMinutes.firstOrNull()?.let(timeFormat::format)
    }
    return listOfNotNull(frequency, reminder).joinToString(PART_SEPARATOR)
}

private fun dayList(days: Set<DayOfWeek>, weekStart: DayOfWeek, locale: Locale): String =
    days.sortedBy { (it.value - weekStart.value + DAYS_PER_WEEK) % DAYS_PER_WEEK }
        .joinToString(DAY_SEPARATOR) { it.getDisplayName(TextStyle.SHORT, locale) }
        .replaceFirstChar { it.titlecase(locale) }

/** The habit-list row's subtitle, in the secondary tone — see [scheduleSummary]. */
@Composable
internal fun HabitScheduleSummary(schedule: HabitSchedule) {
    val strings = rememberScheduleSummaryStrings()
    val timeFormat = rememberTimeOfDayFormat()
    val locale = LocalConfiguration.current.locales[0]
    Text(
        scheduleSummary(schedule.schedule, schedule.slots, strings, timeFormat, locale),
        style = MaterialTheme.typography.bodyMedium,
        color = ConstanzaColors.OnBackgroundVariant,
    )
}

/** [ScheduleSummaryStrings] backed by the composition's resources, so a per-app language override
 *  reaches the subtitle exactly as it reaches `stringResource`. */
@Composable
fun rememberScheduleSummaryStrings(): ScheduleSummaryStrings {
    val resources = LocalResources.current
    return remember(resources) { ResourceScheduleSummaryStrings(resources) }
}

private class ResourceScheduleSummaryStrings(private val resources: Resources) : ScheduleSummaryStrings {
    override val daily: String get() = resources.getString(R.string.habit_list_summary_daily)
    override val timesPerDay: String get() = resources.getString(R.string.habit_list_summary_times_per_day)

    override fun reminderCount(count: Int): String =
        resources.getQuantityString(R.plurals.habit_list_summary_reminder_count, count, count)

    override fun timesPerWeek(times: Int): String =
        resources.getQuantityString(R.plurals.habit_list_summary_times_per_week, times, times)

    override fun monthlyOnDay(dayOfMonth: Int): String =
        resources.getString(R.string.habit_list_summary_monthly, dayOfMonth)

    override fun everyNDays(days: Int): String =
        resources.getQuantityString(R.plurals.habit_list_summary_every_n_days, days, days)
}
