@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jjrapps.constanza.habit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.HabitDot
import com.jjrapps.constanza.core.ui.icons.ConstanzaIcons
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.ConstanzaControlDefaults
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.Spacing

/** The row's left inset — the same 16dp gutter Today's rows use. */
private val ROW_INSET = Spacing.lg

/** Dot-to-name gap, matching Today's rows so the two lists line up. */
private val DOT_TEXT_GAP = 14.dp

/** A one-line row is still a comfortable 64dp target, as on Today. */
private val ROW_MIN_HEIGHT = 64.dp

/** The overflow menu's corner (direction E board "E · Hábitos"). */
private val MENU_CORNER = 14.dp

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
    val pendingDeleteHabit = state.items.firstOrNull { it.habit.id == pendingDeleteId }?.habit
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
        floatingActionButton = { HabitListFab(actions.onCreateHabit) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            HabitListContent(state, actions, onRequestDelete = { pendingDeleteId = it })
        }
    }
}

/** habit-list-back-navigation. Deliberately the same shape as `HabitEditorScreen`'s
 *  `HabitEditorTopBar`: a back icon in the leading navigation slot, not the `actions`-slot "Back"
 *  text button `ProgressScreen`/`SnoozeSettingsScreen` use. Those two are read-only leaves a user
 *  glances at; this screen is somewhere a user does work and must be able to walk out of, and the
 *  leading slot is where every Android user already looks for that. `contentDescription` reuses the
 *  existing `action_back` string rather than adding a second resource with the same word in it.
 *
 *  Graphite redesign: the thin auto-mirrored [ConstanzaIcons.ChevronStart] (Today's own chevron)
 *  replaces the Material arrow, and the bar sits on the screen background rather than the raised
 *  surface; the title keeps `TopAppBar`'s `titleLarge` (22sp/600), left-aligned. */
@Composable
private fun HabitListTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.habit_list_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(ConstanzaIcons.ChevronStart, contentDescription = stringResource(R.string.action_back))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

/** Graphite redesign: the same light-filled, flat FAB on the 18dp `shapes.large` corner as Today's
 *  add-habit button (`tracking.TodayAddHabitFab`) — see its KDoc for why it carries no shadow. */
@Composable
private fun HabitListFab(onCreateHabit: () -> Unit) {
    FloatingActionButton(
        onClick = onCreateHabit,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.habit_list_add_habit))
    }
}

@Composable
private fun HabitListContent(state: HabitListUiState, actions: HabitListActions, onRequestDelete: (Long) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ShowArchivedRow(state.showArchived, actions.onToggleShowArchived)
            HorizontalDivider(modifier = Modifier.padding(horizontal = ROW_INSET))
        }
        if (state.items.isEmpty()) {
            item {
                val emptyRes = if (state.showArchived) {
                    R.string.habit_list_empty_archived
                } else {
                    R.string.habit_list_empty_active
                }
                Text(
                    stringResource(emptyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ConstanzaColors.OnBackgroundVariant,
                    modifier = Modifier.padding(ROW_INSET),
                )
            }
        }
        items(state.items, key = { it.habit.id }) { item ->
            HabitRow(item, actions.onArchiveToggle, actions.onEditHabit, actions.onShowProgress, onRequestDelete)
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
 *
 * Graphite redesign: the label steps back to the secondary tone so the habits lead the screen, and
 * the switch takes [ConstanzaControlDefaults.switchColors].
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
            .padding(ROW_INSET),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.habit_list_show_archived),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
            color = ConstanzaColors.OnBackgroundVariant,
        )
        Switch(checked = showArchived, onCheckedChange = null, colors = ConstanzaControlDefaults.switchColors())
    }
}

/**
 * Graphite redesign (`habit-management`: the list row is dot, name, schedule subtitle and overflow
 * menu). The habit's colour lives only in the leading [HabitDot]; the name renders in the text
 * colour through `Text`'s own `color` — no colour span any more, so `HabitNameColourComposeTest`
 * reads the dot's [com.jjrapps.constanza.core.ui.component.HabitDotColor] and the name's laid-out
 * style instead. Under the name, [HabitScheduleSummary] names the schedule and its reminder.
 *
 * The name keeps `maxLines = 2, overflow = TextOverflow.Ellipsis`: a name that needs a third line is
 * capped and ellipsized rather than growing the row indefinitely. It is composed before the
 * subtitle, so the merged row node's text layout is still the name's (`HabitListRowMenuComposeTest`
 * reads it).
 *
 * Design.md D5 (unchanged) — Progress, Archive/Un-archive and Delete are all items of the trailing
 * overflow menu, in that order, leaving the irreversible item farthest from where the finger lands.
 * Every item sets `menuExpanded = false` before invoking its action. The accepted residual risk is
 * that Archive and Delete are adjacent menu rows; that is bounded by [DeleteHabitDialog]'s
 * confirmation and by Archive being reversible. The menu itself is a neutral raised surface with
 * 14dp corners and a hairline edge; Delete alone takes the destructive tone (M3 `error`).
 */
