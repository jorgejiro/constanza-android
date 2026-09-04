# Exploration: In-app past-day correction (Today gains date navigation)

Origin: carried-forward open item `no-in-app-route-to-edit-a-past-day` in `openspec/config.yaml`.

Artifact store is `hybrid`. The Engram half is `sdd/today-past-day-correction/explore`. This file is
the filesystem half, written by the orchestrator because the `sdd-explore` agent has no write tool.

## Premise (settled, not open)

**Today gains date navigation.** Today stops being pinned to the current date and can move back to
previous days, showing all habits for that date. Two alternatives were considered and rejected by
the maintainer: making `Progress(habitId)` the surface (rejected — Progress is per-habit, and the
painful case is a single night where several habits were missed, which would mean several visits),
and narrowing the spec requirement to the two routes that exist (rejected — the maintainer does want
to correct the past). This exploration investigates HOW, not WHETHER.

## The requirement

`Provisional-Missed Correction` (`openspec/specs/habit-entry-tracking/spec.md:62-86`) makes the
`MISSED -> COMPLETED` correction mandatory along three paths: force-resolution, **a manual in-app
edit of a past day**, and import — each crediting the originally scheduled date. It does not
constrain how far back editing may reach, and does not cap which transitions an edit may perform.
That is a floor, not a ceiling.

Two companion requirements are affected:

- `In-App Answer Date Attribution` (`spec.md:174-188`) mandates writing "the local date currently
  displayed at the moment of the answer". Its wording is built entirely around "midnight passed
  while showing today" and has no language for deliberate navigation away from today.
- `Day-Level Rollup and Per-Slot Display` (`spec.md:120-121`) obliges the screen to "track the
  current local date" across midnight and resume. An `sdd-spec` delta will need to scope that to the
  live-today view.

## The central tension: clock truth vs. viewed date

`TodayViewModel.observedDate` is a single `MutableStateFlow<LocalDate>` (`TodayViewModel.kt:89`) with
**two unconditional clock-driven writers**, both verified:

- `:155` — `currentDateSource.dates().collect { observedDate.value = it }`, the midnight timer.
- `:192` — `refreshDate()`'s `ON_RESUME` correction, `observedDate.value = currentDateSource.today()`.

Add a third, navigation-driven writer and change nothing else, and a midnight rollover or an app
resume while reviewing a past day silently snaps the view back to today. No existing test would
catch it.

`uiState`'s `flatMapLatest` over that date (`:123-151`) is already date-agnostic — the plumbing needs
no change. Only the writers need to stop clobbering a deliberate navigation, which means separating
clock truth from viewed date.

## What already works and must not be rewritten

- `TodayViewModel.answer()` (`:178-184`) writes `uiState.value.date`, never a fresh clock read.
- `EntryWriter.answerInApp` (`EntryWriter.kt:80-93`) branches on whether `occurrenceId` still
  resolves to a live occurrence. For an already force-resolved past MISSED slot it does not — 
  `TodayModel.kt:107-123`'s `toTodaySlot` only looks among `unresolvedOccurrences` — so it falls to
  `writeEntry(habitId, date, ...)` and credits exactly the viewed date. **Verified.** Cover it with
  regression tests; do not modify it.
- `OccurrenceResolver`'s `sweepMidnight`/`forceResolve` both write `MISSED` through a shared
  `writeMissed` and flip the occurrence to `RESOLVED`/`ABANDONED`, dropping it out of
  `observeUnresolved()`. That is precisely what makes the branch above fire correctly for a past day.
- `today-answered-slot-collapse` already renders a past day's resolved slots correctly with no new
  code: any slot carrying an `Entry` collapses to Done/Missed/Skipped plus a `ChangeButton`.

## Known defect to fix as part of this change

`expandedHabitIds` (`:90`) and `reopenedSlots` (`:95`) are keyed by habit id and slot key only, not
by date. They will leak UI state across a date navigation. An explicit reset policy is required.

## Navigation surface options (not decided — for `sdd-design`)

