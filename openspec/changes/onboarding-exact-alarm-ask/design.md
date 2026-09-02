# Design: Ask For Exact Alarms During Onboarding

## Technical Approach

No new architecture. Screen 2 gains a second row; the page it lives on gains a second reason to
exist. Everything else — the `Scaffold` bottom-slot primary action, the `ON_RESUME` re-read, the
presentational page composables, the injectable-scheduler test seam — is reused verbatim.

Three concrete moves:

1. `OnboardingViewModel` gains a third collaborator (`AlarmScheduler`) and a third state source
   (`canScheduleExactAlarms: Boolean`). The page list widens from one applicability test to two.
2. `OnboardingPage.Notifications` is renamed `OnboardingPage.Permissions`. The page is no longer
   about notifications; leaving the name would make the API 31-32 revoked leg read as a
   notifications screen that shows no notification row.
3. `OnboardingPermissionAction.kt` gains a sibling control that owns its own `LocalContext` and
   `Intent`, exactly as `TodayBanners.ExactAlarmBanner` does.

## Architecture Decisions

### Decision 1 — Page existence stays computed once, at construction, from two applicability tests

**Choice**: `includesPermissionPage = notificationApplicable || !alarmScheduler.canScheduleExactAlarms()`,
evaluated in the initializer. `pages` remains the single list that `isLastPage`, `showsProgress` and
the primary-action label all read.

**Alternatives considered**: deriving `pages` from the live state flows, so the page disappears the
moment both permissions are satisfied.

**Rationale**: the previous change kept the list construction-time so a mid-flow grant could not
delete the page the user is standing on. That argument was defensive there. Here it is load-bearing,
because this change *creates* the scenario: on API 31-32 with exact alarms revoked, screen 2 is a
one-row page. A live-derived list would delete it the instant the user returns from settings having
granted — while `index` is `1` and `pages.size` has just become `1`. `pages[index]` throws. The live
alternative costs an index clamp, and a clamp is a silent jump under the user's finger.

The API-31 outcome the published spec asserts as a constant survives as a *consequence*: both tests
answer "not applicable", the list is `[Intro]`, `showsProgress` is false and index 0 is already the
last page. No `Build.VERSION` literal is added anywhere.

### Decision 2 — Row visibility is live; page existence is not

**Choice**: each row reads the live state. Granted renders a confirmation line, denied renders
explanation + action. The page can therefore render two confirmation lines and no button.

**Alternatives considered**: hiding a row that was already granted at construction.

**Rationale**: hiding requires remembering the value at construction — a second, differently-timed
snapshot of the same fact, for the sole purpose of suppressing one line. The proposal rejected a
persisted latch; a construction-time one is the same idea with a shorter life. And the confirmation
line is what makes the settings round-trip legible: the user taps, leaves, grants, returns, and the
row visibly changes. Without it, a successful grant looks identical to a failed one.

### Decision 3 — `Boolean`, not a new decision enum

**Choice**: `OnboardingUiState.canScheduleExactAlarms: Boolean`.

**Alternatives considered**: an `ExactAlarmDecision` enum mirroring `NotificationPermissionDecision`.

**Rationale**: `NotificationPermissionDecision` exists because four states are reachable through a
non-obvious table (`sdkInt`, the grant, and the "we have asked" flag). Exact alarms have no table:
`AlarmManager.canScheduleExactAlarms()` is the whole answer, `minSdk = 31` removes the applicability
axis, and the offer is repeatable so no latch discriminates a fifth state. An enum here would need a
`decide()` that only re-wraps a boolean, and would invite a future reader to look for the table.

Sibling parity is a decision about **weight on screen**, not about type symmetry.

### Decision 4 — One refresh method, not two

**Choice**: `refreshPermission()` becomes `refresh()`, reading both sources in one coroutine.
`OnboardingRoute`'s existing `ON_RESUME` `DisposableEffect` is otherwise untouched.

**Alternatives considered**: adding `refreshExactAlarms()` beside the existing method.

**Rationale**: the same property that keeps the label and the progress dots agreeing. Two methods
means a future lifecycle call site can refresh one and forget the other, and the symptom — screen 2
still claiming exact alarms are off after the user granted them — is precisely the defect the
`ON_RESUME` idiom was introduced to prevent. One method cannot be half-called.

