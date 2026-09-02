```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:d57aa8bf7d130aaea13b62ba757b63c11dd4abc1c238d5abb4daccefff16ae96
verdict: fail
blockers: 1
critical_findings: 5
requirements: 1/5
scenarios: 14/19
test_command: ./gradlew :app:emulatorMatrixGroupDebugAndroidTest --rerun-tasks
test_exit_code: 0
test_output_hash: sha256:3fb250b5760fe6c2d7a8ef24d215db40e1feb0e460ff2b660aece8b65b4cd8f2
build_command: ./gradlew :app:testDebugUnitTest && ./gradlew :app:detekt :app:detektMain :app:lintDebug
build_exit_code: 0
build_output_hash: sha256:aeebbfe2a26eadda4df55b76b8a66b9434c32ce58c6127554fba4aa09aab88b2
```

## Verification Report

**Change**: onboarding-exact-alarm-ask
**Version**: N/A (no versioned spec numbering in this repo)
**Mode**: Standard (strict_tdd scope is `:domain` only; this change is entirely `:app`, so Strict TDD does not apply)
**Branch**: `feat/onboarding-exact-alarm-ask` @ `80fb05e` (base `main` @ `8d4ca18`, not merged)

### Completeness
| Metric | Value |
|--------|-------|
| Tasks total | 20 |
| Tasks complete | 20 |
| Tasks incomplete | 0 |

Note: the launch prompt cited "24 tasks"; the actual `tasks.md` checkbox count is 20 (1.1–1.4, 2.1–2.4, 3.1–3.4, 4.1–4.4, 5.1–5.2, 6.1–6.2). All 20 are ticked and match the code state — this is a metadata discrepancy in the orchestrator's framing, not a defect in the artifact.

### Build & Tests Execution

**Build**: PASSED
```text
$ ./gradlew :app:testDebugUnitTest
BUILD SUCCESSFUL

$ ./gradlew :app:detekt :app:detektMain :app:lintDebug
BUILD SUCCESSFUL
```

**Tests**: PASSED — real re-execution, not relayed apply-phase output
```text
$ ./gradlew :app:emulatorMatrixGroupDebugAndroidTest --rerun-tasks
Starting 100 tests on api31
Finished 102 tests on api31   (100 run, 2 skipped, 0 failed)
Starting 100 tests on api37
Finished 101 tests on api37   (100 run, 1 skipped, 0 failed)
BUILD SUCCESSFUL in 5m 43s
```
Parsed via `xml.etree.ElementTree` against `app/build/outputs/androidTest-results/managedDevice/debug/{api31,api37}/*.xml`:
- api31: 100 testcases, 0 failures, 2 skipped (both `CoreFlowE2ETest` cases gated on API 33+ notification prompts — correctly skipped below API 33).
- api37: 100 testcases, 0 failures, 1 skipped (`CoreFlowE2ETest.a3ApiBelow33SkipsTheOnboardingPermissionScreenEntirely` — correctly skipped above API 33).
- All 7 `OnboardingComposeTest` cases pass on **both** legs.
- `ReconcileWorkerTest` (8 cases) and `AlarmSchedulerTest`/`TodayViewModelTest` banner cases pass unmodified on both legs, confirming pre-existing `reminder-delivery` coverage still holds.
- Both previously-flagged flaky tests (`TodaySlotRowComposeTest.theAnswerLabelsStayOnOneLineNextToALongHabitNameOnAPhone`, `TodayAddHabitComposeTest.theTrailingAddActionSitsBelowEveryHabitRow`) passed clean on this run — no recurrence.

**Coverage**: Not available (no coverage gate configured for this project).

