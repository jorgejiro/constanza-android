# Apply Progress: Habit Deletion

**Mode**: Standard (strict_tdd scope is `:domain` only; this change is all `:app`).
**Work unit**: 1 (Full change — data, VM, UI, tests), single PR per tasks.md's forecast.

## Status

20/20 tasks complete. All five phases done in one batch. Ready for `sdd-verify`.

## Completed Tasks

- [x] 1.1 Hoisted the relaxed `AlarmScheduler` mock to a shared `HabitRepositoryTestFixture` field,
      passed to both `OccurrencePlanner` and `HabitRepository`.
- [x] 1.2 Added `CoreFlowTestFixture.assertNoNotificationPosted(id)` — a bounded (3s) negative poll,
      shorter than the existing 15s positive-wait bound.
- [x] 2.1 Added `EntryDao.observeCountsByHabit()` + `HabitEntryCount`; folded into
      `HabitListViewModel`'s `combine`, exposing `HabitListUiState.entryCounts`.
- [x] 2.2 `HabitDaos` gained `reminderOccurrenceDao` (5th field).
- [x] 2.3 `HabitRepository` constructor gained `alarmScheduler: AlarmScheduler` (6 params); added
      `suspend fun delete(habitId: Long)` following design D1's snapshot → cascade → cancel ordering.
- [x] 3.1 `HabitListViewModel.delete(habitId)`.
- [x] 3.2 Trailing `IconButton(Icons.Filled.MoreVert)` + `DropdownMenu` (single "Delete" item) added
      to `HabitRow.trailingContent`, beside the existing Progress/Archive `TextButton`s.
- [x] 3.3 `DeleteHabitDialog` (M3 `AlertDialog`), `pendingDeleteId` as
      `rememberSaveable { mutableStateOf<Long?>(null) }` in `HabitListScreen`, resolved by id from
      `state.habits`; `HabitListActions.onDeleteHabit` wired through.
