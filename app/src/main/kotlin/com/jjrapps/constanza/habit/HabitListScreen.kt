@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jjrapps.constanza.habit

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
 * [onCreateHabit]/[onEditHabit] are hoisted callbacks the single-Activity host wires to its own
 * in-memory route state.
 */
@Composable
fun HabitListRoute(
    onCreateHabit: () -> Unit,
    onEditHabit: (Long) -> Unit,
    onShowProgress: (Long) -> Unit = {},
    viewModel: HabitListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    HabitListScreen(
        state = state,
        actions = HabitListActions(
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
    val onToggleShowArchived: () -> Unit,
    val onArchiveToggle: (Long, Boolean) -> Unit,
    val onCreateHabit: () -> Unit,
    val onEditHabit: (Long) -> Unit,
    val onShowProgress: (Long) -> Unit = {},
    val onDeleteHabit: (Long) -> Unit = {},
)

/**
 * Presentational: state in, callbacks out, no dependencies of its own.
 *
 * [pendingDeleteId] (task 3.3, design.md D4) is `rememberSaveable` local state, not
 * ViewModel-held, matching [HabitEditorScreen]'s `DiscardChangesDialog` precedent and surviving
 * rotation. It is resolved back to a [com.jjrapps.constanza.domain.model.Habit] by id from
 * [state]'s own list on every recomposition, rather than captured once: a habit that leaves the
 * list (for instance because it was archived, filtering it out of the current view) dismisses its
 * own dialog instead of rendering a stale name against a habit id that no longer resolves.
 */
@Composable
fun HabitListScreen(state: HabitListUiState, actions: HabitListActions) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val pendingDeleteHabit = state.habits.firstOrNull { it.id == pendingDeleteId }
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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.habit_list_title)) }) },
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
