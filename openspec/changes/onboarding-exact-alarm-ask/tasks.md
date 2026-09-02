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
| onboarding: Two-Screen Flow, Applicability-Derived (4) | 2.1, 2.4, 3.3, 4.2, 7.1, 7.2 | Unit (page-list legs, incl. both API 31-32 legs — simulated via mocks, never a real API 31 emulator) + Instrumented (row order on the two-row leg, the "granted shows a confirmation, not an ask" combination, and the API 31 one-row leg's *rendering*, not just its page list) |
| onboarding: Non-Blocking Permission Ask (4) | 2.3, 3.1, 4.4 | Structural (Scaffold's bottom action never routes through a permission control, unchanged) + Unit (`refresh()`) + Instrumented (non-auto-launch) |
| onboarding: Exact-Alarm Onboarding Row [ADDED] (3) | 3.1-3.4, 4.2-4.4, 7.3 | Instrumented — copy, no-auto-launch, and (as of the correction round) an actual instrumented no-restart-on-grant test: a live `mutableStateOf` drives a recomposition of the same composition and asserts the row swaps from ask to confirmation without a new `setContent` call |
| reminder-delivery: Exact-Alarm Permission States (4) | 3 scenarios: pre-existing (`AlarmSchedulerTest`, `ReconcileWorkerTest`), unmodified; 4th ("declining costs nothing"): 5.2 | Unit for 3; architectural-only for the 4th — accepted as sufficient (see Correction Round below): the claim is a structural absence, and a repo-wide grep for any `record*`/persisted exact-alarm method finding none is stronger evidence than a unit test could offer, since a unit test would need to fabricate the very persisted flag the requirement forbids |
| reminder-delivery: Exact-Alarm Banner, Standing Fallback [ADDED] (4) | 3 scenarios: pre-existing (`TodayViewModelTest`), unmodified; 4th ("declining doesn't suppress"): 5.2 | Unit for 3; architectural-only for the 4th — same acceptance as above |

## Phase 7 — Correction Round (`sdd-verify` FAIL response)
(Verify report: 5 CRITICAL, 3 WARNING, 2 SUGGESTION. See `verify-report.md`.)

- [x] 7.1 **Spec fix, not a code fix (CRITICAL-1).** The delta spec's scenario "API 37 with exact
      alarms already granted shows one row" literally said screen 2 renders "exactly one row, for
      notifications" when granted+undecided. The shipped code (and design decision 2) correctly
      renders both rows in that case — the exact-alarm row as a confirmation line, matching how the
      notification row already treats `GRANTED`. The scenario text was wrong, not the code: reworded
      to "API 37 with exact alarms already granted shows a confirmation, not an ask", asserting both
      rows render with only the notification row still asking. See `specs/onboarding/spec.md`.
- [x] 7.2 `OnboardingComposeTest.kt`: added
      `theExactAlarmRowShowsAConfirmationWhileTheNotificationRowIsStillAsking` (closes CRITICAL-1's
      test gap — no case previously paired `canScheduleExactAlarms = true` with a non-`GRANTED`
      notification state) and
      `aNotApplicableNotificationStateRendersNoNotificationContentLeavingOnlyTheExactAlarmRow`
      (closes CRITICAL-3 — the API 31 one-row leg was previously proven only at the page-list level,
      never at rendering).
- [x] 7.3 `OnboardingComposeTest.kt`: added
      `theExactAlarmRowDropsItsAskAndShowsTheConfirmationOnTheSameCompositionWhenGrantedLive`
      (closes CRITICAL-2 — `tasks.md`'s Promise Coverage table previously claimed instrumented
      coverage for "no-restart-on-grant" that did not exist; this is now a real instrumented test
      using a live `mutableStateOf` to drive recomposition without a new `setContent` call).
- [x] 7.4 `design.md`: corrected a cosmetic misattribution (verify report WARNING-3) — the
      `exactAlarmsAllowedScheduler()` reference actually lives in `design.md`'s own Testing Strategy
      section, not `tasks.md`, and no such named factory function exists anywhere in the codebase;
      the actual pattern is the defaulted `alarmScheduler` parameter inline in
      `TodayViewModelTest.buildViewModel` (`TodayViewModelTest.kt:333-334`). Corrected to name that
      call site directly instead of an invented function name.
- [x] 7.5 CRITICAL-4/5 (`reminder-delivery`: "declining costs nothing later" / "does not suppress
      the banner"): judged and accepted as architectural-only proof, not rework. See Promise
      Coverage table above and `verify-report.md`'s own WARNING-1 recommendation, which this
      correction round agrees with.
- Verify: `./gradlew :app:testDebugUnitTest`, `./gradlew :app:detekt :app:detektMain :app:lintDebug`,
  `./gradlew :app:emulatorMatrixGroupDebugAndroidTest --rerun-tasks` (both legs).
