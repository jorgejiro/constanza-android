# Apply Progress: today-past-day-correction — Slice A (PR 1)

**Scope of this run**: Phase 1 + Phase 2 (`TodayViewModel` viewed-date/clock split, three
navigation gestures, UI-state reset, JVM tests, streak regression) + the applicable Phase 7 JVM
verification steps. Phases 3-6 (Slice B: `TodayScreen` UI, strings, instrumented tests, config
close-out) intentionally untouched.

## Completed Tasks

- [x] 1.1-1.6 — `TodayViewModel`: `TodayDate(clock, navigated)` + `viewed` projection, `DateView`
  projection with `distinctUntilChanged`, unconditional clock writers (init timer + `refreshDate`),
  three navigation gestures, `uiState.flatMapLatest` keyed on `dateView`, `isPastDay` added to
  `TodayUiState`.
- [x] 2.1-2.15 — `TodayViewModelTest`: extended `FakeCurrentDateSource`/`buildViewModel` usage
  (added an optional `entryDao` param to `buildViewModel` for call-count verification, non-breaking
  default), 13 new JVM tests + 1 modified existing test (crossing-midnight rollover, added the
  missing expansion-state-unchanged assertion per Decision 3's validation finding).
- [x] 2.16 — Already satisfied by a pre-existing, byte-identical-to-`origin/main`
  `StreakCalculatorTest` test (`streak recomputed after a late correction shows no break`). No new
  domain test needed — confirmed via `git diff origin/main -- domain/.../StreakCalculatorTest.kt`
  (empty diff).
- [x] 7.1, 7.2, 7.4 — full JVM verification pass. 7.3 done for the Slice-A-applicable subset (the
  `TodayContent`/date-format checks named in 7.3 are Slice B). 7.5 (emulator matrix) intentionally
  NOT run — Slice B.

## Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `app/src/main/kotlin/.../tracking/TodayViewModel.kt` | Modified | `TodayDate`/`DateView` state split, `showPreviousDay`/`showNextDay`/`showToday`, `isPastDay` in `TodayUiState`, inlined `readNotificationPermission` to keep function count at detekt's `TooManyFunctions` ceiling |
| `app/src/test/kotlin/.../tracking/TodayViewModelTest.kt` | Modified | 13 new tests (2.2-2.4, 2.6-2.13, 2.15) + 1 modified existing rollover test (2.14) + optional `entryDao` param on `buildViewModel` |
| `openspec/changes/today-past-day-correction/tasks.md` | Modified | Phase 1, 2, and applicable Phase 7 tasks marked `[x]` with evidence |

## Deviations from Design

- Design's `clearPresentedSlotState()` shared-helper suggestion was NOT extracted as a separate
  private function: doing so pushed `TodayViewModel` to 12 (then 11) functions, over detekt's
  default `TooManyFunctions` ceiling of 11 (effectively enforced as `>= 11` fails, so the max
  allowed is 10). Fixed by (1) inlining the two-line clear directly into each of the three gesture
  functions (small duplication, no behavior change) and (2) inlining the trivial private
  `readNotificationPermission` helper into its two call sites (`refreshNotificationPermission`,
  `recordNotificationPermissionRequested`). Both are structural, zero-behavior-change
  consolidations, not the `@Suppress` route the design used for `LongParameterList`.
- Actual authored diff is 443 changed lines (152 in `TodayViewModel.kt`, 291 in
  `TodayViewModelTest.kt`), above the design's/tasks' Slice A estimate of 235-320. Still
  comfortably inside the 800-line session review budget, and this is its own PR (Slice A), so no
  re-split is needed.

## Issues Found

None — all three verification gates pass with real numbers: 221/221 `:app:testDebugUnitTest`
(26/26 `TodayViewModelTest`), 52/52 `:domain:test` (10/10 `StreakCalculatorTest`), 0 detekt issues
on `:domain:detektMain`/`:app:detektMain`, `:app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL.

## Remaining Tasks

- [ ] Phase 3-6 (Slice B: `TodayDateBar`, `TodayScreen` wiring, strings, instrumented tests, config
  close-out) — separate PR, targets `main` after this PR merges (stacked-to-main).
- [ ] Phase 7.5 (emulator matrix) — part of Slice B.

## Workload / PR Boundary

- Mode: chained/stacked PR slice (PR 1 of 2), delivery strategy `ask-on-risk` resolved to
  `stacked-to-main`.
- Current work unit: `slice-a-todayviewmodel-viewed-date`.
- Boundary: starts from `origin/main`, ends with `TodayViewModel`'s new public gesture surface
  (`showPreviousDay`/`showNextDay`/`showToday`, `isPastDay`) landing, unused by any caller yet
  (`TodayScreen.kt` untouched) — app behaves identically at the live edge.
- Rollback: revert `TodayViewModel.kt` and `TodayViewModelTest.kt` only; no other file touched.
- Review budget impact: 443 authored lines, within the 800-line session budget.

## Status

Slice A (Phase 1, 2, applicable Phase 7 JVM tasks) 100% complete. Ready for `sdd-verify`, then PR 1.
