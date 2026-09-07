@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jjrapps.constanza.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
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
import com.jjrapps.constanza.core.ui.rememberTimeOfDayFormat
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
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
/** [muted] is `true` for exactly one section, [TodaySectionKind.DONE] (today-grouped-sections,
 *  design.md) — "Ahora" and "Más tarde" both render at full brightness, since a "Más tarde" row is
 *  still answerable early and dimming it would be a false affordance. Threaded down to
 *  [HabitRollupHeader] and [SlotRow] rather than a `Modifier.alpha()` on this whole `Column`: alpha
 *  is all-or-nothing over a subtree and would wrongly dim the habit's own colour on its name
 *  (identity, not state; the colour overhaul's successor to "never dim the colour dot") and the
 *  answer/Change buttons (live controls in every section) along with the text. */
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
        HabitRollupHeader(row, expanded, multiSlot, onToggleExpanded, muted)
        if (!multiSlot) {
            row.slots.firstOrNull()?.let { slot -> SlotRow(row, slot, zone, actions, muted = muted) }
        } else if (expanded) {
            // No indent, deliberately, and this is where `indented = true` went: an indent here is a
            // second left edge, which is the defect rather than the fix. What distinguishes these
            // rows from one another instead is their reminder time, which `timeIsIdentity` promotes
            // to the front of the line — see [slotStatusText]'s decision 4.
            row.slots.forEach { slot -> SlotRow(row, slot, zone, actions, timeIsIdentity = true, muted = muted) }
        }
    }
}

/** today-row-alignment, decision 3, superseded by the colour overhaul: this Row used to align a
 *  leading colour dot to the name's FIRST LINE via `Alignment.Top`, rather than the row's vertical
 *  centre, so the dot stayed landed on the name however many lines it wrapped to. The dot and its
 *  gap [Spacer] are gone now — see [HabitRollupHeader]'s own KDoc for why — so there is no second
 *  child left to align against the name. `Alignment.Top` stays on this Row anyway: with a
 *  multi-slot habit's Expand/Collapse [TextButton] as the name's only remaining sibling, top
 *  alignment is still what keeps that button pinned to the name's first line rather than drifting
 *  toward the vertical centre of a wrapped, multi-line name. */
