# Design: Collapse an answered Today slot

## Technical Approach

One status branch inside `SlotRow`. A slot whose `status` is `UNKNOWN` keeps `AnswerButtons`
verbatim; any other status renders the same status `Text` plus one `TextButton` labelled Change,
unless that slot is in a reopen set, in which case it renders `AnswerButtons` again. The reopen set
lives in `TodayViewModel` beside `expandedHabitIds` and is cleared for a slot when that slot is
answered. No new composable idiom, no new M3 role, no new colour, no `.dp` literal, no schema.

## Architecture Decisions

### Decision: reopen state is a `Set<TodaySlotKey>` in the ViewModel

**Choice**: `data class TodaySlotKey(val habitId: Long, val slotId: Long?)`, held in a
`MutableStateFlow<Set<TodaySlotKey>>` in `TodayViewModel`, projected into `TodayUiState` exactly as
`expandedHabitIds` is.

**Alternatives considered**: `rememberSaveable` inside `SlotRow`; `Set<Long>` of slot ids;
`(habitId, slotIndex)`.

**Rationale**: `TodaySlot.slotId` is `Long?` — null for a habit with no enabled reminder slot — so
`Set<Long>` cannot express the key at all, and `(habitId, null)` is unambiguous because such a habit
has exactly one slot (`TodayModel.kt:85`). An index key is rejected because disabling a slot
reshuffles positions and would silently move a reopen flag onto a different slot; `slotId` is stable.

`rememberSaveable` is rejected on two grounds. First, `TodayScreen` is strictly presentational
(state in, callbacks out) and hand-built `TodayUiState` values are how it is previewed and tested;
local mutable state inside a row breaks that contract. Second — and decisively — **`rememberSaveable`
survives process death and the ViewModel does not**. `TodayViewModel` captures `today` once at
construction (`TodayViewModel.kt:68`) and never rolls the date over in place; a new day means a new
ViewModel. Reopen keys held in the ViewModel therefore die with the exact object that owns the date
they belong to. Saved-instance-state keys would be restored against a different day's rows. Rotation
is unaffected either way: the ViewModel outlives configuration change, so a reopened slot stays
reopened across rotation, which is the behaviour a user expects mid-correction.

*Note on the brief*: the screen does not "handle a midnight rollover" — it has no in-place rollover
at all. That absence is what makes the ViewModel the correct owner, so the conclusion stands.

### Decision: the answered row names its answer as text, bypassing the snooze sentence

**Choice**: the answered branch renders `"<time> — <Done|Missed|Skipped>"` from the existing
`slotStatusLabel`. `slotStatusText`'s snooze branch is reached only by the pending branch.

**Alternatives considered**: reuse `slotStatusText` unchanged for both branches.

**Rationale**: `slotStatusText` prefers `today_slot_pending_snoozed_until` whenever
`snoozedUntilEpochMs != null` (`TodayScreen.kt:251`). An answered slot whose occurrence has not yet
been resolved would then read "Pending, snoozed until 09:00" while carrying a `COMPLETED` entry —
literally failing the spec's "text naming its specific answer". Split the shared time prefix into a
helper and let each branch pick its own status string.

### Decision: Change is a `TextButton` with a per-slot `contentDescription`

**Choice**: visible label `today_slot_change` = "Change", identical on every row. Accessible label
`today_slot_change_a11y` = `"Change answer for %1$s, %2$s"` filled with the habit name and the
answered status text, set as `contentDescription` via `Modifier.semantics`.

**Alternatives considered**: distinct visible labels per slot; an `IconButton`; putting the
discriminator in the visible text.

**Rationale**: a `TextButton` is reachable by an ordinary tap and by TalkBack's default activate
action — no gesture, satisfying the requirement directly. A per-slot *visible* label would put
"Change 08:00 Done" on a 360dp row that already wraps. Putting the discriminator in
`contentDescription` also keeps the four existing text-based tests intact: Compose's `onNodeWithText`
matches only `Text`/`EditableText` semantics, never `contentDescription`, so an a11y label containing
"Done" cannot collide with `onNodeWithText(today_slot_completed)`.

Colour: `TextButton` inherits `MaterialTheme.colorScheme.primary` for its label, exactly like
Yes/No/Skip. **No M3 role is introduced**, so `Theme.kt`'s role audit is untouched, and
`ConstanzaColors.Accent` is not referenced. State is conveyed by the status text alone.

### Decision: answering a reopened slot re-collapses it

**Choice**: `TodayViewModel.answer` removes the slot's key from the reopen set synchronously,
*before* launching the write coroutine.

**Alternatives considered**: leave the slot expanded after re-answering.

**Rationale**: the answered presentation is a function of the slot's own status, and after
re-answering the slot is answered again. Leaving it open would make two identically-statused slots
render differently for a reason the user cannot see — the opposite of what Slot Independence frames
as status-driven presentation. Cost of the rejected option: a stale open row the user must close by
hand, and a screen where "answered" no longer predicts "collapsed". Removing the key before the
coroutine (rather than reacting to the Room round-trip) keeps the buttons from lingering for a frame.
If the write fails the slot collapses back to its previous status with its Change control intact, so
the state is never unrecoverable.

### Decision: one `SlotActions` holder instead of two new parameters per row

**Choice**: `private data class SlotActions(reopenedKeys, onRequestChange, onAnswer)`, built once in
`TodayContent` and threaded down.

**Alternatives considered**: adding `reopened` + `onRequestChange` as separate parameters.

