# Exploration: today-midnight-rollover

> Written by the orchestrator from the `sdd-explore` phase result (Engram
> `sdd/today-midnight-rollover/explore`, observation 128). The phase agent had no
> write tool available, so the filesystem half of the `hybrid` store is recorded here.
> Every structural claim below was re-verified against the working tree before the
> proposal phase was launched; the verified line numbers are noted inline.

## Current state

`TodayViewModel.kt:79` — `private val today = timeProvider.today()` — is a property
initialiser, evaluated once when Hilt constructs the ViewModel. `today` feeds three
sites, not only the DAO call:

1. `TodayViewModel.kt:108` — `entryDao.observeByDate(today.toString())`, the 2nd of five
   sources in `uiState`'s `combine`.
2. `TodayViewModel.kt:113` — `TodaySnapshot(entriesToday, unresolved, today)`, consumed by
   `buildTodayHabitRow` (`TodayModel.kt:73`), which uses `snapshot.today` both for
   `:domain`'s `rollupDay(schedule, snapshot.today, ...)` (`:81`) and to filter unresolved
   occurrences to today's date (`it.scheduledDate == snapshot.today.toString()`, `:93-96`
   — a load-bearing bound with its own regression test).
3. `TodayViewModel.kt:149` — `answer()`'s write path:
   `entryWriter.answerInApp(habitId, today, slot.slotId, status, slot.occurrenceId)`.

Site 3 is the corruption vector: past midnight, an answer is written against yesterday's
date. Sites 1 and 2 make the screen wrong; site 3 makes the database wrong.

`TodayScreen.kt` and `TodayModel.kt` carry no independent date assumption — they are pure
consumers of whatever date the ViewModel captured.

### The `combine` ceiling is real

`uiState` (`TodayViewModel.kt:106-127`) is
`combine(habits, entries, unresolved, expansionState, permissionBanners)` — exactly five
typed sources, which is kotlinx.coroutines' largest typed `combine` overload. A sixth
source falls through to the vararg `Array<Any?>` overload and loses type safety.

The file already solves this twice, deliberately: `permissionBanners` (`:96-99`) and
`expansionState` (`:101-104`) each exist only to bundle two flows into one so the outer
combine does not need a sixth slot, and both carry load-bearing KDoc saying so
(`:24-27`, `:33-38`). A date source must therefore replace or enrich an existing source,
or restructure the flow — it cannot simply be appended.

### `TimeProvider`

`core/time/TimeProvider.kt` exposes `now()`, `today()` and `zone()` only — no observable
member. `SystemTimeProvider` is the sole production implementation.

detekt's `ForbiddenMethodCall` (`config/detekt/detekt.yml:26-36`) bans `Instant.now`,
`LocalDate.now`, `LocalDateTime.now` and `LocalTime.now` everywhere else, type-resolved
through `detektMain`. The ban is enforced, so any trigger this change adds must read the
clock through `TimeProvider`.

### A DST-proven midnight calculation already exists

`internal fun TimeProvider.millisUntilNextMidnight(): Long` lives at
`scheduling/WorkScheduler.kt:23`, and is unit-tested with no Android dependency in
`MidnightAnchorTest.kt` — spring-forward 23-hour day, fall-back 25-hour day, the
exact-midnight edge, and the negative clamp. It is `internal` to `scheduling` and so is
unreachable from `tracking` as it stands.

`MidnightSweepWorker` self-reschedules to the next local midnight through
`WorkScheduler.scheduleNextMidnightSweep()` (`WorkScheduler.kt:83`), which is the direct
in-repository precedent for an in-process timer anchored the same way.

### Existing broadcast infrastructure

`scheduling/RescheduleReceivers.kt` registers `TimeChangeReceiver` (manifest-declared,
`exported=true`) for `ACTION_TIMEZONE_CHANGED`, `ACTION_DATE_CHANGED` and
`ACTION_TIME_CHANGED` (`:84-85`). It calls `occurrencePlanner.replanAll()` and, on
`ACTION_DATE_CHANGED`, additionally enqueues an immediate `MidnightSweepWorker` (`:71`).
`design.md §9.2` documents these as two of three deliberately redundant midnight-sweep
triggers.

**None of the three has any path into a live ViewModel.** A search for `SharedFlow` or
event-bus patterns across `:app` returned zero matches. Reusing this broadcast means
reusing the OS-signal half only and pairing it with genuinely new in-process plumbing.

### Testing

`FakeTimeProvider` (reassignable `var instant`) is `androidTest`-only. JVM tests
(`TodayViewModelTest`, `MidnightAnchorTest`) use `mockk<TimeProvider>`.
`TodayViewModelTest.buildViewModel()` stubs `today()` to one fixed value and
`observeByDate(TODAY.toString())` for that exact literal — both break under an
observed-date design and need a full audit, not just the `observeByDate` line.

The one `flatMapLatest` precedent in the app is `ProgressViewModel.kt:45`
(`habitId.filterNotNull().flatMapLatest { ... }`), directly analogous to a
`LocalDate`-keyed re-subscription.

