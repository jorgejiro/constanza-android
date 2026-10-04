package com.jjrapps.constanza.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.HabitDot
import com.jjrapps.constanza.core.ui.component.ProgressLine
import com.jjrapps.constanza.core.ui.component.ScreenTopBar
import com.jjrapps.constanza.core.ui.icons.ConstanzaIcons
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.Spacing
import kotlin.math.roundToInt

private const val PERCENT_MULTIPLIER = 100

/** The streak figures: 48sp/500 with tabular digits and -0.03em tracking (graphite board). */
private val StreakNumberSize = 48.sp
private val StreakNumberLineHeight = 52.sp
private const val STREAK_NUMBER_TRACKING = -0.03
private const val TABULAR_FIGURES = "tnum"

/** The compliance percentage, a step above body text so it reads as the row's value. */
private val ComplianceValueSize = 20.sp

/** Task 6b.4 — container. [habitId] is resolved once (no navigation library, task 6a's own
 *  decision, see [ProgressViewModel]'s KDoc); this screen is single-purpose so no
 *  `hasInitialized`/`rememberSaveable` guard is needed the way the editor's is — a fresh [load]
 *  call for the same id is a cheap no-op re-collection, not a content-loss risk. */
@Composable
fun ProgressRoute(habitId: Long, onBack: () -> Unit, viewModel: ProgressViewModel = hiltViewModel()) {
    LaunchedEffect(habitId) { viewModel.load(habitId) }
    val state by viewModel.uiState.collectAsState()
    ProgressScreen(state, onBack)
}

/** Presentational: state in, one callback out — this screen has no writes (habit-progress: Streak
 *  Calculation, Compliance Calculation are both compute-on-read, no I/O).
 *
 *  Graphite redesign: chevron back, the habit's dot and its name as the title; two streak columns
 *  with big tabular figures and a plural-aware "days" unit; a hairline; then the 30-day compliance
 *  label with its percentage right-aligned over a 4dp [ProgressLine]. */
@Composable
fun ProgressScreen(state: ProgressUiState, onBack: () -> Unit = {}) {
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = { ProgressTitle(state) },
                navigationIcon = ConstanzaIcons.ChevronStart,
                navigationLabel = stringResource(R.string.action_back),
                onNavigate = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        ) {
            if (!state.loaded) {
                Text(
                    stringResource(R.string.progress_no_schedule),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ConstanzaColors.OnBackgroundVariant,
                )
                return@Column
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                StreakFigure(stringResource(R.string.progress_current_streak), state.currentStreak, Modifier.weight(1f))
                StreakFigure(stringResource(R.string.progress_best_streak), state.bestStreak, Modifier.weight(1f))
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = Spacing.xl),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            ComplianceBlock(state.complianceRatio)
        }
    }
}

@Composable
private fun ProgressTitle(state: ProgressUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        state.habitColorArgb?.let { argb ->
            HabitDot(colorArgb = argb)
            Spacer(Modifier.width(Spacing.md))
        }
        Text(
            state.habitName.ifEmpty { stringResource(R.string.progress_title) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One streak column: label, then the figure with its unit on the same baseline. */
@Composable
private fun StreakFigure(label: String, days: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = ConstanzaColors.OnBackgroundVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                days.toString(),
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = StreakNumberSize,
                    lineHeight = StreakNumberLineHeight,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = STREAK_NUMBER_TRACKING.em,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                color = ConstanzaColors.OnBackground,
            )
            Text(
                pluralStringResource(R.plurals.progress_streak_days, days),
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
                color = ConstanzaColors.OnBackgroundVariant,
            )
        }
    }
}

@Composable
private fun ComplianceBlock(ratio: Double) {
    val compliancePercent = (ratio * PERCENT_MULTIPLIER).roundToInt()
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.progress_compliance_label),
            modifier = Modifier.weight(1f).alignByBaseline(),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = ConstanzaColors.OnBackgroundVariant,
        )
        Text(
            stringResource(R.string.progress_compliance, compliancePercent),
            modifier = Modifier.alignByBaseline(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = ComplianceValueSize,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            color = ConstanzaColors.OnBackground,
        )
    }
    Spacer(Modifier.height(Spacing.md))
    ProgressLine(fraction = ratio.toFloat())
}
