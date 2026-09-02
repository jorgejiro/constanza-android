# Apply Progress: first-run-onboarding — Work Unit A

Change: `first-run-onboarding` · Work unit: `unit-a-gate-screens-unit-tests` · Date: 2026-09-02
Scope: Phase 0 (verify-only) + Phase 1-4. Phase 5/6/7 explicitly NOT started (Unit B's scope).

## Completed Tasks

- [x] 0.1 Verified `HabitEditorRoute` (`habit/HabitEditorScreen.kt:85-89`) already accepts
      `onBack: () -> Unit`, delivered by merged PR #47. No changes made to that file.
- [x] 1.1 Corrected `specs/onboarding/spec.md`'s "Permission Screen Never Offers A Prompt..."
      requirement text: replaced the wrong "two denials, not reachable on the matrix" claim with the
      corrected one-recorded-ask reachability (design §2.2).
- [x] 1.2 `reminding/ReminderSettingsStore.kt`: added `ONBOARDING_DONE_KEY`, `onboardingDone:
      Flow<Boolean>`, suspend `setOnboardingDone()`; companion object `private` → `internal`.
- [x] 1.3 `core/ui/theme/Dimens.kt`: added `PagerDot = 8.dp`.
- [x] 1.4 `res/values/strings.xml`: added 10 new onboarding strings (both screens, GRANTED/
      SHOULD_REQUEST/BLOCKED variants, Continue/Finish).
- [x] 2.1 `core/ui/MainActivity.kt`: added `ConstanzaRoute.EditorOrigin` enum + defaulted `origin`
      field on `HabitEditor`.
- [x] 2.2 `core/ui/MainActivity.kt`: added `startRoute` param to `ConstanzaApp`, `leaveTo` branch
      wiring `onDone`/`onBack` per origin. No `BackHandler` added (design §2.1/§5.2 supersession).
- [x] 2.3 `core/ui/MainActivity.kt`: added `FirstRunGateViewModel` (internal, `StateFlow<Boolean?>`
      via `stateIn(Eagerly, null)`) and the `FirstRunGate` composable (`setContent` now renders
      `FirstRunGate()` instead of `ConstanzaApp()` directly).
- [x] 2.4 KDoc added to `FirstRunGateViewModel` and `FirstRunGate`.
- [x] 3.1 Created `onboarding/OnboardingViewModel.kt`.
- [x] 3.2 Created `onboarding/OnboardingPermissionAction.kt`.
- [x] 3.3 Created `onboarding/OnboardingScreen.kt`.
- [x] 3.4 Created `onboarding/OnboardingRoute.kt`.
- [x] 4.1 Created `core/ui/FirstRunGateViewModelTest.kt` (3 tests, MockK-backed
      ReminderSettingsStore, Turbine).
- [x] 4.2 Page-list tests inside `onboarding/OnboardingViewModelTest.kt` (2 tests).
- [x] 4.3 Created `onboarding/OnboardingUiStateTest.kt` (2 tests, API-31 label-trap regression
      guard).
- [x] 4.4 Permission-state + finish()/recordRequestedNotificationPermission() tests inside
      `onboarding/OnboardingViewModelTest.kt` (4 tests).

## NOT started (Unit B's scope — left unchecked in tasks.md)

- [ ] Phase 5 (5.1-5.4): `CoreFlowTestFixture` DataStore seeding infrastructure.
- [ ] Phase 6 (6.1-6.6): Instrumented rework + api37 dialog-reshow measurement.
- [ ] Phase 7 (7.1-7.2): carried-forward item + final `./gradlew check` + matrix gate.

## Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command | `./gradlew :app:testDebugUnitTest` — 147/147 passed (11 new: 3 FirstRunGateViewModelTest, 6 OnboardingViewModelTest, 2 OnboardingUiStateTest). |
| Compile | `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` — both BUILD SUCCESSFUL, no new warnings beyond a pre-existing `hiltViewModel()` deprecation already present in `HabitEditorScreen.kt`. |
| detekt | `./gradlew :app:detekt :app:detektMain` — clean after fixing one genuine `MaxLineLength` finding in `OnboardingViewModelTest.kt`. No suppressions added. |
| lint | `./gradlew :app:lintDebug` — BUILD SUCCESSFUL. Only pre-existing findings plus one new `InlinedApi` note on `OnboardingPermissionAction.kt`'s `POST_NOTIFICATIONS` reference, same class already accepted on `TodayBanners.kt`/`NotificationPermission.kt`. |
| Runtime harness | `./gradlew :app:emulatorMatrixGroupDebugAndroidTest --continue` (both legs, full run completed) — **RED, expected and explicitly scoped to Unit B.** api31: 78 tests, 1 skipped, 3 FAILED (`apiBelow33ShowsNoNotificationBannerBecauseThePermissionDoesNotExist`, `creatingAHabitThroughTheUiDeliversItsReminderAndRecordsTheAnswerTappedOnIt`, `removingAHabitThroughTheUiTakesItOffTodayAndOutOfTheSchedule`). api37: 78 tests, 1 skipped, 3 FAILED (`allowingNotificationsThroughTheRealSystemDialogClearsTheTodayBanner`, `creatingAHabitThroughTheUiDeliversItsReminderAndRecordsTheAnswerTappedOnIt`, `removingAHabitThroughTheUiTakesItOffTodayAndOutOfTheSchedule`). Every one of the 6 failures is the identical `ComposeTimeoutException` at `CoreFlowE2ETest.kt:315` inside `launchApp()`, waiting for `today_title` that never renders — root cause is single and understood: the gate now renders onboarding first on the fresh-install emulator and `CoreFlowE2ETest` has no seeding to skip it yet (Phase 5's `CoreFlowTestFixture.seedOnboardingDone()` is Unit B's job). No other failure signature appeared; no flakiness observed across the two full runs. This is exactly the design's own §10/tasks.md's called-out consequence of Unit A landing before Unit B, not a production defect. |
| Rollback boundary | Revert `core/ui/MainActivity.kt`, `reminding/ReminderSettingsStore.kt`, `core/ui/theme/Dimens.kt`, `res/values/strings.xml`, delete `onboarding/` package and its 3 new test files, revert `specs/onboarding/spec.md`'s one-paragraph correction. One orphaned additive DataStore key (`onboarding_done`) on revert; no Room change, no scheduling change. |

## Changed-line budget (against 800, code files only — docs/tasks.md excluded per this change's own forecast methodology)

| File | +/- |
|---|---|
| `core/ui/MainActivity.kt` | 97/15 |
| `core/ui/theme/Dimens.kt` | 5/1 |
| `onboarding/OnboardingPermissionAction.kt` | 89/0 |
| `onboarding/OnboardingRoute.kt` | 57/0 |
| `onboarding/OnboardingScreen.kt` | 123/0 |
| `onboarding/OnboardingViewModel.kt` | 115/0 |
| `reminding/ReminderSettingsStore.kt` | 20/1 |
| `res/values/strings.xml` | 12/0 |
| `core/ui/FirstRunGateViewModelTest.kt` | 84/0 |
| `onboarding/OnboardingUiStateTest.kt` | 41/0 |
| `onboarding/OnboardingViewModelTest.kt` | 143/0 |
| **Total** | **786/17 = 803 authored lines** |

3 lines over the 800 budget — within the tasks.md forecast's own stated margin ("treat 800 as a
floor for the Compose-heavy rows, not a ceiling"). No comments/tests/blank lines were compressed to
force a smaller number, per the review-workload guard's explicit instruction. Flagging as a
`size:exception` candidate for the orchestrator rather than shrinking dishonestly.

## Deviations / decisions not fully specified by design

1. **New string keys** (task 1.4): design named categories ("onboarding copy for both screens,
   GRANTED/BLOCKED variants, Continue/Finish") but not exact keys/wording. Chose 10 new
   `onboarding_*` keys rather than reusing `today_notification_permission_*` strings, keeping the
   onboarding package's copy self-contained and independently editable.
2. **`OnboardingViewModel.next()`** has no explicit bounds guard beyond the ordering contract
   (design §9's `onPrimaryAction` never calls it on the last page) — matches design's literal
   snippet, not over-engineered with a `coerceAtMost`.
3. **Theme.kt untouched**: confirmed no new M3 role is introduced (background/onBackground/
   onSurfaceVariant/primary/onPrimary/outlineVariant are already bound and audited in `Theme.kt`'s
   KDoc) — design §12's own inventory already established this, verified by reading `Theme.kt`
   directly before concluding no edit was needed.

## Attempt authority

Acquired via `gentle-ai sdd-attempt acquire` continuing token
`sha256:b328c870a51db92d14fc24f80fe7853fc8e1bbe3d701b17d47783dda3d532cbd`
(`--request-id apply-unit-a-first-run-onboarding-01`). Evidence revision for settle:
`sha256:2d429261aa69eeb148a7595b31461371a339d7f426edaa9916dcd3a3066d6b29` (sha256 of `git diff --cached`
at completion of this work unit).

---

# Apply Progress: first-run-onboarding — Work Unit B

Change: `first-run-onboarding` · Work unit: `unit-b-instrumented-rework-and-seeding` · Date: 2026-09-02
Scope: Phase 5 (DataStore seeding infrastructure), Phase 6 (instrumented rework + api37 dialog-reshow
measurement), Phase 7 (carried-forward item + final verification). Unit A's Phase 0-4 production code
was not touched, except for one necessary correction described below.

## Completed Tasks

- [x] 5.1 Added `ReminderSettingsDataStoreEntryPoint` (`@EntryPoint`, `@InstallIn(SingletonComponent)`)
      and `CoreFlowTestFixture`'s `EntryPointAccessors.fromApplication` accessor.
- [x] 5.2 `CoreFlowTestFixture`: added `seedOnboardingDone()` and `seedNotificationPermissionUnasked()`
      suspend helpers, writing through the shared `DataStore` using `ReminderSettingsStore`'s
      `internal` keys.
- [x] 5.3 `CoreFlowTestFixture.reset()`: added `settings.edit { it[ReminderSettingsStore.ONBOARDING_DONE_KEY] = false }`;
      `requested_notification_permission` deliberately left untouched.
- [x] 5.4 `CoreFlowE2ETest.launchApp()` split into `launchFirstRunApp()` (awaits onboarding's first
      page) and `launchOnboardedApp()` (seeds the flag, awaits `today_title`) — no default.
- [x] 6.1 **Measured, not assumed.** Ran the real matrix with the a1(deny)/a2(allow) pair as designed.
      **Observed: the api37 image DOES re-show the real `POST_NOTIFICATIONS` dialog for a2 after a1's
      single denial**, once `seedNotificationPermissionUnasked()` clears the app's own latch before
      a2 launches. Design.md §8.3's fallback (swapping which scenario owns the real dialog) was **not**
      needed — the primary design held.
- [x] 6.2 Rewrote `a1DenyingTheOnboardingPromptLeavesTodayOfferingNotificationSettings` (api37): denies
      the real dialog on onboarding screen 2, backs out of the seeded habit editor via the
      `action_back` content description to Today, confirms the settings-deep-link banner, relaunches,
      confirms onboarding does not reappear.
- [x] 6.3 Rewrote `a2AllowingTheOnboardingPromptLeavesTodayWithNoNotificationBanner` (api37): seeds the
      latch unasked, grants the real dialog, backs out to Today, confirms no banner.
- [x] 6.4 Added `a3ApiBelow33SkipsTheOnboardingPermissionScreenEntirely` (api31): screen 2 never
      renders, screen 1's primary action reads "Finish", Today shows no banner after handoff.
- [x] 6.5 `creatingAHabitThroughTheUi...`/`removingAHabitThroughTheUi...` now call
      `launchOnboardedApp()`/`relaunchOnboardedApp()`, pre-seeded `onboarding_done = true`.
- [x] 6.6 Verified: `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` green on both legs (see
      Runtime harness evidence below).
- [x] 7.1 Added carried-forward item `notification-permission-blocked-after-one-ask` to
      `openspec/config.yaml` (design §2.3), owner-conditioned on a future change already touching
      `NotificationPermission`'s Activity-free contract. `habit-editor-has-no-cancel-affordance` was
      not touched (belongs to the editor change).
- [x] 7.2 Verified: `./gradlew check` green (unit tests, lint, detekt on `:app` and `:domain`), and the
      emulator matrix green on both legs.

## Design correction found during measurement (task 5.1)

Design.md §8.1's own snippet declared `ReminderSettingsDataStoreEntryPoint` inside
`androidTest/CoreFlowTestFixture.kt`. Following it literally crashed **every** `CoreFlowE2ETest` method
on the very first real matrix run with:

```
java.lang.ClassCastException: Cannot cast
com.jjrapps.constanza.DaggerConstanzaApplication_HiltComponents_SingletonC$SingletonCImpl to
com.jjrapps.constanza.e2e.ReminderSettingsDataStoreEntryPoint
```

Root cause: this app instruments the REAL `ConstanzaApplication` (no `HiltAndroidTest`/
`HiltTestApplication`), so Hilt's KSP aggregation that generates the `SingletonComponent`
implementation runs once, from `:app:kspDebugKotlin` over `main` sources only. An `@EntryPoint`
declared only in `androidTest` (a separate compilation) is never woven into that already-generated
component, so `EntryPointAccessors.fromApplication` throws at runtime instead of failing to compile.
**Fix:** moved the entry point into `main` (`core/di/DataStoreModule.kt`, `internal` visibility, same
precedent as `ReminderSettingsStore`'s `internal` companion). This is the one Unit-A-owned file this
work unit touched, and it was necessary to make task 5.1 achievable at all — reported here rather than
silently deviating from design.

## Runtime harness evidence (task 6.6/7.2 — the unit's whole point)

Three full `./gradlew :app:emulatorMatrixGroupDebugAndroidTest --continue` runs were needed:

1. **Run 1** (entry point in `androidTest`, per design's literal snippet): every `CoreFlowE2ETest`
   method failed with the `ClassCastException` above, on both legs. Root-caused, entry point moved to
   `main`.
2. **Run 2** (entry point fixed): api37 leg fully GREEN (78 tests, 0 failed, 1 skipped — this is the
   6.1 measurement run). api31 leg: `CoreFlowE2ETest` itself was fully green (5 tests, 2 skipped, 0
   failed — a3/creatingAHabit/removingAHabit all passed), but the whole instrumentation process later
   crashed inside `TodayComposeTest`/`TodayAdaptiveComposeTest` (both pre-existing, unrelated to
   onboarding) with `IllegalStateException: Cannot perform this operation because the connection pool
   has been closed`. Root-caused to a **documented pre-existing flake**: `TodayComposeTest.kt:70-77`'s
   own KDoc explicitly describes this exact async DB-close race surfacing on whichever unrelated test
   happens to be running at the time. Not caused by this change; no production or test-infra file was
   touched to "fix" it, per this unit's scope boundary.
3. **Run 3** (clean re-run, no code changes): **api31 78 tests, 0 failed, 2 skipped** (a1/a2 skip
   below API 33, expected); **api37 78 tests, 0 failed, 1 skipped** (a3 skip at/above API 33,
   expected, reused from run 2 since its inputs were unchanged). Confirmed via `xml.etree.ElementTree`
   parsing of `TEST-api31-_app-.xml`/`TEST-api37-_app-.xml`, not console text or regex.

**Final state: GREEN on both legs**, matching the unit's evidence goal exactly.

`./gradlew check` (unit tests + lint + detekt, `:app` and `:domain`): BUILD SUCCESSFUL.
`./gradlew :app:testDebugUnitTest`: 147/147, unchanged from Unit A.
`./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL.
`./gradlew :app:detekt :app:detektMain :app:lintDebug`: clean, no new findings, no suppressions added.

## Changed-line budget (against this unit's 500-line cap)

```
 app/src/androidTest/kotlin/com/jjrapps/constanza/e2e/CoreFlowE2ETest.kt        | 194 ++++++++++++-------
 app/src/androidTest/kotlin/com/jjrapps/constanza/e2e/CoreFlowTestFixture.kt    |  38 +++-
 app/src/androidTest/kotlin/com/jjrapps/constanza/e2e/SystemPermissionDialog.kt |  38 ++++
 app/src/main/kotlin/com/jjrapps/constanza/core/di/DataStoreModule.kt           |  24 +++
 openspec/config.yaml                                                          |  30 ++++
 5 files changed, 262 insertions(+), 62 deletions(-)
```

262 + 62 = 324 changed lines, well under the 500-line budget. `openspec/changes/first-run-onboarding/tasks.md`
checkbox updates (+42/-12, mostly `[ ]`→`[x]`) are excluded from this count as doc/task bookkeeping, per
this change's own established convention of excluding already-committed doc-only diffs from the
code-review budget.

## Rollback boundary

Revert the three commits on `test/onboarding-instrumented-rework`
(`test(e2e): borrow the app's DataStore for onboarding seeding`,
`test(e2e): rework CoreFlowE2ETest for the first-run onboarding gate`,
`docs(openspec): record the one-ask carried-forward item, close out tasks`). No production behavior
changes beyond the one `@EntryPoint` interface relocated into `main` (test-only consumer, zero runtime
callers outside `androidTest`). No Room change, no scheduling change, no UI change.

## Deviations from design

1. **`ReminderSettingsDataStoreEntryPoint` moved from `androidTest` to `main`** (`core/di/DataStoreModule.kt`)
   — see "Design correction" above. This is a placement fix, not a mechanism change: the entry point's
   shape, the keys it exposes, and how the fixture consumes it are exactly as design.md §8.1 specified.
2. **Back-navigation uses the `action_back` content description**, not `device.pressBack()` — more
   deterministic in a Compose test and consistent with this suite's existing content-description-based
   interactions (e.g. `habit_list_add_habit`). Design.md did not specify the mechanism, only that
   "back from the seeded editor entry reaches Today" needed to be exercised.
3. **`tapDenyOnTheSystemPermissionDialog()` added to `SystemPermissionDialog.kt`** — design.md's a1
   scenario needed a real denial and no deny-helper existed yet; added as the mirror image of the
   existing allow helper, same id-first/text-fallback shape.

## Issues found (reported, not silently patched)

- `TodayComposeTest`'s documented pre-existing async-teardown flake (see Runtime harness evidence,
  run 2) surfaced once during this unit's work. It is unrelated to first-run-onboarding, already
  documented in-repo as a known intermittent failure mode, and out of this unit's scope to fix. Not
  present in the final green run.

## Attempt authority

Continued via `gentle-ai sdd-attempt acquire` token
`sha256:ae9f03c0cee14b51ea1ca6b66b54e4c5494b3cd9a3f0761f77f92e8d01cdc67c`
(`--change first-run-onboarding --work-unit unit-b-instrumented-rework-and-seeding --max-attempts 3
--max-changed-lines 500`). Evidence revision for settle:
`sha256:bcd2292621a6a7d0beefb124c8ab5d3089f20e89d8465ba536a5876ca03bc82b` (sha256 of
`git diff 7b512d3..HEAD`, the full range of this unit's three commits on
`test/onboarding-instrumented-rework`).
