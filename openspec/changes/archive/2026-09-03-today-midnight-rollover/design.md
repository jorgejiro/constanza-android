# Design: Today Follows the Clock Across Midnight

## Technical Approach

Option A, as settled: a midnight-anchored self-rescheduling emitter behind a new `core/time` port,
feeding one `MutableStateFlow<LocalDate>` inside `TodayViewModel` that `flatMapLatest` keys the
existing five-source `combine` on. `ON_RESUME` pushes into the same StateFlow. No broadcast bridge,
no event bus, no second signal. Satisfies the delta spec's ADDED requirement and the rollover /
resume / accepted-gap clauses of `Day-Level Rollup and Per-Slot Display`; `Midnight Transition` is
not read, referenced, or reworded.

## Architecture Decisions

### Decision 1 — the date flow lives in a new `core/time` port, not on `TimeProvider`

| Option | Trade-off | Verdict |
|---|---|---|
| `observeToday()` on `TimeProvider` | turns a stateless synchronous clock port into a stateful one; every `mockk<TimeProvider>` in the repo (incl. `MidnightAnchorTest`) must then satisfy a flow | rejected |
| new `CurrentDateSource` in `core/time` | one file, one `@Binds` in the existing `TimeModule`; fake is a `MutableStateFlow` | **chosen** |
| private flow in the ViewModel | timer arithmetic un-fake-able; JVM tests must drive real delay maths to test the screen | rejected |

`CurrentDateSource` also exposes `zone()`, so `TodayViewModel` **replaces** its `TimeProvider`
parameter rather than gaining a ninth: the constructor stays at eight and the existing
`@Suppress("LongParameterList")` is neither widened nor joined by a new one. This is the bundling
answer `HabitDaos`/`SchedulingDaos` established, applied to a port instead of DAOs.

### Decision 2 — `flatMapLatest` over the date, combine unchanged inside

The five-source `combine` keeps exactly five typed sources; the date is a **key outside** it, never
a sixth input. Precedent: `ProgressViewModel.kt:45` (same file-level `@OptIn(ExperimentalCoroutinesApi)`).

```kotlin
private val observedDate = MutableStateFlow(currentDate.current())   // seeded, never null

val uiState: StateFlow<TodayUiState> = observedDate.flatMapLatest { date ->
    combine(habits, entryDao.observeByDate(date.toString()), unresolved, expansionState, permissionBanners) {
        habits, entriesToday, unresolved, expansion, banners ->
        val snapshot = TodaySnapshot(entriesToday, unresolved, date)   // rollupDay + the :93-96 filter
        TodayUiState(rows = …, date = date, …)
    }
}.stateIn(viewModelScope, SharingStarted.Eagerly, TodayUiState(zone = …, date = currentDate.current()))
```

`observedDate` is fed by the timer (collected in `init`) and by a new `refreshDate()` called from
`TodayRoute`'s existing `ON_RESUME` block — exactly the shape `canScheduleExactAlarms` /
`refreshExactAlarmPermission` already uses. StateFlow conflation gives distinct-until-changed free,
so one rollover means one re-subscription.

### Decision 3 — all three consumers

| Site | How it gets the live date |
|---|---|
| `:108` `observeByDate` | `date.toString()` inside the `flatMapLatest` lambda; a new date re-subscribes Room |
| `:113` `TodaySnapshot` | same lambda parameter → `rollupDay` and `TodayModel.kt:93-96`'s `scheduledDate ==` bound follow automatically; **`TodayModel.kt` is unchanged** and its regression test stays valid |
| `:149` `answer()` | reads `uiState.value.date` — the date carried by the emission that built the row the user tapped |

`answer()` deliberately does **not** read `observedDate.value` or re-read the clock: both can have
advanced between render and tap, attributing the answer to a date whose rows are not on screen.
`uiState.value.date` makes written date == displayed date by construction. Rows exist only after a
real emission, so the seeded initial value is unreachable from a tap.

### Decision 4 — move `millisUntilNextMidnight()` to `core/time/TimeProvider.kt`, public

