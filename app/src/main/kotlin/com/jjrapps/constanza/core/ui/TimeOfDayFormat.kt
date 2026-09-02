package com.jjrapps.constanza.core.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MINUTES_PER_HOUR = 60

/** Both halves always two digits: `21:05`, never `21:5`, and `08:00`, never `8:0`. */
private const val PATTERN_24_HOUR = "HH:mm"

/**
 * `9:15 PM`, not `09:15 PM`. The hour is deliberately unpadded here while [PATTERN_24_HOUR] pads
 * it: a leading zero on a 12-hour clock is not a convention anywhere that uses one, and the reason
 * `21:05` needs padding — keeping a column of times the same width — does not survive the trailing
 * day-period marker anyway. The *minute* stays padded in both, which is the half the old
 * `"%02d:%02d"`/`21:5` defect was about.
 */
private const val PATTERN_12_HOUR = "h:mm a"

/**
 * **The app's one time-of-day renderer.** Every screen that puts a wall-clock time on screen goes
 * through this: `tracking.TodayScreen`'s slot rows and snooze text, and `habit.ReminderTimeField`'s
 * row and its picker dialog. It replaces three separate copies of the same decision — a
 * `DateTimeFormatter.ofPattern("HH:mm")` and a bare `"%02d:%02d"` inside `TodayScreen` alone, plus a
 * third in `ReminderTimeField` whose KDoc said it was matching the second "character for character"
 * precisely because there was nowhere shared to put it.
 *
 * **What follows the device and what does not.** The *hour cycle* follows the device: whatever the
 * user picked in Settings > System > Date & time, read through
 * [DateFormat.is24HourFormat][android.text.format.DateFormat.is24HourFormat]. The *digit layout* is
 * this app's ([PATTERN_24_HOUR]/[PATTERN_12_HOUR]) rather than the locale's, and the locale supplies
 * only the day-period text — `PM` in `en-US`, `p. m.` in `es-ES`. That split is the point of using
 * an explicit pattern instead of
 * [DateTimeFormatter.ofLocalizedTime][java.time.format.DateTimeFormatter.ofLocalizedTime]:
 * `ofLocalizedTime` derives the hour cycle from the *locale*, so a US-English phone switched to
 * 24-hour would still be handed `9:15 PM`, which is exactly the bug this class exists to fix. The
 * setting has to win over the locale, and only a chosen pattern lets it.
 *
 * Cheap to build and cheap to hold — one [DateTimeFormatter], which is immutable and thread-safe —
 * so [rememberTimeOfDayFormat] hands out an instance per composition rather than a singleton, and
 * nothing needs to invalidate a cache when the device setting changes.
 */
class TimeOfDayFormat(
    /** Exposed so `habit.ReminderTimeField` can hand the same answer to M3's `TimePickerState`,
     *  which needs the boolean rather than a formatted string. One read, two consumers. */
    val is24Hour: Boolean,
    locale: Locale,
) {
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (is24Hour) PATTERN_24_HOUR else PATTERN_12_HOUR, locale)

    /** [minuteOfDay] is minutes since local midnight, `0..1439` — the shape `ReminderSlot` stores. */
    fun format(minuteOfDay: Int): String =
        format(LocalTime.of(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR))

    fun format(time: LocalTime): String = formatter.format(time)
}

/**
 * The composition's [TimeOfDayFormat], built from the device's 12/24-hour setting and the
 * configuration's locale.
 *
 * Reading that setting is **not** a clock read, so `config/detekt/detekt.yml`'s `ForbiddenMethodCall`
 * ban does not apply and none of it is circumvented here: the ban is on `Instant.now`,
 * `LocalDate.now`, `LocalDateTime.now` and friends — on learning *what time it is* — while this asks
 * only *how the user wants a time written*. Every actual instant still arrives through
 * [com.jjrapps.constanza.core.time.TimeProvider], unchanged.
 *
 * The locale comes from [LocalConfiguration] rather than `Locale.getDefault()` so a per-app language
 * override is honoured. Keyed on both, so a locale change recomposes with the new day-period text.
 *
 * **Known limit, stated rather than hidden:** Android carries the 12/24 preference in
 * `Settings.System`, not in `Configuration`, so changing it while this app is in the foreground does
 * not recompose anything — the new format appears the next time the Activity is created. That is how
 * every app that reads `is24HourFormat` behaves; closing the gap would mean a `ContentObserver` on
 * `Settings.System.TIME_12_24`, which is more machinery than a setting nobody flips twice a day
 * deserves.
 */
@Composable
fun rememberTimeOfDayFormat(): TimeOfDayFormat {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) {
        TimeOfDayFormat(
            is24Hour = DateFormat.is24HourFormat(context),
            locale = configuration.locales[0],
        )
    }
}
