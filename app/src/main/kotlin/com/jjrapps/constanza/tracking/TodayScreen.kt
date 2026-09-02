@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jjrapps.constanza.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.HabitColorDot
import com.jjrapps.constanza.core.ui.theme.Spacing
import com.jjrapps.constanza.domain.model.DayStatus
import com.jjrapps.constanza.domain.model.EntryStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MINUTES_PER_HOUR = 60
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

/** Task 6b.1 — container, matching [com.jjrapps.constanza.habit.HabitListRoute]'s hoisted-route
 *  navigation shape (design.md §14, no navigation library). Task 6b.9: re-checks
 *  [TodayViewModel.refreshExactAlarmPermission] on `ON_RESUME`, since the user can grant the
 *  permission from system Settings and come back without any Room write to react to. The same
 *  hook re-checks [TodayViewModel.refreshNotificationPermission] for the identical reason — a
 *  `BLOCKED` notification permission can only be undone from system settings. */
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
        onManageHabits = onManageHabits,
        onAddHabit = onAddHabit,
        onOpenSettings = onOpenSettings,
        onNotificationPermissionRequested = viewModel::recordNotificationPermissionRequested,
    )
}

/** Presentational: state in, callbacks out, no dependencies of its own.
 *
 *  `LongParameterList` is suppressed on this one function for the same class of reason
 *  `config/detekt/detekt.yml` already relaxes `FunctionNaming`: the rule has no notion of Compose,
 *  where one state object plus a hoisted lambda per event IS the parameter list, and collapsing
 *  five callbacks into a holder object to satisfy a count would make the screen harder to read and
 *  harder to preview, not easier. Suppressed on the declaration only — the threshold still applies
 *  to every other function in this file. */
@Composable
@Suppress("LongParameterList")
fun TodayScreen(
    state: TodayUiState,
    onToggleExpanded: (Long) -> Unit,
    onAnswer: (Long, TodaySlot, InAppEntryStatus) -> Unit,
    onManageHabits: () -> Unit,
    onAddHabit: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onNotificationPermissionRequested: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today_title)) },
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.today_settings))
                    }
                    TextButton(onClick = onManageHabits) {
                        Text(stringResource(R.string.today_manage_habits))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            TodayContent(state, onToggleExpanded, onAnswer, onAddHabit, onNotificationPermissionRequested)
        }
    }
}

@Composable
private fun TodayContent(
    state: TodayUiState,
    onToggleExpanded: (Long) -> Unit,
    onAnswer: (Long, TodaySlot, InAppEntryStatus) -> Unit,
    onAddHabit: () -> Unit,
    onNotificationPermissionRequested: () -> Unit,
) {
    // Two layouts, chosen by whether there is a list at all, rather than one LazyColumn with an
    // empty branch inside it. A `fillParentMaxSize` item is sized against the whole viewport and
    // knows nothing about the banners above it, so with both banners showing the "centred" action
    // was pushed into the bottom third of a real screen — seen on the emulator, not reasoned about.
    // A Column whose empty state takes `weight(1f)` centres in the space that is actually left.
    // Nothing scrolls in that case anyway: at most two banners and one call to action.
    if (state.rows.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TodayPermissionBanners(state, onNotificationPermissionRequested)
            TodayEmptyState(onAddHabit, modifier = Modifier.weight(1f))
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { TodayPermissionBanners(state, onNotificationPermissionRequested) }
        items(state.rows, key = { it.habitId }) { row ->
            HabitRollupRow(row, row.habitId in state.expandedHabitIds, state.zone, onToggleExpanded, onAnswer)
        }
        item { TrailingAddHabitAction(onAddHabit) }
    }
}

/** OA-2, as revised (design.md §1): a single-slot habit reads as one plain row; a multi-slot
 *  habit shows the day rollup and expands to each independently answerable slot. */
@Composable
private fun HabitRollupRow(
    row: TodayHabitRow,
    expanded: Boolean,
    zone: ZoneId,
    onToggleExpanded: (Long) -> Unit,
    onAnswer: (Long, TodaySlot, InAppEntryStatus) -> Unit,
) {
    if (row.slots.size <= 1) {
        val slot = row.slots.firstOrNull()
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                // `end` padding added with the SlotRow width fix below: a long habit name — the
                // reported case was a full sentence — otherwise runs to the very edge of the
                // screen with nothing between it and the bezel.
                modifier = Modifier.padding(start = 16.dp, end = Spacing.lg, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HabitColorDot(row.colorArgb)
                Text(row.habitName)
            }
            if (slot != null) SlotRow(row.habitId, slot, zone, onAnswer)
        }
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = { HabitColorDot(row.colorArgb) },
            headlineContent = { Text(row.habitName) },
            supportingContent = { Text(stringResource(dayStatusLabel(row.dayStatus))) },
            trailingContent = {
                val labelRes = if (expanded) R.string.today_collapse else R.string.today_expand
                TextButton(onClick = { onToggleExpanded(row.habitId) }) { Text(stringResource(labelRes)) }
            },
        )
        if (expanded) {
            row.slots.forEach { slot -> SlotRow(row.habitId, slot, zone, onAnswer, indented = true) }
        }
    }
}