Widening it in place would make `core/time` depend on `scheduling` (wrong direction); duplicating
would leave two DST-sensitive copies with one test. Measured cost of the move (grep, whole repo):
**one** production call site, `WorkScheduler.kt:83`, plus `MidnightAnchorTest` — two import lines
and the KDoc block carried verbatim. Zero behaviour change; `MidnightAnchorTest`'s assertions are
untouched.

### Decision 5 — the timer re-anchors, it never counts days

Invariant: **every emission is `timeProvider.today()` read at emission time, and every delay is
`millisUntilNextMidnight()` recomputed at that same moment.** Never `previous.plusDays(1)`, never
`delay(24h)`. `delay()` does not advance while the device sleeps, so a wake-up can be late — and a
late tick still emits the *true* current date and re-anchors to the *true* next midnight, so it is
late, never wrong. Backgrounded sleep is corrected by `ON_RESUME` before anything renders. The
helper clamps to `0` at exact midnight, so the loop applies a small positive floor to avoid spinning.

## Data Flow

    TimeProvider.today()/now() ──► CurrentDateSource.dates ─┐
    ON_RESUME → refreshDate() ─────────────────────────────►├─► observedDate: StateFlow<LocalDate>
                                                             │
                        flatMapLatest(date) ─► combine(5) ─► uiState(date, rows) ─► answer() writes uiState.value.date

## File Changes

| File | Action | Description |
|---|---|---|
| `core/time/CurrentDateSource.kt` | Create | port + midnight-ticking impl |
| `core/di/TimeModule.kt` | Modify | one `@Binds` |
| `core/time/TimeProvider.kt` | Modify | receives the public `millisUntilNextMidnight()` |
| `scheduling/WorkScheduler.kt` | Modify | helper removed; import added |
| `tracking/TodayViewModel.kt` | Modify | `observedDate`, `flatMapLatest`, `refreshDate()`, `uiState.date`, all three sites |
| `tracking/TodayScreen.kt` | Modify | `refreshDate()` in the existing `ON_RESUME` block |
| `test/.../TodayViewModelTest.kt` | Modify | fixed-date stub audit (below) |
| `test/.../MidnightDateSourceTest.kt` | Create | virtual-time timer tests |
| `test/.../MidnightAnchorTest.kt` | Modify | import only |

`tracking/TodayModel.kt` needs **no** change (contradicting the proposal's affected-areas row):
`TodaySnapshot.today` is already a parameter.

## Testing Strategy

| Layer | What | How |
|---|---|---|
| Unit (JVM) | timer re-anchoring, no duplicate emission, late wake-up emits actual date (+1 not assumed), exact-midnight floor | `runTest` virtual time, `mockk<TimeProvider>` over a mutable `Instant` |
| Unit (JVM) | rollover re-queries once; `rollupDay` + unresolved filter follow; `answer()` writes the new date and never the old; `refreshDate()` resume path | fake `CurrentDateSource` backed by `MutableStateFlow` |
| Instrumented | **nothing new** | GMD images are unrooted and the wall clock cannot be moved, so an instrumented rollover test would assert nothing; both legs must stay green as regression only |

`TodayViewModelTest.buildViewModel()` audit: line 356's `every { today() } returns TODAY` is replaced
by the fake source, and line 349's `observeByDate(TODAY.toString())` becomes an `any()`-dispatching
stub over a date→flow map, so an unanticipated date fails legibly instead of as a `MockKException`.
Lines 111/145/167/181/214 keep `TODAY` and stay green.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. The broadcast/IPC option was rejected, so no new external surface.

## Stack

`stack:` stays `ratified` with `still_unpinned` empty. `CurrentDateSource` is an internal port — no
library, module, or version change — and is ratified here rather than pinned.

## Migration / Rollout

No migration required. Code-only; no schema, no persisted format, no alarm or WorkManager change.

## Open Questions

None. The foregrounded-timezone-travel gap is accepted and specified; recording it in
`openspec/config.yaml` `carried_forward_open_items` (id `today-foregrounded-timezone-travel`, owner
condition: a change that already needs an in-process app-wide time signal) is a **tasks-phase work
item**, not a spec artifact.
