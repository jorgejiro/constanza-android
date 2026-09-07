@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jjrapps.constanza.tracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.rememberTimeOfDayFormat
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.Spacing
import com.jjrapps.constanza.domain.model.EntryStatus
import java.time.Instant
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
    val actions = remember(onAnswer) {
        SlotActions(
            onAnswer = onAnswer,
            onOpenChangeDialog = { row, slot ->
                changeDialogTarget = ChangeDialogTarget(row.habitId, row.habitName, slot)
            },
        )
    }
    val dateNavActions = remember(onPreviousDay, onNextDay, onToday) {
        DateNavActions(onPreviousDay, onNextDay, onToday)
    }
    changeDialogTarget?.let { target ->
        ChangeAnswerDialog(
            habitName = target.habitName,
            current = target.slot.status,
            onSelect = { status ->
                onAnswer(target.habitId, target.slot, status)
                changeDialogTarget = null
            },
            onDismiss = { changeDialogTarget = null },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today_title)) },
                actions = {
                    TextButton(onClick = onManageHabits) {
                        Text(stringResource(R.string.today_manage_habits))
                    }
                    // today-status-icons: a gear is the single most universally recognised icon in
                    // mobile UI, and the top bar is exactly where horizontal space is scarcest — a
                    // clear win for an icon over the word here. `Icons.Filled.Settings` is part of
                    // `material-icons-core` (app/build.gradle.kts), the only icon artifact this
                    // project depends on.
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.today_settings),
                        )
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

/** today-row-alignment, decision 1, revised by the colour overhaul: the ONE left edge every line
 *  of a habit row starts at.
 *
 *  today-row-alignment fixed a defect where the name row and the status row disagreed about their
 *  left edge, and derived this token from the colour dot's own geometry so the two could never
 *  drift apart again: the row's own [Spacing.lg] gutter, plus the dot's 24dp slot, plus one
 *  [Spacing.sm] gap before the text — 48dp. The colour overhaul removes the dot outright: a habit's
 *  identity colour now paints its name text directly (see [HabitRollupHeader]), so there is no dot
 *  slot and no gap left to reserve room for, and this token collapses back to the plain [Spacing.lg]
 *  screen margin every other screen already uses. One token rather than a literal is still the
 *  point — the header row, [SlotRow] and the section headers below all read this same value, so
 *  they cannot silently drift apart again. Measured: widens the text column by 32dp of 360 (8.9%)
 *  on the reference 360dp phone. */
// today-grouped-sections: `internal`, not `private` — [TodaySectionHeader]/[TodaySectionDivider]
// in TodaySectionHeader.kt need this same one left edge, and moving them out of this file (rather
// than growing it past detekt's file-level TooManyFunctions threshold) is exactly what
// TodayDateBar.kt, TodayBanners.kt and TodayAddHabitAction.kt already did for the identical reason.
internal val ROW_CONTENT_INSET = Spacing.lg

/** One holder instead of two extra parameters on both [HabitRollupRow] and [SlotRow], which would
 *  otherwise push each past detekt's unconfigured `LongParameterList` default of 6 — and, one level
 *  up, past [TodayContent]'s own threshold too, which is why this is built in [TodayScreen] and
 *  threaded down as one value rather than passing `onAnswer`/`onOpenChangeDialog` separately.
 *
 *  today-one-line-row: the old `reopenedKeys`/`onRequestChange` fields (today-answered-slot-collapse,
 *  design.md decision 5) are gone. That set existed only so a row already answered could ask to see
 *  its answer buttons again after "Cambiar"; the change dialog now answers directly and needs no
 *  presented "reopened" state to reveal anything. [onOpenChangeDialog] replaces it — an answered
 *  slot's row calls it with the row and the tapped slot, and [TodayScreen] turns that into the
 *  dialog target it holds as local state. */
private data class SlotActions(
    val onAnswer: (Long, TodaySlot, InAppEntryStatus) -> Unit,
    val onOpenChangeDialog: (TodayHabitRow, TodaySlot) -> Unit,
)

