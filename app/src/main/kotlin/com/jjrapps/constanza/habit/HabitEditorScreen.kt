@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jjrapps.constanza.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjrapps.constanza.R

/**
 * Tasks 6a.1 (non-schedule half)/6a.2/6a.3 — container. [habitId] is `null` for creation, an
 * existing id for editing; the effect below resets/loads the [HabitEditorViewModel] accordingly
 * whenever it changes, since this single [viewModel] instance is reused across every visit to
 * this route in this Activity-scoped, no-navigation-library setup (design.md §14).
 */
@Composable
fun HabitEditorRoute(
    habitId: Long?,
    onDone: () -> Unit,
    viewModel: HabitEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(habitId) {
        if (habitId == null) viewModel.startCreate() else viewModel.startEdit(habitId)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { onDone() }
    }
    val state by viewModel.uiState.collectAsState()
    HabitEditorScreen(
        state = state,
        actions = HabitEditorActions(
            onNameChange = viewModel::onNameChange,
            onQuestionChange = viewModel::onQuestionChange,
            onColorChange = viewModel::onColorChange,
            onNotesChange = viewModel::onNotesChange,
            onSave = viewModel::save,
        ),
    )
}

/** Bundles [HabitEditorScreen]'s callbacks, keeping it under detekt's `LongParameterList`
 *  threshold — same reasoning as `scheduling.SchedulingDaos`/`habit.HabitDaos`. */
data class HabitEditorActions(
    val onNameChange: (String) -> Unit,
    val onQuestionChange: (String) -> Unit,
    val onColorChange: (Int) -> Unit,
    val onNotesChange: (String) -> Unit,
    val onSave: () -> Unit,
)

/** Presentational: state in, callbacks out, no dependencies of its own. No fixed orientation and
 *  no hardcoded widths — a single responsive layout that scrolls, so it does not structurally
 *  block slice ii's C1/C4 adaptive verification (ui-adaptive-layout). */
@Composable
fun HabitEditorScreen(state: HabitEditorUiState, actions: HabitEditorActions) {
    val titleRes = if (state.habitId == null) {
        R.string.habit_editor_title_create
    } else {
        R.string.habit_editor_title_edit
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(titleRes)) }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = actions.onNameChange,
                label = { Text(stringResource(R.string.habit_editor_name_label)) },
                isError = state.nameError,
                supportingText = if (state.nameError) {
                    { Text(stringResource(R.string.habit_editor_name_error)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.question,
                onValueChange = actions.onQuestionChange,
                label = { Text(stringResource(R.string.habit_editor_question_label)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = actions.onNotesChange,
                label = { Text(stringResource(R.string.habit_editor_notes_label)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.habit_editor_color_label),
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            ColorSwatchRow(selected = state.colorArgb, onColorChange = actions.onColorChange)
            Button(onClick = actions.onSave, modifier = Modifier.padding(top = 24.dp)) {
                Text(stringResource(R.string.habit_editor_save))
            }
        }
    }
}

private val SWATCH_SIZE = 40.dp
private val SWATCH_BORDER = 3.dp

@Composable
private fun ColorSwatchRow(selected: Int, onColorChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HabitColorPalette.SWATCHES.forEach { swatch ->
            val borderColor = if (swatch == selected) MaterialTheme.colorScheme.primary else Color.Transparent
            Row(
                modifier = Modifier
                    .size(SWATCH_SIZE)
                    .clip(CircleShape)
                    .background(Color(swatch))
                    .border(SWATCH_BORDER, borderColor, CircleShape)
                    .clickable { onColorChange(swatch) },
            ) {}
        }
    }
}
