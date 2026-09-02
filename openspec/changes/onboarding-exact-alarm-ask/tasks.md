# Tasks: Ask For Exact Alarms During Onboarding

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | Code-only ~350-450 (production + tests, new instrumented file included); all-in with the already-committed SDD docs (proposal 196 + spec 170 + design 241 = 607) ≈ 1,650-1,750 |
| Judged against | 800 (session `review_budget_lines`); code-only also clears the skill's classic 400-line default |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR, 3 commits (rename / viewmodel+row / instrumented test) |
| Delivery strategy | single-pr |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

The proposal's 900-1,200 / 1,250-1,700 forecast counted proposal+spec+design (607 lines, already
committed) as part of the same PR. This repo's convention — already-committed planning docs land
in their own commit and are excluded from the code PR's budget — means the number this PR is
actually judged against is the code-only figure above, which clears 800 comfortably.

### Work Units (commits within PR 1)

| Unit | Goal | Focused test | Runtime harness | Rollback boundary |
|---|---|---|---|---|
| A | Enum rename `Notifications`→`Permissions`, zero behavior change | `./gradlew :app:testDebugUnitTest --tests "com.jjrapps.constanza.onboarding.*"` | N/A — rename only | Revert 3 production + 2 test files |
| B | `AlarmScheduler` collaborator, applicability OR, `refresh()`, row UI, strings | Same command, new cases added | N/A until Unit C | Revert `OnboardingViewModel/Screen/PermissionAction/Route.kt` + `strings.xml` |
| C | First `androidTest/.../onboarding/` package | N/A (compose-only tests) | `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` (API 31 + API 37, nothing attached) | Delete the new package |

## Phase 1 — Enum Rename (own commit, mechanical)
- [x] 1.1 `app/src/main/kotlin/.../onboarding/OnboardingViewModel.kt`: rename `OnboardingPage.Notifications`→`Permissions`; update KDoc and the `buildList` call site.
- [x] 1.2 `app/src/main/kotlin/.../onboarding/OnboardingScreen.kt`: rename `OnboardingNotificationsPage`→`OnboardingPermissionsPage`.
- [x] 1.3 `app/src/main/kotlin/.../onboarding/OnboardingRoute.kt`: update the `when` branch to `Permissions`/`OnboardingPermissionsPage`.
- [x] 1.4 `app/src/test/kotlin/.../onboarding/OnboardingUiStateTest.kt` and `OnboardingViewModelTest.kt`: update enum references.
- Verify: `./gradlew :app:testDebugUnitTest --tests "com.jjrapps.constanza.onboarding.*"`.

## Phase 2 — ViewModel: AlarmScheduler + Applicability
(Req: onboarding — Two-Screen Flow, Applicability-Derived)
- [x] 2.1 `OnboardingViewModel.kt`: inject `AlarmScheduler`; add `canScheduleExactAlarms: Boolean` to `OnboardingUiState`; widen `includesPermissionPage` to `notificationApplicable || !alarmScheduler.canScheduleExactAlarms()`.
- [x] 2.2 **Same commit as 2.1** — `OnboardingViewModelTest.kt`: add an `alarmScheduler` param to `buildViewModel`, defaulted to `mockk { every { canScheduleExactAlarms() } returns true }` (mirrors `TodayViewModelTest.kt:333-334`). Do this before any row UI lands: a relaxed `mockk`'s default `false` would silently arm the new row in every existing test.
- [x] 2.3 Rename `refreshPermission()`→`refresh()`, reading both sources in one `combine`; update `OnboardingRoute.kt`'s `ON_RESUME` call site.
- [x] 2.4 Add unit cases: API-31-fresh-install leg (`NOT_APPLICABLE` + granted → `[Intro]`); API-31-revoked leg (`NOT_APPLICABLE` + denied → `[Intro, Permissions]`); `refresh()` updates both facts from one call.
- Verify: `./gradlew :app:testDebugUnitTest --tests "com.jjrapps.constanza.onboarding.*"`.