No `*ReceiverTest*` exists anywhere in the repository — the five broadcast receivers are
already the least-verified part of the app, and their protected actions cannot be sent
via adb. A timer design is fully virtual-time-testable at the JVM level and adds no new
instrumented surface; a broadcast design adds one that has no testing precedent here.

### Spec boundary

`habit-entry-tracking`'s `Midnight Transition` (`spec.md:43-45`) is pure `:domain`
`Entry.status` semantics — `UNKNOWN` becoming `MISSED` — enforced by
`OccurrenceResolver.sweepMidnight`. It is completely unaffected by this defect: a
perfectly correct domain sweep does nothing for a screen whose Room query never asks
about today's row in the first place.

The spec delta therefore belongs near `Day-Level Rollup and Per-Slot Display`, not near
`Midnight Transition`, and must not reword the latter.

## Affected areas

| Path | Why |
|---|---|
| `tracking/TodayViewModel.kt` | defect site; all three uses of `today` |
| `tracking/TodayModel.kt` | `TodaySnapshot.today` consumer |
| `core/time/TimeProvider.kt` | candidate home for a public midnight-delay helper |
| `scheduling/WorkScheduler.kt` | `millisUntilNextMidnight()` is `internal` here |
| `scheduling/RescheduleReceivers.kt` | only if a broadcast bridge is added |
| `app/src/test/.../TodayViewModelTest.kt` | fixed-date stubs must become dynamic |
| `openspec/specs/habit-entry-tracking/spec.md` | delta near Day-Level Rollup |

## Approaches

### 1. Self-rescheduling timer through `TimeProvider`

Mirror `MidnightSweepWorker`'s pattern in process.

- **For:** no new signal type; reuses DST-proven delay arithmetic; fully JVM-testable
  with coroutine virtual time.
- **Against:** `millisUntilNextMidnight()` must move out of `internal`; a pure timer does
  not react to a manual clock change or timezone travel while foregrounded, because
  `delay()` counts monotonic elapsed time, not wall-clock time.
- **Effort:** low-medium.

### 2. Bridge `ACTION_DATE_CHANGED` / `ACTION_TIMEZONE_CHANGED` into an in-process signal

- **For:** covers natural rollover, manual clock change and zone travel in one mechanism;
  matches `design.md §9.2`'s redundant-trigger philosophy.
- **Against:** genuinely new plumbing with zero SharedFlow/event-bus precedent in `:app`;
  receiver instances are per-broadcast, so the signal must be Hilt-singleton-scoped; no
  receiver-test precedent exists anywhere in the repository.
- **Effort:** medium.

### 3. Both, following the repository's own redundant-trigger precedent

Timer as primary, plus re-reading `timeProvider.today()` on the existing `ON_RESUME` hook
(`TodayScreen.kt:58-67`) as defence in depth — never as the sole mechanism, which the
backlog entry rules out because resume does not fire for an app already foregrounded at
midnight.

- **For:** closes both scenarios the backlog names with the least new plumbing; reuses two
  mechanisms already proven in this codebase; follows the domain side's own precedent.
- **Against:** leaves the foregrounded manual-clock-change case open.
- **Effort:** low-medium.

## Recommendation

Approach 3, with one caveat the orchestrator adds to the phase result: the gap approach 3
leaves open is not only a *manual* clock change. Because `delay()` is monotonic, a timer
anchored in one zone does not fire at the new zone's midnight after **timezone travel**
while the app is foregrounded — a materially more likely scenario on a phone than someone
hand-editing their clock, and one the existing `TimeChangeReceiver` already receives a
signal for. Whether to bridge that signal is a real design decision, and the design phase
must decide it explicitly rather than inherit approach 3's framing.

## Risks

- Relocating `millisUntilNextMidnight()` out of `internal` touches code three other
  classes and tests depend on.
- `TodayViewModelTest.buildViewModel()`'s single-fixed-date stubs need a full audit.
- A ViewModel-scoped timer offers no guarantee beyond the ViewModel's own lifetime, unlike
  `MidnightSweepWorker`'s WorkManager-backed trigger.
- A broadcast bridge must stay decoupled from the domain sweep's three existing triggers.

## Key learnings

1. `TodayViewModel`'s `uiState` combine is already at kotlinx.coroutines' five-flow typed
   `combine` ceiling, so a date source must replace or enrich an existing source rather
   than add a sixth.
2. A DST-proven, JVM-unit-tested midnight-delay calculation
   (`TimeProvider.millisUntilNextMidnight()`) already exists but is `internal` to the
   `scheduling` package and unreachable from `tracking`.
3. The existing `TimeChangeReceiver` / `ACTION_DATE_CHANGED` broadcast has no path into any
   live ViewModel today, and this repository has zero in-process SharedFlow or event-bus
   precedent to bridge one.
4. Coroutine `delay()` is driven by elapsed monotonic time, not wall-clock time, so a pure
   midnight timer cannot react to a manual system clock change or to timezone travel.
5. The domain-side `Midnight Transition` spec requirement is unaffected by this defect and
   must not be touched by its fix; the correct spec delta belongs near `Day-Level Rollup
   and Per-Slot Display` instead.
