# Proposal: Today Gains Date Navigation, So a Past Day Can Finally Be Corrected

## Intent

`Provisional-Missed Correction` names three paths that must be able to answer an already-resolved
occurrence. Two exist. The third — "a manual in-app edit of a past day" — has no surface, and the
spec asserts it does, so a reader checking compliance would tick it.

This is the **sixteenth instance of this repository's defining failure mode**: a capability written,
tested at the layer below, and never given a caller. Verified, not assumed:

- `EntryWriter.writeEntry` accepts an arbitrary `LocalDate`.
- `EntryWriter.answerInApp` (`EntryWriter.kt:80-93`) falls to that direct write when the occurrence
  no longer resolves — exactly the state a force-resolved past `MISSED` slot is in — and credits the
  viewed date.
- `TodayViewModel.answer()` (`:178-184`) already writes `uiState.value.date`, never a fresh clock read.

**The write path is complete. Nothing can call it with a past date.** Practically: a reminder missed
while the phone was off resolves to `MISSED` overnight, the notification is gone, and Today only ever
shows today.

Backlog: `no-in-app-route-to-edit-a-past-day`.

## Scope

### In Scope

- **Today gains date navigation.** Backward unbounded; forward stops at today; a date with nothing
  scheduled renders empty. (Settled by the maintainer — not reopened here.)
- **Clock truth separates from viewed date** inside `TodayViewModel`. See Approach.
- **Unrestricted editing of a past day**: any slot, any of COMPLETED / MISSED / SKIPPED, freely
  changeable. The `MISSED → COMPLETED` floor is satisfied as a subset.
- **Add-habit affordance hidden on a past day**, replaced by past-day empty-state text.
- **Fix `expandedHabitIds` (`:90`) and `reopenedSlots` (`:95`)**, which are keyed by habit id and slot
  key only — never by date — and therefore leak UI state across a navigation. An explicit reset
  policy is required; the mechanism (clear-on-navigate vs. key-by-date) is `sdd-design`'s.
- New copy in both `values/` and `values-es/` strings.

### Out of Scope

- **`N_TIMES_PER_WEEK`'s hardcoded zero period-progress** (`DayRollup.kt:33`, `OccurrenceResolver.kt:27`).
  Pre-existing, unrelated to navigation. Recorded as a conscious call, not an oversight.
- **`Progress(habitId)` as the surface**, and **narrowing the requirement** to the two routes that
  exist. Both considered and rejected by the maintainer.
- **Rewriting the write path.** `EntryWriter.answerInApp`, `TodayModel.toTodaySlot`,
  `OccurrenceResolver`, and `today-answered-slot-collapse`'s collapsed-row rendering are already
  correct for a past day. **Proving them beats changing them**: they are load-bearing for today's
  live path, a modification risks that path for no gain, and the proof is a regression test that
  outlives this change. `sdd-design` MUST list them as "prove unchanged", never skip them.
- Repairing entries written against the wrong date before `today-midnight-rollover` landed.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `habit-entry-tracking` — three requirements, each for a distinct reason:
  - **`Provisional-Missed Correction`** — its third path finally gets a caller. Confirm the reading
    explicitly (unbounded reach, unrestricted transitions) rather than by omission.
  - **`In-App Answer Date Attribution`** — worded entirely as "midnight passed while showing today".
    It has no language for a deliberately navigated date and MUST gain it.
  - **`Day-Level Rollup and Per-Slot Display`** — its "track the current local date" obligation MUST
    be scoped to the live-today view, not a navigated-away past view.

`sdd-spec` writes the deltas; this proposal only names them.

## Approach

**The invariant, stated as a position and not a design:** the clock MUST NOT move the viewed date
while the user has deliberately navigated away from the live edge, and the viewed date MUST resume
following the clock once the user returns to it. Every write MUST land on the date the tapped row was
built against.

This matters because `observedDate` (`TodayViewModel.kt:89`) has **two unconditional clock-driven
writers** — the midnight timer at `:155` and `refreshDate()`'s `ON_RESUME` correction at `:192`. Add
a third, navigation-driven writer and change nothing else, and a rollover or any resume gesture
(screen lock, a notification swipe) silently snaps a reviewed past day back to today. **No existing
test would catch it**, because no existing test navigates.

