package com.jjrapps.constanza.localization

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import com.jjrapps.constanza.R

/**
 * app-localization: Three-State Language Override — the picker, as a third section on the existing
 * Settings screen, following the precedent `DataPortabilitySection` already set. There is no new
 * route.
 *
 * The three options are fixed in order: System default, English, Español. Each language names
 * itself in its own language, which is the standard Android convention and the one thing a user
 * hunting for their language can always read — so "Español" is not translated in `values-es/`, and
 * neither is "English".
 *
 * [LifecycleStartEffect] re-reads on every `ON_START` (design.md D2) so a change made from Android
 * Settings on API 33+ is reflected here even when it happened while this screen was backgrounded.
 */
@Composable
fun LanguageSection(viewModel: LanguageSettingsViewModel = hiltViewModel()) {
    val selected by viewModel.selected.collectAsState()

    LifecycleStartEffect(viewModel) {
        viewModel.refresh()
        onStopOrDispose { }
    }

    LanguageSectionContent(selected = selected, onSelect = viewModel::select)
}

/**
 * Presentational half, following this codebase's container/presentational split: it takes the
 * selection and the callback and owns no state, so a Compose test can drive it with
 * `createComposeRule()` and no Hilt-enabled Activity.
 */
@Composable
fun LanguageSectionContent(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(stringResource(R.string.settings_language_section_title))
        AppLanguage.entries.forEach { language ->
            LanguageRow(
                language = language,
                selected = language == selected,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun LanguageRow(language: AppLanguage, selected: Boolean, onSelect: (AppLanguage) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = { onSelect(language) }, role = Role.RadioButton)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(languageLabel(language), modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.SystemDefault -> stringResource(R.string.settings_language_system_default)
        AppLanguage.English -> stringResource(R.string.settings_language_english)
        AppLanguage.Spanish -> stringResource(R.string.settings_language_spanish)
    }