### Decision 5 — Filled `Button`, matching the notification row exactly

**Choice**: the exact-alarm action is a `Button`, the same composable, padding token and placement
the `SHOULD_REQUEST`/`BLOCKED` branches already use.

**Alternatives considered**: `OutlinedButton`, to read as the lighter of the two asks.

**Rationale**: two reasons converge. Visually, a lighter control encodes "less important", which
contradicts the settled sibling-parity decision. Mechanically, `OutlinedButton` is in
`ControlStrokeCallSiteTest.GUARDED_CONTROLS`, so it would have to carry
`border = ConstanzaControlDefaults.outlinedButtonBorder(enabled)` or fail the build — and the
stroke override exists to rescue controls Material routes through the wrong role, not to decorate a
control that had no reason to be outlined. A filled `Button` sidesteps the obligation honestly.

### Decision 6 — No launcher, no callback on the exact-alarm row

**Choice**: the control's only outbound edge is `context.startActivity(...)`. It takes no
`onRequested` callback and registers no `ActivityResultLauncher`.

**Alternatives considered**: `rememberLauncherForActivityResult(StartActivityForResult())` for
symmetry with the notification row.

**Rationale**: the notification row's launcher exists to fire `recordRequestedNotificationPermission()`
— a persisted flag this change adds no equivalent of. `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` returns
no meaningful result, and `ON_RESUME` already covers the return. A launcher would be a second,
racing refresh path for one fact.

### Decision 7 — Retire "Fix" by changing the string value, not the key

**Choice**: `today_exact_alarm_banner_action` keeps its key and becomes `Open settings`, matching
`onboarding_permission_blocked_action`. `TodayBanners.kt` is not edited.

**Alternatives considered**: renaming the key to something onboarding-neutral.

**Rationale**: nothing asserts the literal `Fix` anywhere in the suite, so the value change is the
whole fix. Renaming the key would touch a production file for zero behavioural reason and put
`ExactAlarmBanner` into a diff the proposal explicitly scoped as behaviour-untouched.

## The exact-alarm row's states

| Live `canScheduleExactAlarms` | Renders | Action |
|---|---|---|
| `true` | one confirmation line (`onSurfaceVariant`) | none |
| `false` | explanation body + `Button` | `startActivity(ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName")` |

There is no `NOT_APPLICABLE` state: `minSdk = 31` means the permission always exists as a concept,
which is why `AlarmScheduler` contains no `Build.VERSION` branch and why this change adds none.
Refresh is `ON_RESUME` only — the same idiom, the same single call site.

## Data Flow

```
    ON_RESUME ──→ OnboardingRoute ──→ viewModel.refresh()
                                          │
                       ┌──────────────────┴──────────────────┐
                       ▼                                     ▼
        NotificationPermission.decide()        AlarmScheduler.canScheduleExactAlarms()
                       │                                     │
                       └────────→ combine(index, …, …) ──────┘
                                          │
                                          ▼
                                 OnboardingUiState
                                  ├─ pages   (fixed at construction)
                                  └─ 2 live permission facts → row content only
```

## Interfaces