- [x] 3.4 `strings.xml`: `habit_list_more_options`, `habit_list_delete`, `habit_delete_dialog_title`,
      `habit_delete_dialog_body` (`<plurals>`, project's first), `habit_delete_dialog_confirm`.
      Dismiss reuses `action_cancel`.
- [x] 4.1–4.4 `HabitRepositoryDeleteTest.kt` (new, 4 tests): cascade across all four child tables,
      alarm cancellation via mock verification, archiving-untouched isolation, zero-entry parity.
- [x] 4.5–4.6 `HabitDeleteDialogComposeTest.kt` (new, 3 tests): non-zero count rendering, zero-count
      honesty, decline leaves every table unchanged.
- [x] 4.7 Visual check performed on a real API 31 (`Constanza_API31`) emulator at a synthesized
      360dp width (density 480 on a 1080px-wide device). See Risks/Findings below — not cramped.
- [x] 4.8 Rewrote `CoreFlowE2ETest`'s `:309-317` KDoc rationale; kept the survives/archived/stamped
      assertion; split `REMOVED_HABIT` into `ARCHIVED_HABIT` + `DELETED_HABIT`; reworded the `:337`
      assertion message.
- [x] 4.9 New `deletingAHabitThroughTheUiRemovesItAndItsHistoryAndSilencesItsReminder` E2E test.
- [x] 5.1 `./gradlew check` green.
- [x] 5.2 `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` green on both legs.

## Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :app:testDebugUnitTest` → 190/190, 0 failures, 0 errors (parsed via `xml.etree.ElementTree` over `app/build/test-results/testDebugUnitTest/TEST-*.xml`) |
| Runtime harness command/scenario and exact result | `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` → api31: 111 tests, 0 failures, 2 skipped (permission-gated methods not applicable on API 31); api37: 111 tests, 0 failures, 1 skipped. All 4 new repository tests, all 3 new compose tests, and both new/rewritten `CoreFlowE2ETest` methods passed on both legs. |
| Rollback boundary | Revert this work unit's single commit set (Phase 1–4 source + test files). No Room schema/migration to unwind — confirmed no `app/schemas/**` diff appeared. |

## Verification (real output)

- `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL (only
  pre-existing `hiltViewModel` deprecation warnings, unrelated to this change).
- `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL, 190/190 tests, 0 failures/errors (includes
  `ControlStrokeCallSiteTest`, `ViewModelTeardownCallSiteTest`, updated `HabitListViewModelTest`).
- `./gradlew :app:detekt :app:detektMain :app:lintDebug` → BUILD SUCCESSFUL; `detekt.xml` empty (0
  issues); `lint-results-debug.xml` has 8 issues, all pre-existing (`InlinedApi` x3,
  `ModifierFactoryExtensionFunction`, `DataExtractionRules`, `UnusedResources` for an unrelated
  pre-existing string, `UseKtx` x2) — none touch habit-deletion code or strings.
- `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` → BUILD SUCCESSFUL in 6m29s.
  - api31: 113 started (2 skipped: `a1Denying…`/`a2Allowing…`, gated below API 33), 111 completed,
    0 failed.
  - api37: 112 started (1 skipped: `a3ApiBelow33…`, gated at API 33+), 111 completed, 0 failed.
  - `TodaySlotRowComposeTest` (known flake, untouched by this change): all 3 methods passed on both
    legs this run — no re-run needed.
- `./gradlew check` → BUILD SUCCESSFUL (compile + unit + lint, all UP-TO-DATE from the runs above).
- No `app/schemas/**` file appeared in the diff — confirmed via `git status --short | rg schemas`
  returning no matches. Design's "no migration" claim holds.

## Manual Visual Check (task 4.7)

Performed on the real `Constanza_API31` emulator (not a screenshot test, an actual look), per the
launch prompt's requirement. Installed `app-debug.apk`, created a habit through the real UI,
synthesized a 360dp-equivalent width by setting `wm density 480` on the 1080px-wide device (native
density 420 gives ~411dp; 480 gives exactly 360dp), and screenshotted the habit list both with a
short habit name ("Garden") and a long one ("A very long habit name indeed").

**Verdict: not cramped.** Progress, Archive, and the new overflow (⋮) icon are evenly spaced with a
visually-measured ~24dp gap on both sides (Progress↔Archive and Archive↔overflow), matching each
other — the row does not look tighter than a two-button row would. The overflow icon never touches
the screen edge or the Archive button in either habit-name case. The long habit name wraps the
`ListItem` headline onto multiple lines (pre-existing `ListItem` behavior, not new in this change —
the headline column was already narrowed by two trailing buttons before this work), but Progress,
Archive and the overflow icon stay aligned and fully tappable regardless of how tall the headline
grows. No `Spacing`/`Dimens` adjustment was needed.

**Confirmation dialog** (screenshotted for the "Garden" habit, 0 entries): title "Delete Garden?"
names the habit; body "0 recorded answers will be permanently deleted along with this habit. This
cannot be undone." states the count honestly via the `other` plural category (spec scenario 2).
Buttons "Cancel"/"Delete" are legible and well-separated. Matches `DiscardChangesDialog`'s shape.

Emulator was killed after the check (`adb emu kill`); `wm density` was reset before teardown. The
physical device `RFCY21GNC5Y` was never touched. `emulator-5554` was free when claimed (checked via
`adb devices` before starting) and was released (killed) after use.

## Changed-Line Count vs the Attempt's 600-Line Ceiling

**Reporting honestly rather than trimming comments/tests to fit, per the work-unit-commits skill's
explicit instruction not to compress or restyle code to hit a budget number.**

`git diff --cached --numstat` over exactly the 11 changed/added app-scoped files (tasks.md/design
docs already landed in earlier commits and are excluded, matching tasks.md's own "code-only" framing):

| File | + | - |
|---|---|---|
| `e2e/CoreFlowE2ETest.kt` | 75 | 16 |
| `e2e/CoreFlowTestFixture.kt` | 31 | 0 |
| `habit/HabitDeleteDialogComposeTest.kt` (new) | 144 | 0 |
| `habit/HabitRepositoryDeleteTest.kt` (new) | 148 | 0 |
| `habit/HabitRepositoryTestFixture.kt` | 11 | 2 |
| `core/data/dao/Daos.kt` | 10 | 0 |
| `habit/HabitListScreen.kt` | 87 | 5 |
| `habit/HabitListViewModel.kt` | 21 | 1 |
| `habit/HabitRepository.kt` | 27 | 2 |
| `res/values/strings.xml` | 13 | 0 |
| `test/habit/HabitListViewModelTest.kt` | 11 | 4 |
| **Total** | **578** | **30** |

**608 changed lines total — 8 over the attempt's `--max-changed-lines 600` ceiling.**

This is a discrepancy worth naming rather than silently resolving: `tasks.md`'s own committed
Review Workload Forecast (from `sdd-tasks`, already merged before this apply attempt) states the
session's cached `review_budget_lines: 800`, forecasts this exact code-only range as
"≈350–450 lines," and concludes "Low risk with wide margin, well inside 800" — recommending a single
PR with no `size:exception`. The actual code-only total (608) exceeds that forecast's upper bound
(450) by 158 lines, mostly because the project's established convention (visible in every existing
file read for this change) is heavy KDoc explaining *why*, not just *what* — matched here rather
than under-documented to hit a smaller number. 608 is comfortably inside the 800 budget `tasks.md`
was scoped against, but it is 8 lines over the narrower 600-line ceiling this specific apply attempt
was launched with.

I did not cut comments, tests, or documentation to close that 8-line gap, per instruction. Flagging
for the parent orchestrator/maintainer to reconcile the 600 vs. 800 figures; recommending
`size:exception` against the 600 ceiling given the work is one cohesive, already-forecast unit that
cannot be sliced further without breaking Phase 1's blocking relationship to Phase 4 (a partial PR
would land test infrastructure with nothing yet using it, or tests with no infrastructure to run
against).

## Deviations from Design

None — implementation matches `design.md` D1–D5 exactly, including the explicit "no `replanAll()`"
constraint and the two-line division of proof for "no reminder fires" (4.2 repository-mock
verification vs. 4.9 handler null-guard via real broadcast).

## Issues Found

None. No schema JSON appeared (confirmed). All five constructor call sites needing the new
`HabitDaos`/`HabitRepository`/`HabitListViewModel` parameters were found and updated: production
code uses Hilt `@Inject constructor` (no manual wiring needed), `HabitRepositoryTestFixture`
(androidTest) and `HabitListViewModelTest` (unit test, mocked `EntryDao`) were both updated.

## Remaining Tasks

None. All 20 tasks complete.
