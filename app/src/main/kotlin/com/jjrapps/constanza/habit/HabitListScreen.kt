@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jjrapps.constanza.habit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.HabitColorDot
import com.jjrapps.constanza.domain.model.Habit

/**
 * Task 6a.4 (habit-management: Habit Archiving) — container. No navigation library is used
 * (design.md §14 defers that to a future work unit once a second stack of screens exists);
 * [onBack]/[onCreateHabit]/[onEditHabit] are hoisted callbacks the single-Activity host wires to its
 * own in-memory route state.
 *
 * **[onBack] (habit-list-back-navigation).** This screen used to have no exit of any kind: no
 * navigation icon, no `BackHandler`, and a host that hoists a one-way route — so the system back
 * gesture reached the Activity default and closed the whole app on a user who had only come here to
 * manage a habit. [onBack] is deliberately required rather than defaulted to `{}`, for the same
 * reason [com.jjrapps.constanza.progress.ProgressRoute] and [HabitEditorRoute] require theirs: a
 * caller that forgets it is exactly the defect this parameter exists to make impossible, and a
 * default would let it come back silently.
 */
@Composable
fun HabitListRoute(
    onBack: () -> Unit,
    onCreateHabit: () -> Unit,
    onEditHabit: (Long) -> Unit,
    onShowProgress: (Long) -> Unit = {},
    viewModel: HabitListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    HabitListScreen(
        state = state,
        actions = HabitListActions(
            onBack = onBack,
            onToggleShowArchived = viewModel::toggleShowArchived,
            onArchiveToggle = viewModel::setArchived,
            onCreateHabit = onCreateHabit,
            onEditHabit = onEditHabit,
            onShowProgress = onShowProgress,
            onDeleteHabit = viewModel::delete,
        ),
    )
}

/** Bundles [HabitListScreen]'s callbacks, keeping it under detekt's `LongParameterList`
 *  threshold — same reasoning as [HabitEditorActions]. [onDeleteHabit] (habit-management: Habit
 *  Deletion) is called only after [DeleteHabitDialog] is confirmed — nothing runs before that. */
data class HabitListActions(
    /** The way out of the list, shared by the top bar's back arrow and the system back gesture —
     *  see [HabitListScreen]. Bundled here rather than being its own [HabitListScreen] parameter so
     *  that "callbacks out" stays one thing in this file, matching [HabitEditorActions.onBackRequest]
     *  rather than [com.jjrapps.constanza.progress.ProgressScreen]'s loose parameter. */
    val onBack: () -> Unit,
    val onToggleShowArchived: () -> Unit,
    val onArchiveToggle: (Long, Boolean) -> Unit,
    val onCreateHabit: () -> Unit,
    val onEditHabit: (Long) -> Unit,
    val onShowProgress: (Long) -> Unit = {},
    val onDeleteHabit: (Long) -> Unit = {},
)

/**
 * Presentational: state in, callbacks out, no dependencies of its own beyond the back gesture
 * itself.
 *
 * [pendingDeleteId] (task 3.3, design.md D4) is `rememberSaveable` local state, not
 * ViewModel-held, matching [HabitEditorScreen]'s `DiscardChangesDialog` precedent and surviving
 * rotation. It is resolved back to a [com.jjrapps.constanza.domain.model.Habit] by id from
 * [state]'s own list on every recomposition, rather than captured once: a habit that leaves the
 * list (for instance because it was archived, filtering it out of the current view) dismisses its
 * own dialog instead of rendering a stale name against a habit id that no longer resolves.
 *
 * **The [BackHandler] lives here, not in [HabitListRoute]** (habit-list-back-navigation), which is
 * the opposite of where [HabitEditorRoute] keeps its own. That is not an inconsistency: the editor's
 * handler exists to make a DECISION — leave, or ask about unsaved edits first — and a decision is
 * the container's job. This list holds nothing the user can lose, so there is no decision to make
 * and nothing for a container to own; keeping the handler beside the back arrow is what makes it
 * self-evident that the gesture and the icon call the one same `actions.onBack` rather than merely
 * looking like they should. It is disabled while [DeleteHabitDialog] is up for the reason the
 * editor's is: the dialog is its own window and answers back through its own `onDismissRequest`, so
 * an enabled handler behind it would be a second claimant on the same gesture the moment that stops
 * being true — and here the consequence would be leaving the screen instead of cancelling a delete.
 */
@Composable
fun HabitListScreen(state: HabitListUiState, actions: HabitListActions) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val pendingDeleteHabit = state.habits.firstOrNull { it.id == pendingDeleteId }
    BackHandler(enabled = pendingDeleteHabit == null, onBack = actions.onBack)
    if (pendingDeleteHabit != null) {
        DeleteHabitDialog(
            habitName = pendingDeleteHabit.name,
            entryCount = state.entryCounts[pendingDeleteHabit.id] ?: 0,
            onConfirm = {
                actions.onDeleteHabit(pendingDeleteHabit.id)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
    Scaffold(
        topBar = { HabitListTopBar(actions.onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = actions.onCreateHabit) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.habit_list_add_habit))
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            HabitListContent(state, actions, onRequestDelete = { pendingDeleteId = it })
        }
    }
}