```kotlin
enum class OnboardingPage { Intro, Permissions }

data class OnboardingUiState(
    val pages: List<OnboardingPage>,
    val index: Int,
    val permission: NotificationPermissionDecision,
    val canScheduleExactAlarms: Boolean,
)
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `onboarding/OnboardingViewModel.kt` | Modify | Inject `AlarmScheduler`; page rename; OR-ed page test; third state flow; `refresh()` |
| `onboarding/OnboardingScreen.kt` | Modify | `OnboardingNotificationsPage` → `OnboardingPermissionsPage`, hosting both rows split by `Spacing.md` |
| `onboarding/OnboardingPermissionAction.kt` | Modify | Add `OnboardingExactAlarmAction(canSchedule)` with its own `LocalContext` + `Intent` |
| `onboarding/OnboardingRoute.kt` | Modify | `viewModel::refresh`; pass the second fact into the renamed page |
| `res/values/strings.xml` | Modify | 3 new onboarding keys; `today_exact_alarm_banner_action` value `Fix` → `Open settings` |
| `test/.../OnboardingViewModelTest.kt` | Modify | `alarmScheduler` param on `buildViewModel`, defaulted to granted |
| `test/.../OnboardingUiStateTest.kt` | Modify | Enum rename; new state field |
| `androidTest/.../onboarding/OnboardingComposeTest.kt` | Create | First instrumented onboarding test |

`TodayBanners.kt` and `AlarmScheduler.kt` are **not** modified.

## Testing Strategy

| Layer | What to test | Approach |
|-------|--------------|----------|
| Unit (JVM) | The three legs of the applicability table; `refresh()` updates both facts; existing four-state coverage survives the rename | `OnboardingViewModelTest`, mocked `NotificationPermission` + mocked `AlarmScheduler` |
| Unit (JVM) | `isLastPage` / `showsProgress` still read `pages.lastIndex` | `OnboardingUiStateTest` |
| Instrumented | Both rows render; each row's two/four states; the primary action stays present and enabled in every one of them; no auto-launch | New `OnboardingComposeTest`, presentational composables over hand-built `OnboardingUiState` |

**`buildViewModel` must default `alarmScheduler` to granted.** A `mockk(relaxed = true)` answers a
`Boolean` with `false`, which would silently add the exact-alarm row to every existing onboarding
test — the identical trap the defaulted `alarmScheduler` parameter in `TodayViewModelTest.kt:333-334`
(`buildViewModel`) exists to avoid on the Today side. Stub it explicitly; do not inherit the relaxed
default.

**The instrumented test renders the presentational composables, not the ViewModel.** Two reasons.
First, `OnboardingViewModel` is in `ViewModelTeardownCallSiteTest.GUARDED_VIEW_MODELS`, so a bare
constructor in `androidTest` fails the build, and the only exemption is `HabitRepositoryTestFixture.register`
— dragging an in-memory Room database into a test about two text rows, to satisfy a guard about
database teardown, for a ViewModel that touches no database. Second, driving `OnboardingUiState`
directly is the strongest form of the proposal's device-free requirement: the assertions read the
state we passed, never the emulator's grant defaults, so both matrix legs run identical assertions
with nothing attached. The API 31-32 "screen 2 does not exist" claim is a ViewModel fact and is
proven in the JVM layer, not on an emulator whose real grant state we would otherwise be asserting.

**Non-auto-launch** is asserted with `UiDevice.currentPackageName` (already a dependency —
`e2e/SystemPermissionDialog.kt`): compose screen 2 in the denied state, idle, assert the app is
still foreground. `espresso-intents` is deliberately not added for one assertion.

## Process death while in system settings

**This change makes the existing limit materially more likely to bite, and the recommendation still
stands: do not fix it here.**

Before this change, the only mid-flow exit was the notification `BLOCKED` branch, which requires a
prior denial — not the fresh-install path. This change puts a deliberate app exit on the *common*
path: every API 33+ fresh install meets the denied exact-alarm row. Exposure moves from rare to
routine.

Not fixing it here, argued rather than assumed:

- The failure is one extra `Continue` tap. No data is lost, and the grant itself lives in the
  system, so the user's tap still worked.
- The soft landing is already built: because state is re-read and the page list is recomputed at
  construction, a restarted onboarding shows the *granted* confirmation line. The user sees their
  action took effect rather than being asked the same question twice.
- A correct fix is not "add `SavedStateHandle` to `index`". A restored index must be reconciled
  against a page list re-derived from permissions that changed while the process was dead — exactly
  the out-of-bounds case Decision 1 avoids in the live path. That is a state-machine change with its
  own spec requirement, and folding it in here would put the flow's persistence model inside a change
  whose entire claim is that it adds no persistence.

Recommend a follow-up change (`onboarding-index-survives-process-death`) carrying both the
`SavedStateHandle` and the index/page reconciliation. Record the widened exposure in this change's
verify report.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. The one `Intent` added is a system settings deep link already used
verbatim by `ExactAlarmBanner`.

## Migration / Rollout

No migration required. No persisted field, no DataStore key, no Room migration, no manifest change.

## Open Questions

- [ ] Play Console policy on offering `SCHEDULE_EXACT_ALARM` during onboarding (proposal risk 1,
      unverifiable in an offline phase). Does not block implementation: the manifest is unchanged.