**Rationale**: separate parameters push both `HabitRollupRow` and `SlotRow` to 7 parameters. detekt's
`LongParameterList` is unconfigured in `config/detekt/detekt.yml`, so its default function threshold
(6) applies, and this file's existing suppression is scoped to `TodayScreen`'s declaration only. The
holder keeps both rows at their current 5 parameters and adds no suppression. `SlotRow` also swaps
`habitId: Long` for the `TodayHabitRow` it needs anyway for the accessible label. The holder captures
lambdas, so build it with `remember(...)` keyed on the incoming callbacks and the reopen set; row
recomposition on a set change is desired, and this screen already recomposes wholesale on state.

## Data Flow

    ChangeButton ──onRequestChange(key)──→ TodayViewModel.reopenedSlots (+key)
                                                      │
    AnswerButtons ──onAnswer(...)──→ answer() ─────────┤ (−key, synchronous)
                                          │            │
                                     EntryWriter       ▼
                                          │      TodayUiState.reopenedSlots
                                          ▼            │
                                   Room ──→ uiState ───┴──→ SlotRow branch

## File Changes

| File | Action | Description |
|---|---|---|
| `tracking/TodayModel.kt` | Modify | Add `TodaySlotKey` + `TodaySlot.keyIn(habitId)` |
| `tracking/TodayViewModel.kt` | Modify | `reopenedSlots` flow, `requestChange`, clear-on-answer, `TodayUiState.reopenedSlots`, sixth `combine` source |
| `tracking/TodayScreen.kt` | Modify | `SlotActions` holder, `SlotRow` status branch, `ChangeButton`, split status-text helper |
| `res/values/strings.xml` | Modify | `today_slot_change`, `today_slot_change_a11y` |
| `androidTest/.../TodayAnsweredSlotComposeTest.kt` | Create | The new behaviour's tests |

`combine` takes five typed flows at most, so the sixth source follows the existing
`PermissionBanners` pattern: bundle `expandedHabitIds` and `reopenedSlots` into one private
value rather than dropping to the vararg overload, which erases every source to `Any?`.

## Interfaces / Contracts

```kotlin
data class TodaySlotKey(val habitId: Long, val slotId: Long?)
fun TodaySlot.keyIn(habitId: Long) = TodaySlotKey(habitId, slotId)

// TodayUiState
val reopenedSlots: Set<TodaySlotKey> = emptySet()

// TodayViewModel
fun requestChange(key: TodaySlotKey)   // adds
fun answer(habitId: Long, slot: TodaySlot, status: InAppEntryStatus)  // removes, then writes
```

## Spec Conformance

| Scenario | Mechanism |
|---|---|
| Answering one slot leaves another untouched | Unchanged. Per-slot `Entry` write; covered by `TodayComposeTest` |
| Reopening one answered slot leaves a sibling collapsed | `reopenedSlots` is keyed per slot; the sibling's key is absent |
| Single-slot habit with null slot identifier | Key is `(habitId, null)`; branch reads `slot.status`, never `slotId` |
| Day rollup reports partial completion | Unchanged. `:domain` `rollupDay`; no code touched |
| Missed alongside completed reports partial | Unchanged. `:domain` `rollupDay`; no code touched |
| No completion at all reports missed | Unchanged. `:domain` `rollupDay`; no code touched |
| Today screen shows independent slot rows | Unchanged. `HabitRollupRow` expansion; out of scope |
| Answered slot names its answer, one route, no colour | Answered branch: `slotStatusLabel` text + one `TextButton`, no M3 role added |
| Change route gesture-free, names its own slot | `TextButton.onClick` + per-slot `contentDescription` |

Four rollup/independence scenarios are restated by the MODIFIED requirements but describe behaviour
this change does not touch; they need no new work, only no regression. No scenario is judged wrong.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit | Rollup precedence | Existing `:domain` tests; untouched |
| Instrumented | Answered slot collapse, sibling independence, a11y label | New `TodayAnsweredSlotComposeTest` |
| Static | detekt/ktlint parameter counts, no raw `.dp` | `./gradlew check` |

**No existing Today test needs updating.** Verified against each fixture rather than inherited:

- `TodayAdaptiveComposeTest` seeds two enabled slots and writes no `Entry` (lines 71–76), so both are
  `UNKNOWN` and both render Yes. `ANSWER_BUTTON_COUNT_PER_HABIT = 2` holds — confirmed, not assumed.
- `TodayComposeTest` taps Yes/Skip on pending slots and then asserts *status* text; both assertions
  survive collapse.
- `TodaySlotRowComposeTest` seeds one pending slot in all three tests; the answered-copy test asserts
  `today_slot_completed`, still rendered after collapse.
- `TodayAddHabitComposeTest` measures geometry against a pending slot's Yes button.

This contradicts the proposal's "four existing Today tests adjusted". The proposal was written before
the fixtures were checked; the fixtures win.

The new class seeds a 3-slot habit and answers each slot through the UI (Yes, then No, then Skip,
each at index 0 of the remaining nodes — deterministic because answered slots drop their buttons),
awaiting each status between taps. It uses `awaitOneRowWithSlots` / `awaitSlotStatus` from
`TodayScreenWaits.kt` and introduces no per-class `waitUntil`.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary.

## Migration / Rollout

No migration required. Reopen state is in-memory only.

## Open Questions

- [ ] Two distinct habits sharing a name, both with no reminder time and the same status, produce
      identical accessible labels. Uniqueness holds within a habit (the spec's scenario) and across
      habits by name+time; `habitId`, the only fully unique discriminator, is not speakable.
