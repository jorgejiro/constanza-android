```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:b2d67f0d4870f2d69561440bc78f4e85a2446e5c0428ee3f437585c4fc6ca134
verdict: pass
blockers: 0
critical_findings: 0
requirements: 7/7
scenarios: 15/15
test_command: "./gradlew :app:testDebugUnitTest && ./gradlew :app:emulatorMatrixGroupDebugAndroidTest --rerun-tasks"
test_exit_code: 0
test_output_hash: sha256:a19db79c1d942cd89afb2a9ab1f87b40fca11ecc893e696da7620d0cc1a5a822
build_command: "./gradlew :app:detekt :app:detektMain :app:lintDebug --rerun-tasks"
build_exit_code: 0
build_output_hash: sha256:9e7ef7a9018fc2ac0cfbd3a3a313abdca33e6bcf84a7a2b3241d2acac22117b9
```

## Verification Report

**Change**: first-run-onboarding
**Branch**: `main` at `2500265` (PR #49 Unit A + PR #50 Unit B, both merged)
**Mode**: Standard (`strict_tdd_scope: ":domain only"` — this change is entirely `:app`, strict TDD not active)

### Note on the orchestrator's stated spec counts

The launch briefing stated "6 requirements, 11 scenarios" for `specs/onboarding/spec.md`. Actual count,
verified with `rg -c "^### Requirement:"` / `rg -c "^#### Scenario:"`: **6 requirements, 10 scenarios**.
`specs/reminder-response/spec.md`: 1 MODIFIED requirement, 5 scenarios (matches the briefing). Combined
authoritative total used for this report and for `sdd-verify-validate`: **7 requirements, 15 scenarios**.
This is a briefing/artifact mismatch, not a code defect — flagging per the skill's instruction rather than
silently adopting the stated 11.

### Completeness
| Metric | Value |
|--------|-------|
| Tasks total | 29 (tasks.md, Phases 0–7) |
| Tasks complete | 29 |
| Tasks incomplete | 0 |

Both apply-progress records (Unit A: Phase 0–4; Unit B: Phase 5–7) checked out against actual source —
every task claimed complete has a corresponding, working code artifact (see Correctness table below).
No checkbox was taken on faith.

### Build & Tests Execution

**Unit tests**: ✅ 147 passed / 0 failed / 0 skipped (fresh run, `--rerun-tasks`-forced re-execution then
parsed via `xml.etree.ElementTree` over `app/build/test-results/testDebugUnitTest/*.xml`)
```text
./gradlew :app:testDebugUnitTest --rerun-tasks
BUILD SUCCESSFUL
total 147, failures 0, errors 0, skipped 0
```

**Static analysis**: ✅ Passed (fresh run, `--rerun-tasks`)
```text
./gradlew :app:detekt :app:detektMain :app:lintDebug --rerun-tasks
BUILD SUCCESSFUL in 18s — 35 actionable tasks: 35 executed
```

**Device-free instrumented matrix**: ✅ Passed on both legs (fresh run, `--rerun-tasks`, NOT trusted from
the PR's own report — run independently in this session)
```text
./gradlew :app:emulatorMatrixGroupDebugAndroidTest --rerun-tasks --continue
BUILD SUCCESSFUL in 4m 13s — 84 actionable tasks: 84 executed

api31: tests=78 failures=0 errors=0 skipped=2   (a1, a2 correctly skipped — API<33)
api37: tests=78 failures=0 errors=0 skipped=1   (a3 correctly skipped — API>=33)
```
Both XML result files parsed with `xml.etree.ElementTree` (never regex) over
`app/build/outputs/androidTest-results/managedDevice/debug/{api31,api37}/TEST-{leg}-_app-.xml`.
Zero `<failure>` or `<error>` elements found in either file. `a1DenyingTheOnboardingPromptLeaves...`
and `a2AllowingTheOnboardingPromptLeaves...` both ran (not skipped) and passed on api37, driving the
**real** `com.android.permissioncontroller` dialog through UiAutomator for both deny and grant.
`a3ApiBelow33SkipsTheOnboardingPermissionScreenEntirely` ran and passed on api31.

**Coverage**: N/A — `testing.coverage.available: false` in `openspec/config.yaml`; not required by this
change's gates.

### Spec Compliance Matrix — `onboarding`

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Once-Per-Install Onboarding Gate | Fresh install shows onboarding first | `FirstRunGateViewModelTest.kt` (unit) + `CoreFlowE2ETest.launchFirstRunApp()` (a1/a2/a3, instrumented) | ✅ COMPLIANT |
| Once-Per-Install Onboarding Gate | Completed onboarding never reappears | `CoreFlowE2ETest.a1...` relaunches and asserts `onboarding_screen1_title` `.assertDoesNotExist()` | ✅ COMPLIANT |
| Two-Screen Flow, API-Conditional | API 37 shows both screens | `OnboardingViewModelTest` (page-list unit tests) + `CoreFlowE2ETest.a1/a2` (instrumented) | ✅ COMPLIANT |
| Two-Screen Flow, API-Conditional | API 31 shows only screen 1 | `OnboardingUiStateTest` (label-trap unit regression) + `CoreFlowE2ETest.a3` (instrumented) | ✅ COMPLIANT |
| Non-Blocking Permission Ask | Denial still completes onboarding | `OnboardingViewModelTest.\`finish writes...regardless...\`` + `CoreFlowE2ETest.a1` (real deny, then Finish tapped) | ✅ COMPLIANT |
| Non-Blocking Permission Ask | Grant completes onboarding | Same unit test + `CoreFlowE2ETest.a2` (real grant, then Finish tapped) | ✅ COMPLIANT |
| Permission Screen Never Offers A Prompt The System Will Silently Refuse | Blocked state on entry offers the settings deep link | `OnboardingPermissionAction.kt:71-87` (BLOCKED branch, `Intent(ACTION_APP_NOTIFICATION_SETTINGS)`, never the launcher) + `CoreFlowE2ETest.a1` (denies once, asserts `onboarding_permission_blocked_body`) | ✅ COMPLIANT |
| Completion Commits At Handoff, Never On A Content Outcome | Leaving the editor without saving does not reopen onboarding | `OnboardingRoute.kt:40-47` (ordering contract: `onFinished()` before `viewModel.finish()`) + `CoreFlowE2ETest.a1` (backs out of the seeded editor with no habit saved, relaunches, onboarding absent) | ✅ COMPLIANT |
| Finish Handoff Into Habit Creation With A Back Escape | Finishing onboarding opens the editor | `MainActivity.kt:178-198` (`FirstRunGate`, seeded `startRoute`) + `CoreFlowE2ETest.a1/a2/a3` (`action_back` content description present → editor was open) | ✅ COMPLIANT |
| Finish Handoff Into Habit Creation With A Back Escape | Back from the seeded editor entry reaches Today | `MainActivity.kt:124-134` (`leaveTo` = `Today` for `EditorOrigin.Onboarding`) + `CoreFlowE2ETest.a1/a2/a3` (tap `action_back`, assert `today_title`/banner state) | ✅ COMPLIANT |

**Compliance summary**: 10/10 onboarding scenarios compliant.

### Spec Compliance Matrix — `reminder-response` (MODIFIED delta)

| Scenario | Test | Result |
|---|---|---|
| API 33+ denial still allows in-app answering | Unaffected — existing `TodayViewModelTest` (unmodified, still 147/147 green) | ✅ COMPLIANT |
| API 31 device gets notifications with no prompt | `CoreFlowE2ETest.a3` — asserts `notificationManager.areNotificationsEnabled()` and no banner | ✅ COMPLIANT |
| Onboarding's ask writes the latch, and Today does not re-write it | Traced in source (see Latch Contract below) + `CoreFlowE2ETest.a2` (grants inside onboarding, Today shows no banner — proves Today did not re-ask) | ✅ COMPLIANT |
| The banner is the fallback where onboarding never wrote the latch | Unaffected — existing `TodayViewModel`/`TodayBanners` coverage (unmodified) | ✅ COMPLIANT |
| The banner never re-prompts once blocked | Unaffected — existing coverage (unmodified); `TodayBanners.kt:81-89` unchanged | ✅ COMPLIANT |

**Compliance summary**: 5/5 reminder-response scenarios compliant.

**Combined**: 15/15 scenarios compliant, 7/7 requirements compliant.

### Latch Contract — traced end to end (the load-bearing requirement)

`REQUESTED_NOTIFICATION_PERMISSION_KEY` (`ReminderSettingsStore.kt:74`) has exactly two writers, both
traced to source:

1. `OnboardingViewModel.recordRequestedNotificationPermission()` (`OnboardingViewModel.kt:99-104`) →
   `settingsStore.recordRequestedNotificationPermission()`. Wired as `OnboardingRoute.kt:53`'s
   `onPermissionRequested = viewModel::recordRequestedNotificationPermission`, itself the result
   callback of `OnboardingPermissionAction.kt:45-47`'s `rememberLauncherForActivityResult`. That
   callback fires only when `permissionLauncher.launch(...)` is invoked
   (`OnboardingPermissionAction.kt:62`, the `SHOULD_REQUEST` button only) — i.e. only after the system
   actually shows the dialog and returns.
2. `TodayViewModel.recordNotificationPermissionRequested()` (`TodayViewModel.kt:138-143`) →
   `reminderSettingsStore.recordRequestedNotificationPermission()`. Wired identically:
   `TodayScreen.kt:72`'s `onNotificationPermissionRequested = viewModel::recordNotificationPermissionRequested`
   is the result callback of `TodayBanners.kt:67-69`'s launcher, which fires only when
   `permissionLauncher.launch(...)` is invoked (`TodayBanners.kt:88`, the non-blocked branch only —
   the `BLOCKED` branch uses `Intent(ACTION_APP_NOTIFICATION_SETTINGS)` and never touches the launcher).

**Both writers are structurally incapable of writing without having shown the dialog**: the write is the
launcher's own result callback, and the launcher only produces a result after `.launch()` runs, which
only happens from the `SHOULD_REQUEST`-only UI branch in both surfaces. Once written, `decide()` moves
the decision away from `SHOULD_REQUEST` (to `BLOCKED` or `GRANTED`), which removes the launcher-bearing
button from both surfaces — so a second write from the same surface is unreachable without an
intervening state change the system itself gates. Confirmed **exactly once per dialog actually shown**.
`a2` also demonstrates cross-surface non-interference at runtime: the dialog is answered inside
onboarding, and Today (reached immediately after) shows no banner — proof Today's own write path never
fired for that decision.

### `BLOCKED` Path — both surfaces confirmed

- Onboarding screen 2 (`OnboardingPermissionAction.kt:71-87`): `Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(EXTRA_APP_PACKAGE, packageName)`, **never the launcher**. Matches `TodayBanners.kt:82-89` byte-for-byte per the KDoc's own claim, confirmed by direct comparison.
- Today's banner (`TodayBanners.kt:81-89`): same `Intent`, gated by `if (blocked)`, before ever reaching `permissionLauncher.launch(...)`.
- Instrumented proof: `CoreFlowE2ETest.a1` denies the real dialog once, asserts `onboarding_permission_blocked_body` renders (not a re-invoked prompt), finishes onboarding, and asserts Today renders `today_notification_permission_open_settings` — the deep-link banner, not a re-ask.
- The reference app's dead-button defect (`bebe-agua-android OnboardingScreen.kt:492`) does **not** appear here: both `BLOCKED` branches render a live button wired to a working `Intent`, never a no-op launcher call.

### API 31-32 Divergence — confirmed genuinely absent, labels correct

- `OnboardingViewModel.kt:59-65`: `includesPermissionPage` computed once from `decide(hasRequestedBefore = false) != NOT_APPLICABLE`; `pages` built via `buildList` conditionally adding `OnboardingPage.Notifications`. On API 31-32 `decide()`'s first branch (`sdkInt < 33 -> NOT_APPLICABLE`) makes this list `[Intro]` only — screen 2 is never constructed, not merely hidden.
- `OnboardingUiState.isLastPage` reads `pages.lastIndex`, never a literal `1` (`OnboardingViewModel.kt:33`) — confirmed by `OnboardingUiStateTest`'s explicit one-page-vs-two-page regression test.
- `OnboardingScaffold` (`OnboardingScreen.kt:53-59`) derives the "Continue"/"Finish" label from `isLastPage`, so on API 31-32 screen 1 correctly reads "Finish".
- `CoreFlowE2ETest.a3` (api31, instrumented) asserts both facts live: `onboarding_screen2_title.assertDoesNotExist()` **and** `onboarding_action_finish.assertIsDisplayed()` on screen 1.

### The Commit Point — write-once, never gated on content

- `OnboardingRoute.kt:40-47`: `onPrimaryAction` calls `onFinished()` (synchronous, sets `startRoute`) **before** `viewModel.finish()` (launches the suspend `DataStore.edit`) — the exact ordering design.md §9 requires, at the single call site that owns it.
- `OnboardingViewModel.finish()` (`OnboardingViewModel.kt:108-110`) takes no argument and is called unconditionally from the last-page primary action — never gated on whether `HabitEditorRoute` subsequently saves anything.
- Crash-window reasoning (design.md §9's table) checks out against source: nothing writes `onboarding_done = true` except `finish()`, and nothing calls `finish()` except the last-page primary action — there is no code path where the flag is `true` but the flow was never shown.
- `CoreFlowE2ETest.a1` proves the "leaving without saving" case end to end: the seeded editor is opened, the user backs out via `action_back` with no habit created, and a subsequent relaunch does not show onboarding again — `onboarding_done` was committed at handoff, not at save.

### Correctness (Static Evidence)
| Requirement/Item | Status | Notes |
|---|---|---|
| No `BackHandler` in the gate (D10 superseded, design §2.1) | ✅ Confirmed | `MainActivity.kt` — no `BackHandler` import or usage anywhere in the file. |
| `ConstanzaColors`/raw `.dp` discipline in `onboarding/` | ✅ Confirmed | `rg` over `app/src/main/kotlin/.../onboarding/` shows zero `ConstanzaColors` references and zero raw `.dp` literals; `Dimens.PagerDot` is the one new token, used correctly. |
| `strings.xml` onboarding copy | ✅ Confirmed | All 10 keys present (`onboarding_screen1_title` … `onboarding_action_finish`), matching design §6/§12's named categories. |
| `ReminderSettingsDataStoreEntryPoint` placement | ✅ Confirmed, and correctly documented as a deviation | Lives in `core/di/DataStoreModule.kt` (`main`, not `androidTest` as design's literal snippet said) — `internal` visibility, single consumer (`CoreFlowTestFixture`), root-caused and recorded in both the file's own KDoc and Unit B's apply-progress. See assessment below. |
| `habit-editor-has-no-cancel-affordance` | ✅ Still `open` | `openspec/config.yaml:102-129` — not touched by this change, exactly as design §2.1 required. |
| `notification-permission-blocked-after-one-ask` | ✅ Genuinely deferred, not a gap dressed up as one | `openspec/config.yaml:290-319` — `status: open`, real owner condition (a future change already touching `NotificationPermission`'s Activity-free contract), reasoned fail-safe direction recorded (under-prompts, never over-prompts; `BLOCKED` always offers a working settings deep link). |

### Coherence (Design)
| Decision | Followed? | Notes |
|---|---|---|
| A1 — retained `StateFlow<Boolean?>` on `FirstRunGateViewModel`, not `collectAsState(initial=null)` over a cold flow | ✅ Yes | `MainActivity.kt:160-168`, `SharingStarted.Eagerly`. |
| A2 — two separate state holders (`FirstRunGateViewModel`, `OnboardingViewModel`) | ✅ Yes | Confirmed structurally separate; the gate's `when` never reads `OnboardingViewModel` state. |
| A3 — `EditorOrigin` carried in the route, no gate-level `BackHandler` | ✅ Yes | `MainActivity.kt:86-134`. |
| A4 — page list computed once from API applicability, live decision drives only content | ✅ Yes | `OnboardingViewModel.kt:59-76`. |
| A5 — seeding through the app's own singleton `DataStore` via `@EntryPoint` | ✅ Yes, with a corrected placement | See Correctness table above. |
| A6 — no migration; every install onboards once | ✅ Yes | `ReminderSettingsStore.kt:39-40`, `onboardingDone` absent → `false`. |
| A7 — no new shared component, one new `Dimens` token | ✅ Yes | `Dimens.PagerDot` only. |

### Issues Found

**CRITICAL**: None.

**WARNING**:
1. `TodayComposeTest`/`TodayAdaptiveComposeTest`'s documented pre-existing async DB-teardown race
   (`TodayComposeTest.kt:70-77`'s own KDoc) is a real, if intermittent, threat to the device-free
   matrix's trustworthiness as a gate: when it fires, it can crash the shared instrumentation process
   and misattribute a failure to whichever unrelated test is running at the time — as it did once during
   Unit B's own apply work (documented in apply-progress.md, run 2 of 3). It did **not** fire in this
   verification's own fresh matrix run (both legs clean, zero failures). Not introduced by this change,
   already root-caused and documented in-repo, and out of this change's scope to fix — but it means a
   green matrix run is probabilistically green, not deterministically so, until that race is fixed.
   Recommend a follow-up carried-forward item if one does not already exist for it (none was found in
   `openspec/config.yaml`).

**SUGGESTION**:
1. `ReminderSettingsDataStoreEntryPoint`'s placement in `core/di/DataStoreModule.kt` (production `main`
   source) for an androidTest-only consumer is a narrow, well-justified, and correctly `internal`-scoped
   concession — it is the direct consequence of this app instrumenting the real `ConstanzaApplication`
   (no `HiltAndroidTest`), not a design shortcut, and it is documented in the file's own KDoc with the
   exact `ClassCastException` evidence that forced the move. Acceptable as shipped. If a second
   test-only `@EntryPoint` is ever needed, consider a single `androidTest`-facing `@Module` file in
   `main` to keep this pattern from spreading interface-by-interface through `DataStoreModule.kt`.
2. Unit A's changed-line count landed at 803/800 (apply-progress.md), 3 lines over its own forecast
   budget. Immaterial and already self-reported with an honest accounting; no action needed.

### Verdict
**PASS**

Every onboarding requirement and reminder-response scenario maps to real, working implementation and a
real, passing test — 15/15 scenarios compliant, re-derived from source rather than taken from the
apply-progress self-reports. The latch contract (this change's load-bearing requirement) holds
structurally in both writers. The `BLOCKED` deep-link path is present and correct in both surfaces, with
no dead-button defect. The API 31-32 divergence is genuinely absent (not merely hidden) with correct
labels, proven at both unit and instrumented layers. The completion commit point is write-once and never
gated on habit creation, confirmed in source and by the `a1` back-without-saving instrumented scenario.
Both carried-forward items (`habit-editor-has-no-cancel-affordance`, `notification-permission-blocked-after-one-ask`)
are genuinely open/deferred, not silently closed or dressed-up gaps — no twelfth instance of this
repository's promised-in-KDoc-never-in-spec failure mode was found. The one WARNING (a pre-existing,
already-documented test-infra flake) does not block PASS; it is a known probabilistic risk to the gate's
determinism, not a defect in this change.
