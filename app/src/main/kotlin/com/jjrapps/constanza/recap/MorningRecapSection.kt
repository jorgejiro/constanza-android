package com.jjrapps.constanza.recap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.SectionHeader
import com.jjrapps.constanza.core.ui.component.SettingsRowDivider
import com.jjrapps.constanza.core.ui.component.SettingsSwitchRow
import com.jjrapps.constanza.core.ui.theme.Spacing
import com.jjrapps.constanza.habit.ReminderTimeField

/** day-review-and-morning-recap (S6): the morning recap's own section on the Settings screen. */
@Composable
fun MorningRecapSection(viewModel: MorningRecapSettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    MorningRecapSectionContent(
        uiState = uiState,
        onEnabledChange = viewModel::setEnabled,
        onTimeChange = viewModel::setTimeMinuteOfDay,
    )
}

/** Presentational half: a switch, and — only while it is on, as in
 *  [com.jjrapps.constanza.reminding.DayReviewSectionContent] — the app's one time-of-day control. */
@Composable
fun MorningRecapSectionContent(
    uiState: MorningRecapSettingsUiState,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (Int) -> Unit,
) {
    Column {
        SectionHeader(stringResource(R.string.settings_morning_recap_section_title))
        SettingsSwitchRow(
            title = stringResource(R.string.settings_morning_recap_toggle),
            subtitle = stringResource(R.string.settings_morning_recap_toggle_supporting),
            checked = uiState.enabled,
            onCheckedChange = onEnabledChange,
        )
        SettingsRowDivider()
        if (uiState.enabled) {
            ReminderTimeField(
                minuteOfDay = uiState.timeMinuteOfDay,
                onMinuteOfDayChange = onTimeChange,
                label = stringResource(R.string.settings_morning_recap_time_label),
                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md),
            )
            SettingsRowDivider()
        }
    }
}