## Phase 3 — Exact-Alarm Row
(Req: onboarding — Exact-Alarm Onboarding Row [ADDED]; Non-Blocking Permission Ask)
- [x] 3.1 `OnboardingPermissionAction.kt`: add `OnboardingExactAlarmAction(canSchedule: Boolean)` — own `LocalContext`; granted → confirmation `Text`; denied → body `Text` + filled `Button` → `startActivity(ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName")`. No launcher, no callback (Decision 6).
- [x] 3.2 `res/values/strings.xml`: add 3 keys for the row's granted body, denied body, and action label, stating reminders still arrive (degraded), never that they stop; change `today_exact_alarm_banner_action` value `Fix`→`Open settings` (key unchanged, `TodayBanners.kt` untouched).
- [x] 3.3 `OnboardingScreen.kt`: `OnboardingPermissionsPage` hosts both rows split by `Spacing.md`, notification row first (spec ordering).
- [x] 3.4 `OnboardingRoute.kt`: pass `state.canScheduleExactAlarms` into the page.
- Verify: `./gradlew :app:testDebugUnitTest --tests "com.jjrapps.constanza.onboarding.*"` (compose-only change; behavior proven in Phase 4).

## Phase 4 — Instrumented Coverage
(new `androidTest/.../onboarding/` package — does not exist today)
- [x] 4.1 Create package `app/src/androidTest/kotlin/com/jjrapps/constanza/onboarding/`.
- [x] 4.2 `OnboardingComposeTest.kt` (new): render `OnboardingPermissionsPage`/`OnboardingScaffold` over hand-built `OnboardingUiState` — never bare-construct `OnboardingViewModel` (it is in `ViewModelTeardownCallSiteTest.GUARDED_VIEW_MODELS`, and the only exemption drags in an unrelated Room fixture). Assert both rows render, row order, degradation copy, primary action stays present+enabled across all 4 live-state combinations.
- [x] 4.3 Assert the granted state renders only a confirmation line, no button (Decision 2).
- [x] 4.4 Assert non-auto-launch: compose the denied state, idle, assert `UiDevice.currentPackageName` (per `e2e/SystemPermissionDialog.kt`) is still this app's package.
- Verify: `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` (API 31 + API 37, nothing attached — the binding convention for both API-conditional legs).

## Phase 5 — reminder-delivery Delta: Confirm, Don't Re-Test
- [x] 5.1 Confirm `TodayViewModelTest.kt:222-247` already proves "Banner renders/disappears on live `canScheduleExactAlarms`" (Req: Exact-Alarm Banner, Standing Fallback [ADDED]) — no new test needed; `AlarmScheduler.kt`/`TodayBanners.kt` are unmodified.
- [x] 5.2 Confirm by inspection that the new `AlarmScheduler` collaborator on `OnboardingViewModel` has no `record*`/persisted method, unlike `recordRequestedNotificationPermission` — this absence of coupling is what proves "declining onboarding's offer costs nothing later" and "does not suppress the banner." **This is an architectural proof, not a unit test** — record it in the PR description, not as a test file.

## Phase 6 — Full Regression
- [x] 6.1 `./gradlew check` (unit tests + lint + detekt) — runs no instrumented test.
- [x] 6.2 `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` — the only command that proves either API-conditional leg; `check` alone proves nothing instrumented.

## Promise Coverage

| Spec requirement (scenarios) | Task(s) | Proof, honestly stated |
|---|---|---|
| onboarding: Two-Screen Flow, Applicability-Derived (4) | 2.1, 2.4, 3.3, 4.2 | Unit (page-list legs, incl. both API 31-32 legs — simulated via mocks, never a real API 31 emulator) + Instrumented (row order on the two-row leg) |
| onboarding: Non-Blocking Permission Ask (4) | 2.3, 3.1, 4.4 | Structural (Scaffold's bottom action never routes through a permission control, unchanged) + Unit (`refresh()`) + Instrumented (non-auto-launch) |
| onboarding: Exact-Alarm Onboarding Row [ADDED] (3) | 3.1-3.4, 4.2-4.4 | Instrumented only — copy, no-auto-launch, no-restart-on-grant |
| reminder-delivery: Exact-Alarm Permission States (4) | 3 scenarios: pre-existing (`AlarmSchedulerTest`, `ReconcileWorkerTest`), unmodified; 4th ("declining costs nothing"): 5.2 | Unit for 3; architectural-only, untested, for the 4th |
| reminder-delivery: Exact-Alarm Banner, Standing Fallback [ADDED] (4) | 3 scenarios: pre-existing (`TodayViewModelTest`), unmodified; 4th ("declining doesn't suppress"): 5.2 | Unit for 3; architectural-only, untested, for the 4th |