@Composable
private fun SlotRow(
    habitId: Long,
    slot: TodaySlot,
    zone: ZoneId,
    onAnswer: (Long, TodaySlot, InAppEntryStatus) -> Unit,
    indented: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) 32.dp else 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // `weight(1f)` is load bearing, not styling, and is the same fix ExactAlarmBanner and
        // NotificationPermissionBanner already carry in TodayBanners.kt. Without it the status text
        // takes whatever width it wants and `SpaceBetween` squeezes the button group into the
        // remainder, so "Skip" wrapped mid-word as "Ski / p" — reported from a real Galaxy S25.
        // The buttons keep their intrinsic width and the text wraps instead, which is the right way
        // round: a wrapped sentence is readable, a wrapped control label is not.
        Text(
            slotStatusText(slot, zone),
            modifier = Modifier.weight(1f).padding(end = Spacing.sm),
        )
        AnswerButtons(onAnswer = { status -> onAnswer(habitId, slot, status) })
    }
}

@Composable
private fun AnswerButtons(onAnswer: (InAppEntryStatus) -> Unit) {
    Row {
        TextButton(onClick = { onAnswer(InAppEntryStatus.COMPLETED) }) {
            Text(stringResource(R.string.today_answer_yes))
        }
        TextButton(onClick = { onAnswer(InAppEntryStatus.MISSED) }) {
            Text(stringResource(R.string.today_answer_no))
        }
        TextButton(onClick = { onAnswer(InAppEntryStatus.SKIPPED) }) {
            Text(stringResource(R.string.today_answer_skip))
        }
    }
}

/** [zone] comes from [TodayUiState.zone] ([com.jjrapps.constanza.core.time.TimeProvider.zone]),
 *  never `ZoneId.systemDefault()` directly — the same clock-access ban design.md §4 enforces
 *  everywhere else (config/detekt/detekt.yml `ForbiddenMethodCall`). */
@Composable
private fun slotStatusText(slot: TodaySlot, zone: ZoneId): String {
    val time = slot.minuteOfDay?.let { minute ->
        "%02d:%02d".format(minute / MINUTES_PER_HOUR, minute % MINUTES_PER_HOUR)
    }
    val statusText = if (slot.snoozedUntilEpochMs != null) {
        // Still ahead of the status itself: a snoozed slot is pending WITH a time attached, and
        // that time is the more useful half of the sentence.
        stringResource(
            R.string.today_slot_pending_snoozed_until,
            TIME_FORMATTER.format(Instant.ofEpochMilli(slot.snoozedUntilEpochMs).atZone(zone)),
        )
    } else {
        stringResource(slotStatusLabel(slot.status))
    }
    return if (time != null) "$time — $statusText" else statusText
}

/** today-row-answering-is-cramped-and-always-on, defect 2. The fallback here used to be
 *  `slot.status.name`, which put the Kotlin constant `COMPLETED` on screen; this mirrors
 *  [dayStatusLabel]'s existing shape instead, which is what it should have done from the start.
 *
 *  Exhaustive over [EntryStatus] with no `else`, deliberately: adding a member to that enum must
 *  break this compile rather than silently reach a default. [EntryStatus.UNKNOWN] is the pending
 *  case and keeps the string it already had. */
private fun slotStatusLabel(status: EntryStatus) = when (status) {
    EntryStatus.COMPLETED -> R.string.today_slot_completed
    EntryStatus.MISSED -> R.string.today_slot_missed
    EntryStatus.SKIPPED -> R.string.today_slot_skipped
    EntryStatus.UNKNOWN -> R.string.today_slot_pending
}

private fun dayStatusLabel(status: DayStatus) = when (status) {
    DayStatus.ALL_COMPLETED -> R.string.today_status_all_completed
    DayStatus.PARTIAL -> R.string.today_status_partial
    DayStatus.ANY_MISSED -> R.string.today_status_any_missed
    DayStatus.ALL_SKIPPED -> R.string.today_status_all_skipped
    DayStatus.PENDING, DayStatus.NOT_DUE -> R.string.today_status_pending
}