@Composable
private fun HabitRollupHeader(
    row: TodayHabitRow,
    expanded: Boolean,
    multiSlot: Boolean,
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
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            // Colour overhaul: a habit's identity colour now paints its own name directly rather
            // than a leading dot beside it — the future habits-by-days report needs colour ON the
            // name to follow a row across a grid, which a dot beside the text cannot do.
            // [demotedSuffix]'s `leadColor` carries [row.colorArgb] on the name span only; the
            // day-status suffix below is state, not identity, and keeps its own muted/quiet tone
            // regardless, through the same `muted` plumbing every other state-only colour on this
            // row already uses.
            //
            // A multi-slot habit's own line has no answerable slot, so the day rollup is the only
            // state it can carry — and it has to carry it while collapsed. Demoted rather than given
            // its own line: it is a summary of the slot lines, not a peer of the habit name.
            demotedSuffix(
                row.habitName,
                stringResource(dayStatusLabel(row.dayStatus)).takeIf { multiSlot },
                muted,
                leadColor = Color(row.colorArgb),
            ),
            // `weight(1f)` for the same reason [SlotRow]'s status text carries one: without it the
            // name takes what it wants and the expand control is squeezed into the remainder.
            modifier = Modifier.weight(1f),
            style = nameStyle,
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
 *  status (14sp) then time (11sp) — three steps down, in the order the eye should take them.
 *
 *  today-grouped-sections, design.md: [muted] recolours only this row's own lead/suffix text — via
 *  [slotStatusText]'s own `muted` pass-through, so the demoted time suffix dims WITH the lead
 *  rather than staying at the brighter `onSurfaceVariant` — and tightens its bottom padding to
 *  [Spacing.xs]. It never touches [AnswerButtons] or [ChangeButton]: both are live controls in
 *  every section, in every row.
 *
 *  today-slot-status-air: `verticalAlignment = Alignment.CenterVertically` used to size this whole
 *  Row from its tallest child — [AnswerButtons]/[ChangeButton] — and centre the status text inside
 *  it. A snoozed slot's status ("Aplazado hasta 15:02") wraps to two lines of `bodyMedium`, which
 *  alone very nearly fills that row: the centring slack that gave a one-line status its visible
 *  headroom under the habit name collapsed to almost nothing, while every one-line row kept it. The
 *  name-to-status gap should not depend on how many lines the status happens to wrap to.
 *
 *  Switching to `Alignment.Top` alone would have driven that headroom to zero on EVERY row, not
 *  just the wrapped one — the trap: `Top` alignment means zero air by definition, and a one-line
 *  row was never air-free. So this stays `Alignment.Top` for layout, and [statusTopPadding] gives
 *  the text back the exact air a one-line row already had before this change: half the slack
 *  `Center` used to leave above one line, `(rowHeight - oneLineHeight) / 2`, floored at 0dp so a
 *  future taller line height (e.g. accessibility font scaling) never goes negative.
 *
 *  [rowHeight] is `max(ButtonDefaults.MinHeight, LocalMinimumInteractiveComponentSize.current)`,
 *  not `ButtonDefaults.MinHeight` alone (40dp) — a `TextButton` is a Material 3 `Surface`, and every
 *  clickable `Surface` wraps itself in `Modifier.minimumInteractiveComponentSize()`, which pads a
 *  control up to the accessible touch-target size (48dp by default) *outside* its own visual 40dp
 *  box when the two differ. That outer 48dp box, not the button's own 40dp, is what `Center` was
 *  ever centring the status text against. Measured at 360dp/2.625x: seeding `ButtonDefaults
 *  .MinHeight` alone into the formula reproduced a visibly SMALLER gap on one-line rows than they
 *  had before this change (26px against a real 37px, i.e. 10dp against a real ~14dp) — the fix
 *  would have shipped its own version of this same defect, just smaller. Reading the touch-target
 *  token this row's own buttons are actually built on, rather than only the button's inner content
 *  height, is what makes the formula answer to what `Center` really did.
 *
 *  That is the same padding for one line and two: constant, not derived from the wrap count. The
 *  buttons stay centred (`Modifier.align(Alignment.CenterVertically)`, RowScope-local) rather than
 *  also going `Top` — measured both ways at 360dp/2.625x; top-aligning them additionally pins
 *  Sí·No·Omitir to the row's first line, which reads as belonging to only half a two-line status,
 *  while centring keeps them anchored to the status block as a whole regardless of its height. */
@Composable
@Suppress("LongParameterList") // muted is the row-level emphasis flag HabitRollupRow threads down.
private fun SlotRow(
    row: TodayHabitRow,
    slot: TodaySlot,
    zone: ZoneId,
    actions: SlotActions,
    timeIsIdentity: Boolean = false,
    muted: Boolean = false,
) {
    val key = slot.keyIn(row.habitId)
    // A reopened slot is pending for display purposes, which is why `bypassSnooze` is derived from
    // this rather than from the stored status: the branch below and the sentence must agree.
    val pending = slot.status == EntryStatus.UNKNOWN || key in actions.reopenedKeys
    // today-status-icons, point 3: `TodayHabitRow.slots.size` is the real source of truth for
    // "does this habit have more than one slot", not `timeIsIdentity` — see [AnsweredStatusRow]'s
    // own KDoc and [slotStatusText]'s "today-status-icons supersedes..." section for why the latter
    // still gates the PENDING sentence's time display (it already agrees with this at every call
    // site) while this recomputes the real count for the ANSWERED glyph row below.
    val multiSlot = row.slots.size > 1
    val statusText = slotStatusText(slot, zone, timeIsIdentity, bypassSnooze = !pending, muted = muted)
    val statusBottomPadding = if (muted) Spacing.xs else 8.dp
    // today-slot-status-air: see the doc comment above. Derived from real tokens rather than a
    // literal — the row's own real driving height, `max(ButtonDefaults.MinHeight,
    // LocalMinimumInteractiveComponentSize.current)`, against one line of this row's own
    // `bodyMedium` — so a future type-scale, touch-target or button-size change carries this
    // constant along with it.
    val rowHeight = maxOf(ButtonDefaults.MinHeight, LocalMinimumInteractiveComponentSize.current)
    val statusTopPadding = with(LocalDensity.current) {
        ((rowHeight - MaterialTheme.typography.bodyMedium.lineHeight.toDp()) / 2).coerceAtLeast(0.dp)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ROW_CONTENT_INSET, end = 16.dp, bottom = statusBottomPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        // `weight(1f)` is load bearing, not styling, and is the same fix ExactAlarmBanner and
        // NotificationPermissionBanner already carry in TodayBanners.kt. Without it the status text
        // takes whatever width it wants and `SpaceBetween` squeezes the button group into the
        // remainder, so "Skip" wrapped mid-word as "Ski / p" — reported from a real Galaxy S25.
        // The buttons keep their intrinsic width and the text wraps instead, which is the right way
        // round: a wrapped sentence is readable, a wrapped control label is not. today-row-alignment
        // moved this row's start from 16dp to 48dp to fix the header/status left-edge mismatch; the
        // colour overhaul's removal of the leading dot moves [ROW_CONTENT_INSET] back to 16dp (see
        // its own KDoc), so this column regains the 32dp it lost — back to 141dp at 360dp rather
        // than 109dp — while the buttons stay unmoved at 165dp-344dp.
        if (pending) {
            Text(
                statusText,
                modifier = Modifier.weight(1f).padding(end = Spacing.sm, top = statusTopPadding),
                style = MaterialTheme.typography.bodyMedium,
                color = if (muted) ConstanzaColors.OnBackgroundMuted else Color.Unspecified,
            )
            AnswerButtons(
                modifier = Modifier.align(Alignment.CenterVertically),
                onAnswer = { status -> actions.onAnswer(row.habitId, slot, status) },
            )
        } else {
            // today-status-icons: the status WORD [statusText] used to render here is gone — a
            // glyph carries it now — but [statusText] itself is still computed above and still fed
            // to [ChangeButton], whose own spoken accessible label (`today_slot_change_a11y`) is
            // unaffected by this slice and still wants the full sentence, time included.
            val timeFormat = rememberTimeOfDayFormat()
            val time = slot.minuteOfDay?.let(timeFormat::format)?.takeIf { multiSlot }
            AnsweredStatusRow(
                status = slot.status,
                time = time,
                muted = muted,
                modifier = Modifier.weight(1f).padding(end = Spacing.sm, top = statusTopPadding),
            )
            ChangeButton(
                row.habitName,
                statusText,
                modifier = Modifier.align(Alignment.CenterVertically),
                onClick = { actions.onRequestChange(key) },
            )
        }
    }
}

@Composable
private fun AnswerButtons(onAnswer: (InAppEntryStatus) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
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
 *  sentences by exact match, so the difference is visible rather than a matter of taste.
 *
 *  today-status-icons: explicitly [ConstanzaColors.OnBackgroundMuted] rather than the `primary`
 *  this `TextButton` used to inherit — "Cambiar" is a repeated per-row action, one on every
 *  answered slot on the whole screen, and at `primary` it competed with the habit names for
 *  attention. Still a `TextButton` with the same label and the same 48dp touch target; whether it
 *  eventually becomes an icon or disappears is a separate, still-open design question. */
@Composable
private fun ChangeButton(
    habitName: String,
    answeredStatus: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spoken = answeredStatus.text.replace(TODAY_SLOT_STATUS_GAP, " ")
    val description = stringResource(R.string.today_slot_change_a11y, habitName, spoken)
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = ConstanzaColors.OnBackgroundMuted),
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Text(stringResource(R.string.today_slot_change))
    }
}
