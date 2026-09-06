@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jjrapps.constanza.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.HabitColorDot
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.Spacing
import com.jjrapps.constanza.domain.model.EntryStatus
import java.time.ZoneId

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
        onRequestChange = viewModel::requestChange,
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
    onPreviousDay: () -> Unit = {},
    onNextDay: () -> Unit = {},
    onToday: () -> Unit = {},
) {
    val actions = remember(onRequestChange, onAnswer, state.reopenedSlots) {
        SlotActions(state.reopenedSlots, onRequestChange, onAnswer)
    }
    val dateNavActions = remember(onPreviousDay, onNextDay, onToday) {
        DateNavActions(onPreviousDay, onNextDay, onToday)
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
        // today-add-habit-is-not-a-fab: the same slot, the same icon and the same corner
        // `HabitListScreen` uses, so the two screens agree on what creating a habit looks like.
        //
        // today-past-day-correction, design.md decision 5: absent, not disabled, while a past day
        // is on screen — a habit's schedule starts when it is created, so there is nothing a
        // back-dated create could mean, and this change's convention is that an action that cannot
        // apply is not rendered at all. The guard sits here rather than inside the composable so
        // that `Scaffold` lays out with no floating action button at all, leaving nothing for the
        // gesture navigation bar to reserve room around.
        floatingActionButton = { if (!state.isPastDay) TodayAddHabitFab(onAddHabit) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            TodayContent(state, onToggleExpanded, actions, onNotificationPermissionRequested, dateNavActions)
        }
    }
}

/** today-add-habit-is-not-a-fab: room left at the end of the habit list for the floating
 *  add-habit button to sit over. A Material 3 [androidx.compose.material3.FloatingActionButton] is
 *  56dp tall and `Scaffold` insets it by 16dp, so 88dp clears it with a row's worth of air to
 *  spare. */
private val FAB_SCROLL_CLEARANCE = 88.dp

/** today-row-alignment, decision 1: the ONE left edge every line of a habit row starts at.
 *
 *  Computed from the tokens the colour dot is actually drawn with, never written as a `48.dp`
 *  literal: the row's own [Spacing.lg] gutter, plus [Dimens.HabitDotSlot] — the dot's full slot, not
 *  its 12dp core — plus one [Spacing.sm] so 16sp text does not sit flush against the dot the way it
 *  did before this change. Change any of the three and the status line follows the habit name
 *  automatically, which is the whole point: the defect being fixed here WAS two independent
 *  literals that had drifted apart. `HabitRollupRow`'s name row padded `start = 16.dp` and then drew
 *  a 24dp dot, putting the name at 40dp, while [SlotRow] padded `start = 16.dp` (or 32dp when it was
 *  `indented`) — so a row's status line hung 24dp to the LEFT of the name it belonged to, and the
 *  indented case disagreed with both. Measured at 360dp before the fix; that is what "mal alineada"
 *  meant. Resolves to 48dp today. */
private val ROW_CONTENT_INSET = Spacing.lg + Dimens.HabitDotSlot + Spacing.sm

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

/** today-past-day-correction, design.md decision 4: [TodayContent] already carries 5 parameters;
 *  three more loose callbacks would push it to 8, past detekt's unconfigured `LongParameterList`
 *  default of 6 — and the `@Suppress("LongParameterList")` on `fun TodayScreen` above covers only
 *  that declaration, never this private sibling. Built the same way [SlotActions] is: one holder
 *  instead of three separate parameters. */
private data class DateNavActions(
    val onPreviousDay: () -> Unit,
    val onNextDay: () -> Unit,
    val onToday: () -> Unit,
)

/** today-add-habit-is-not-a-fab: `TodayContentActions` used to bundle `onAddHabit` with
 *  `onNotificationPermissionRequested`, because with [DateNavActions] alongside them
 *  [TodayContent] would have carried six loose parameters and detekt's unconfigured
 *  `LongParameterList` check fires at `parameterCount >= functionThreshold` (6), not only above it.
 *  The FAB moved up to [TodayScreen]'s `Scaffold` slot, so the holder was down to a single field —
 *  five parameters again, and a one-field holder is noise rather than a defence. */
