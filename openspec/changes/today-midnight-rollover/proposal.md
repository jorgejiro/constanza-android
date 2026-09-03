# Proposal: Today Follows the Clock Across Midnight

## Intent

`TodayViewModel.kt:79` reads `timeProvider.today()` in a property initialiser — once, at Hilt
construction. A ViewModel outlives backgrounding, so Today stays bound to the date it was born with.
Two failures, different urgency:

- **Screen wrong.** `:108` `observeByDate(today)` and `:113` `TodaySnapshot(..., today)` (driving
  `rollupDay` and the unresolved-occurrence date filter). Yesterday's slots render as today's, with
  no visible cue.
- **Database wrong.** `:149` `answer()` → `entryWriter.answerInApp(habitId, today, ...)`. An answer
  given after midnight persists against yesterday. Corruption of the core table by ordinary use —
  this is why it cannot wait.

Backlog: `today-never-rolls-over-at-midnight`.

## Scope

### In Scope
- Date becomes an observed source; `flatMapLatest` re-subscribes the entry flow. `uiState`'s
  `combine` is already at kotlinx.coroutines' five-source typed ceiling, so a date cannot be a sixth.
- All three `today` consumers corrected together, write path included.
- `millisUntilNextMidnight()` reachable from `tracking` (today `internal` to `scheduling`).
- Audit `TodayViewModelTest`'s fixed-date stubs (`today()` **and** `observeByDate(TODAY)`).

### Out of Scope
- `habit-entry-tracking`'s **`Midnight Transition`**: the `:domain` `UNKNOWN` → `MISSED` sweep in
  `OccurrenceResolver.sweepMidnight`. Unaffected by this defect; MUST NOT be reworded.
- Repairing entries already written against the wrong date (open item
  `no-in-app-route-to-edit-a-past-day`).
- Poll, retry, longer wait, or resume-only refresh — ruled out by the backlog entry: resume never
  fires for an app already foregrounded at midnight.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `habit-entry-tracking`: `Day-Level Rollup and Per-Slot Display` — Today MUST track the current
  local date while displayed, and MUST attribute an in-app answer to the date then in effect.

## Approach

Exploration approach 3: a self-rescheduling in-process timer anchored to the next local midnight —
the in-app mirror of `MidnightSweepWorker` — feeding a date flow that `flatMapLatest` keys the entry
query on, plus a `today()` re-read on Today's existing `ON_RESUME` hook as defence in depth. Every
clock read goes through `TimeProvider`; detekt's `ForbiddenMethodCall` bans the alternatives.

### Design-phase decision: timezone travel while foregrounded

Deliberately unsettled here. `delay()` counts monotonic time, so a timer anchored in one zone never
fires at the new zone's midnight, even though the phone updates its own clock. `ON_RESUME` corrects
it once the user leaves and returns, so the residual gap is *continuous foregrounding across a zone
change* — narrower than the raw scenario, but likelier than a hand-edited clock.

| Option | Cost | Result |
|---|---|---|
| **A** — timer + `ON_RESUME`, gap named | none beyond the above | foregrounded zone travel stays wrong until the screen is re-entered |
| **B** — also bridge `TimeChangeReceiver`'s `ACTION_DATE_CHANGED` / `ACTION_TIMEZONE_CHANGED` into a Hilt-singleton signal | the first SharedFlow/event bus in `:app`, and the first receiver test in the repository (protected broadcasts, unsendable via adb) | covered |

**Recommended: A.** It closes the corruption vector for every scenario the backlog names, is fully
verifiable in JVM virtual time with no new instrumented surface, and fits one PR. B stacks the
repository's least-verified construct onto an already load-bearing fix, and stays cheap later — the
receiver exists, so bridging it is additive. Design MUST decide explicitly; choosing A means
recording the gap as a carried-forward item, not leaving it silent.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `tracking/TodayViewModel.kt` | Modified | all three `today` uses; flow restructured around a date source |
| `tracking/TodayModel.kt` | Modified | `TodaySnapshot.today` consumer |
| `tracking/TodayScreen.kt` | Modified | `ON_RESUME` date re-read beside the existing permission re-checks |
| `core/time/TimeProvider.kt` | Modified | home for the public midnight-delay helper |
| `scheduling/WorkScheduler.kt` | Modified | `millisUntilNextMidnight()` leaves `internal` |
| `app/src/test/.../TodayViewModelTest.kt` | Modified | fixed-date stubs become dynamic |
| `openspec/specs/habit-entry-tracking/spec.md` | Modified | delta near Day-Level Rollup only |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Widening `millisUntilNextMidnight()` disturbs `scheduling` | Med | move visibility only; `MidnightAnchorTest` already pins the DST cases |
| Fixed-date stubs mask a broken date source | Med | audit `buildViewModel()` whole; add a virtual-time rollover test |
| Date source re-subscribes per emission and thrashes Room | Low | distinct-until-changed; assert one re-subscription per rollover |
| Foregrounded timezone travel | Med | named accepted gap under A, closed under B |
| 800-line review budget | Low | forecast comfortably under; no schema, module, or new UI |

## Rollback Plan

Code-only: no Room schema change, no migration, no persisted-format change, and no change to alarm
or WorkManager scheduling — the helper moves visibility, not behaviour, and `MidnightSweepWorker`'s
trigger chain is untouched. Reverting the single PR restores prior behaviour exactly. Entries
written against the wrong date beforehand are neither repaired by the fix nor re-broken by a revert.

## Dependencies

None external. `stack:` is `ratified` with `still_unpinned` empty, so no existing stack element is
treated as unratified here. The two elements this change introduces — a public midnight-delay helper
on `TimeProvider`, and (only under B) an in-process date-change signal — are unratified by
construction and are the design phase's to settle.

## Success Criteria

- [ ] Crossing local midnight with Today open re-queries and re-renders against the new date, with
      no user interaction.
- [ ] An answer given after midnight is written against the new date.
- [ ] `rollupDay` and the unresolved-occurrence filter both follow the new date.
- [ ] `Midnight Transition` is byte-identical after the change.
- [ ] `:app:testDebugUnitTest`, `:app:detekt`, `:app:detektMain`, `:app:lintDebug` green, including a
      virtual-time rollover test.
- [ ] `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` green on api31 and api37. Device-free.
- [ ] Under option A, the foregrounded-timezone-travel gap is recorded in `openspec/config.yaml`.