/** today-one-line-row, point 2: which slot's change dialog [TodayScreen] currently shows, if any.
 *  [habitName] rides along rather than being looked up again from [TodayUiState.rows] by
 *  [habitId] — the dialog is titled with it, and by the time it renders the row it came from may
 *  already be gone from that list (e.g. `groupTodayRows` moved it to another section on the exact
 *  frame the dialog opened). */
private data class ChangeDialogTarget(val habitId: Long, val habitName: String, val slot: TodaySlot)

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
            // today-grouped-sections, design.md: three ordered sections ("Ahora" / "Más tarde" /
            // "Hecho"), each introduced by a header and, under it, a hairline rule that stops at
            // ROW_CONTENT_INSET rather than running full-bleed. A section [state.sections] never
            // carries with zero rows, so this loop never emits an empty section's header at all —
            // the design's explicit "empty group renders nothing" rule, for free.
            state.sections.forEach { section ->
                val muted = section.kind == TodaySectionKind.DONE
                item(key = "section-header-${section.kind}") { TodaySectionHeader(section.kind) }
                item(key = "section-divider-${section.kind}") { TodaySectionDivider() }
                items(section.rows, key = { it.habitId }) { row ->
                    val expanded = row.habitId in state.expandedHabitIds
                    HabitRollupRow(row, expanded, state.zone, onToggleExpanded, actions, muted)
                }
            }
        }
    }
}

/** OA-2, as revised (design.md §1): a single-slot habit reads as one plain row; a multi-slot habit
 *  shows the day rollup and expands to each independently answerable slot.
 *
 *  today-one-line-row rewrites what "one plain row" means, following the owner-approved
 *  `TodayOneLineRowPrototype`: a single-slot habit's name and its trailing content — two answer
 *  pills, or the answered glyph — now share ONE line ([SingleSlotRow]), rather than the name
 *  sitting on its own line above a second line carrying the old text-and-buttons [SlotRow]. The
 *  defect this fixes was measured, not merely disliked: the old three-line shape (name, then a
 *  near-empty line with a small glyph hard left and "Cambiar" hard right) fit 9 habits in 700dp
 *  where the merged one-line shape needs about 842dp for 6.
 *
 *  A multi-slot habit is unaffected by that merge — [HabitRollupHeader] still carries only the
 *  name, its day rollup and the expand control, and each of its slot lines ([SlotRow]) still has
 *  no name to share a line with, since today-row-alignment already established that no slot line
 *  ever repeats it. */
/** [muted] is `true` for exactly one section, [TodaySectionKind.DONE] (today-grouped-sections,
 *  design.md) — "Ahora" and "Más tarde" both render at full brightness, since a "Más tarde" row is
 *  still answerable early and dimming it would be a false affordance. Threaded down to every child
 *  below rather than a `Modifier.alpha()` on this whole `Column`: alpha is all-or-nothing over a
 *  subtree and would wrongly dim the habit's own colour on its name (identity, not state) and the
 *  answer pills (live controls in every section) along with the text. */
@Composable
@Suppress("LongParameterList") // muted is the row-level emphasis flag every child below already threads.
private fun HabitRollupRow(
    row: TodayHabitRow,
    expanded: Boolean,
    zone: ZoneId,
    onToggleExpanded: (Long) -> Unit,
    actions: SlotActions,
    muted: Boolean,
) {
    val multiSlot = row.slots.size > 1
    // today-grouped-sections: a muted row's own outer gap is 0dp rather than [Spacing.xs] (4dp) —
    // there is no smaller `Spacing` tier, and a muted row needs to read tighter than a live one's
    // own 4dp, not merely match it.
    val rowVerticalPadding = if (muted) 0.dp else 4.dp
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = rowVerticalPadding)) {
        if (multiSlot) {
            HabitRollupHeader(row, expanded, onToggleExpanded, muted)
            if (expanded) {
                // No indent, deliberately — an indent here is a second left edge, which is the
                // defect today-row-alignment fixed rather than something to reintroduce. What
                // distinguishes these rows from one another instead is their reminder time, hard
                // right on each line (see [TodaySlotTrailing]).
                row.slots.forEach { slot -> SlotRow(row, slot, zone, actions, muted = muted) }
            }
        } else {
            SingleSlotRow(row, row.slots.single(), zone, actions, muted)
        }
    }
}

