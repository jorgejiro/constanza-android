@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jjrapps.constanza.reminding

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjrapps.constanza.R
import com.jjrapps.constanza.portability.DataPortabilitySection

private const val MINUTES_PER_HOUR = 60

/** Task 6b.5 — container. */
@Composable
fun SnoozeSettingsRoute(onBack: () -> Unit, viewModel: SnoozeSettingsViewModel = hiltViewModel()) {
    val current by viewModel.currentDuration.collectAsState()
    SnoozeSettingsScreen(current = current, onSelect = viewModel::select, onBack = onBack)
}

/** Presentational: exactly the seven [SnoozeDuration] values, default 20 minutes
 *  (reminder-response: Snooze Configuration and Re-arm). */
@Composable
fun SnoozeSettingsScreen(current: SnoozeDuration, onSelect: (SnoozeDuration) -> Unit, onBack: () -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_snooze_title)) },
                actions = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(SnoozeDuration.entries, key = { it.name }) { duration ->
                SnoozeDurationRow(duration, duration == current, onSelect)
            }
            // Tasks 7.2/7.3/7.4: export/import lives on this same Settings screen, not a new
            // route — see DataPortabilitySection's own KDoc for why.
            item { DataPortabilitySection() }
        }
    }
}

@Composable
private fun SnoozeDurationRow(duration: SnoozeDuration, selected: Boolean, onSelect: (SnoozeDuration) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = { onSelect(duration) }, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(durationLabel(duration), modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun durationLabel(duration: SnoozeDuration): String =
    if (duration.minutes < MINUTES_PER_HOUR) {
        stringResource(R.string.settings_snooze_minutes, duration.minutes)
    } else {
        stringResource(R.string.settings_snooze_hours, duration.minutes / MINUTES_PER_HOUR)
    }
