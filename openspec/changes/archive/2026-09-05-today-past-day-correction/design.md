# Design: Today Gains Date Navigation, So a Past Day Can Finally Be Corrected

## Technical Approach

One `MutableStateFlow<TodayDate>` replaces `observedDate` (`TodayViewModel.kt:89`). `TodayDate`
carries clock truth and an optional deliberate navigation; the viewed date is a **projection**, not a
stored value. Both existing clock-driven writers (`:155`, `:192`) keep writing **unconditionally** —
they just write the `clock` field, which no longer decides what is on screen while a navigation is
active. `uiState`'s `flatMapLatest` (`:123-151`) keys on a two-field projection of that state and is
otherwise untouched. `TodayScreen` gains one date bar and one past-day branch; `EntryWriter`,
`TodayModel`, `OccurrenceResolver` and the collapsed-row rendering are **not modified**, only proven.

This extends `today-midnight-rollover` rather than undoing it: `CurrentDateSource` stays the
clock-truth port, the `flatMapLatest` stays, the `ON_RESUME` correction stays, and no broadcast
bridge or event bus is introduced.

## Architecture Decisions

### Decision 1 — one date state object with a projected viewed date, not guarded writers

**Invariant.** Let `clock` be the latest value from `CurrentDateSource` (timer or `today()`), and
`navigated` be a `LocalDate?` set only by a user gesture. Then:

1. `viewed = navigated ?: clock` — total, with no branch at any writer.
2. Every clock-driven writer assigns `clock` only, always, with no condition.
3. Return-to-live-edge is `navigated = null`, which makes `viewed` the **current** `clock` — so a
   date change that happened while away is caught up by construction, with no extra clock read.