/** habit-list-back-navigation. Deliberately the same shape as `HabitEditorScreen`'s
 *  `HabitEditorTopBar`: an [Icons.AutoMirrored.Filled.ArrowBack] in the leading navigation slot,
 *  not the `actions`-slot "Back" text button `ProgressScreen`/`SnoozeSettingsScreen` use. Those two
 *  are read-only leaves a user glances at; this screen is somewhere a user does work and must be
 *  able to walk out of, and the leading slot is where every Android user already looks for that. The
 *  icon ships in `material-icons-core` — already this project's only icon artifact — and its
 *  auto-mirrored variant flips itself in right-to-left locales. `contentDescription` reuses the
 *  existing `action_back` string rather than adding a second resource with the same word in it. */
@Composable
private fun HabitListTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.habit_list_title)) },
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

@Composable
private fun HabitListContent(state: HabitListUiState, actions: HabitListActions, onRequestDelete: (Long) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { ShowArchivedRow(state.showArchived, actions.onToggleShowArchived) }
        if (state.habits.isEmpty()) {
            item {
                val emptyRes = if (state.showArchived) {
                    R.string.habit_list_empty_archived
                } else {
                    R.string.habit_list_empty_active
                }
                Text(stringResource(emptyRes), modifier = Modifier.padding(16.dp))
            }
        }
        items(state.habits, key = { it.id }) { habit ->
            HabitRow(habit, actions.onArchiveToggle, actions.onEditHabit, actions.onShowProgress, onRequestDelete)
        }
    }
}

/**
 * The whole row toggles, not just the switch. `toggleable` sits before `padding` so the padded
 * surface is part of the target, and the switch takes `onCheckedChange = null` so it reports state
 * without handling the click twice — the standard Material pattern for a labelled setting.
 *
 * This is also what makes the row one merged semantics node carrying both the label and the toggle
 * state, instead of a clickable switch beside an inert `Text`. That earlier shape gave a screen
 * reader an orphan label and a control with no name, shrank the target to the switch alone, and made
 * `onNodeWithText(label).performClick()` a no-op — which is how `HabitListArchiveComposeTest` found
 * it. The test was right and the row was wrong.
 */
@Composable
private fun ShowArchivedRow(showArchived: Boolean, onToggleShowArchived: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = showArchived,
                onValueChange = { onToggleShowArchived() },
                role = Role.Switch,
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.habit_list_show_archived))
        Switch(checked = showArchived, onCheckedChange = null)
    }
}

/**
 * Design.md D2: Progress and Archive stay the row's inline [TextButton]s exactly as before; Delete
 * is the only action moved behind the trailing [IconButton]'s [DropdownMenu]. That split keeps an
 * irreversible action from sharing the reversible ones' one-tap visual weight, and it leaves the
 * "Archive"/"Un-archive" text nodes `CoreFlowE2ETest` and `HabitListArchiveComposeTest` already
 * locate by text untouched.
 */
@Composable
private fun HabitRow(
    habit: Habit,
    onArchiveToggle: (Long, Boolean) -> Unit,
    onEditHabit: (Long) -> Unit,
    onShowProgress: (Long) -> Unit,
    onRequestDelete: (Long) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    ListItem(
        leadingContent = { HabitColorDot(habit.colorArgb) },
        headlineContent = { Text(habit.name) },
        supportingContent = habit.question?.let { question -> { Text(question) } },
        trailingContent = {
            Row {
                TextButton(onClick = { onShowProgress(habit.id) }) {
                    Text(stringResource(R.string.habit_list_progress))
                }
                TextButton(onClick = { onArchiveToggle(habit.id, !habit.archived) }) {
                    Text(
                        stringResource(
                            if (habit.archived) R.string.habit_list_unarchive else R.string.habit_list_archive,
                        ),
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.habit_list_more_options),
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.habit_list_delete)) },
                            onClick = {
                                menuExpanded = false
                                onRequestDelete(habit.id)
                            },
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable { onEditHabit(habit.id) },
    )
}

/** habit-management: Habit Deletion (design.md D4). Same [AlertDialog] shape as
 *  [HabitEditorScreen]'s `DiscardChangesDialog`; what differs is what is at stake, so unlike that
 *  dialog this one names the subject and the exact count destroyed. [entryCount] renders through
 *  [pluralStringResource] rather than a hand-picked string, so zero renders as the honest "0
 *  recorded answers will be…" via the `other` category — English has no CLDR `zero`, and nothing
 *  branches on the count beyond its own copy. */
@Composable
private fun DeleteHabitDialog(habitName: String, entryCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.habit_delete_dialog_title, habitName)) },
        text = { Text(pluralStringResource(R.plurals.habit_delete_dialog_body, entryCount, entryCount)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.habit_delete_dialog_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
