package com.jjrapps.constanza.reminding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.ScreenTopBar
import com.jjrapps.constanza.core.ui.component.SectionHeader
import com.jjrapps.constanza.core.ui.icons.ConstanzaIcons
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.Spacing
import com.jjrapps.constanza.localization.LanguageSection
import com.jjrapps.constanza.portability.DataPortabilitySection

private const val MINUTES_PER_HOUR = 60

/** Task 6b.5 — container. */
@Composable
fun SnoozeSettingsRoute(onBack: () -> Unit, viewModel: SnoozeSettingsViewModel = hiltViewModel()) {
    val current by viewModel.currentDuration.collectAsState()
    SnoozeSettingsScreen(current = current, onSelect = viewModel::select, onBack = onBack)
}

/**
 * The three Hilt-backed sections below the snooze chips, as slots. Production passes nothing and
 * gets the real sections; a test or render passes their presentational halves, which is what lets
 * the whole screen be drawn without a Hilt-enabled Activity.
 */
data class SettingsSections(
    val dayReview: @Composable () -> Unit = { DayReviewSection() },
    val language: @Composable () -> Unit = { LanguageSection() },
    val data: @Composable () -> Unit = { DataPortabilitySection() },
)

/**
 * Presentational: exactly the seven [SnoozeDuration] values, default 20 minutes
 * (reminder-response: Snooze Configuration and Re-arm), followed by the day-review, language and
 * data sections.
 *
 * Graphite redesign: chevron back and the title; the snooze durations as a wrap of pill chips; the
 * sections in the board's order — default snooze, day review, language, data & backup. Each export
 * and import, day-review and language section still lives on this one screen rather than its own
 * route (see each section's KDoc).
 */
@Composable
fun SnoozeSettingsScreen(
    current: SnoozeDuration,
    onSelect: (SnoozeDuration) -> Unit,
    onBack: () -> Unit = {},
    sections: SettingsSections = SettingsSections(),
) {
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = ConstanzaIcons.ChevronStart,
                navigationLabel = stringResource(R.string.action_back),
                onNavigate = onBack,
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            // app-localization: the screen title is the generic "Settings", so the snooze chips
            // carry their own heading.
            item { SectionHeader(stringResource(R.string.settings_snooze_section_title)) }
            item {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    SnoozeDuration.entries.forEach { duration ->
                        SnoozeDurationChip(duration, duration == current, onSelect)
                    }
                }
            }
            item { sections.dayReview() }
            item { sections.language() }
            item { sections.data() }
        }
    }
}

/**
 * One snooze duration as a 40dp pill chip: selected is light-filled with dark ink, the others
 * outlined in the control stroke (3.53:1). It keeps the radio semantics the old radio row had —
 * `selectable` with [Role.RadioButton] inside the row's `selectableGroup` — so TalkBack still
 * announces "selected, 1 of 7" and a test can still ask which duration is selected.
 *
 * `internal` rather than `private` so `SnoozeSectionHeadingComposeTest` can render the actual
 * production chip next to the actual production section heading.
 */
@Composable
internal fun SnoozeDurationChip(duration: SnoozeDuration, selected: Boolean, onSelect: (SnoozeDuration) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .heightIn(min = Dimens.Chip)
            .clip(CircleShape)
            .background(if (selected) colors.primary else Color.Transparent)
            .border(BorderStroke(Dimens.FieldBorder, if (selected) colors.primary else colors.outline), CircleShape)
            .selectable(selected = selected, onClick = { onSelect(duration) }, role = Role.RadioButton)
            .padding(horizontal = Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            durationLabel(duration),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
            color = if (selected) colors.onPrimary else colors.onBackground,
        )
    }
}

@Composable
private fun durationLabel(duration: SnoozeDuration): String =
    if (duration.minutes < MINUTES_PER_HOUR) {
        stringResource(R.string.settings_snooze_minutes, duration.minutes)
    } else {
        stringResource(R.string.settings_snooze_hours, duration.minutes / MINUTES_PER_HOUR)
    }
