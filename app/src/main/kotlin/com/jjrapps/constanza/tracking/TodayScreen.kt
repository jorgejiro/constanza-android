package com.jjrapps.constanza.tracking

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jjrapps.constanza.core.ui.theme.Spacing

/** Task 6b.1 — container, matching [com.jjrapps.constanza.habit.HabitListRoute]'s hoisted-route
 *  navigation shape (design.md §14, no navigation library). Task 6b.9: re-checks
 *  [TodayViewModel.refreshExactAlarmPermission] on `ON_RESUME`, since the user can grant the
 *  permission from system Settings and come back without any Room write to react to. The same
 *  hook re-checks [TodayViewModel.refreshNotificationPermission] for the identical reason — a
 *  `BLOCKED` notification permission can only be undone from system settings.
 *
 *  today-midnight-rollover, task 2.7: the same `ON_RESUME` hook also calls
 *  [TodayViewModel.refreshDate], correcting the displayed date if the app was backgrounded across
 *  local midnight — the spec's resume scenario. It runs first, since a stale date should not be
 *  left standing behind a permission re-check that happens to run before it. */
@Composable
fun TodayRoute(
    onManageHabits: () -> Unit,
    onAddHabit: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDate()
                viewModel.refreshExactAlarmPermission()
                viewModel.refreshNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    TodayScreen(
        state = state,
        onToggleExpanded = viewModel::toggleExpanded,
        onAnswer = viewModel::answer,
        onClearAnswer = viewModel::clearAnswer,
        onManageHabits = onManageHabits,
        onAddHabit = onAddHabit,
        onOpenSettings = onOpenSettings,
        onNotificationPermissionRequested = viewModel::recordNotificationPermissionRequested,
        onPreviousDay = viewModel::showPreviousDay,
        onNextDay = viewModel::showNextDay,
        onToday = viewModel::showToday,
    )
}

/** Presentational: state in, callbacks out, no dependencies of its own.
 *
 *  `LongParameterList` is suppressed on this one function for the same class of reason
 *  `config/detekt/detekt.yml` already relaxes `FunctionNaming`: the rule has no notion of Compose,
 *  where one state object plus a hoisted lambda per event IS the parameter list, and collapsing
 *  the callbacks into a holder object to satisfy a count would make the screen harder to read and
 *  harder to preview, not easier. Suppressed on the declaration only — the threshold still applies
 *  to every other function in this file. */
@Composable
@Suppress("LongParameterList")
fun TodayScreen(
    state: TodayUiState,
    onToggleExpanded: (Long) -> Unit,
    onAnswer: (Long, TodaySlot, InAppEntryStatus) -> Unit,
    onClearAnswer: (Long, TodaySlot) -> Unit = { _, _ -> },
    onManageHabits: () -> Unit,
    onAddHabit: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onNotificationPermissionRequested: () -> Unit = {},
    onPreviousDay: () -> Unit = {},
    onNextDay: () -> Unit = {},
    onToday: () -> Unit = {},
) {
    // today-one-line-row, point 2: which answered slot's dialog is open, if any. Purely
    // presentation state that lives and dies with one tap — unlike [TodayUiState.expandedHabitIds]
    // it never needs to survive a recomposition triggered from elsewhere, so it is plain
    // composition-local state rather than anything the ViewModel holds.
    var changeDialogTarget by remember { mutableStateOf<ChangeDialogTarget?>(null) }
    val actions = remember(onAnswer, onToggleExpanded) {
        SlotActions(
            onAnswer = onAnswer,
            onOpenChangeDialog = { row, slot ->
                changeDialogTarget = ChangeDialogTarget(row.habitId, row.habitName, slot)
            },
            onToggleExpanded = onToggleExpanded,
        )
    }
    val headerActions = remember(onManageHabits, onOpenSettings, onPreviousDay, onNextDay, onToday) {
        TodayHeaderActions(onManageHabits, onOpenSettings, onPreviousDay, onNextDay, onToday)
    }
    val progress = remember(state.rows) { todayProgress(state.rows) }
    changeDialogTarget?.let { target ->
        ChangeAnswerDialog(
            habitName = target.habitName,
            current = target.slot.status,
            // today-clear-answer: never on a past day (design.md's deliberate asymmetry — see
            // ChangeAnswerDialog's KDoc).
            showNotAnsweredOption = !state.isPastDay,
            onSelect = { status ->
                onAnswer(target.habitId, target.slot, status)
                changeDialogTarget = null
            },
            onClear = {
                onClearAnswer(target.habitId, target.slot)
                changeDialogTarget = null
            },
            onDismiss = { changeDialogTarget = null },
        )
    }
    Scaffold(
        // Graphite redesign: the header replaces the `TopAppBar` and the date bar that sat under
        // it. As the top bar it stays fixed above both of [TodayContent]'s layouts.
        topBar = { TodayHeader(state.date, state.isPastDay, progress, headerActions) },
        // today-add-habit-is-not-a-fab: the same slot, the same icon and the same corner
        // `HabitListScreen` uses, so the two screens agree on what creating a habit looks like.
        //
        // today-past-day-correction, design.md decision 5: absent, not disabled, while a past day
        // is on screen — a habit's schedule starts when it is created, so there is nothing a
        // back-dated create could mean. The guard sits here rather than inside the composable so
        // that `Scaffold` lays out with no floating action button at all.
        floatingActionButton = { if (!state.isPastDay) TodayAddHabitFab(onAddHabit) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            TodayContent(state, actions, onNotificationPermissionRequested)
        }
    }
}

