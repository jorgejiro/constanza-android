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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.HabitColorDot
import com.jjrapps.constanza.core.ui.rememberTimeOfDayFormat
import com.jjrapps.constanza.core.ui.theme.Spacing
import com.jjrapps.constanza.domain.model.DayStatus
import com.jjrapps.constanza.domain.model.EntryStatus
import java.time.Instant
import java.time.ZoneId

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
        onRequestChange = viewModel::requestChange,
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
    onRequestChange: (TodaySlotKey) -> Unit,
    onManageHabits: () -> Unit,
    onAddHabit: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onNotificationPermissionRequested: () -> Unit = {},
) {
    val actions = remember(onRequestChange, onAnswer, state.reopenedSlots) {
        SlotActions(state.reopenedSlots, onRequestChange, onAnswer)
    }
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
            TodayContent(state, onToggleExpanded, actions, onAddHabit, onNotificationPermissionRequested)
        }
    }
}

/** today-answered-slot-collapse, design.md decision 5: one holder instead of two extra parameters
 *  on both [HabitRollupRow] and [SlotRow], which would otherwise push each past detekt's
 *  unconfigured `LongParameterList` default of 6 — and, one level up, past [TodayContent]'s own
 *  threshold too, which is why this is built in [TodayScreen] and threaded down as one value rather
 *  than passing `onAnswer`/`onRequestChange` separately. Built with `remember`, keyed on the
 *  callbacks and the reopen set it carries — the callbacks are stable member references from the
 *  ViewModel, and the set is what should actually invalidate a row's recomposition. */
private data class SlotActions(
    val reopenedKeys: Set<TodaySlotKey>,
    val onRequestChange: (TodaySlotKey) -> Unit,
    val onAnswer: (Long, TodaySlot, InAppEntryStatus) -> Unit,
)

@Composable
private fun TodayContent(
    state: TodayUiState,
    onToggleExpanded: (Long) -> Unit,
    actions: SlotActions,
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
            HabitRollupRow(row, row.habitId in state.expandedHabitIds, state.zone, onToggleExpanded, actions)
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
    actions: SlotActions,
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
            if (slot != null) SlotRow(row, slot, zone, actions)
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
            row.slots.forEach { slot -> SlotRow(row, slot, zone, actions, indented = true) }
        }
    }
}

/** today-answered-slot-collapse, design.md decision 2: a pending slot ([EntryStatus.UNKNOWN]), or
 *  one the user just asked to change via [SlotActions.reopenedKeys], keeps [AnswerButtons]
 *  verbatim. Any other status instead shows the text naming its own answer plus one [ChangeButton]
 *  — never both, and never Yes/No/Skip alongside a resolved status. [row] replaces the old bare
 *  `habitId: Long` parameter because the Change control's accessible label needs the habit name
 *  too (design.md decision 3), and this keeps the parameter count at 5 rather than adding a sixth. */
@Composable
private fun SlotRow(
    row: TodayHabitRow,
    slot: TodaySlot,
    zone: ZoneId,
    actions: SlotActions,
    indented: Boolean = false,
) {
    val key = slot.keyIn(row.habitId)
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
        if (slot.status == EntryStatus.UNKNOWN || key in actions.reopenedKeys) {
            Text(
                slotStatusText(slot, zone),
                modifier = Modifier.weight(1f).padding(end = Spacing.sm),
            )
            AnswerButtons(onAnswer = { status -> actions.onAnswer(row.habitId, slot, status) })
        } else {
            val statusText = slotStatusText(slot, zone, bypassSnooze = true)
            Text(statusText, modifier = Modifier.weight(1f).padding(end = Spacing.sm))
            ChangeButton(row.habitName, statusText, onClick = { actions.onRequestChange(key) })
        }
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

/** today-answered-slot-collapse, design.md decision 3. Reachable by an ordinary tap and by
 *  TalkBack's default activate action — no gesture, satisfying the spec's "gesture-free" scenario
 *  directly. The visible label is identical on every row; [contentDescription] carries the
 *  discriminator instead, which is also why this cannot collide with the four existing
 *  `onNodeWithText` assertions — Compose's text matcher never reads `contentDescription`. */
@Composable
private fun ChangeButton(habitName: String, answeredStatusText: String, onClick: () -> Unit) {
    val description = stringResource(R.string.today_slot_change_a11y, habitName, answeredStatusText)
    TextButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(stringResource(R.string.today_slot_change))
    }
}

/** [zone] comes from [TodayUiState.zone] ([com.jjrapps.constanza.core.time.TimeProvider.zone]),
 *  never `ZoneId.systemDefault()` directly — the same clock-access ban design.md §4 enforces
 *  everywhere else (config/detekt/detekt.yml `ForbiddenMethodCall`).
 *
 *  Both times here — the slot's own time and the snoozed-until time — go through the one
 *  [com.jjrapps.constanza.core.ui.TimeOfDayFormat]. They used to be formatted two different ways in
 *  this one function: a `DateTimeFormatter.ofPattern("HH:mm")` file constant for the snooze and a
 *  bare `"%02d:%02d"` literal for the slot, which is two copies of a decision that has to agree.
 *
 *  The format is remembered per row rather than hoisted into [TodayContent] and threaded down. That
 *  is deliberate: threading it would add a sixth parameter to both [HabitRollupRow] and [SlotRow]
 *  to save one small immutable object per visible row, and the rows are already carrying every
 *  argument they can justify.
 *
 *  [bypassSnooze] is today-answered-slot-collapse, design.md decision 2: `SlotRow`'s answered
 *  branch passes `true` so the snooze sentence below is never even reached for a slot already
 *  carrying a resolved `Entry` — `&&` short-circuits before `snoozedUntilEpochMs` is read at all,
 *  not merely before it renders. Without this an answered slot whose occurrence had not yet been
 *  resolved would read "Pending, snoozed until 09:00" over a `COMPLETED` entry, a literal failure
 *  of the spec's "text naming its specific answer". */
@Composable
private fun slotStatusText(slot: TodaySlot, zone: ZoneId, bypassSnooze: Boolean = false): String {
    val timeFormat = rememberTimeOfDayFormat()
    val time = slot.minuteOfDay?.let(timeFormat::format)
    val statusText = if (!bypassSnooze && slot.snoozedUntilEpochMs != null) {
        // Still ahead of the status itself: a snoozed slot is pending WITH a time attached, and
        // that time is the more useful half of the sentence.
        stringResource(
            R.string.today_slot_pending_snoozed_until,
            timeFormat.format(Instant.ofEpochMilli(slot.snoozedUntilEpochMs).atZone(zone).toLocalTime()),
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