`uiState`'s `flatMapLatest` (`:123-151`) is already date-agnostic — the plumbing needs no change.
Only the writers do. The class layout, the reset trigger, the navigation affordance, and how the
viewed date is communicated are `sdd-design`'s to settle; the exploration recommends in-place
navigation with no route change.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `tracking/TodayViewModel.kt` | Modified | viewed date vs. clock truth; guard both existing writers; navigation entry points; reset policy for `expandedHabitIds`/`reopenedSlots` |
| `tracking/TodayScreen.kt` | Modified | navigation affordance, visible date, return-to-today, past-day empty state, add-habit hidden |
| `res/values*/strings.xml` | Modified | new copy, English and Spanish |
| `tracking/TodayModel.kt` | Unchanged | `TodaySnapshot.today` holds the viewed date; rename is optional cleanup |
| `tracking/EntryWriter.kt` | Unchanged | prove by regression, do not modify |
| `core/time/CurrentDateSource.kt` | Unchanged | clock-truth port stays exactly that |
| `app/src/test/.../TodayViewModelTest.kt` | Modified | `FakeCurrentDateSource` already separates timer from synchronous read — extend, do not redesign |
| `app/src/androidTest/.../tracking/` | New | past-day review-and-correct flow; audit `CoreFlowE2ETest` for date-label assumptions |
| `openspec/specs/habit-entry-tracking/spec.md` | Modified | three deltas above |
| `openspec/config.yaml` | Modified | close `no-in-app-route-to-edit-a-past-day` |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Clock/navigation coexistence bug ships silently | **High** | It passes every existing test. Dedicated unit tests are mandatory: midnight ticks while viewing a past day; resume while viewing a past day; return-to-today |
| `expandedHabitIds`/`reopenedSlots` leak across dates | High | Named in scope with an explicit reset policy; assert across a navigation |
| Unrestricted editing lets a past COMPLETED become MISSED | Med | Intended. Settled product decision; the UI, not the write path, would be the place to restrict |
| `CoreFlowE2ETest` unaudited for date-label assumptions | Med | Audit task in `sdd-tasks` |
| 800-line review budget | **Med** | See forecast |

## Size Forecast

Derived per area, authored additions plus deletions.

| Area | Est. lines |
|---|---|
| `TodayViewModel.kt` | 60–80 |
| `TodayScreen.kt` (346 lines today) | 90–130 |
| `strings.xml` ×2 | ~16 |
| `TodayViewModelTest.kt` | 120–160 |
| New instrumented past-day test | 100–140 |
| `EntryWriter` null-occurrence regression coverage | 40–60 |
| Spec delta + config close-out | 60–100 |
| **Total** | **490–690** |

**Recommendation: one PR, with a pre-declared slice point.** The forecast fits 800 at the midpoint
but has no headroom at the top. If `sdd-tasks` forecasts above ~700, split on the seam the
architecture already provides:

- **Slice A** — `TodayViewModel` viewed-date/clock-truth split, navigation entry points, UI-state
  reset, unit tests. No UI. Autonomous, JVM-verifiable, revertible alone.
- **Slice B** — the `TodayScreen` affordance, strings, instrumented test, spec delta, config close-out.

Do not split A along the ViewModel/test boundary: the coexistence bug is only provable with both.

## Rollback Plan

Code-only. **No Room schema change, no migration, no persisted-format change, and no change to alarm
or WorkManager scheduling** — `OccurrenceResolver`, `MidnightSweepWorker`, and `EntryWriter` are
untouched. Reverting the PR (or slice B, then A) restores prior behaviour exactly.

Entries corrected through the new surface before a revert **stay corrected**: they are ordinary
`Entry` upserts on the `UNIQUE(habitId, date, slotId)` conflict target, indistinguishable from any
other in-app answer, and every reader recomputes from current state. A revert removes the route, not
the data.

## Dependencies

None external. `stack:` is `ratified` with `still_unpinned` empty; **no existing stack element is
treated as unratified here.** The one element this change introduces — a viewed-date concept distinct
from the `CurrentDateSource` clock-truth port — is unratified by construction and is `sdd-design`'s
to settle.

## Success Criteria

- [ ] From Today, the user can reach an arbitrarily distant past day and return to today.
- [ ] Forward navigation stops at today; no future date is reachable.
- [ ] A past `MISSED` slot corrected to `COMPLETED` writes the originally-scheduled date, and the
      day rollup and streak recompute from it.
- [ ] Any past slot can be set to any of COMPLETED / MISSED / SKIPPED, and changed again.
- [ ] Local midnight passing while a past day is displayed does **not** move the view.
- [ ] `ON_RESUME` while a past day is displayed does **not** move the view.
- [ ] Both of the above still behave exactly as `today-midnight-rollover` requires while at the live edge.
- [ ] Expanded habits and reopened slots do not leak across a date navigation.
- [ ] The add-habit affordance is absent on a past day; an empty past day states so in words, in both
      locales.
- [ ] `EntryWriter.kt`, `TodayModel.kt`, and `OccurrenceResolver.kt` are byte-identical after the
      change, with new tests proving their behaviour.
- [ ] `./gradlew check` and `:app:compileDebugAndroidTestKotlin` green.
- [ ] `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` green on api31 and api37. Device-free.
- [ ] `no-in-app-route-to-edit-a-past-day` closed in `openspec/config.yaml`.
- [ ] `N_TIMES_PER_WEEK` zero-progress remains recorded as an open carried-forward item, untouched.