/** today-add-habit-is-not-a-fab: room left at the end of the habit list for the floating
 *  add-habit button to sit over. A Material 3 [androidx.compose.material3.FloatingActionButton] is
 *  56dp tall and `Scaffold` insets it by 16dp, so 88dp clears it with a row's worth of air to
 *  spare. */
private val FAB_SCROLL_CLEARANCE = 88.dp

/** The ONE left edge every Today row, section header and banner starts at: the plain [Spacing.lg]
 *  screen margin. One token rather than a literal so the rows (`TodayHabitRows.kt`) and the
 *  section headers (`TodaySectionHeader.kt`) cannot silently drift apart. */
internal val ROW_CONTENT_INSET = Spacing.lg

/** One holder for every per-row callback, so the row composables in `TodayHabitRows.kt` stay under
 *  detekt's `LongParameterList` default of 6. [onOpenChangeDialog] is called by an answered slot's
 *  own row with the row and the tapped slot; [TodayScreen] turns that into the dialog target it
 *  holds as local state. */
internal data class SlotActions(
    val onAnswer: (Long, TodaySlot, InAppEntryStatus) -> Unit,
    val onOpenChangeDialog: (TodayHabitRow, TodaySlot) -> Unit,
    val onToggleExpanded: (Long) -> Unit,
)

/** today-one-line-row, point 2: which slot's change dialog [TodayScreen] currently shows, if any.
 *  [habitName] rides along rather than being looked up again from [TodayUiState.rows] by
 *  [habitId] — the dialog is titled with it, and by the time it renders the row it came from may
 *  already be gone from that list (e.g. `groupTodayRows` moved it to another section on the exact
 *  frame the dialog opened). */
private data class ChangeDialogTarget(val habitId: Long, val habitName: String, val slot: TodaySlot)

@Composable
private fun TodayContent(
    state: TodayUiState,
    actions: SlotActions,
    onNotificationPermissionRequested: () -> Unit,
) {
    // Two layouts, chosen by whether there is a list at all, rather than one LazyColumn with an
    // empty branch inside it. A `fillParentMaxSize` item is sized against the whole viewport and
    // knows nothing about the banners above it, so with both banners showing the "centred" text
    // was pushed into the bottom third of a real screen — seen on the emulator, not reasoned about.
    // A Column whose empty state takes `weight(1f)` centres in the space that is actually left.
    if (state.rows.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TodayPermissionBanners(state, onNotificationPermissionRequested)
            if (state.isPastDay) {
                TodayPastDayEmptyState(modifier = Modifier.weight(1f))
            } else {
                TodayEmptyState(modifier = Modifier.weight(1f))
            }
        }
        return
    }
    // today-add-habit-is-not-a-fab: the FAB floats over the list rather than scrolling with it, so
    // the end of the list reserves room for it in the scroll region — not as a fixed padding, so a
    // short list still starts at the top of the viewport.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = FAB_SCROLL_CLEARANCE),
    ) {
        item { TodayPermissionBanners(state, onNotificationPermissionRequested) }
        // today-grouped-sections, design.md: three ordered sections ("Now" / "Later" /
        // "Answered"), each introduced by a header. A section [state.sections] never carries with
        // zero rows, so this loop never emits an empty section's header at all.
        state.sections.forEach { section ->
            item(key = "section-header-${section.kind}") { TodaySectionHeader(section.kind) }
            items(section.rows, key = { it.habitId }) { row ->
                val expanded = row.habitId in state.expandedHabitIds
                TodayHabitRow(row, section.kind, expanded, state.zone, actions)
            }
        }
    }
}
