package com.jjrapps.constanza.tracking

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.ProgressLine
import com.jjrapps.constanza.core.ui.icons.ConstanzaIcons
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The header's three icons, drawn at the board's 22px inside their 44dp buttons. */
private val HEADER_ICON_SIZE = 22.dp

/** The day chevrons, a step quieter than the header actions. */
private val CHEVRON_ICON_SIZE = 20.dp

/** The height the date line reports for its 44dp chevrons — the line's own text height, so the
 *  chevrons' touch targets overlap the gaps above and below rather than pushing the line apart. */
private val DATE_LINE_HEIGHT = 24.dp

/** The board's 15px date and count, between `bodyMedium` (14) and `bodyLarge` (17). */
private val DATE_LINE_FONT_SIZE = 15.sp

/** Every action the header offers, as one holder so [TodayHeader] stays under detekt's
 *  `LongParameterList` threshold — the same reason `SlotActions` exists in `TodayScreen.kt`. */
internal data class TodayHeaderActions(
    val onManageHabits: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onPreviousDay: () -> Unit,
    val onNextDay: () -> Unit,
    val onToday: () -> Unit,
)

/**
 * Today's header in the graphite design (direction E): the "Today" title with two quiet icon
 * actions (habit list, settings), then the date and the "N of M" progress line with its bar.
 *
 * It replaces both the `TopAppBar` and the separate date bar that sat under it, and it is the
 * `Scaffold`'s top bar, so it stays fixed above the list in both of `TodayContent`'s layouts — the
 * date is the "you are not on today" signal on a past day, and a signal that scrolls away is not
 * one (today-past-day-correction, design.md decision 4).
 *
 * Day navigation stays where the date is: a compact previous-day chevron trails the date, the
 * next-day chevron joins it only on a past day (nothing lies forward of the live edge, so the
 * control is absent rather than disabled), and the "Today" jump sits in the title row while a past
 * day is shown.
 *
 * The progress line and its bar are left out entirely when no habit is due: "0 of 0" says nothing
 * the empty-state sentence below does not say better.
 */
@Composable
internal fun TodayHeader(date: LocalDate, isPastDay: Boolean, progress: TodayProgress, actions: TodayHeaderActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(top = Spacing.lg, bottom = Spacing.sm),
    ) {
        Row(
            // The end gap is the screen margin less the icon button's own inner padding, so the
            // glyphs — not their invisible touch targets — line up with the margin.
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.today_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
            )
            if (isPastDay) {
                TextButton(onClick = actions.onToday) { Text(stringResource(R.string.today_back_to_today)) }
            }
            HeaderIconButton(ConstanzaIcons.List, stringResource(R.string.today_manage_habits), actions.onManageHabits)
            HeaderIconButton(ConstanzaIcons.Sliders, stringResource(R.string.today_settings), actions.onOpenSettings)
        }
        DateLine(date, isPastDay, progress, actions)
        if (progress.total > 0) {
            ProgressLine(
                fraction = progress.fraction,
                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md),
            )
        }
    }
}

@Composable
private fun HeaderIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(Dimens.CompactIconButton)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(HEADER_ICON_SIZE))
    }
}

/**
 * The date, long and capitalised ("Sunday, September 28" / "Domingo, 28 de septiembre"), built
 * from the locale's own best pattern for weekday + day + month rather than a fixed format, and
 * keyed on [LocalConfiguration] so a per-app language override is honoured, not only the device
 * locale.
 */
@Composable
private fun DateLine(date: LocalDate, isPastDay: Boolean, progress: TodayProgress, actions: TodayHeaderActions) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
    }
    val lineStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = DATE_LINE_FONT_SIZE)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The date and its chevrons share the space the count leaves; the date yields first and
        // ellipsizes rather than wrapping, so the line stays one line on a narrow phone.
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatter.format(date).replaceFirstChar { it.titlecase(locale) },
                modifier = Modifier.weight(1f, fill = false),
                style = lineStyle,
                color = ConstanzaColors.OnBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DayChevron(ConstanzaIcons.ChevronStart, stringResource(R.string.today_previous_day), actions.onPreviousDay)
            if (isPastDay) {
                DayChevron(ConstanzaIcons.ChevronEnd, stringResource(R.string.today_next_day), actions.onNextDay)
            }
        }
        if (progress.total > 0) {
            Text(
                stringResource(R.string.today_progress, progress.answered, progress.total),
                style = lineStyle.copy(fontFeatureSettings = "tnum"),
            )
        }
    }
}

@Composable
private fun DayChevron(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.reportCompactHeight(DATE_LINE_HEIGHT).size(Dimens.CompactIconButton),
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = ConstanzaColors.OnBackgroundVariant,
            modifier = Modifier.size(CHEVRON_ICON_SIZE),
        )
    }
}

/**
 * Reports [paintedHeight] to the parent instead of this element's real measured height, centring
 * the real content around it. The real size, position and hit area are unchanged — semantics read
 * the placed child — so a 44dp touch target can sit in a text-height line without growing it.
 */
private fun Modifier.reportCompactHeight(paintedHeight: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val reportedHeight = paintedHeight.roundToPx()
    layout(placeable.width, reportedHeight) {
        placeable.place(0, (reportedHeight - placeable.height) / 2)
    }
}

/**
 * today-past-day-correction, design.md decision 5: the past-day counterpart of [TodayEmptyState],
 * saying `today_empty_past` where that one says `today_empty`. Both are text only; what separates
 * them is what floats above them, since [TodayAddHabitFab] is rendered on a live today and not on a
 * past day.
 */
@Composable
internal fun TodayPastDayEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.today_empty_past), color = ConstanzaColors.OnBackgroundVariant)
    }
}
