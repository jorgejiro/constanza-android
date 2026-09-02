@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jjrapps.constanza.habit

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.HabitPalette

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
 *
 * **[onBack] and the discard confirmation (carried-forward item
 * `habit-editor-has-no-cancel-affordance`).** Before this, the only exit from the editor was a
 * successful [onDone]; the top bar had no navigation icon and nothing in the app handled system
 * back, so backing out of a half-filled form closed the whole app. [onBack] follows the same
 * hoisted-callback convention [com.jjrapps.constanza.progress.ProgressRoute] and
 * [com.jjrapps.constanza.reminding.SnoozeSettingsRoute] already use.
 *
 * Leaving is immediate when nothing was touched ([HabitEditorUiState.isDirty] is `false`) — a
 * confirmation on an untouched form is pure nagging. Otherwise the discard dialog is shown first.
 * Its visibility is [rememberSaveable] here rather than ViewModel state for the same reason
 * [hasInitialized] is: it is the container's navigation concern, and it must survive the
 * configuration change that disposes this composition, so a rotation with the dialog open does not
 * silently drop the question the user was answering.
 */
@Composable
fun HabitEditorRoute(
    habitId: Long?,
    onDone: () -> Unit,
    onBack: () -> Unit,
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
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val requestBack = { if (state.isDirty) showDiscardDialog = true else onBack() }

    // The first BackHandler in this codebase, and the reason the rest of the app does not need one:
    // every other screen is either the Activity's start destination (Today, where the platform
    // default of finishing the Activity IS the right behaviour) or a leaf whose only unsaved state
    // is nothing at all. This editor is the one screen holding work the user can lose, so it is the
    // one screen that has to intercept the gesture instead of letting it reach the Activity. Kept
    // here, in the container, so HabitEditorScreen stays presentational and the icon and the
    // gesture provably share one code path: both call requestBack.
    //
    // Disabled while the dialog is up so the two do not stack. The dialog is its own window and
    // handles back through onDismissRequest; leaving this enabled would be a second handler
    // competing for the same gesture the moment that stops being true.
    BackHandler(enabled = !showDiscardDialog) { requestBack() }

    HabitEditorScreen(
        state = state,
        actions = HabitEditorActions(
            onNameChange = viewModel::onNameChange,
            onQuestionChange = viewModel::onQuestionChange,
            onColorChange = viewModel::onColorChange,
            onNotesChange = viewModel::onNotesChange,
            onSave = viewModel::save,
            onBackRequest = requestBack,
            onDiscardConfirm = {
                showDiscardDialog = false
                onBack()
            },
            onDiscardDismiss = { showDiscardDialog = false },
        ),
        showDiscardDialog = showDiscardDialog,
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
    /** The back arrow. Whether it leaves straight away or opens the discard dialog is the
     *  container's decision, not this screen's — see [HabitEditorRoute]. */
    val onBackRequest: () -> Unit = {},
    val onDiscardConfirm: () -> Unit = {},
    val onDiscardDismiss: () -> Unit = {},
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
    showDiscardDialog: Boolean = false,
) {
    val titleRes = if (state.habitId == null) {
        R.string.habit_editor_title_create
    } else {
        R.string.habit_editor_title_edit
    }
    if (showDiscardDialog) {
        DiscardChangesDialog(onConfirm = actions.onDiscardConfirm, onDismiss = actions.onDiscardDismiss)
    }
    Scaffold(
        topBar = { HabitEditorTopBar(titleRes, actions.onBackRequest) },
        containerColor = ConstanzaColors.Background,
    ) { padding ->
        HabitEditorForm(
            state = state,
            actions = actions,
            onScheduleParamChange = onScheduleParamChange,
            onSlotAction = onSlotAction,
            modifier = Modifier.padding(padding),
        )
    }
}

/** The scrolling form itself, split out of [HabitEditorScreen] so that composable stays the
 *  screen's frame — title, chrome, and the discard dialog — rather than growing past detekt's
 *  `LongMethod` threshold every time a field is added. Same presentational contract: state in,
 *  callbacks out. */
@Composable
private fun HabitEditorForm(
    state: HabitEditorUiState,
    actions: HabitEditorActions,
    onScheduleParamChange: (ScheduleParamAction) -> Unit,
    onSlotAction: (SlotAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
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

/** Task 6.0 decision: the unit-5 pin to [ConstanzaColors.Background] (`colors =
 *  TopAppBarDefaults.topAppBarColors(containerColor = ConstanzaColors.Background)`) is REMOVED. Its
 *  own KDoc justification — "`surfaceContainer` is not one of the roles `ConstanzaColors` repoints" —
 *  is exactly the gap task 6.0 closes at the theme layer: `Theme.kt`'s `DarkColors` now binds
 *  `surfaceContainer` to [ConstanzaColors.Surface], so the bar is warm by default across every
 *  screen, not just this one. Keeping a per-screen override here would silently re-diverge from
 *  every other top bar in the app (`ProgressScreen`, `SnoozeSettingsScreen`) the moment either of
 *  those needed a different tone, for no remaining reason. The composable extraction itself is kept
 *  — it still exists purely to hold [titleRes], the same reason `EditorNameField` is its own
 *  composable, not to hold a colour override.
 *
 *  The navigation icon is the editor's cancel affordance (carried-forward item
 *  `habit-editor-has-no-cancel-affordance`). It is an icon here rather than the `actions`-slot
 *  "Back" text button `ProgressScreen`/`SnoozeSettingsScreen` use: those two are read-only leaves
 *  where back is a minor action, while this screen's whole purpose is a form the user may need to
 *  abandon, and the leading navigation slot is where every Android user already looks for that.
 *  [Icons.AutoMirrored.Filled.ArrowBack] ships in `material-icons-core`, the only icon artifact
 *  this project depends on (`app/build.gradle.kts`), so no new dependency is pulled in; the
 *  auto-mirrored variant flips itself in right-to-left locales. `contentDescription` reuses the
 *  existing `action_back` string rather than adding a second resource with the same word in it. */
@Composable
private fun HabitEditorTopBar(titleRes: Int, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(titleRes)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
        },
    )
}

/** The discard confirmation (carried-forward item `habit-editor-has-no-cancel-affordance`). Shown
 *  only when the form is actually dirty — [HabitEditorRoute] makes that call, this composable only
 *  renders. A plain M3 [AlertDialog]: every colour role it reads (`surfaceContainerHigh`, `scrim`,
 *  `onSurface`, `onSurfaceVariant`, `primary`) is already audited in `core/ui/theme/Theme.kt`, and
 *  `DataPortabilityScreen`'s import confirmation is the same shape, so this introduces no new
 *  theming surface. The dismiss button reuses `action_cancel` for the same reason that one does. */
@Composable
private fun DiscardChangesDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.habit_editor_discard_title)) },
        text = { Text(stringResource(R.string.habit_editor_discard_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.habit_editor_discard_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
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

@Composable
private fun ColorSwatchRow(selected: Int, onColorChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HabitPalette.ARGB.forEach { swatch ->
            val borderColor = if (swatch == selected) MaterialTheme.colorScheme.primary else Color.Transparent
            Row(
                modifier = Modifier
                    .size(Dimens.Swatch)
                    .clip(CircleShape)
                    .background(Color(swatch))
                    .border(Dimens.SwatchBorder, borderColor, CircleShape)
                    .clickable { onColorChange(swatch) },
            ) {}
        }
    }
}
