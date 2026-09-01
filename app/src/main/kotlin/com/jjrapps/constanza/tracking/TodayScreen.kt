@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jjrapps.constanza.tracking

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jjrapps.constanza.R
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
 *  permission from system Settings and come back without any Room write to react to. */
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
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshExactAlarmPermission()
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
    )
}

/** Presentational: state in, callbacks out, no dependencies of its own. */
@Composable
fun TodayScreen(
    state: TodayUiState,
    onToggleExpanded: (Long) -> Unit,
    onAnswer: (Long, TodaySlot, InAppEntryStatus) -> Unit,
    onManageHabits: () -> Unit,
    onOpenSettings: () -> Unit = {},
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
            TodayContent(state, onToggleExpanded, onAnswer)
        }
    }
}

@Composable
private fun TodayContent(
    state: TodayUiState,
    onToggleExpanded: (Long) -> Unit,
    onAnswer: (Long, TodaySlot, InAppEntryStatus) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
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

/** Task 6b.9 (design §12/§13.1): non-blocking — reminders still fire, degraded to a 10-minute
 *  inexact window (design §13.4's measurement), so this is informational, not a gate. One tap
 *  deep-links to [Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM]; the default on a fresh install
 *  targeting API 33+ is denied, so this is the common path, not an edge case. */
@Composable
private fun ExactAlarmBanner() {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // `weight(1f)` is load bearing, not styling: without it the text takes whatever width it
        // wants and `SpaceBetween` pushes the action button clean off the right edge, so the one tap
        // §13.1 promises becomes unreachable while the banner still looks fine. Found by task G.7's
        // manual matrix on a 1080dp-wide Pixel 10 — the automated `sw = 600dp` test (6b.8) asserts
        // the habit rows, not this banner.
        Text(
            stringResource(R.string.today_exact_alarm_banner),
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        TextButton(onClick = {
            val uri = Uri.parse("package:${context.packageName}")
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, uri))
        }) {
            Text(stringResource(R.string.today_exact_alarm_banner_action))
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
            Text(row.habitName, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            if (slot != null) SlotRow(row.habitId, slot, zone, onAnswer)
        }
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
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
