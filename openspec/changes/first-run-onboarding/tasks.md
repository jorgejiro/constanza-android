# Tasks: First-Run Onboarding

Change: `first-run-onboarding` · Phase: `sdd-tasks` · Date: 2026-09-02
Inputs: `proposal.md`, `design.md`, `specs/{onboarding,reminder-response}/spec.md`, `openspec/config.yaml`

> **BLOCKING EXTERNAL PRECONDITION — read before anything else.** This change depends on branch
> `fix/habit-editor-cancel` (in flight, branched off `main`), which gives `HabitEditorTopBar` a
> navigation icon, gives `HabitEditorRoute` an `onBack: () -> Unit` parameter, and installs the
> editor's own `BackHandler` + confirm-before-discard dialog. **`sdd-apply` MUST NOT start until
> that branch has merged.** No task below duplicates that work, and no task closes the
> carried-forward item `habit-editor-has-no-cancel-affordance` — that item and the unsaved-edits
> decision belong to the editor change; onboarding only supplies a destination (design §2.1).

## Review Workload Forecast

Docs (`proposal.md` 279, `specs/onboarding/spec.md` 96, `specs/reminder-response/spec.md` 51,
`design.md` 647 = 1073 lines) are **already committed** per this phase's own "Read first" input —
they land in their own commits, not in the implementation PR this forecast governs. `tasks.md`
(this file, ~230 lines) follows the same already-committed-doc convention and is excluded from the
code-PR estimate below, consistent with `habit-tracking-mvp`'s own PR-1-is-docs precedent.

This is an independent re-forecast from the actual task list, not a repeat of `proposal.md`'s
770–1055 (which bundled docs and code into one number). Session review budget is **800** lines
(`review_budget_lines`), reusing the house "400-line" guard label literally per convention.

| Component | Tasks | Est. lines |
|---|---|---|
| `ReminderSettingsStore` + `Dimens` + `strings.xml` | 1.2–1.4 | 60–90 |
| `MainActivity.kt` (gate + route wiring) | 2.1–2.4 | 45–60 |
| Onboarding package (4 new files) | 3.1–3.4 | 230–280 |
| Unit tests (gate + ViewModel) | 4.1–4.4 | 90–130 |
| `CoreFlowTestFixture` (seeding + reset) | 5.1–5.4 | 50–70 |
| `CoreFlowE2ETest` (rename + rewrite + pre-seed) | 6.2–6.5 | 110–150 |
| Spec correction + carried-forward item | 1.1, 7.1 | 10–20 |
| **Total (code + tests)** | | **595–800** |

Design §2's note that dropping the gate `BackHandler` while adding `EditorOrigin` is line-neutral is
folded into the `MainActivity.kt` row already. The upper bound lands exactly at budget, and this
codebase's own measured lesson (`habit-tracking-mvp`'s 6a/6b Compose units ran 1.9×/1.1× over their
own estimates) means treat 800 as a floor for the Compose-heavy rows (onboarding package, unit tests),
not a ceiling.

