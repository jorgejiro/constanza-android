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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjrapps.constanza.R

/**
 * Tasks 6a.1 (non-schedule half)/6a.2/6a.3 — container. [habitId] is `null` for creation, an
 * existing id for editing; the effect below resets/loads the [HabitEditorViewModel] accordingly
 * whenever it changes, since this single [viewModel] instance is reused across every visit to
 * this route in this Activity-scoped, no-navigation-library setup (design.md §14).
 *
 * Task 6a.7 (ui-adaptive-layout: no content loss across a configuration change). [hasInitialized]
 * guards the reset/load effect so it runs once per [habitId], not once per composition: a
 * configuration change disposes and rebuilds this whole composable, which would otherwise re-run
 * `startCreate()`/`startEdit()` on the SAME retained [viewModel] and silently wipe whatever the
 * user had typed — the ViewModel instance itself survives a real rotation via its ViewModelStore,
 * but a plain (non-saveable) `LaunchedEffect` key does not know that and fires again regardless.
 * Keying `rememberSaveable` on [habitId] means navigating to a genuinely different habit still
 * re-initializes correctly, while a same-habit config change restores `hasInitialized = true` and
 * leaves the already-in-progress state alone. Found via task 6a.7's own rotation test.
 */
@Composable
fun HabitEditorRoute(
    habitId: Long?,
    onDone: () -> Unit,
    viewModel: HabitEditorViewModel = hiltViewModel(),
) {
    var hasInitialized by rememberSaveable(habitId) { mutableStateOf(false) }
    LaunchedEffect(habitId) {
        if (!hasInitialized) {
            if (habitId == null) viewModel.startCreate() else viewModel.startEdit(habitId)
            hasInitialized = true
        }
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
        onScheduleParamChange = viewModel::onScheduleParamChange,
        onSlotAction = viewModel::onSlotAction,
    )
}

/** Bundles [HabitEditorScreen]'s non-schedule callbacks, keeping it under detekt's
 *  `LongParameterList` threshold — same reasoning as `scheduling.SchedulingDaos`/`habit.HabitDaos`. */
data class HabitEditorActions(
    val onNameChange: (String) -> Unit,
    val onQuestionChange: (String) -> Unit,
    val onColorChange: (Int) -> Unit,
    val onNotesChange: (String) -> Unit,
    val onSave: () -> Unit,
)

/** Presentational: state in, callbacks out, no dependencies of its own. No fixed orientation and
 *  no hardcoded widths — a single responsive layout that scrolls, so it does not structurally
 *  block slice ii-b's C1/C4 adaptive verification (ui-adaptive-layout). [onScheduleParamChange]/
 *  [onSlotAction] are single sealed-action callbacks (task 6a.1, slice ii-a) rather than one lambda
 *  per field — the same `LongParameterList`-avoidance reasoning as [HabitEditorActions], without
 *  needing a wrapper data class for either. IME and caret restoration across a configuration change
 *  (tasks 6a.5/6a.9) is [focusRestoring], applied to all three text fields and sharing one saveable
 *  record of which field held focus. */
@Composable
fun HabitEditorScreen(
    state: HabitEditorUiState,
    actions: HabitEditorActions,
    onScheduleParamChange: (ScheduleParamAction) -> Unit,
    onSlotAction: (SlotAction) -> Unit,
) {
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
            // Shared across all three text fields on purpose (task 6a.9): it holds WHICH field
            // gained focus last, so a rotation restores the caret and keyboard where the user was
            // rather than always to the name field.
            val focusedFieldId = rememberSaveable { mutableStateOf<String?>(null) }
            EditorNameField(
                name = state.name,
                onNameChange = actions.onNameChange,
                nameError = state.nameError,
                focusedFieldId = focusedFieldId,
            )
            OutlinedTextField(
                value = state.question,
                onValueChange = actions.onQuestionChange,
                label = { Text(stringResource(R.string.habit_editor_question_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .then(focusRestoring(FIELD_QUESTION, focusedFieldId)),
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = actions.onNotesChange,
                label = { Text(stringResource(R.string.habit_editor_notes_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .then(focusRestoring(FIELD_NOTES, focusedFieldId)),
            )
            Text(
                stringResource(R.string.habit_editor_color_label),
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            ColorSwatchRow(selected = state.colorArgb, onColorChange = actions.onColorChange)
            Text(
                stringResource(R.string.habit_editor_schedule_label),
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            ScheduleSection(
                state = state,
                onScheduleParamChange = onScheduleParamChange,
                onSlotAction = onSlotAction,
            )
            Button(onClick = actions.onSave, modifier = Modifier.padding(top = 24.dp)) {
                Text(stringResource(R.string.habit_editor_save))
            }
        }
    }
}

/**
 * Task 6a.5 / 6a.9 (ui-adaptive-layout: Soft Keyboard Visibility Not Assumed Across Configuration
 * Change). Android 17 does not restore IME visibility across an unhandled configuration change, and
 * this app declares no `android:configChanges` (design.md §5.7 C4), so a rotation always destroys
 * and recreates the composition.
 *
 * **Latch on focus gain, ratified 2026-09-01 (task 6a.9).** The earlier shape kept a per-field
 * boolean written as `onFocusChanged { wasFocused = it.isFocused }`, which never worked: the callback
 * fires with `false` while the Activity is torn down and overwrote the flag before it was saved, so
 * the keyboard never came back and neither did the caret. Task G.7 measured that on the device —
 * `mInputShown` `true` → `false`, and no field focused afterwards (design.md §13.5, finding 2).
 *
 * The latch never clears, which is the ratified behaviour: a keyboard the user dismissed on purpose
 * does come back after a rotation. What it deliberately does **not** do is steal focus. A bare
 * per-field latch would have said "this field held focus at some point", so rotating while typing in
 * Notes would have thrown the caret back to Name. This latch records **which** field gained focus
 * last — the one that had it when the rotation happened — so restoration lands where the user was.
 */
/** Field ids for task 6a.9's focus latch. Stable strings rather than an enum so the
 *  `rememberSaveable` holding one needs no custom Saver. */
private const val FIELD_NAME = "name"
private const val FIELD_QUESTION = "question"
private const val FIELD_NOTES = "notes"

@Composable
private fun focusRestoring(fieldId: String, focusedFieldId: MutableState<String?>): Modifier {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (focusedFieldId.value == fieldId) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    return Modifier
        .focusRequester(focusRequester)
        .onFocusChanged { if (it.isFocused) focusedFieldId.value = fieldId }
}

@Composable
private fun EditorNameField(
    name: String,
    onNameChange: (String) -> Unit,
    nameError: Boolean,
    focusedFieldId: MutableState<String?>,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.habit_editor_name_label)) },
        isError = nameError,
        supportingText = if (nameError) {
            { Text(stringResource(R.string.habit_editor_name_error)) }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(focusRestoring(FIELD_NAME, focusedFieldId)),
    )
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
