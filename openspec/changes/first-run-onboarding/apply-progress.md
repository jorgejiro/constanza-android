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