**Verdict: Medium risk, not comfortably under budget.** Recommending the precautionary split the
proposal itself named, rather than discovering an overrun mid-implementation:

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| A | Flag + gate + onboarding package + unit tests (1.2–4.4) | PR 1 | `./gradlew :app:testDebugUnitTest` | N/A — no instrumented consumer yet; onboarding is unreachable without B's seeding, but is reachable via a manual fresh install | Revert restores `setContent { ConstanzaApp() }`; one orphaned additive DataStore key |
| B | Instrumented rework + seeding fixture (5.1–6.6) | PR 2, depends on A | `./gradlew :app:testDebugUnitTest` (fixture compiles against A's keys) | `./gradlew :app:emulatorMatrixGroupDebugAndroidTest`, both legs, nothing attached | Revert restores the pre-change `CoreFlowE2ETest`/`CoreFlowTestFixture`; no production code touched |

```text
Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: Medium
```

Chain strategy is `pending` because `delivery_strategy` is cached `single-pr`, which per the
review-workload guard means the orchestrator must require an explicit `size:exception` before
`sdd-apply` if it chooses to keep this as one PR, or ask the user to pick `stacked-to-main` vs.
`feature-branch-chain` for the A/B split above. Do not default silently either way.

**Threat matrix**: N/A (design §14) — no shell command, subprocess, VCS automation, or routing
surface in this change. No RED-test threat-matrix tasks are required.

## Phase 0: Blocking External Precondition

- [ ] 0.1 Confirm `fix/habit-editor-cancel` has merged into `main` and `HabitEditorRoute`
      (`habit/HabitEditorScreen.kt` (read-only)) accepts `onBack: () -> Unit`. `sdd-apply` MUST NOT
      begin against a two-parameter `HabitEditorRoute` (design §2.1, §15).

## Phase 1: Spec Correction & Data Foundation

- [ ] 1.1 Correct `specs/onboarding/spec.md`'s "Permission Screen Never Offers A Prompt The System
      Will Silently Refuse" requirement text: replace "requires two denials ... not reachable on the
      device-free instrumented matrix ... verified by a unit test instead" with the corrected
      reachability — `BLOCKED` is one recorded ask plus no grant, reachable in a single instrumented
      step (design §2.2).
- [ ] 1.2 Modify `reminding/ReminderSettingsStore.kt`: add `ONBOARDING_DONE_KEY`,
      `onboardingDone: Flow<Boolean>`, suspend `setOnboardingDone()`; change the companion object
      from `private` to `internal` (design §8.1, A5; onboarding spec "Once-Per-Install Onboarding
      Gate").
- [ ] 1.3 Modify `core/ui/theme/Dimens.kt`: add `PagerDot = 8.dp` (design §12, A7).
- [ ] 1.4 Modify `res/values/strings.xml`: add onboarding copy for both screens, the
      `GRANTED`/`BLOCKED` permission-screen variants, and Continue/Finish labels (design §6, §12).

## Phase 2: Gate & Handoff (`MainActivity.kt`) — depends on Phase 0

- [ ] 2.1 Modify `core/ui/MainActivity.kt`: add `ConstanzaRoute.EditorOrigin` enum and a defaulted
      `origin` field on `HabitEditor` (design §5.1, A3).
- [ ] 2.2 Modify `core/ui/MainActivity.kt`: add the `startRoute` parameter to `ConstanzaApp` and the
      `leaveTo` branch wiring both `onDone` and `onBack` per `origin` (design §5.1, §5.2, §5.3;
      onboarding spec "Finish Handoff Into Habit Creation With A Back Escape").
- [ ] 2.3 Modify `core/ui/MainActivity.kt`: add `FirstRunGateViewModel`
      (`StateFlow<Boolean?>` from `ReminderSettingsStore.onboardingDone` via
      `stateIn(..., Eagerly, null)`) and the `FirstRunGate` composable wrapping `setContent`'s
      tri-state `when`, with `rememberSaveable` `startRoute` seeded inside `onFinished` (design §4.1,
      A1, A2).
- [ ] 2.4 Add KDoc to `FirstRunGateViewModel` documenting it as the app's second top-level state
      holder and why the blank `null` branch renders nothing (design §4.2).

## Phase 3: Onboarding Package (production) — depends on Phase 1

- [ ] 3.1 Create `onboarding/OnboardingViewModel.kt`: `pages` computed once from
      `includesPermissionPage`, `index`, live `NotificationPermission` decision, `next()`,
      `recordRequestedNotificationPermission()`, `finish()` (design §7, §9, A4; onboarding spec
      "Two-Screen Flow, API-Conditional").
- [ ] 3.2 Create `onboarding/OnboardingPermissionAction.kt`: the four-state control with its own
      `LocalContext`, `RequestPermission()` launcher, and the `ACTION_APP_NOTIFICATION_SETTINGS`
      intent, mirroring `TodayBanners.kt:82-89` (design §6; onboarding spec "Permission Screen Never
      Offers A Prompt The System Will Silently Refuse").
- [ ] 3.3 Create `onboarding/OnboardingScreen.kt`: `OnboardingScaffold` (bottom-slot primary action,
      conditional progress dots via `Dimens.PagerDot`) and the two page bodies (design §6, §7, §12).
- [ ] 3.4 Create `onboarding/OnboardingRoute.kt`: `hiltViewModel()` container, `ON_RESUME` re-read
      via `DisposableEffect`+`LifecycleEventObserver` (mirrors `TodayScreen.kt:55-61`), `onFinished`
      hoisted, and the `onPrimaryAction` ordering contract — `onFinished()` before
      `viewModel.finish()` (design §6, §9).

## Phase 4: Unit Tests — depends on Phases 2, 3

- [ ] 4.1 `FirstRunGateViewModelTest`: tri-state `null` → `false`/`true`, Turbine over the
      `StateFlow`, fake `ReminderSettingsStore` backed by `MutableStateFlow`. Verify:
      `./gradlew :app:testDebugUnitTest` (design §10 row 1; onboarding spec "Once-Per-Install
      Onboarding Gate").
- [ ] 4.2 `OnboardingViewModelTest` — page list: 2 pages when applicable, 1 when `NOT_APPLICABLE`;
      MockK on `NotificationPermission` is mandatory (`SDK_INT` is `0` under
      `isReturnDefaultValues`). Verify: `./gradlew :app:testDebugUnitTest` (onboarding spec
      "Two-Screen Flow, API-Conditional").
- [ ] 4.3 `OnboardingUiStateTest` — `isLastPage`/`showsProgress` at both page counts, the API-31
      label-trap regression guard. Verify: `./gradlew :app:testDebugUnitTest` (design §7; onboarding
      spec scenario "API 31 shows only screen 1").
- [ ] 4.4 `OnboardingViewModelTest` — all four permission states map to the right control, and
      `finish()` writes the flag through a fake store. Verify: `./gradlew :app:testDebugUnitTest`
      (onboarding spec "Non-Blocking Permission Ask", "Completion Commits At Handoff, Never On A
      Content Outcome").

## Phase 5: Test Seeding Infrastructure — depends on Phase 1

- [ ] 5.1 Modify `e2e/CoreFlowTestFixture.kt`: add the `@EntryPoint`
      `ReminderSettingsDataStoreEntryPoint` (`SingletonComponent`) and its
      `EntryPointAccessors.fromApplication` accessor (design §8.1, A5).
- [ ] 5.2 Modify `e2e/CoreFlowTestFixture.kt`: add `seedOnboardingDone()` and
      `seedNotificationPermissionUnasked()` suspend helpers writing through the shared `DataStore`
      using the now-`internal` keys (design §8.1, §8.3).
- [ ] 5.3 Modify `e2e/CoreFlowTestFixture.kt`'s `reset()`: add
      `settings.edit { it[ReminderSettingsStore.ONBOARDING_DONE_KEY] = false }`; do NOT touch
      `requested_notification_permission` (design §8.2).
- [ ] 5.4 Modify `e2e/CoreFlowE2ETest.kt`: split `launchApp()` into `launchFirstRunApp()` (awaits
      onboarding's first page) and `launchOnboardedApp()` (seeds the flag, awaits `today_title`) —
      no default (design §8.4).

## Phase 6: Instrumented Rework & Measurement — depends on Phases 3, 5

- [ ] 6.1 **Measure, do not reason further.** Run
      `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` with the a1(deny)/a2(allow) sequence as
      designed (§8.3). Observe whether the api37 image re-shows the `POST_NOTIFICATIONS` dialog for
      a2 after a1's single denial. If it does not, apply the documented fallback before finalizing
      6.2/6.3: keep a1 as the real-dialog scenario, and reduce a2 to seeding the latch unasked on a
      fresh method with no prior denial (design §8.3, §15).
- [ ] 6.2 Rename/rewrite `a1DenyingTheOnboardingPromptLeavesTodayOfferingNotificationSettings`
      (api37): walk onboarding, deny the real dialog on screen 2, assert Today renders
      `today_notification_permission_open_settings`, then relaunch and assert onboarding does not
      reappear. This is the corrected `BLOCKED`-reachability scenario, proven in a single
      instrumented step (design §2.2, §10; onboarding spec "Permission Screen Never Offers A
      Prompt...", "Completion Commits At Handoff...", "Finish Handoff Into Habit Creation With A
      Back Escape"; reminder-response delta "Onboarding's ask writes the latch...").
- [ ] 6.3 Rename/rewrite `a2AllowingTheOnboardingPromptLeavesTodayWithNoNotificationBanner` (api37):
      seed the latch unasked (5.2), walk onboarding, grant the real dialog, assert Today shows no
      banner (onboarding spec "Grant completes onboarding"; reminder-response delta "The banner is
      the fallback...").
- [ ] 6.4 Add `a3ApiBelow33SkipsTheOnboardingPermissionScreenEntirely` (api31): walk onboarding,
      assert screen 2 never renders, screen 1's primary action reads "Finish", Today shows no banner
      (design §7; onboarding spec "Two-Screen Flow, API-Conditional" scenario "API 31 shows only
      screen 1"; reminder-response delta "API 31 device gets notifications with no prompt").
- [ ] 6.5 Update `creatingAHabitThroughTheUi...` and `removingAHabitThroughTheUi...` to call
      `launchOnboardedApp()` with `onboarding_done = true` pre-seeded (design §8.4 — regression,
      unaffected by this change's own scope).
- [ ] 6.6 Verify: `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` green on both `api31`/`api37`
      legs, nothing attached (`testing.instrumented.device_free_matrix`).

## Phase 7: Carried-Forward Items & Final Verification

- [ ] 7.1 Add a new carried-forward item `notification-permission-blocked-after-one-ask` to
      `openspec/config.yaml`'s `carried_forward_open_items.items`, owner-conditioned on any future
      change already touching `NotificationPermission`'s Activity-free contract (design §2.3). Do
      NOT touch or close `habit-editor-has-no-cancel-affordance` — that item belongs to the editor
      change (design §2.1).
- [ ] 7.2 Verify: `./gradlew check` green (unit tests, lint, detekt) and
      `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` green — the change's two success gates
      (proposal Success Criteria).

## Promise Coverage (every design/spec obligation → owning task)

| Obligation | Source | Task(s) / layer |
|---|---|---|
| Fresh install shows onboarding first | onboarding spec | 4.1 (unit), 6.2/6.3/6.4 (instrumented) |
| Completed onboarding never reappears | onboarding spec | 4.1 (unit), 6.2 (instrumented relaunch), 6.5 (pre-seeded regression) |
| API 37 shows both screens | onboarding spec | 6.2, 6.3 (instrumented) |
| API 31 shows only screen 1 | onboarding spec | 4.3 (unit), 6.4 (instrumented) |
| Denial still completes onboarding | onboarding spec | 4.4 (unit), 6.2 (instrumented) |
| Grant completes onboarding | onboarding spec | 4.4 (unit), 6.3 (instrumented) |
| Blocked state on entry offers the settings deep link | onboarding spec | 3.2 (production), 4.4 (unit), 6.2 (instrumented — corrected reachability, design §2.2) |
| Leaving the editor without saving does not reopen onboarding | onboarding spec | 3.1/3.4 (write ordering, design §9), 4.1/4.4 (unit), 6.2 (instrumented relaunch) |
| Finishing onboarding opens the editor | onboarding spec | 2.2, 3.1 (production), 6.2/6.3/6.4 (instrumented) |
| Back from the seeded editor entry reaches Today | onboarding spec | 2.2 (production; relies on 0.1's editor `BackHandler`), 6.2/6.3/6.4 (instrumented) |
| API 33+ denial still allows in-app answering | reminder-response delta | unaffected — existing `TodayViewModelTest` coverage (D1, unchanged) |
| API 31 device gets notifications with no prompt | reminder-response delta | unaffected coverage; 6.4 (instrumented) confirms no banner |
| Onboarding's ask writes the latch, Today does not re-write it | reminder-response delta | 3.1 (production call site), 6.3 (instrumented) |
| Banner is the fallback where onboarding never wrote the latch | reminder-response delta | unaffected — existing `TodayViewModel`/`TodayBanners` coverage (unchanged) |
| Banner never re-prompts once blocked | reminder-response delta | unaffected — existing coverage (unchanged) |
| D10 superseded — no gate `BackHandler`, `onBack` is a destination only | design §2.1 | 0.1 (precondition), 2.1, 2.2 |
| `BLOCKED` reachability corrected | design §2.2 | 1.1 (spec text), 6.1 (measurement), 6.2 (instrumented) |
| Accepted one-ask conservatism, recorded not fixed | design §2.3 | 7.1 (carried-forward item) |
| A6 — no migration; every pre-existing install onboards once | design §11 | 1.2 (flag absent → false) |
| Design token / colour-role discipline (no raw `.dp`, no `ConstanzaColors.Accent` in content) | design §12 | 1.3, 1.4, 3.2, 3.3 |
| Unverified §8.3 dialog-reshow assumption | design §15 | 6.1 (measured, not reasoned about further) |

## Verification Command Reference

| Layer | Command | Applies to |
|---|---|---|
| JVM unit | `./gradlew :app:testDebugUnitTest` | Phases 2–5 (regression: existing suite) |
| Instrumented, device-free matrix | `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` | Phase 6 (api31 + api37, nothing attached) |
| Aggregate | `./gradlew check` | Phase 7 (final gate — unit tests, lint, detekt; runs no instrumented test) |
| N/A | — | `./gradlew check` alone never proves Phase 6; both commands are required for the proposal's Success Criteria |

## Note on document size

This document exceeds the default 530-word task-artifact budget deliberately, matching the house
precedent set by `habit-tracking-mvp` and `warm-dark-design-system`: 2 specs, a corrected reachability
claim, a hard external dependency, and full requirement traceability cannot fit that budget without
losing the Review Workload Forecast and the Promise Coverage table the user asked for by name.
Content stays checklist-only, one to three lines per task, with no prose padding beyond the design
citations needed to keep each task self-contained.