4. `navigated` is only ever set to a date strictly earlier than `clock`, so `navigated != null` is
   exactly "the user is on a past day" (see Decision 5's `isPastDay`).

```kotlin
private data class TodayDate(val clock: LocalDate, val navigated: LocalDate? = null) {
    val viewed: LocalDate get() = navigated ?: clock
}
private val dateState = MutableStateFlow(TodayDate(currentDateSource.today()))

// :155  init  -> dateState.update { it.copy(clock = date) }          // unconditional
// :192  refreshDate() -> dateState.update { it.copy(clock = currentDateSource.today()) }  // unconditional

private data class DateView(val date: LocalDate, val isPastDay: Boolean)
private val dateView = dateState
    .map { DateView(it.viewed, it.navigated != null) }
    .distinctUntilChanged()          // a clock tick while navigated away emits nothing
```

`showNextDay()` computes `next = viewed.plusDays(1)` and sets `navigated = null` when `next >= clock`
rather than pinning `navigated = clock`. Pinning would freeze the view on that date across the next
midnight — the same bug, re-entered through the forward door.

| Option | Trade-off | Verdict |
|---|---|---|
| Guard both writers with `if (atLiveEdge)` | The correctness condition is restated at every writer; a third writer added later silently reintroduces the bug — the exact failure mode the proposal names. Return-to-today then needs a fourth clock read. | rejected |
| Two flows (`clockDate`, `navigatedDate`) + `combine` | Correct, but two `.value` reads and a second `stateIn` to feed `uiState`'s seed at `:150`; the two fields are never independently meaningful. | rejected |
| One `TodayDate` + projection | One atomic `update`, writers stay unconditional, `distinctUntilChanged` reproduces exactly the `MutableStateFlow` conflation `today-midnight-rollover` relied on. | **chosen** |

### Decision 2 — in-place navigation, no route change

**Choice**: `ConstanzaRoute.Today` is untouched; the viewed date lives in `TodayViewModel` and dies
with it.

**Basis (the one the exploration named): process death while reviewing a past day must land on
today.** A past day is a transient review context, not a destination. Today is the start
destination, so a date argument on it means a cold start can open three weeks in the past with no
relationship to the clock. Worse, it splits state lifetimes: `expandedHabitIds` and `reopenedSlots`
die with the ViewModel, so a restored route date would rebuild a past day with cleared expansion —
precisely the mismatch `today-answered-slot-collapse` rejected `rememberSaveable` for ("state
restored against a different day's rows").

**What the losing option would have bought**: process-death survival, and a deep-linkable
"review this date" destination. Rotation is *not* among its gains — the ViewModel already survives
configuration change, so the navigated date survives rotation under the chosen option too. Nothing
in scope asks for either gain.

### Decision 3 — clear on navigate, do not key by date

**Choice**: `expandedHabitIds` (`:90`) and `reopenedSlots` (`:95`) are cleared by the three
navigation entry points, and only when the navigation actually changes `viewed`.

**Rationale**: both flows already document themselves as "presented state only, never persisted,
since neither means anything once the day rolls over". Keying by date would *preserve* meaning this
design says is not there, and — because backward navigation is unbounded — would grow without limit
and need its own eviction policy. Clearing is one line in the same gesture handler and makes
`today-answered-slot-collapse`'s reasoning structural: the state cannot outlive the date it belongs
to. Write the clears first and the date last, so no emission pairs a new date with a stale set.

**Deliberately narrow**: a *midnight rollover at the live edge* still leaves both sets alone, exactly
as today. **Correction, found in validation:** that behaviour is NOT tested. `TodayViewModelTest.kt:270`
asserts Room re-subscription, the rollup and the occurrence filter across a rollover — nothing in that
file touches `expandedHabitIds`/`reopenedSlots` across one. The behaviour is structurally true (no code
clears them), so the decision stands, but its justification rested on coverage that does not exist.
`sdd-tasks` MUST add that missing assertion to the existing rollover test rather than leave the claim
unbacked. It is also harmless
(a reopened key on a fresh `UNKNOWN` slot hits the branch that already shows the answer buttons).
Widening the clear to every viewed-date change would change shipped, tested behaviour for no gain.

### Decision 4 — a date bar under the app bar; return-to-today is a control that only exists off the live edge

**Choice**: one new `TodayDateBar` composable in a new `tracking/TodayDateBar.kt`, rendered above
the permission banners in **both** branches.

**Settled in validation — the three callbacks travel in a holder, not as loose parameters.**
`TodayContent` (`TodayScreen.kt:144-151`) already takes 5 parameters; adding `onPreviousDay`,
`onNextDay` and `onToday` would make 8. `LongParameterList` is unconfigured in
`config/detekt/detekt.yml`, so detekt's default function threshold of 6 applies under
`build.maxIssues: 0`, and the `@Suppress("LongParameterList")` at `TodayScreen.kt:95` covers only
`fun TodayScreen`'s own declaration, never a sibling private composable. Introduce a
`DateNavActions` holder in exactly the shape `today-answered-slot-collapse` used for `SlotActions`
(`TodayScreen.kt:129-143`). No new suppression is added — that was Decision 4's stated intent.

**Settled in validation — the bar is hoisted ABOVE the `LazyColumn`, not made its first item.**
In the non-empty branch `TodayContent` is a `LazyColumn` (`TodayScreen.kt:167`), so a bar placed as
its first element scrolls out of view on a long list. This decision's whole rationale is that the
bar's PRESENCE is the "you are not on today" signal, and a signal that scrolls away is not a signal.
Hoist it so it stays fixed above the scrolling content in both branches. The `TopAppBar`
is not touched at all, so every existing assertion on `today_title` / `today_settings` /
`today_manage_habits` survives untouched.

```
[ ‹ ]            4 Sep 2026                            (live edge)
[ ‹ ]            2 Sep 2026            [ › ] [ Today ] (past day)
```

| Element | Control | Why |
|---|---|---|
| Previous day | `IconButton` + `Icons.AutoMirrored.Filled.KeyboardArrowLeft`, `contentDescription = today_previous_day` | `IconButton` has a transparent container, so **no new filled control** is introduced and the carried-forward 1.17:1 `SurfaceSelected` fill item is not repeated. Adds no M3 role, so `Theme.kt`'s role audit and `ColorContrastTest` stay untouched — the same discipline `ChangeButton` followed. |
| Date label | `Text`, `Modifier.weight(1f)`, centred | `weight(1f)` is the load-bearing fix `SlotRow` and both banners already carry; without it the label takes what it wants and the controls wrap mid-word (the recorded "Ski / p" defect). It also makes the bar scale cleanly at `sw >= 600dp`, satisfying `Minimal Adaptive Resilience` with no dedicated layout. |
| Next day | same, `KeyboardArrowRight`, `today_next_day` — **absent** at the live edge | There is nothing forward to reach, so the control is removed rather than disabled: a permanently-disabled control on the app's most-visited screen is noise. |
| Back to today | `TextButton`, label `today_back_to_today` — **absent** at the live edge | Its presence *is* the "you are not on today" signal, reinforced by a label naming another date. Absent at the live edge, the bar holds exactly one control, so the crowded three-control state is the deliberate, transient one. |

`material-icons-core` is the only icon artifact in this project (`libs.versions.toml:45`); both arrow
icons ship in it. If either is absent at implementation time, fall back to text-label buttons — the
repo's established rule, already recorded in `DataPortabilityScreen.kt:31-32`.

**Date formatting**: `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(...)`,
remembered locally in `TodayDateBar` on `LocalConfiguration`, so a per-app language override is
honoured. This is **not** a contradiction of `TimeOfDayFormat`'s "explicit pattern, never
`ofLocalizedTime`" rule: that rule exists because the *hour cycle* is a device setting that must beat
the locale. Date field order has no competing device setting — the locale is the authority — so the
localized formatter is correct. It stays local rather than moving to `core/ui` because there is
exactly one call site; `TimeOfDayFormat` exists only because three sites had duplicated the decision.
This is a format read, not a clock read, so detekt's `ForbiddenMethodCall` ban does not apply.

### Decision 5 — a past day states its emptiness and offers no intake

**Choice**: `TodayUiState` gains `val isPastDay: Boolean = false` (defaulting to the value that
reproduces existing behaviour, the same discipline `notificationPermission` already uses). Then:

- Empty branch (`TodayContent`, `state.rows.isEmpty()`): render `TodayPastDayEmptyState()` —
  text only, `today_empty_past` — instead of `TodayEmptyState(onAddHabit)`.
- Non-empty branch: emit the `TrailingAddHabitAction(onAddHabit)` item only when `!isPastDay`.
- Permission banners still render on a past day. They are about the app's ability to remind, not
  about the viewed date; suppressing them would be scope creep.

`TodayPastDayEmptyState` lives in the new `TodayDateBar.kt` alongside the bar, following the repo's
existing per-concern split (`TodayBanners.kt`, `TodayAddHabitAction.kt`) rather than growing
`TodayScreen.kt` or misfiling past-day copy into the add-habit file.

## Data Flow

    CurrentDateSource.dates() ──┐
    ON_RESUME → refreshDate() ──┴─► dateState.clock          (unconditional, always)
    ‹ / › / Today  ─────────────────► dateState.navigated    (gesture only; clears expansion sets)
                                             │
                     map { DateView(viewed, navigated != null) }.distinctUntilChanged()
                                             │
                     flatMapLatest ─► combine(5) ─► uiState(date, isPastDay, rows)
                                             │
                     answer() ─► entryWriter.answerInApp(habitId, uiState.value.date, …)

## File Changes

| File | Action | Est. lines (add+del) | Slice |
|---|---|---|---|
| `tracking/TodayViewModel.kt` | Modify | 70–90 | A |
| `test/.../tracking/TodayViewModelTest.kt` | Modify | 130–170 | A |
| `tracking/TodayDateBar.kt` | Create | 70–95 | B |
| `tracking/TodayScreen.kt` | Modify | 35–50 | B |
| `res/values/strings.xml` | Modify | 6–8 | B |
| `res/values-es/strings.xml` | Modify | 6–8 | B |
| `androidTest/.../tracking/TodayPastDayComposeTest.kt` | Create | 100–140 | B |
| `androidTest/.../tracking/EntryWriteParityTest.kt` | Modify | 40–60 | B |
| `androidTest/.../tracking/TodayAddHabitComposeTest.kt` | Modify (contingent) | 0–15 | B |
| `openspec/changes/today-past-day-correction/specs/habit-entry-tracking/spec.md` | ALREADY WRITTEN — 0 | 0 | B |
| `openspec/config.yaml` | Modify | 5–15 | B |
| **Total** | | **462–651** | |
| **Slice A subtotal** | | **200–260** | |
| **Slice B subtotal** | | **262–391** | |

**Forecast conclusion (settled in validation): ONE PR.** The earlier 517–736 figure double-counted
the spec delta, which is already written. Recomputed at 462–651, the top of the range sits below both
the 800-line review budget and the proposal's own ~700 split trigger (`proposal.md:129-130`), so the
pre-declared A/B seam stays declared but unused. `sdd-tasks` re-forecasts from its own task list; if
ITS number crosses ~700, hold the seam and split — the rows are already assigned.

Unchanged and **proven, not modified**: `tracking/EntryWriter.kt`, `tracking/TodayModel.kt`,
`reminding/OccurrenceResolver.kt`, `core/time/CurrentDateSource.kt`, and `SlotRow`'s collapsed-row
branch.

## Interfaces / Contracts

```kotlin
// TodayViewModel — new public surface, three gestures
fun showPreviousDay()   // navigated = viewed.minusDays(1); clears expansion sets
fun showNextDay()       // next >= clock -> navigated = null; else navigated = next
fun showToday()         // navigated = null  (re-attaches to clock, catching up)

// TodayUiState
val isPastDay: Boolean = false
```

`answer()`, `refreshDate()`, `requestChange()` and `toggleExpanded()` keep their exact signatures.

## Testing Strategy

**`:domain` (strict TDD)**: this change adds **no `:domain` production code** — the whole change is
`:app` UI/ViewModel plus resources. Strict TDD therefore has no subject here; existing `rollupDay`
tests stay green as regression. `sdd-tasks` must not manufacture a `:domain` task.

**Gaps found in validation — `sdd-tasks` MUST give these a vehicle.** Walking all 24 delta-spec
scenarios against the table below, twenty have an explicit vehicle and two do not:

- *"Any past slot is freely re-editable to any status"* — the table's test 12 covers only
  `MISSED → Change → Yes → Done`. The unrestricted-editing decision is a settled premise AND a
  success criterion (`proposal.md:162`), so it needs a test walking `COMPLETED → MISSED → SKIPPED`
  twice in a row. Nothing in the code restricts it; that is precisely why it must be pinned.
- *"Streak interaction"* — covered here only as "existing `rollupDay` tests stay green". Streak
  recompute after a past-day correction needs to be named explicitly.

**Accepted boundary (validation finding, not a defect).** Invariant 4 states `navigated != null` is
exactly "on a past day". A *backward* clock move — westward timezone travel making `today()` return
an earlier date — would leave `navigated >= clock` and show past-day chrome on the live date until
the next forward tap self-heals it. This is consistent with the already-accepted foregrounded
timezone-travel boundary in the delta spec and the open item `today-foregrounded-timezone-travel`.
Do not build machinery for it.

**`:app` (non-strict)** — the coexistence tests the proposal declares MANDATORY:

| # | Layer | Scenario | Vehicle |
|---|---|---|---|
| 1 | JVM | Midnight tick while on a past day does not move `uiState.value.date` | `FakeCurrentDateSource.advanceTo` |
| 2 | JVM | `refreshDate()` while on a past day does not move it | `FakeCurrentDateSource.current` alone (the backgrounded-resume shape it already models) |
| 3 | JVM | `showToday()` after a tick that fired while away lands on the **new** clock date | both fake halves |
| 4 | JVM | At the live edge, both writers still move the view | `today-midnight-rollover` regression |
| 5 | JVM | Forward-onto-today re-attaches: a later tick then moves the view | proves `navigated = null`, not pinning |
| 6 | JVM | `showNextDay()` at the live edge is a no-op; no future date is reachable | — |
| 7 | JVM | N backward steps reach `clock - N` (unbounded) | — |
| 8 | JVM | `answer()` on a past day passes the **viewed** date to `answerInApp` | `coVerify` |
| 9 | JVM | Both expansion sets are empty after a navigation that changes `viewed` | — |
| 10 | JVM | A tick while navigated away neither clears them nor re-subscribes Room | `observeByDate` called once for that date |
| 11 | JVM | `isPastDay` is true off the live edge, false at it | — |
| 12 | Instr. | Navigate back, a force-resolved `MISSED` slot shows Missed + Change; Change → Yes → Done | new `TodayPastDayComposeTest` |
| 13 | Instr. | Add-habit affordance absent on a past day; present again after `Today` | same |
| 14 | Instr. | An empty past day shows `today_empty_past` and no button | same |
| 15 | Instr. | `answerInApp(habitId, pastDate, slotId, COMPLETED, occurrenceId = null)` upserts on the **past** date and does not resurrect the `RESOLVED` occurrence | `EntryWriteParityTest` extension — the "prove `EntryWriter` unchanged" regression |
| 16 | JVM | A past occurrence absent from `observeUnresolved()` builds a slot with `occurrenceId == null` | `TodayViewModelTest` — the "prove `TodayModel.toTodaySlot` unchanged" regression |

Test 15 is the load-bearing proof of the proposal's central claim: `toTodaySlot` only reads
`unresolvedOccurrences`, so a force-resolved past slot arrives at `answerInApp` with a **null**
`occurrenceId` and falls to `writeEntry(habitId, date, …)`, crediting the viewed date.

`TodayViewModelTest`: extend `FakeCurrentDateSource` (`:82-104`) — do **not** redesign it; its
`emissions`/`current` split is already the exact shape these tests need. Extend
`buildViewModel()`'s `entriesByDate` map with each past date a test navigates to; `entryDaoStub`
(`:111-123`) already fails legibly on an unregistered date.

`TodayPastDayComposeTest` goes through `todayViewModel()` (`TodayViewModelTestFactory.kt`), which
wires the real `SelfReschedulingCurrentDateSource`. Unlike rollover, navigation is user-driven, so
no wall clock has to move — an instrumented navigation test proves something a rollover one could
not. Reuse `TodayScreenWaits.kt`; introduce no per-class `waitUntil`.

Every new string needs an entry in **both** `values/strings.xml` and `values-es/strings.xml` or
`StringResourceParityTest` fails: `today_previous_day`, `today_next_day`, `today_back_to_today`,
`today_empty_past`.

Commands: `./gradlew check`, `./gradlew :app:compileDebugAndroidTestKotlin`, and
`./gradlew :app:emulatorMatrixGroupDebugAndroidTest` (device-free, api31 + api37), each with
`JAVA_HOME` pointed at Android Studio's bundled JBR.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. Decision 2 explicitly declines the route change, so no navigation
argument surface is added.

## Migration / Rollout

No migration required. Code-only: no Room schema change, no persisted format change, no alarm or
WorkManager change. Entries corrected through the new surface are ordinary `Entry` upserts on the
`UNIQUE(habitId, date, slotId)` conflict target and survive a revert of this change.

## Open Questions

- [ ] `TodayAddHabitComposeTest` measures geometry and `TodayAdaptiveComposeTest` checks clipping;
      the date bar adds vertical extent above the banners. Neither is expected to break, but both
      must be re-run before the contingent line estimate above is dropped to zero.
- [ ] `CoreFlowE2ETest` is still unaudited for date-label and add-habit-position assumptions. This is
      a `sdd-tasks` work item, not a design decision.
