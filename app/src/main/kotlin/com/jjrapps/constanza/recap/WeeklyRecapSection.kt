package com.jjrapps.constanza.recap

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.SectionHeader
import com.jjrapps.constanza.core.ui.component.SettingsRowDivider
import com.jjrapps.constanza.core.ui.component.SettingsSwitchRow
import com.jjrapps.constanza.core.ui.rememberTimeOfDayFormat

/** Container: the weekly recap's Settings section. */
@Composable
fun WeeklyRecapSection(viewModel: WeeklyRecapSettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.enabled.collectAsState()
    WeeklyRecapSectionContent(enabled = enabled, onEnabledChange = viewModel::setEnabled)
}

/** Presentational: a heading and one switch row saying when the recap arrives. */
@Composable
fun WeeklyRecapSectionContent(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.settings_weekly_recap_section_title))
        SettingsSwitchRow(
            title = stringResource(R.string.settings_weekly_recap_toggle),
            subtitle = stringResource(
                R.string.settings_weekly_recap_toggle_supporting,
                rememberTimeOfDayFormat().format(WEEKLY_RECAP_MINUTE_OF_DAY),
            ),
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
        SettingsRowDivider()
    }
}