/** today-one-line-row: the header row's own natural content height — close to [MaterialTheme]'s
 *  `bodyLarge.lineHeight` (24sp, the M3 token) — that [HabitRollupHeader]'s expand/collapse
 *  control is reported at via [reportPaintedHeight], rather than the ~48dp its own touch-target
 *  padding would otherwise force the header `Row` to grow to. Slightly generous for a MUTED
 *  header (`bodyMedium.lineHeight` is 20sp), which only ever makes that row a hair taller than
 *  pure text alone would, never shorter — an overshoot, not a regression. */
private val EXPANDER_REPORTED_HEIGHT = 24.dp

/** A multi-slot habit's own name line: no trailing content at all (today-one-line-row) — every
 *  slot's own state lives on its own [SlotRow] line below, once expanded — only the day rollup
 *  demoted after the name, and the expand/collapse control.
 *
 * today-one-line-row, vertical-rhythm fix: `verticalAlignment` is [Alignment.CenterVertically]
 * now, not [Alignment.Top]. A render exposed why: [TextButton] carries its own ~48dp touch-target
 * padding (Material's `minimumInteractiveComponentSize`) around a shorter visual label, and with
 * `Top` alignment that taller box sat pinned to the row's top edge while its OWN label centred
 * inside it — visually landing well below the habit name's own top-anchored first line, which
 * read as "the name on one line, the expander floating alone on the next" even though both are
 * one `Row`. Centring the row instead lines up the label's own centre with the name's, and
 * [reportPaintedHeight] (see its own KDoc) stops that same ~48dp box from setting the row's
 * height at all, letting it overlap into the row's own padding exactly like the answer pills'
 * identical fix.
 *
 * Trade-off, stated rather than silently made: [Alignment.Top] was originally chosen so the
 * control stays pinned to a WRAPPED, multi-line name's first line rather than drifting toward the
 * wrapped block's vertical centre. `Centre`-aligning trades that rare case (a name long enough to
 * wrap AND still fit the control beside it) for fixing the common one this render actually showed
 * broken. Neither this function nor [demotedSuffix] truncates the name to force a fit — a name
 * that does not fit still wraps, exactly as before; only the control's own vertical anchor point
 * against a WRAPPED name changed. If a name plus its day-status suffix plus the control
 * genuinely cannot share one line even before wrapping, the day-status suffix is what should give
 * way first (it is already demoted, state rather than identity) — that is not implemented here,
 * since no render has yet shown it necessary. */
@Composable
private fun HabitRollupHeader(
    row: TodayHabitRow,
    expanded: Boolean,
    onToggleExpanded: (Long) -> Unit,
    muted: Boolean,
) {
    // today-grouped-sections: [muted] never touches the habit's own colour (identity, not state) —
    // the same rule that used to protect the colour dot now protects the coloured name text. It
    // still changes the name's size step-down and the demoted suffix's tone, both state rather than
    // identity.
    val nameTopPadding = if (muted) Spacing.xs else Spacing.sm
    val nameStyle = if (muted) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    Row(
        // `end` padding: a long habit name — the reported case was a full sentence — otherwise runs
        // to the very edge of the screen with nothing between it and the bezel.
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, top = nameTopPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // Colour overhaul: a habit's identity colour now paints its own name directly rather
            // than a leading dot beside it. [demotedSuffix]'s `leadColor` carries [row.colorArgb]
            // on the name span only; the day-status suffix below is state, not identity, and keeps
            // its own muted/quiet tone regardless, through the same `muted` plumbing every other
            // state-only colour on this row already uses.
            //
            // A multi-slot habit's own line has no answerable slot, so the day rollup is the only
            // state it can carry — and it has to carry it while collapsed. Demoted rather than given
            // its own line: it is a summary of the slot lines, not a peer of the habit name.
            demotedSuffix(
                row.habitName,
                stringResource(dayStatusLabel(row.dayStatus)),
                muted,
                leadColor = Color(row.colorArgb),
            ),
            // `weight(1f)`: without it the name takes what it wants and the expand control is
            // squeezed into the remainder.
            modifier = Modifier.weight(1f),
            style = nameStyle,
        )
        val labelRes = if (expanded) R.string.today_collapse else R.string.today_expand
        TextButton(
            onClick = { onToggleExpanded(row.habitId) },
            modifier = Modifier.reportPaintedHeight(EXPANDER_REPORTED_HEIGHT),
        ) { Text(stringResource(labelRes)) }
    }
}

