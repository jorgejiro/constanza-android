package com.jjrapps.constanza.localization

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.SectionHeader
import com.jjrapps.constanza.core.ui.component.SettingsNavigationRow
import com.jjrapps.constanza.core.ui.component.SettingsRowDivider
import com.jjrapps.constanza.core.ui.component.SingleChoiceDialog

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
 * selection and the callback and owns no state beyond whether its dialog is open, so a Compose test
 * can drive it with `createComposeRule()` and no Hilt-enabled Activity.
 *
 * Graphite redesign: one row naming the current choice, opening a small dialog with the three
 * options as radio rows.
 */
@Composable
fun LanguageSectionContent(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    Column {
        SectionHeader(stringResource(R.string.settings_language_section_title))
        SettingsNavigationRow(title = languageLabel(selected), onClick = { showDialog = true })
        SettingsRowDivider()
    }
    if (showDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_language_section_title),
            options = AppLanguage.entries.map { it to languageLabel(it) },
            selected = selected,
            onSelect = {
                onSelect(it)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.SystemDefault -> stringResource(R.string.settings_language_system_default)
        AppLanguage.English -> stringResource(R.string.settings_language_english)
        AppLanguage.Spanish -> stringResource(R.string.settings_language_spanish)
    }
