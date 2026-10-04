package com.jjrapps.constanza.reminding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.SectionHeader
import com.jjrapps.constanza.core.ui.component.SettingsNavigationRow
import com.jjrapps.constanza.core.ui.component.SettingsRowDivider
import com.jjrapps.constanza.core.ui.component.SingleChoiceDialog
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.Spacing
import com.jjrapps.constanza.core.ui.rememberTimeOfDayFormat
import com.jjrapps.constanza.habit.ReminderTimeField

/**
 * day-review, slice C (day-review-notification): the nightly day-review's own settings, as a
 * fourth section on this same Settings screen — [com.jjrapps.constanza.portability
 * .DataPortabilitySection] and [com.jjrapps.constanza.localization.LanguageSection]'s own
 * precedent, so no new route.
 */
@Composable
fun DayReviewSection(viewModel: DayReviewSettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    DayReviewSectionContent(
        uiState = uiState,
        onReviewTimeChange = viewModel::setReviewTimeMinuteOfDay,
        onFiresEveryNightChange = viewModel::setReviewFiresEveryNight,
    )
}

/**
 * Presentational half, following this codebase's container/presentational split — see
 * [com.jjrapps.constanza.localization.LanguageSectionContent]'s own reasoning: it takes state and
 * callbacks and owns none itself (beyond whether its dialog is open), so a Compose test can drive it
 * with `createComposeRule()` and no Hilt-enabled Activity.
 *
 * Graphite redesign: two list rows. "Review time" is [ReminderTimeField], the app's one
 * time-of-day control (label left, time right). "When to notify" shows the current mode as its
 * subtitle and opens a small dialog holding the two named options.
 *
 * **Two named options rather than a `Switch`.** This setting is a boolean in Kotlin but not one on
 * screen: its off position still posts a notification, just a conditional one. A switch labelled
 * "every night" would read as "notify me / do not notify me", which is the one thing it does not
 * mean. Two named options each say what they do.
 *
 * The 23:45 ceiling ([REVIEW_TIME_LATEST_MINUTE_OF_DAY]) is surfaced as supporting text under the
 * time row rather than blocked in the picker itself: [ReminderTimeField]'s dialog is shared with
 * the habit editor's own reminder times, which carry no such ceiling. Stating the ceiling in copy —
 * in the device's own time format, via [rememberTimeOfDayFormat] — keeps
 * [DayReviewSettingsStore]'s silent clamp from reading as a lie.
 */
@Composable
fun DayReviewSectionContent(
    uiState: DayReviewSettingsUiState,
    onReviewTimeChange: (Int) -> Unit,
    onFiresEveryNightChange: (Boolean) -> Unit,
) {
    var showModeDialog by rememberSaveable { mutableStateOf(false) }
    Column {
        SectionHeader(stringResource(R.string.settings_day_review_section_title))
        ReminderTimeField(
            minuteOfDay = uiState.reviewTimeMinuteOfDay,
            onMinuteOfDayChange = onReviewTimeChange,
            label = stringResource(R.string.settings_day_review_time_label),
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
        val timeFormat = rememberTimeOfDayFormat()
        Text(
            stringResource(
                R.string.settings_day_review_time_supporting,
                timeFormat.format(REVIEW_TIME_LATEST_MINUTE_OF_DAY),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = ConstanzaColors.OnBackgroundMuted,
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md),
        )
        SettingsRowDivider()
        SettingsNavigationRow(
            title = stringResource(R.string.settings_day_review_mode_label),
            subtitle = stringResource(modeLabelRes(uiState.reviewFiresEveryNight)),
            onClick = { showModeDialog = true },
        )
        SettingsRowDivider()
    }
    if (showModeDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_day_review_mode_label),
            options = listOf(true, false).map { it to stringResource(modeLabelRes(it)) },
            selected = uiState.reviewFiresEveryNight,
            onSelect = {
                onFiresEveryNightChange(it)
                showModeDialog = false
            },
            onDismiss = { showModeDialog = false },
        )
    }
}

@StringRes
private fun modeLabelRes(firesEveryNight: Boolean): Int = if (firesEveryNight) {
    R.string.settings_day_review_mode_every_night
} else {
    R.string.settings_day_review_mode_only_if_unanswered
}
