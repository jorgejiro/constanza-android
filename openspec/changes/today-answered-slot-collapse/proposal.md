# Proposal: Collapse an answered Today slot

## Intent

Today renders Yes/No/Skip on every slot at all times, answered or not, so a finished day and an
untouched day look nearly identical and the screen never reports progress at a glance. This is the
third and last defect of `today-row-answering-is-cramped-and-always-on`; PR #57 fixed the layout
and raw-enum defects and left this one because it modifies published requirements.

## Scope

### In Scope

- `SlotRow`: a pending slot keeps Yes/No/Skip; an answered slot shows its state plus one Change control.
- Change reveals the existing `AnswerButtons` inline for that slot only; answering re-collapses it.
- Reopen state hoisted into `TodayUiState`, keyed per slot.
- TalkBack: Change is labelled with its own slot's time and status.

### Out of Scope

- `HabitRollupRow`'s multi-slot header. It renders no answer buttons, and its `dayStatusLabel`
  already separates "All done" from "Pending" — no defect to fix.
- Swipe, row-tap and tap-to-cycle gestures (rejected below).
- Persisting reopen state across process death; it is meaningless once the day rolls over, the same
  reasoning `expandedHabitIds` already carries.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `habit-entry-tracking`: **Slot Independence** — a slot's control set MAY differ by that slot's own
  state, and reopening or answering one slot MUST NOT change another's presentation.
  **Day-Level Rollup and Per-Slot Display** — an answered slot MUST read as answered and MUST keep a
  route to change its answer.

Constrain the design but are NOT modified: **Provisional-Missed Correction** (its mandated in-app
edit route is preserved, only reshaped) and `ui-adaptive-layout`.

## Approach

A labelled button, not a gesture and not a dialog.

| Decision | Rationale |
|---|---|
| Inline reveal via Change | Reuses `AnswerButtons` verbatim — no third idiom |
| Not an `AlertDialog` | Reserved here for irreversible loss (discard, delete); re-answering is reversible |
| Not an overflow menu | `HabitListScreen:176` reserves that menu for the irreversible action alone |
| Not a row tap | Collides with the rollup row's existing expand/collapse tap |
| Behaviour inside `SlotRow` | Both `HabitRollupRow` shapes inherit it with no branch |
| Text, never colour or icon | State must survive TalkBack; `Accent` is chrome-only, and no new M3 role is introduced |

`TodaySlot.slotId` is nullable, so reopen state keys on `(habitId, slotId)` — not `Set<Long>`.

Nothing in the stack is treated as unratified: no new dependency, no new colour role, no `.dp` literal.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `app/src/main/kotlin/com/jjrapps/constanza/tracking/TodayScreen.kt` | Modified | `SlotRow` branch, Change control, threading |
| `app/src/main/kotlin/com/jjrapps/constanza/tracking/TodayViewModel.kt` | Modified | Reopen set and toggle, mirroring `expandedHabitIds` |
| `app/src/main/res/values/strings.xml` | Modified | Change label plus its accessibility description |
| `app/src/androidTest/kotlin/com/jjrapps/constanza/tracking/` | New/Modified | One new collapse test; four existing Today tests adjusted |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Row height shift breaks `TodayAdaptiveComposeTest` | Med | Both its slots are pending, so its button count holds; re-run the matrix |
| One extra tap to correct an answer | Low | Correction is the rare path; answering is the common one |
| Null `slotId` collides across habits | Low | The key carries `habitId` |

## Rollback Plan

Revert the branch. No schema, alarm, or persisted-data change: reopen state is in-memory only, so
nothing survives that would need migrating back.

## Dependencies

None.

## Success Criteria

- [ ] An answered slot shows no Yes/No/Skip and exactly one Change control.
- [ ] Change reveals the actions for that slot only; a sibling slot is untouched.
- [ ] Works in the single-slot row and the expanded multi-slot row alike.
- [ ] TalkBack reaches Change with no gesture and announces slot state as text.
- [ ] `./gradlew check` and `:app:emulatorMatrixGroupDebugAndroidTest` green on both legs.

## Size Forecast

Measured against the files above, code-only and all-in: **350–450 changed lines** against the 800
budget. Production ~130, inflated by this repository's KDoc-per-decision convention; tests ~250–320,
dominated by one new Compose class. That plausibly exceeds the 400-line default, which is what the
cached 800 budget and `single-pr` strategy exist to cover.
