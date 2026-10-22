package com.jjrapps.constanza.recap

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.HabitDot
import com.jjrapps.constanza.core.ui.component.ProgressLine
import com.jjrapps.constanza.core.ui.component.ScreenTopBar
import com.jjrapps.constanza.core.ui.icons.ConstanzaIcons
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.Spacing
import com.jjrapps.constanza.domain.RecapMark
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** The day-mark circle, and the glyph inside it (the board's 28px circle, 14/12px glyphs). */
private val MarkSize = 28.dp
private val DoneGlyphSize = 14.dp
private val MissedGlyphSize = 12.dp

/** The dot plus its gap to the name: the marks start under the name, not the dot. */
private val NameInset = 22.dp

/** The right-hand `done/total` column, wide enough for "7/7" at 17sp. */
private val CountWidth = 40.dp

/** weekly-recap — container. [weekStart] is the Monday a notification named (ISO date string, the
 *  route's serializable form), or `null` when opened from Today, meaning last week. */
@Composable
fun WeeklyRecapRoute(weekStart: String?, onBack: () -> Unit, viewModel: WeeklyRecapViewModel = hiltViewModel()) {
    LaunchedEffect(weekStart) { viewModel.load(weekStart?.let(LocalDate::parse)) }
    BackHandler(onBack = onBack)
    val state by viewModel.uiState.collectAsState()
    WeeklyRecapScreen(state, onBack)
}

/**
 * weekly-recap, design option B "Semana día a día": the week range with `NN % · X de Y` over a
 * [ProgressLine]; one Monday-to-Sunday initials row; then per habit its dot and name, its seven day
 * marks (✓ on a raised circle, ✕ in an outlined one, nothing on a day with nothing to count) and its
 * `done/total`; a legend closes the list. Each habit row reads to TalkBack as one sentence.
 */
@Composable
fun WeeklyRecapScreen(state: WeeklyRecapUiState, onBack: () -> Unit = {}) {
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = { Text(stringResource(R.string.weekly_recap_title)) },
                navigationIcon = ConstanzaIcons.ChevronStart,
                navigationLabel = stringResource(R.string.action_back),
                onNavigate = onBack,
            )
        },
    ) { padding ->
        val recap = state.recap ?: return@Scaffold
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.lg)) {
            item { RecapHeader(recap) }
            if (recap.rows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.weekly_recap_empty),
                        modifier = Modifier.padding(top = Spacing.xl),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ConstanzaColors.OnBackgroundVariant,
                    )
                }
            } else {
                item { DayInitials() }
                items(recap.rows, key = { it.habitId }) { row -> RecapRow(row) }
                item { Legend() }
            }
        }
    }
}

@Composable
private fun RecapHeader(recap: LoadedWeeklyRecap) {
    val locale = LocalConfiguration.current.locales[0]
    val range = weekRangeLabel(recap.weekStart, locale)
    Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Text(
            range,
            modifier = Modifier.weight(1f).alignByBaseline(),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
            color = ConstanzaColors.OnBackgroundVariant,
        )
        Text(
            stringResource(R.string.weekly_recap_percent, recap.percent),
            modifier = Modifier.alignByBaseline(),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = ConstanzaColors.OnBackground,
        )
        Text(
            stringResource(R.string.weekly_recap_count_suffix, recap.done, recap.total),
            modifier = Modifier.alignByBaseline(),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
            color = ConstanzaColors.OnBackgroundVariant,
        )
    }
    Spacer(Modifier.height(Spacing.lg))
    ProgressLine(fraction = if (recap.total == 0) 0f else recap.done.toFloat() / recap.total)
}

/** L M X J V S D (or M T W T F S S): the platform's narrow day names, aligned over the mark grid. */
@Composable
private fun DayInitials() {
    val locale: Locale = LocalConfiguration.current.locales[0]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xxl, start = NameInset, end = CountWidth + Spacing.md)
            .clearAndSetSemantics { },
    ) {
        DayOfWeek.entries.forEach { day ->
            Text(
                day.getDisplayName(TextStyle.NARROW, locale),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = ConstanzaColors.OnBackgroundMuted,
            )
        }
    }
}

@Composable
private fun RecapRow(row: WeeklyRecapRow) {
    val description = stringResource(R.string.weekly_recap_row_a11y, row.name, row.done, row.total)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
            .padding(vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row {
            HabitDot(row.colorArgb, modifier = Modifier.padding(top = Spacing.sm))
            Spacer(Modifier.width(NameInset - Dimens.HabitDot))
            Text(row.name, style = MaterialTheme.typography.bodyLarge, color = ConstanzaColors.OnBackground)
        }
        Row(modifier = Modifier.padding(start = NameInset), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f)) {
                row.marks.forEach { mark ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { DayMark(mark) }
                }
            }
            Spacer(Modifier.width(Spacing.md))
            Text(
                stringResource(R.string.weekly_recap_habit_count, row.done, row.total),
                modifier = Modifier.width(CountWidth),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = ConstanzaColors.OnBackground,
            )
        }
    }
    HorizontalDivider(color = ConstanzaColors.Divider)
}

@Composable
private fun DayMark(mark: RecapMark) {
    when (mark) {
        RecapMark.DONE -> Box(
            modifier = Modifier.size(MarkSize).background(ConstanzaColors.SurfaceSelected, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ConstanzaIcons.Check,
                contentDescription = null,
                tint = ConstanzaColors.OnBackground,
                modifier = Modifier.size(DoneGlyphSize),
            )
        }
        RecapMark.MISSED -> Box(
            modifier = Modifier.size(MarkSize).border(1.dp, ConstanzaColors.ControlStroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ConstanzaIcons.Close,
                contentDescription = null,
                tint = ConstanzaColors.OnBackgroundMuted,
                modifier = Modifier.size(MissedGlyphSize),
            )
        }
        RecapMark.NONE -> Spacer(Modifier.size(MarkSize))
    }
}

@Composable
private fun Legend() {
    Row(
        modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xxl).semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            stringResource(R.string.weekly_recap_legend_done),
            style = MaterialTheme.typography.bodyMedium,
            color = ConstanzaColors.OnBackgroundMuted,
        )
        Text(
            stringResource(R.string.weekly_recap_legend_missed),
            style = MaterialTheme.typography.bodyMedium,
            color = ConstanzaColors.OnBackgroundMuted,
        )
    }
}
