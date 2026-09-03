# Tasks: Today Follows the Clock Across Midnight

## Review Workload Forecast

> Corrected by the orchestrator: the phase agent forecast against a 400-line budget,
> but this session's cached preflight budget is 800 lines. The ~300-380 estimate is
> under both figures, so no `size:exception` is required and no decision blocks apply.

| Field | Value |
|-------|-------|
| Estimated changed lines | ~300–380 (additions + deletions) |
| 800-line budget risk (session budget) | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | single-pr |
| Chain strategy | n/a — no exception needed |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: size-exception
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | `core/time` port: move helper + `CurrentDateSource` | PR 1 | `./gradlew :app:testDebugUnitTest --tests "*MidnightDateSourceTest*" --tests "*MidnightAnchorTest*"` | N/A — virtual-time JVM test only, no device behavior to demo | Revert `CurrentDateSource.kt`, `TimeModule.kt` binding, and the moved helper without touching `TodayViewModel.kt` |
| 2 | `TodayViewModel`/`TodayScreen` wiring + fixture audit | PR 1 | `./gradlew :app:testDebugUnitTest --tests "*TodayViewModelTest*"` | Both matrix legs (`./gradlew :app:emulatorMatrixGroupDebugAndroidTest`, api31+api37) as regression only | Revert `TodayViewModel.kt`, `TodayScreen.kt`, `TodayViewModelTest.kt`; unit 1 stays intact and unused |
| 3 | Config record + verification | PR 1 | `./gradlew :app:detektMain` | N/A — documentation edit, no runtime behavior | Revert the `openspec/config.yaml` entry independently |

## Phase 1: `core/time` Foundation

- [x] 1.1 Move `millisUntilNextMidnight()` from `scheduling/WorkScheduler.kt:23` to `core/time/TimeProvider.kt`, public, carrying its KDoc verbatim; update the one production call site `WorkScheduler.kt:83` and its import. Zero behavior change.
- [x] 1.2 Update the import in `test/.../scheduling/MidnightAnchorTest.kt` to the new location only; assertions stay untouched (regression, not RED).
- [x] 1.3 RED: write `test/.../core/time/MidnightDateSourceTest.kt` — timer re-reads `today()`/`millisUntilNextMidnight()` at each emission (never `plusDays(1)`), a late wake-up after simulated sleep emits the true current date exactly once with no duplicate, and the loop applies a positive delay floor at exact midnight so it never spins.
- [x] 1.4 GREEN: create `core/time/CurrentDateSource.kt` — port exposing the current-date stream and `zone()`, backed by a self-rescheduling loop built on `TimeProvider`, satisfying 1.3.
- [x] 1.5 Add `@Binds bindCurrentDateSource` to `core/di/TimeModule.kt`.

## Phase 2: `TodayViewModel` Wiring

- [x] 2.1 Add a `date: LocalDate` field to `TodayUiState` (`tracking/TodayViewModel.kt`).
- [x] 2.2 Replace `TodayViewModel`'s `TimeProvider` constructor param with `CurrentDateSource` (stays 8 params; `zone()` pass-through); `@Suppress("LongParameterList")` unwidened.
- [x] 2.3 Add `observedDate: MutableStateFlow<LocalDate>` seeded from the injected source's current date; collect the source's stream into it from `init`.
- [x] 2.4 Wrap the existing five-source `combine` (`TodayViewModel.kt:106-127`) in `observedDate.flatMapLatest { date -> ... }`; `observeByDate(date.toString())` and `TodaySnapshot(entriesToday, unresolved, date)` both key off the lambda's `date`; set `TodayUiState.date = date`.
- [x] 2.5 Add `refreshDate()`, pushing the source's current date into `observedDate`.
- [x] 2.6 Update `answer()` (`TodayViewModel.kt:149`) to read `uiState.value.date` instead of the removed `today` val — this is the data-corruption fix (`In-App Answer Date Attribution`).
- [x] 2.7 Call `refreshDate()` from `TodayScreen.kt`'s existing `ON_RESUME` `DisposableEffect` (`TodayScreen.kt:58-67`), alongside `refreshExactAlarmPermission()`/`refreshNotificationPermission()`.

## Phase 3: Test Fixture Audit + Coverage

- [x] 3.1 Audit `TodayViewModelTest.kt` `buildViewModel()` (lines ~331-363): replace `mockk<TimeProvider>` with a fake `CurrentDateSource` (`MutableStateFlow`-backed); change `every { observeByDate(TODAY.toString()) }` (line 349) to an `any()`-dispatching stub over a date→flow map so an unanticipated date fails legibly, not as an opaque `MockKException`. Confirm lines 111/145/167/181/214 (`TODAY` literal) stay green.
- [x] 3.2 Add a rollover test: advancing the fake date re-subscribes `observeByDate` and both `rollupDay` and the unresolved filter follow the new date.
- [x] 3.3 Add the answer-attribution test: after the displayed date advances, `answer()` writes against the new date only, never the previous one (covers the spec's `In-App Answer Date Attribution` scenarios).
- [x] 3.4 Add a `refreshDate()` resume test: a stale `observedDate` left over from backgrounding is corrected to the fake source's current value.

## Phase 4: Config + Verification

- [x] 4.1 Add a `today-foregrounded-timezone-travel` entry under `carried_forward_open_items.items` in `openspec/config.yaml`, recording the accepted foregrounded-timezone-travel gap with owner condition: a future change that already needs an in-process app-wide time signal.
- [x] 4.2 Run `./gradlew :app:detektMain` — confirm no `ForbiddenMethodCall` violation at the moved/new clock reads.
- [x] 4.3 Run both matrix legs (`./gradlew :app:emulatorMatrixGroupDebugAndroidTest`, api31 + api37) as regression only; no new instrumented test is added — GMD images are unrooted and cannot move the wall clock.
  Green on both legs 2026-09-03: api31 115 tests / 0 failures / 0 errors / 2 skipped, api37 115 / 0 / 0 / 1. The skips are complementary by design — api31 omits the two runtime `POST_NOTIFICATIONS` prompt tests that do not apply below API 33, api37 omits the one asserting that below 33 the permission screen is skipped entirely.