**Matrix trustworthiness**: The matrix ran clean end-to-end, but two of its ~200 cases have now timed out once each in the last day (unrelated files, per this session's briefing) and both are still live in the suite with no retry/quarantine mechanism. A gate that can flake ~1% of its 200 cases without a retry policy is not fully trustworthy as a hard merge gate over time, even though today's specific run is unambiguous (0 failures on either leg). Recommend a documented retry-once policy for the matrix task rather than treating a clean run as proof the flake is gone.

### Spec Compliance Matrix

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| onboarding: Two-Screen Flow, Applicability-Derived | API 37 fresh install shows both rows, notifications first | `OnboardingComposeTest.bothRowsRenderTogetherWithNotificationsAboveExactAlarms` (both legs) | ✅ COMPLIANT |
| onboarding: Two-Screen Flow, Applicability-Derived | API 31 fresh install has nothing to ask | `OnboardingViewModelTest.the page list is intro-only on API 31 fresh install, where nothing applies` | ✅ COMPLIANT |
| onboarding: Two-Screen Flow, Applicability-Derived | API 31 with exact alarms revoked shows one row | `OnboardingViewModelTest.the page list includes the permissions page on API 31 with exact alarms revoked` | ⚠️ PARTIAL — proves the *page* exists, never proves the *page renders exactly one row*; no compose test ever composes `OnboardingPermissionsPage` with `permission = NOT_APPLICABLE` |
| onboarding: Two-Screen Flow, Applicability-Derived | API 37 with exact alarms already granted shows one row (notifications only) | none | ❌ FAILING — see CRITICAL-1 below |
| onboarding: Non-Blocking Permission Ask | Notification denial still completes onboarding | `OnboardingComposeTest.thePrimaryActionStaysEnabledWhenNotificationsAreBlockedAndExactAlarmsAreDenied` + `OnboardingScaffold` structural non-gating | ✅ COMPLIANT |
| onboarding: Non-Blocking Permission Ask | Notification grant completes onboarding | `OnboardingComposeTest.thePrimaryActionStaysEnabledWhenNotificationsAndExactAlarmsAreBothGranted` | ✅ COMPLIANT |
| onboarding: Non-Blocking Permission Ask | Returning from exact-alarm settings without granting still completes onboarding | `OnboardingComposeTest.thePrimaryActionStaysEnabledWhen...ExactAlarmsAreDenied` (x2) + `theDeniedExactAlarmRowNeverAutoLaunchesTheSettingsIntent` | ✅ COMPLIANT |
| onboarding: Non-Blocking Permission Ask | Granting exact alarms and returning still completes onboarding | `OnboardingComposeTest.thePrimaryActionStaysEnabledWhen...ExactAlarmsAreGranted` (x2) + `OnboardingViewModelTest.refresh re-reads both...` | ✅ COMPLIANT |
| onboarding: Exact-Alarm Onboarding Row [ADDED] | Row copy states degradation, not silence | `strings.xml` review + `OnboardingComposeTest.bothRowsRenderTogetherWithNotificationsAboveExactAlarms` / `theGrantedExactAlarmRowIsAConfirmationLineWithNoButton` render the exact copy | ✅ COMPLIANT |
| onboarding: Exact-Alarm Onboarding Row [ADDED] | Row never auto-launches the settings intent | `OnboardingComposeTest.theDeniedExactAlarmRowNeverAutoLaunchesTheSettingsIntent` (both legs) | ✅ COMPLIANT |
| onboarding: Exact-Alarm Onboarding Row [ADDED] | Granting via the deep link updates the screen without restart | `OnboardingViewModelTest.refresh re-reads both the notification permission and exact-alarm eligibility from one call` (unit only) | ❌ UNTESTED — see CRITICAL-2 below |
| reminder-delivery: Exact-Alarm Permission States | Denied before habit creation still delivers, inexactly | `AlarmSchedulerTest` (pre-existing, unmodified, passing) | ✅ COMPLIANT |
| reminder-delivery: Exact-Alarm Permission States | Revoked mid-session degrades already-armed reminders | `ReconcileWorkerTest` (pre-existing, unmodified, passing both legs) | ✅ COMPLIANT |
| reminder-delivery: Exact-Alarm Permission States | Re-granted permission upgrades pending reminders | `ReconcileWorkerTest` (pre-existing, unmodified, passing both legs) | ✅ COMPLIANT |
| reminder-delivery: Exact-Alarm Permission States | Declining onboarding's offer costs nothing later | Architectural proof only (absence of a `record*`/persisted method on the `AlarmScheduler` collaborator) | ❌ UNTESTED — see WARNING-1 below (judged acceptable) |
| reminder-delivery: Exact-Alarm Banner, Standing Fallback [ADDED] | Banner renders whenever eligibility is denied | `TodayViewModelTest.the banner state mirrors canScheduleExactAlarms...` (pre-existing, unmodified, passing) | ✅ COMPLIANT |
| reminder-delivery: Exact-Alarm Banner, Standing Fallback [ADDED] | Banner disappears once granted, no restart needed | `TodayViewModelTest.refreshExactAlarmPermission re-reads a permission granted after construction` (pre-existing) | ✅ COMPLIANT |
| reminder-delivery: Exact-Alarm Banner, Standing Fallback [ADDED] | Declining onboarding's ask does not suppress the banner | Same architectural absence proof as above | ❌ UNTESTED — see WARNING-1 below (judged acceptable) |
| reminder-delivery: Exact-Alarm Banner, Standing Fallback [ADDED] | Banner action deep-links and never auto-launches | `ExactAlarmBanner` (pre-existing, unmodified) | ✅ COMPLIANT |

**Compliance summary**: 14/19 scenarios COMPLIANT, 1 PARTIAL, 3 UNTESTED, 1 FAILING.

### Correctness (Static Evidence)

| Requirement | Status | Notes |
|---|---|---|
| Load-bearing Decision 1 (page list is construction-time, no index clamp) | ✅ Confirmed | `OnboardingViewModel.pages` is a `val` computed once in the constructor from `includesPermissionPage`; `index`/`page` (`pages[index]`) carry **no clamp, no `coerceIn`, no `min()`** anywhere in `OnboardingViewModel.kt` or `OnboardingUiState`. Re-verified by direct source read, not by relaying the apply report. |
| Both API 31-32 branches implemented | ✅ Confirmed, ⚠️ under-tested | Fresh-install (`NOT_APPLICABLE` + granted → `[Intro]`) and revoked-exact-alarms (`NOT_APPLICABLE` + denied → `[Intro, Permissions]`) both exist as distinct unit test cases and both run on the real API 31 emulator leg — but the API 31 emulator leg's `OnboardingComposeTest` cases all use a hardcoded two-row `pages` list and never compose the one-row content, so the *rendering* of the one-row leg is unverified on real API 31 hardware/emulator. |
| Relaxed-MockK trap avoided | ✅ Confirmed | `OnboardingViewModelTest.buildViewModel`'s `alarmScheduler` parameter defaults to `mockk { every { canScheduleExactAlarms() } returns true }`, never `mockk(relaxed = true)`. All 9 pre-existing tests that don't pass an explicit `alarmScheduler` therefore build with the row suppressed, as intended — confirmed no existing test silently gained the new row. |
| Non-blocking holds for both permissions | ✅ Confirmed | `OnboardingScaffold`'s `bottomBar` primary action is a `Scaffold` sibling of page content (`OnboardingRoute.kt:38-57`); it never reads `NotificationPermissionDecision` or `canScheduleExactAlarms` to decide enablement. `OnboardingExactAlarmAction` has no callback wired to the primary action at all — its only outbound edge is `startActivity`. |
| No new persistence for exact alarms | ✅ Confirmed | `ReminderSettingsStore` has exactly three DataStore keys (`SNOOZE_DURATION_MINUTES_KEY`, `REQUESTED_NOTIFICATION_PERMISSION_KEY`, `ONBOARDING_DONE_KEY`) — no exact-alarm key added. `AlarmScheduler` exposes only `schedule`/`cancel`/`canScheduleExactAlarms` — no `record*` method. `rg` for `requested_exact_alarm|recordRequestedExactAlarm|hasRequestedExactAlarm` across `app/src` returns zero matches. |
| `TodayBanners.kt` untouched, "Fix" retired by value | ✅ Confirmed | `git diff 8d4ca18..HEAD -- app/src/main/kotlin/.../tracking/TodayBanners.kt` is empty (zero lines changed). `strings.xml`'s `today_exact_alarm_banner_action` value changed `Fix` → `Open settings`; the key is untouched and the only reference to it (`TodayBanners.kt:157`) is unmodified. |
| Play policy for `SCHEDULE_EXACT_ALARM` during onboarding | ✅ Honestly unresolved | `design.md`'s "Open question" section still states this was never verified (offline phase) and explicitly does not block implementation. Not quietly resolved. |

### Design Coherence

| Decision | Followed? | Notes |
|---|---|---|
| 1. Page list construction-time, no live re-derivation, no clamp | ✅ Yes | Verified directly in source, see above. This is the single most important guarantee in this change and it holds. |
| 2. Row visibility is live; granted → confirmation line, not hidden | ⚠️ Followed, but conflicts with the spec's own text | The design explicitly rejects hiding a granted/satisfied row. That is fine as an implementation choice, but the delta spec's own "Two-Screen Flow, Applicability-Derived" requirement says screen 2 "MUST render exactly the rows that apply" and its own scenario text demands "exactly one row" in the already-granted case. Decision 2 was never reconciled against that specific scenario text — see CRITICAL-1. |
| 3. Plain `Boolean`, no decision enum | ✅ Yes | `canScheduleExactAlarms: Boolean` on `OnboardingUiState`, no new enum type. |
| 4. One `refresh()` method, both facts in one coroutine | ✅ Yes | `refresh()` calls `readPermission()` then updates `canScheduleExactAlarms.value` in the same `viewModelScope.launch` block. |
| 5. Filled `Button`, not `OutlinedButton` | ✅ Yes | `OnboardingExactAlarmAction` uses `Button`; `ControlStrokeCallSiteTest.GUARDED_CONTROLS` count is unchanged (no new `OutlinedButton` call site). |
| 6. No launcher, no callback on the exact-alarm row | ✅ Yes | `OnboardingExactAlarmAction(canSchedule: Boolean)` takes no callback parameter; its only side effect is `context.startActivity(...)`. |
| 7. Retire "Fix" by string value, not key | ✅ Yes | Confirmed by diff, see above. |
| Process-death exposure widens, deliberately not fixed | ✅ Judged acceptable | `index` is a plain `MutableStateFlow(0)` with no `SavedStateHandle`; process death during screen 2 does reset to screen 1 on restart, exactly as the design describes. The stated cost (one extra "Continue" tap, no data loss, confirmation line renders correctly on the restarted flow) is accurate given the code. The design correctly avoids the trap of a shallow fix (restoring only `index` without reconciling it against a re-derived, possibly-shorter page list) and files an explicit follow-up (`onboarding-index-survives-process-death`). Sound tradeoff, not a defect. |

### Issues Found

**CRITICAL**:

1. **Scenario "API 37 with exact alarms already granted shows one row (notifications only)" is FAILING, not merely untested.** `OnboardingPermissionsPage` unconditionally composes both `OnboardingPermissionAction` *and* `OnboardingExactAlarmAction` (`OnboardingScreen.kt:134-136`) — there is no `NOT_APPLICABLE`-equivalent hidden branch for the exact-alarm row. When `canScheduleExactAlarms() == true`, `OnboardingExactAlarmAction` still renders a confirmation `Text` (`onboarding_exact_alarm_granted_body`, `OnboardingPermissionAction.kt:107-113`). So on the scenario's own GIVEN (exact alarms already granted, notifications undecided), screen 2 actually renders **two** visible rows — the notification ask plus the exact-alarm confirmation line — not "exactly one row, for notifications" as the delta spec's scenario literally states. No test exercises this combination at the compose level (every `OnboardingComposeTest` case that uses `canScheduleExactAlarms = true` pairs it with `permission = GRANTED`, never with an undecided/blocked notification state), so nothing would catch this. This is a genuine, uncaught divergence between the delta spec text and the shipped behavior — either the spec's "renders exactly N rows" language needs correcting to describe "N actionable rows" (the code's actual model, which mirrors the pre-existing notification-row pattern where GRANTED also renders a confirmation line, never nothing), or the exact-alarm row needs an actual hidden state to match the spec literally. This is exactly the class of defect this whole change exists to eliminate (a promise made in one artifact — here, the spec — never honored in the other), just pointed in the opposite direction from the usual pattern in this repo.