@Composable
private fun HabitRow(
    item: HabitListItem,
    onArchiveToggle: (Long, Boolean) -> Unit,
    onEditHabit: (Long) -> Unit,
    onShowProgress: (Long) -> Unit,
    onRequestDelete: (Long) -> Unit,
) {
    val habit = item.habit
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditHabit(habit.id) }
            .heightIn(min = ROW_MIN_HEIGHT)
            .padding(start = ROW_INSET, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HabitDot(habit.colorArgb)
        Spacer(Modifier.width(DOT_TEXT_GAP))
        Column(modifier = Modifier.weight(1f).padding(vertical = Spacing.md)) {
            Text(
                habit.name,
                style = MaterialTheme.typography.bodyLarge,
                color = ConstanzaColors.OnBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.schedule?.let { HabitScheduleSummary(it) }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.habit_list_more_options),
                    tint = ConstanzaColors.OnBackgroundVariant,
                )
            }
            HabitRowMenu(
                expanded = menuExpanded,
                archived = habit.archived,
                onDismiss = { menuExpanded = false },
                onChoice = { choice ->
                    when (choice) {
                        RowMenuChoice.PROGRESS -> onShowProgress(habit.id)
                        RowMenuChoice.ARCHIVE_TOGGLE -> onArchiveToggle(habit.id, !habit.archived)
                        RowMenuChoice.DELETE -> onRequestDelete(habit.id)
                    }
                },
            )
        }
    }
}

/** The overflow menu's three items, in their on-screen order. */
private enum class RowMenuChoice { PROGRESS, ARCHIVE_TOGGLE, DELETE }

/** See [HabitRow]'s KDoc for the order and the tones. Each item dismisses the menu first. */
@Composable
private fun HabitRowMenu(
    expanded: Boolean,
    archived: Boolean,
    onDismiss: () -> Unit,
    onChoice: (RowMenuChoice) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(MENU_CORNER),
        containerColor = ConstanzaColors.SurfaceRaised,
        tonalElevation = 0.dp,
        border = BorderStroke(Dimens.FieldBorder, ConstanzaColors.Divider),
    ) {
        val archiveRes = if (archived) R.string.habit_list_unarchive else R.string.habit_list_archive
        MenuItem(stringResource(R.string.habit_list_progress), ConstanzaColors.OnBackground) {
            onDismiss()
            onChoice(RowMenuChoice.PROGRESS)
        }
        MenuItem(stringResource(archiveRes), ConstanzaColors.OnBackground) {
            onDismiss()
            onChoice(RowMenuChoice.ARCHIVE_TOGGLE)
        }
        MenuItem(stringResource(R.string.habit_list_delete), MaterialTheme.colorScheme.error) {
            onDismiss()
            onChoice(RowMenuChoice.DELETE)
        }
    }
}

@Composable
private fun MenuItem(label: String, color: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal)) },
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = color),
    )
}

/** habit-management: Habit Deletion (design.md D4). Same [AlertDialog] shape as
 *  [HabitEditorScreen]'s `DiscardChangesDialog`; what differs is what is at stake, so unlike that
 *  dialog this one names the subject and the exact count destroyed. [entryCount] renders through
 *  [pluralStringResource] rather than a hand-picked string, so zero renders as the honest "0
 *  recorded answers will be…" via the `other` category — English has no CLDR `zero`, and nothing
 *  branches on the count beyond its own copy.
 *
 *  Graphite redesign: the dialog sits on the neutral raised surface and the confirm button alone
 *  takes the destructive tone (M3 `error`); Cancel stays in the neutral interactive tone. */
@Composable
private fun DeleteHabitDialog(habitName: String, entryCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConstanzaColors.SurfaceRaised,
        title = { Text(stringResource(R.string.habit_delete_dialog_title, habitName)) },
        text = {
            Text(
                pluralStringResource(R.plurals.habit_delete_dialog_body, entryCount, entryCount),
                color = ConstanzaColors.OnBackgroundVariant,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.habit_delete_dialog_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
