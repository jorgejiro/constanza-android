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
            TodayContent(state, onToggleExpanded, onAnswer, onNotificationPermissionRequested)
        }
    }
}

@Composable
private fun TodayContent(
    state: TodayUiState,
    onToggleExpanded: (Long) -> Unit,
    onAnswer: (Long, TodaySlot, InAppEntryStatus) -> Unit,
    onNotificationPermissionRequested: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Above the exact-alarm banner deliberately: a late reminder is a degraded reminder, a
        // missing permission is no reminder at all.
        if (state.notificationPermission.needsBanner()) {
            item {
                NotificationPermissionBanner(
                    decision = state.notificationPermission,
                    onPermissionRequested = onNotificationPermissionRequested,
                )
            }
        }
        if (!state.canScheduleExactAlarms) {
            item { ExactAlarmBanner() }
        }
        if (state.rows.isEmpty()) {
            item { Text(stringResource(R.string.today_empty), modifier = Modifier.padding(16.dp)) }
        }
        items(state.rows, key = { it.habitId }) { row ->
            HabitRollupRow(row, row.habitId in state.expandedHabitIds, state.zone, onToggleExpanded, onAnswer)
        }
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
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
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
    ) {
        Text(slotStatusText(slot, zone))
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
    val statusText = when {
        slot.snoozedUntilEpochMs != null -> stringResource(
            R.string.today_slot_pending_snoozed_until,
            TIME_FORMATTER.format(Instant.ofEpochMilli(slot.snoozedUntilEpochMs).atZone(zone)),
        )
        slot.status == EntryStatus.UNKNOWN -> stringResource(R.string.today_slot_pending)
        else -> slot.status.name
    }
    return if (time != null) "$time — $statusText" else statusText
}

private fun dayStatusLabel(status: DayStatus) = when (status) {
    DayStatus.ALL_COMPLETED -> R.string.today_status_all_completed
    DayStatus.PARTIAL -> R.string.today_status_partial
    DayStatus.ANY_MISSED -> R.string.today_status_any_missed
    DayStatus.ALL_SKIPPED -> R.string.today_status_all_skipped
    DayStatus.PENDING, DayStatus.NOT_DUE -> R.string.today_status_pending
}