@Composable
private fun TodayContent(
    state: TodayUiState,
    onToggleExpanded: (Long) -> Unit,
    actions: SlotActions,
    onNotificationPermissionRequested: () -> Unit,
    dateNavActions: DateNavActions,
) {
    // Two layouts, chosen by whether there is a list at all, rather than one LazyColumn with an
    // empty branch inside it. A `fillParentMaxSize` item is sized against the whole viewport and
    // knows nothing about the banners above it, so with both banners showing the "centred" action
    // was pushed into the bottom third of a real screen — seen on the emulator, not reasoned about.
    // A Column whose empty state takes `weight(1f)` centres in the space that is actually left.
    // Nothing scrolls in that case anyway: at most two banners and one sentence.
    //
    // today-past-day-correction, design.md decision 4: [TodayDateBar] is hoisted above BOTH layouts
    // below rather than placed as the LazyColumn's first item, so it stays fixed on screen — its
    // presence is the "you are not on today" signal, and a signal that scrolls away is not one.
    if (state.rows.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TodayDateBar(
                state.date,
                state.isPastDay,
                dateNavActions.onPreviousDay,
                dateNavActions.onNextDay,
                dateNavActions.onToday,
            )
            TodayPermissionBanners(state, onNotificationPermissionRequested)
            if (state.isPastDay) {
                TodayPastDayEmptyState(modifier = Modifier.weight(1f))
            } else {
                TodayEmptyState(modifier = Modifier.weight(1f))
            }
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TodayDateBar(
            state.date,
            state.isPastDay,
            dateNavActions.onPreviousDay,
            dateNavActions.onNextDay,
            dateNavActions.onToday,
        )
        // today-add-habit-is-not-a-fab: the trailing add-habit item that used to close this list is
        // gone, and with it the vertical padding it happened to leave at the end. The FAB floats
        // over the list rather than scrolling with it, so without this the last habit's answer
        // buttons sit underneath it once the list is scrolled to the bottom. Reserved in the
        // scroll region, not as a fixed `Modifier.padding`, so a short list still starts at the top
        // of the viewport.
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = FAB_SCROLL_CLEARANCE),
        ) {
            item { TodayPermissionBanners(state, onNotificationPermissionRequested) }
            items(state.rows, key = { it.habitId }) { row ->
                HabitRollupRow(row, row.habitId in state.expandedHabitIds, state.zone, onToggleExpanded, actions)
            }
        }
    }
}

/** OA-2, as revised (design.md §1): a single-slot habit reads as one plain row; a multi-slot habit
 *  shows the day rollup and expands to each independently answerable slot.
 *
 *  today-row-alignment, decision 2: both branches now share [HabitRollupHeader], and the multi-slot
 *  branch no longer uses `ListItem`. `ListItem` with `leadingContent` places its headline at
 *  16 + 24 + 16 = 56dp, which would have been a THIRD left edge on a screen whose entire defect was
 *  having two — so the one thing this change exists to remove would have survived in precisely the
 *  case [SlotRow]'s old `indented` parameter existed for.
 *
 *  Dropping `ListItem` costs two things, both accepted knowingly rather than overlooked. The first
 *  is its `supportingContent` slot, which was carrying the day rollup; [HabitRollupHeader] carries
 *  it by hand instead. The second is its container fill: a multi-slot header used to sit on a
 *  faintly lighter band (`ListItem` reads `surfaceContainer`), and now sits on the page like every
 *  other row. That was visible in the renders the change was approved from and was approved with
 *  it — the grouping cue a collapsed row needs is its own rollup text, not a background tint. Do not
 *  reintroduce the band by wrapping this header in a `Surface`: the band is what made the header a
 *  different width from its own slot lines in the first place. That is not a detail — a COLLAPSED
 *  multi-slot row is the one row on this screen with no slot line under it, so without the rollup it
 *  shows no state whatsoever. It was written that way once during this change and nothing on screen
 *  or in the suite said so; `TodayComposeTest.aCollapsedMultiSlotRowStillNamesItsDayStatus` exists
 *  because of that. */
@Composable
private fun HabitRollupRow(
    row: TodayHabitRow,
    expanded: Boolean,
    zone: ZoneId,
    onToggleExpanded: (Long) -> Unit,
    actions: SlotActions,
) {
    val multiSlot = row.slots.size > 1
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        HabitRollupHeader(row, expanded, multiSlot, onToggleExpanded)
        if (!multiSlot) {
            row.slots.firstOrNull()?.let { slot -> SlotRow(row, slot, zone, actions) }
        } else if (expanded) {
            // No indent, deliberately, and this is where `indented = true` went: an indent here is a
            // second left edge, which is the defect rather than the fix. What distinguishes these
            // rows from one another instead is their reminder time, which `timeIsIdentity` promotes
            // to the front of the line — see [slotStatusText]'s decision 4.
            row.slots.forEach { slot -> SlotRow(row, slot, zone, actions, timeIsIdentity = true) }
        }
    }
}

/** today-row-alignment, decision 3: the colour dot is aligned to the name's FIRST LINE rather than
 *  to the row's vertical centre.
 *
 *  `bodyLarge`'s line box is 24dp and [Dimens.HabitDotSlot] is 24dp, so [Alignment.Top] lands the
 *  dot exactly on that first line with no offset constant to keep in sync, and it stays landed
 *  however many lines the name wraps to. `Alignment.CenterVertically`, which this replaces, put the
 *  dot in the GAP between the two lines of a wrapped name — the reported list has two such names —
 *  where it read as belonging to neither. That was the other half of "mal alineada".
 *
 *  The [Spacer] is not decoration either: [HabitColorDot] centres a 12dp core inside a 24dp slot, so
 *  butting the name straight against the slot leaves 6dp between glyph and dot. [ROW_CONTENT_INSET]
 *  includes the same [Spacing.sm] so the name and the status line below it still agree. */