/** today-one-line-row: the merged single-slot row — the habit name, `weight(1f)`, and its trailing
 *  content ([TodaySlotTrailing]) hard right, both on the one line the habit's single slot needs.
 *  This is what used to be [HabitRollupHeader] (the name alone) immediately followed by a separate
 *  [SlotRow] (the status text and its buttons, on their own line below); today-status-icons already
 *  established that a single-slot habit shows no day rollup at all — its own slot state already
 *  says everything the row has — so there is nothing left to demote onto the name here, unlike
 *  [HabitRollupHeader]'s multi-slot day rollup.
 *
 *  today-one-line-row, point 2: an ANSWERED slot's whole line is now the tap target that opens
 *  [ChangeAnswerDialog] — "Cambiar" is gone, so the row itself has to say it is tappable. A pending
 *  slot's line carries no click of its own; only its two [TodayAnswerPills] do.
 *
 *  today-one-line-row, vertical-rhythm correction: [Dimens.MinTouchTarget] is a real MINIMUM
 *  height on this whole row now, not merely on a child inside it. The first cut of this redesign
 *  read "fix it downward, not upward" as absolute and kept this row at text height even once
 *  answered — correct while the row was passive, wrong the moment it became the ONLY route to
 *  [ChangeAnswerDialog]: a muted (Contestados) answered row measured 28.6dp tall on a real render,
 *  a control a finger can miss. [reportPaintedHeight] (on the pills, `TodayAnswerPills.kt`, and on
 *  the multi-slot expander, [HabitRollupHeader]) still does real work here — it stops THEIR OWN
 *  48dp touch targets from becoming a SECOND, independent reason this row grows past the floor —
 *  but the floor itself now belongs to the row, applied unconditionally to both pending and
 *  answered lines so both settle at the same minimum for the same reason. */
@Composable
private fun SingleSlotRow(row: TodayHabitRow, slot: TodaySlot, zone: ZoneId, actions: SlotActions, muted: Boolean) {
    val nameTopPadding = if (muted) Spacing.xs else Spacing.sm
    val nameStyle = if (muted) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    val statusBottomPadding = if (muted) Spacing.xs else 8.dp
    val answered = slot.status != EntryStatus.UNKNOWN
    var rowModifier = Modifier.fillMaxWidth()
    if (answered) {
        val description = answeredRowDescription(row.habitName, slot, time = null)
        rowModifier = rowModifier
            .clickable(role = Role.Button, onClick = { actions.onOpenChangeDialog(row, slot) })
            .semantics { contentDescription = description }
    }
    rowModifier = rowModifier
        .heightIn(min = Dimens.MinTouchTarget)
        .padding(
            start = Spacing.lg,
            end = Spacing.lg,
            top = nameTopPadding,
            bottom = statusBottomPadding,
        )
    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            demotedSuffix(row.habitName, suffix = null, muted = muted, leadColor = Color(row.colorArgb)),
            modifier = Modifier.weight(1f),
            style = nameStyle,
        )
        Spacer(Modifier.width(Spacing.sm))
        TodaySlotTrailing(row, slot, zone, timeIsIdentity = false, muted, actions)
    }
}

/** today-row-alignment's multi-slot slot line, revised by today-one-line-row: the status TEXT and
 *  its button row are gone (see [TodaySlotTrailing]) — this is now only [ROW_CONTENT_INSET] on the
 *  left, [TodaySlotTrailing] hard right ([Arrangement.End]), and — for an answered slot — the whole
 *  line as its own change-dialog tap target, exactly like [SingleSlotRow]'s merged row.
 *
 *  [Dimens.MinTouchTarget] applies here unconditionally too — see [SingleSlotRow]'s own KDoc for
 *  why the floor belongs to the row rather than to any one child inside it. */