2. **Scenario "Granting via the deep link updates the screen without restart" has no instrumented coverage, despite `tasks.md`'s Promise Coverage table claiming "Instrumented only" proof for it.** No `OnboardingComposeTest` case changes `canScheduleExactAlarms` mid-composition and re-asserts the row disappearing; the only covering test is the ViewModel-level `refresh()` unit test, which proves the *data* re-reads correctly but not that the *composable* recomposes and drops the row without a restart. The underlying mechanism (`collectAsState()` + `combine(...)`) is standard reactive Compose and very likely correct, but the apply-phase's own coverage claim overstates what was actually tested — this should be corrected in the record even if a new test is not written.

3. **Scenario "API 31 with exact alarms revoked shows one row" is only proven at the page-list level, not the rendering level.** No compose test ever passes `permission = NotificationPermissionDecision.NOT_APPLICABLE` into `OnboardingPermissionsPage`, so the actual one-row rendering (confirming `OnboardingPermissionAction`'s `NOT_APPLICABLE → Unit` branch produces no visible content) has never been exercised on device, including on the real API 31 emulator leg that ran today.

4 & 5. **The two "declining costs/suppresses nothing" scenarios remain formally UNTESTED at runtime** (`reminder-delivery`: "Declining onboarding's offer costs nothing later" and "Declining onboarding's ask does not suppress the banner"). Per this skill's decision gate, an untested required scenario is CRITICAL by default. Judgment call (see WARNING-1): this is the one class of untested scenario I assess as **acceptable**, because the property being claimed is a structural absence (no persisted coupling exists), and the maximal available proof — grepping the entire `app/src` tree for any `record*`/persisted exact-alarm method and finding none — is stronger evidence for "nothing is spent" than a unit test could be, since a unit test would need to fabricate the very persisted flag the requirement says must not exist. Recorded as CRITICAL per the mandatory gate, but I recommend the orchestrator/user accept this pair without rework.

**WARNING**:

1. See CRITICAL-4/5 above — recommend downgrading those two specifically to WARNING-and-accept once a human confirms the architectural-absence proof is sufficient for this class of negative claim.
2. The emulator matrix's trustworthiness as a hard gate is time-limited: two tests in unrelated, untouched files have each timed out once in the last day and are still live with no retry policy. Today's run is unambiguous (0 failures, both legs), but this is evidence quality, not evidence of the flakes being fixed.
3. `tasks.md`'s Review Workload Forecast references `TodayViewModelTestFactory.exactAlarmsAllowedScheduler()` as an established pattern; no such named factory function exists in the codebase (the actual pattern is a defaulted `alarmScheduler` parameter inline in `buildViewModel`). Cosmetic — the pattern itself was followed correctly — but the artifact's cross-reference is inaccurate.

**SUGGESTION**:

1. Consider adding one `OnboardingComposeTest` case with `permission = NOT_APPLICABLE, canScheduleExactAlarms = false` (closes CRITICAL-3) and one case with `permission = SHOULD_REQUEST/BLOCKED, canScheduleExactAlarms = true` asserting the notification body renders and deciding, deliberately, whether the exact-alarm confirmation line is expected to co-render (closes CRITICAL-1 by making the spec/code disagreement explicit and testable either way).
2. Consider a `LaunchedEffect`/recomposition-driven instrumented test that flips a `mutableStateOf`-backed `canScheduleExactAlarms` and asserts the row's content swaps without recreating the composition, to make good on the "Instrumented only" claim in `tasks.md` (closes CRITICAL-2).

### Verdict
**FAIL**

The implementation is largely sound — the load-bearing Decision 1 (no live page-list re-derivation, no index clamp) holds exactly as designed, both API 31-32 branches exist and are unit-proven, no new persistence was added, `TodayBanners.kt` is genuinely untouched, and all 200 real device-test cases across both API legs pass on a fresh `--rerun-tasks` run. But one spec scenario ("API 37 with exact alarms already granted shows one row") is not merely untested — it is contradicted by the shipped code, and no test exists that would have caught it. That, plus two further genuinely untested rendering-level scenarios (API 31's one-row leg, and the no-restart-on-grant live-update claim), are enough required-scenario gaps to fail this verification under the "a spec scenario is compliant only when a covering test passed at runtime" rule. None of these findings threaten user data or crash safety; all are fixable with either a spec-wording correction or a small number of additional instrumented test cases.