@Composable
private fun HabitRollupHeader(
    row: TodayHabitRow,
    expanded: Boolean,
    multiSlot: Boolean,
    onToggleExpanded: (Long) -> Unit,
) {
    Row(
        // `end` padding: a long habit name — the reported case was a full sentence — otherwise runs
        // to the very edge of the screen with nothing between it and the bezel.
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        HabitColorDot(row.colorArgb)
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            // A multi-slot habit's own line has no answerable slot, so the day rollup is the only
            // state it can carry — and it has to carry it while collapsed. Demoted rather than given
            // its own line: it is a summary of the slot lines, not a peer of the habit name.
            demotedSuffix(row.habitName, stringResource(dayStatusLabel(row.dayStatus)).takeIf { multiSlot }),
            // `weight(1f)` for the same reason [SlotRow]'s status text carries one: without it the
            // name takes what it wants and the expand control is squeezed into the remainder.
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (multiSlot) {
            val labelRes = if (expanded) R.string.today_collapse else R.string.today_expand
            TextButton(onClick = { onToggleExpanded(row.habitId) }) { Text(stringResource(labelRes)) }
        }
    }
}

/** today-answered-slot-collapse, design.md decision 2: a pending slot ([EntryStatus.UNKNOWN]), or
 *  one the user just asked to change via [SlotActions.reopenedKeys], keeps [AnswerButtons] verbatim.
 *  Any other status instead shows the text naming its own answer plus one [ChangeButton] — never
 *  both, and never Yes/No/Skip alongside a resolved status. [row] replaces the old bare
 *  `habitId: Long` parameter because the Change control's accessible label needs the habit name too
 *  (design.md decision 3), and this keeps the parameter count at 5 rather than adding a sixth.
 *
 *  today-row-alignment, decision 1: `indented: Boolean` is gone and [timeIsIdentity] took its slot.
 *  The old parameter chose between a 32dp and a 16dp start padding and BOTH disagreed with the
 *  name's own 40dp edge one line above; every line now starts at [ROW_CONTENT_INSET]. The status
 *  text also drops from the inherited `bodyLarge` to `bodyMedium`, so a row reads name (16sp) then
 *  status (14sp) then time (11sp) — three steps down, in the order the eye should take them. */
@Composable
private fun SlotRow(
    row: TodayHabitRow,
    slot: TodaySlot,
    zone: ZoneId,
    actions: SlotActions,
    timeIsIdentity: Boolean = false,
) {
    val key = slot.keyIn(row.habitId)
    // A reopened slot is pending for display purposes, which is why `bypassSnooze` is derived from
    // this rather than from the stored status: the branch below and the sentence must agree.
    val pending = slot.status == EntryStatus.UNKNOWN || key in actions.reopenedKeys
    val statusText = slotStatusText(slot, zone, timeIsIdentity, bypassSnooze = !pending)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ROW_CONTENT_INSET, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // `weight(1f)` is load bearing, not styling, and is the same fix ExactAlarmBanner and
        // NotificationPermissionBanner already carry in TodayBanners.kt. Without it the status text
        // takes whatever width it wants and `SpaceBetween` squeezes the button group into the
        // remainder, so "Skip" wrapped mid-word as "Ski / p" — reported from a real Galaxy S25.
        // The buttons keep their intrinsic width and the text wraps instead, which is the right way
        // round: a wrapped sentence is readable, a wrapped control label is not. Still true after
        // this row moved right: [ROW_CONTENT_INSET] costs this column 32dp against the 141dp it had
        // at a 16dp start, and the buttons are unmoved at 165dp-344dp on a 360dp screen.
        Text(
            statusText,
            modifier = Modifier.weight(1f).padding(end = Spacing.sm),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (pending) {
            AnswerButtons(onAnswer = { status -> actions.onAnswer(row.habitId, slot, status) })
        } else {
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
 *  `onNodeWithText` assertions — Compose's text matcher never reads `contentDescription`.
 *
 *  today-row-alignment: [answeredStatus] arrives as the rendered [AnnotatedString] rather than as a
 *  plain `String`, and the label collapses [TODAY_SLOT_STATUS_GAP] back to a single space before
 *  reading it out. Those extra spaces are typographic separation between two rendered halves, not
 *  words — a label is spoken, not laid out — and `TodayAnsweredSlotComposeTest` asserts these
 *  sentences by exact match, so the difference is visible rather than a matter of taste. */
@Composable
private fun ChangeButton(habitName: String, answeredStatus: AnnotatedString, onClick: () -> Unit) {
    val spoken = answeredStatus.text.replace(TODAY_SLOT_STATUS_GAP, " ")
    val description = stringResource(R.string.today_slot_change_a11y, habitName, spoken)
    TextButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(stringResource(R.string.today_slot_change))
    }
}
