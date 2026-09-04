package com.jjrapps.constanza.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * today-past-day-correction, design.md decision 4: fixed above the scrolling content in BOTH of
 * [TodayContent]'s branches, never as the non-empty branch's `LazyColumn` first item — this bar's
 * PRESENCE is the "you are not on today" signal, and a signal that scrolls out of view on a long
 * list is not a signal. `TopAppBar` is untouched; this sits below it.
 *
 * [onNextDay] and [onToday] are only ever wired to a control when [isPastDay] is `true`: there is
 * nothing forward to reach at the live edge, so the control is removed rather than rendered
 * disabled (design.md decision 4's table) — a permanently-disabled control on the app's
 * most-visited screen is noise, not information.
 *
 * The date is formatted with [DateTimeFormatter.ofLocalizedDate], remembered locally and keyed on
 * [LocalConfiguration] so a per-app language override (not only the device locale) is honoured.
 * This stays local rather than moving into `core/ui` — unlike
 * [com.jjrapps.constanza.core.ui.TimeOfDayFormat], which exists because three call sites had
 * duplicated its decision, this one has exactly one.
 */
@Composable
internal fun TodayDateBar(
    date: LocalDate,
    isPastDay: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val formatter = remember(configuration) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(configuration.locales[0])
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPreviousDay) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.today_previous_day),
            )
        }
        // `weight(1f)` is the same load-bearing fix SlotRow and both permission banners already
        // carry (TodayScreen.kt, TodayBanners.kt): without it the label takes what it wants and the
        // trailing controls wrap mid-word once both are showing.
        Text(
            formatter.format(date),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        if (isPastDay) {
            IconButton(onClick = onNextDay) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.today_next_day),
                )
            }
            TextButton(onClick = onToday) {
                Text(stringResource(R.string.today_back_to_today))
            }
        }
    }
}

/**
 * today-past-day-correction, design.md decision 5: the past-day counterpart of [TodayEmptyState] —
 * text only, `today_empty_past`, with no add-habit call to action at all. Lives alongside
 * [TodayDateBar] rather than in `TodayAddHabitAction.kt`, following the same per-concern split
 * that file and `TodayBanners.kt` already established, instead of misfiling past-day copy into the
 * add-habit file it deliberately does NOT offer.
 */
@Composable
internal fun TodayPastDayEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.today_empty_past))
    }
}