@Composable
private fun SlotRow(row: TodayHabitRow, slot: TodaySlot, zone: ZoneId, actions: SlotActions, muted: Boolean = false) {
    val statusBottomPadding = if (muted) Spacing.xs else 8.dp
    val answered = slot.status != EntryStatus.UNKNOWN
    var rowModifier = Modifier.fillMaxWidth()
    if (answered) {
        val timeFormat = rememberTimeOfDayFormat()
        val time = slot.minuteOfDay?.let(timeFormat::format)
        val description = answeredRowDescription(row.habitName, slot, time)
        rowModifier = rowModifier
            .clickable(role = Role.Button, onClick = { actions.onOpenChangeDialog(row, slot) })
            .semantics { contentDescription = description }
    }
    rowModifier = rowModifier
        .heightIn(min = Dimens.MinTouchTarget)
        .padding(start = ROW_CONTENT_INSET, end = 16.dp, bottom = statusBottomPadding)
    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TodaySlotTrailing(row, slot, zone, timeIsIdentity = true, muted, actions)
    }
}

/** The accessible label an answered row's click action carries — unchanged in substance from the
 *  old "Cambiar" `TextButton`'s own label (today-answered-slot-collapse, design.md decision 3): the
 *  habit name plus the slot's own answered sentence, [time] first when this is a multi-slot habit's
 *  own line (`null` on a single-slot row, today-status-icons point 3). `today_slot_change_a11y` is
 *  reused verbatim — the row is a new tap target, but what tapping it DOES has not changed. */
@Composable
private fun answeredRowDescription(habitName: String, slot: TodaySlot, time: String?): String {
    val statusWord = stringResource(slotStatusLabel(slot.status))
    val spoken = if (time != null) "$time $statusWord" else statusWord
    return stringResource(R.string.today_slot_change_a11y, habitName, spoken)
}

/**
 * today-one-line-row: the trailing content every Today slot line ends in, hard right — the merged
 * name line for a single-slot habit ([SingleSlotRow]), or one [SlotRow] line per slot once a
 * multi-slot habit is expanded. Lifted straight from `TodayOneLineRowPrototype`'s own `Trailing`:
 * the scheduled time (only when [timeIsIdentity], today-status-icons point 3) leads, then either
 * two [TodayAnswerPills] or the answered glyph — never a status WORD, which is the whole point:
 * a row offering Sí/No is pending by definition, so "Pendiente" said nothing "Sí"/"No" did not
 * already say louder.
 *
 * The one thing the prototype never had to model is a still-SNOOZED pending slot — its own
 * timestamp is real information a redesign must not quietly drop, so it renders here as a second
 * demoted line of text before the pills, exactly where "Pendiente, aplazado hasta…" used to sit.
 */
@Composable
@Suppress("LongParameterList") // muted is the row-level emphasis flag every caller above already threads.
private fun TodaySlotTrailing(
    row: TodayHabitRow,
    slot: TodaySlot,
    zone: ZoneId,
    timeIsIdentity: Boolean,
    muted: Boolean,
    actions: SlotActions,
) {
    val timeFormat = rememberTimeOfDayFormat()
    val time = slot.minuteOfDay?.takeIf { timeIsIdentity }?.let(timeFormat::format)
    val demotedColor = if (muted) ConstanzaColors.OnBackgroundMuted else Color.Unspecified
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (time != null) {
            Text(time, style = MaterialTheme.typography.bodyMedium, color = demotedColor)
            Spacer(Modifier.width(Spacing.sm))
        }
        if (slot.status == EntryStatus.UNKNOWN) {
            slot.snoozedUntilEpochMs?.let { epochMs ->
                Text(snoozeSentence(epochMs, zone), style = MaterialTheme.typography.bodyMedium, color = demotedColor)
                Spacer(Modifier.width(Spacing.sm))
            }
            TodayAnswerPills(row.habitName) { status -> actions.onAnswer(row.habitId, slot, status) }
        } else {
            AnsweredStatusRow(status = slot.status)
        }
    }
}

@Composable
private fun snoozeSentence(snoozedUntilEpochMs: Long, zone: ZoneId): String {
    val timeFormat = rememberTimeOfDayFormat()
    return stringResource(
        R.string.today_slot_pending_snoozed_until,
        timeFormat.format(Instant.ofEpochMilli(snoozedUntilEpochMs).atZone(zone).toLocalTime()),
    )
}
