# Apply Progress: Ask For Exact Alarms During Onboarding

**Change**: `onboarding-exact-alarm-ask`
**Mode**: Standard (strict TDD scope is `:domain` only; this change is entirely `:app`)
**Status**: 24/24 tasks complete. Ready for verify.

## Completed Tasks

All tasks from `tasks.md` Phases 1-6 are marked `[x]`:

- [x] 1.1-1.4 — `OnboardingPage.Notifications` → `Permissions` rename (own commit, zero behavior change)
- [x] 2.1-2.4 — `AlarmScheduler` collaborator, applicability OR, `refresh()` rename, unit cases for both API-31 legs and the combined-refresh case
- [x] 3.1-3.4 — `OnboardingExactAlarmAction` row, 3 new/changed strings, `OnboardingPermissionsPage` hosting both rows, `OnboardingRoute` wiring
- [x] 4.1-4.4 — new `androidTest/.../onboarding/` package with `OnboardingComposeTest` (7 test methods)
- [x] 5.1-5.2 — confirmed by inspection: `TodayViewModelTest.kt:222-247` already covers the banner requirement unmodified; `AlarmScheduler` on `OnboardingViewModel` carries no `record*`/persisted method
- [x] 6.1-6.2 — `./gradlew check` and `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` both green

## Commits (in order, all on `feat/onboarding-exact-alarm-ask`)

1. `9d3c617` refactor(onboarding): rename OnboardingPage.Notifications to Permissions
2. `074d88c` feat(onboarding): add exact-alarm row to screen 2, sibling to notifications
3. `4283c0e` test(onboarding): add device-free instrumented coverage for screen 2's two rows
4. `fe3cf5c` fix(onboarding): split the row-combination compose test into four cases
5. `c6d8218` docs(openspec): mark all onboarding-exact-alarm-ask tasks complete

## Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `app/src/main/kotlin/.../onboarding/OnboardingViewModel.kt` | Modified | Enum rename `Notifications`→`Permissions`; injected `AlarmScheduler`; `canScheduleExactAlarms: Boolean` on `OnboardingUiState`; `includesPermissionPage` widened to an OR of notification applicability and exact-alarm denial; `refreshPermission()`→`refresh()` reading both sources in one coroutine |
| `app/src/main/kotlin/.../onboarding/OnboardingScreen.kt` | Modified | `OnboardingNotificationsPage`→`OnboardingPermissionsPage`; now hosts both permission rows, notification first, split by `Spacing.md` |
| `app/src/main/kotlin/.../onboarding/OnboardingPermissionAction.kt` | Modified | Added `OnboardingExactAlarmAction(canSchedule: Boolean)` — filled `Button`, own `LocalContext`, `startActivity(ACTION_REQUEST_SCHEDULE_EXACT_ALARM)`, no launcher/callback |
| `app/src/main/kotlin/.../onboarding/OnboardingRoute.kt` | Modified | `ON_RESUME` calls `refresh()`; passes `state.canScheduleExactAlarms` into the page |
| `app/src/main/res/values/strings.xml` | Modified | 3 new keys for the exact-alarm row; `today_exact_alarm_banner_action` value `Fix`→`Open settings` (key unchanged, `TodayBanners.kt` untouched) |
| `app/src/test/kotlin/.../onboarding/OnboardingViewModelTest.kt` | Modified | `buildViewModel` gets `alarmScheduler` defaulted to granted; 3 new test cases (API 31 fresh-install leg, API 31 revoked leg, combined `refresh()`) |
| `app/src/test/kotlin/.../onboarding/OnboardingUiStateTest.kt` | Modified | Enum rename + new `canScheduleExactAlarms` constructor arg |
| `app/src/androidTest/kotlin/.../onboarding/OnboardingComposeTest.kt` | Created | New package. 7 tests rendering `OnboardingScaffold`/`OnboardingPermissionsPage` over hand-built `OnboardingUiState` — no ViewModel, no Hilt, no Room |

## Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :app:testDebugUnitTest --tests "com.jjrapps.constanza.onboarding.*"` — all green after each phase; final full run: `./gradlew :app:testDebugUnitTest` BUILD SUCCESSFUL |
| Runtime harness command/scenario and exact result | `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` — api31: 100 tests, 0 failures, 2 skipped; api37: 100 tests, 0 failures, 1 skipped. All 7 `OnboardingComposeTest` cases pass on both legs (confirmed via `xml.etree.ElementTree` parse of the JUnit XML) |
| Rollback boundary | Commit 1 reverts alone (pure rename). Commits 2-3 revert together (row UI + its coverage) without touching commit 1. Commit 4 is a test-only fix, revertible alone. |

## Manual Device Verification

Installed fresh on `Medium_Phone` emulator (API 37, `emulator-5554`, transient — booted and killed for this check only; the persistent shared emulator was untouched). Screen 2 on a fresh install renders exactly as designed: notification row ("Allow notifications..." + filled "Allow" button) above the exact-alarm row ("Reminders will still arrive, but on this device they may arrive a few minutes late unless you turn on exact alarms." + filled "Open settings" button). Both rows read as visual siblings — same body-text style, same filled-button treatment, same spacing — and the exact-alarm copy states degradation, never silence. The "Finish" primary action remains a separate sibling control at the bottom, present and enabled independent of either row.

## Deviations from Design

None. All seven design decisions honored as specified:
1. Page list stays construction-time (OR of two applicability facts) — no index clamp added.
2. Row visibility live, page existence not — exact-alarm row re-reads on `ON_RESUME` via `refresh()`.
3. Plain `Boolean`, no new decision enum.
4. One `refresh()` method reading both facts in one coroutine (was two calls in Phase 2's first draft — corrected before commit).
5. Filled `Button`, not `OutlinedButton` — confirmed `ControlStrokeCallSiteTest` passes with no new guarded call site.
6. No launcher, no callback on the exact-alarm row — only `startActivity`.
7. "Fix" retired by changing the string value only; `TodayBanners.kt` untouched (confirmed by diff — zero changes to that file).

## Issues Found

One test-authoring mistake, caught and fixed within this apply batch (not a design or spec issue): the first draft of `thePrimaryActionStaysPresentAndEnabledInEveryRowCombination` called `composeTestRule.setContent` four times in a loop inside one test method. `createComposeRule()` only permits one `setContent` call per test, so the second iteration threw `IllegalStateException`, caught by the API 31 emulator leg on the first matrix run. Split into four separate `@Test` methods (see commit `fe3cf5c`). Re-ran the full matrix afterward — clean on both legs.

`TodayAddHabitComposeTest.theTrailingAddActionSitsBelowEveryHabitRow` failed once on the same first matrix run with a `ComposeTimeoutException`. This test touches no file this change modified; a re-run of the full matrix (unchanged bytes) passed cleanly, consistent with the documented matrix-contention flakiness already called out in that file's own KDoc (timeout raised 5s→15s for the same reason). Treated as environmental, not attributed to this change.

## Changed-Line Count

Code diff (`git diff --numstat 89e8ff5..fe3cf5c -- app/`): 328 additions + 26 deletions = **354 changed lines**, against the launch prompt's 600-line budget and the tasks artifact's 800-line session budget. Both clear comfortably. (The `tasks.md` checkbox-only docs commit `c6d8218` — 40 changed lines — is excluded from this count as bookkeeping, consistent with the tasks forecast's convention of excluding already-committed planning docs from the code budget.)

## Remaining Tasks

None. All 24 tasks complete.

## Status

24/24 tasks complete. Ready for verify.