| Option | Effort | Trade-off |
|---|---|---|
| In-place navigation inside `TodayScreen`, no new route | Low–Medium | Smallest surface, matches the maintainer's framing most literally; viewed date does not survive process death |
| Carry the date on `ConstanzaRoute.Today` | Medium | Survives rotation and process death the way `HabitEditor`/`Progress` already do; couples to the route a concept better modelled as ViewModel state |
| A separate day-detail route | Medium–High | Effectively re-litigates the already-rejected separate-screen alternative |

`sdd-design` decides between the first two, on the basis of the desired behaviour when the process
dies while reviewing a past day.

## Test surface

- `TodayViewModelTest.kt`'s `FakeCurrentDateSource` (`:82-104`) already separates "timer-emitted"
  from "synchronous current" values — exactly the shape needed to test navigation against the clock.
  Extend it; do not redesign it.
- All androidTest Today files go through `TodayViewModelTestFactory.kt`, which wires the real
  `SelfReschedulingCurrentDateSource`. None navigate today, so they remain valid regression. A new
  instrumented test for the review-and-correct flow is needed.
- `CoreFlowE2ETest` was not audited for date-label assumptions in this pass.

## Risks

- The clock/navigation coexistence bug ships silently — no existing test would catch it.
- `expandedHabitIds`/`reopenedSlots` leak across a date change without an explicit fix.
- The write path permits unrestricted past editing once a UI exists. That is a product decision, not
  an implementation gap.
- `CoreFlowE2ETest` unaudited.

## Open product questions

Blocking `sdd-propose` for 1, 3 and 4, which materially change scope.

1. How far back may navigation go — unbounded, a fixed window, or bounded by habit creation?
2. Is a future date reachable at all, or is this past-only?
3. What does the add-habit affordance do while viewing a past day?
4. Is editing a past day unrestricted (any slot, any of COMPLETED/MISSED/SKIPPED), or scoped to
   provisional-missed occurrences only?
5. How does the screen communicate which date is being viewed, and how does the user return to today?
6. Does the pre-existing `N_TIMES_PER_WEEK` hardcoded-zero-progress limitation need revisiting for
   past-day rollups? Likely out of scope, but it should be a conscious call.

## Recommendation

Split clock truth from viewed date inside `TodayViewModel`; leave `CurrentDateSource` and its
semantics untouched; gate both existing `observedDate` writers so they only advance the viewed value
while the user is at the live edge. Prefer in-place navigation. Treat `EntryWriter` and `TodayModel`
as already correct, proven by new regression tests rather than modified.

## Settled product decisions (maintainer, 2026-09-04)

These answer open questions 1–4 above. They are settled; `sdd-propose` and `sdd-design` take them as
premises and must not re-open them.

1. **How far back**: unbounded. No window, no habit-creation bound. There is no arbitrary limit to
   draw on screen or to test, and a date with nothing scheduled simply renders empty.
2. **Future dates**: unreachable. Forward navigation stops at today. A future day could only ever be
   read-only, and the requirement is about correcting the past.
3. **Add-habit affordance on a past day**: hidden, replaced by past-day empty-state text. Creating a
   habit dated three weeks ago is meaningless — a schedule starts when the habit is created — and a
   past day is a review surface, not an intake surface. An empty past day says so in words rather
   than offering a button that would silently create a habit dated today.
4. **Editing scope**: unrestricted. Any slot, any of COMPLETED / MISSED / SKIPPED, and free to change
   again. The write path already permits exactly this; restricting it would mean building and testing
   machinery to forbid something nobody asked to forbid. The requirement's MISSED -> COMPLETED floor
   is satisfied by this superset.

Open question 5 (how the viewed date is communicated and how the user returns to today) is a UI
design decision and is delegated to `sdd-design`, not to the maintainer.

Open question 6 (`N_TIMES_PER_WEEK` hardcoded-zero progress) is deliberately OUT OF SCOPE for this
change. It is a pre-existing limitation, unrelated to date navigation, and folding it in would widen
the change for no gain. Recorded as a conscious call, not an oversight.
