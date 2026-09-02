# Proposal: First-Run Onboarding

A new user's first launch explains what Constanza is, asks for the notification permission at the
one moment it makes sense, and hands off into creating their first habit. The Today banner stops
being the primary ask and becomes the safety net.

> Size note: this proposal exceeds the 450-word phase default deliberately. Nine decisions were
> settled before this phase and must be recorded with their reasoning, or `sdd-spec` and
> `sdd-design` will re-litigate them.

## Intent

**The problem, in the user's terms.** A clean install drops the user straight onto an empty Today
screen showing one sentence — "Nothing due today." — with no explanation of what the app is and no
visible way to add anything. If they are on API 33+, the notification permission is still ungranted,
so the reminders that are the entire point of the app are silently inert until they happen to notice
a banner.

**Why now.** `post-notifications-never-requested` was closed by adding a Today banner, which works
but asks at the wrong moment: a banner on an empty screen has no context to justify itself. Asking
inside a flow that has just explained why reminders matter converts better and is the standard
pattern. This change is what the banner was always meant to precede.

**Success.** A new user reaches their first habit having understood the app and having made an
informed, non-blocking decision about notifications. Both API boundary legs are provable on the
device-free matrix.

## Scope

### In Scope

- A tri-state onboarding gate wrapping `ConstanzaApp()` in `MainActivity.setContent`.
- An `onboarding` package: two screens, one ViewModel, an onboarding-local page scaffold.
- A new `onboarding_done` boolean key in the existing `reminder_settings` DataStore.
- A second call site for the existing `recordRequestedNotificationPermission()` latch write.
- Finish handoff into habit creation, with a scoped back escape to Today.
- `CoreFlowE2ETest` rework plus net-new androidTest DataStore seeding infrastructure.
- One new capability spec (`onboarding`) and one delta (`reminder-response`).

### Out of Scope

| Excluded | Why |
|---|---|
| Exact-alarm / battery-optimisation prompts | The archived design (`archive/2026-09-01-habit-tracking-mvp/design.md:1011-1017`) rejected unsolicited first-launch system-settings prompts as poor UX and Play-policy discouraged. Unchanged. Stays on `ExactAlarmBanner`. |
| Fixing Today's empty state | Carried-forward item `today-has-no-add-habit-affordance` (`openspec/config.yaml:102`). This change routes around it, does not depend on it, does not close it. |
| A cancel affordance in the habit editor generally | New carried-forward item proposed below. |
| Promoting anything into `core/ui/component/` | See "Shared component question". |
| Removing `TodayViewModel`'s latch write | Deliberately kept — see D4. |
| Re-onboarding, skip links, or a settings entry to replay onboarding | No demand; adds a second flag and a second entry point. |

## Settled Decisions

These arrived settled from the pre-proposal handoff. They are recorded here as the contract for
`sdd-spec` and `sdd-design`, not reopened.

| # | Decision | Reasoning |
|---|---|---|
| D1 | Onboarding owns the notification ask; the Today banner becomes the safety net, logic unchanged. | The banner still has two real audiences: users who denied during onboarding, and installs predating this change. Deleting it would regress both. |
| D2 | The gate is a wrapper composable in `setContent`, tri-state `Boolean?` (`null` → blank hold, `false` → onboarding, `true` → `ConstanzaApp()`). NOT a new `ConstanzaRoute` member. | `rememberSaveable`'s initial-value producer (`MainActivity.kt:83`) runs synchronously; the flag needs a suspend DataStore read. Any synchronous default flashes the wrong screen for one frame. A sealed member would need the same nested async gate anyway, while adding a back-targetless route to a type built for rotation-survival of mid-edit screens. |
| D3 | `onboarding_done` lives in the existing `reminder_settings` DataStore. No second DataStore, no Hilt qualifier. | `ReminderSettingsStore` is an unqualified `@Singleton` already injectable everywhere. `DataStoreModule.kt:18-21` states the precedent: single scalar reads with no shared schema risk. |
| D4 | Onboarding's permission screen calls the same `reminderSettingsStore.recordRequestedNotificationPermission()`. `TodayViewModel`'s write path is kept. | One shared write method rather than two that can diverge. The Today path is the safety net's own write and stays reachable for pre-onboarding installs. |
| D5 | Two screens. Screen 1 explains the app (built to grow to more later). Screen 2 explains why notifications matter and asks. On API 31-32 screen 2 does not exist at all. | On API 31-32 `NotificationPermission.decide` answers `NOT_APPLICABLE` — there is no permission to request, so a screen asking for one would be a lie. Skipping it is the honest behaviour and is directly assertable on the matrix's API 31 leg. |
| D6 | Exact-alarm permission is out of scope. | See Out of Scope. Nothing in exploration reopened it. |
| D7 | Onboarding finishes by opening habit creation directly. | The flow closes with an action rather than with an empty list. Sidesteps `today-has-no-add-habit-affordance` without depending on fixing it. |
| D8 | Instrumented tests that are not about onboarding pre-seed `onboarding_done = true`. The two permission-boundary tests in `CoreFlowE2ETest` walk the real flow. | Walking onboarding in all four tests would make every unrelated assertion pay for a flow it is not testing, and would couple habit-creation and habit-removal tests to onboarding's UI copy. Pre-seeding keeps the coupling where the contract actually lives. |
| D9 | The permission is non-blocking: onboarding completes with notifications denied and the app stays a usable manual tracker. | Matches `reminder-response`'s existing `Notification Permission Scope` requirement that a denial leaves Today fully usable. A blocking ask would contradict a shipped spec. |

### D10 — Settled here: what happens when the user leaves the editor without creating a habit

**Decision.** `onboarding_done` is committed at the moment onboarding hands off to the editor —
never on habit save. The onboarding-seeded editor entry additionally gets a `BackHandler` in the
gate that routes to `Today`.

**Reasoning.**

1. **The flag must not depend on a content outcome.** Writing it only after a habit is saved would
   re-run onboarding — including the permission screen — on every launch until the user creates
   something. D9 makes the permission explicitly non-blocking; a flag that loops until the user
   produces content would quietly reintroduce the block through the back door.
2. **The editor currently has no way out except saving.** Verified: `HabitEditorTopBar`
   (`HabitEditorScreen.kt:191-193`) renders a title and no navigation icon, and `onDone`
   (`:76-78`) fires only from `viewModel.events` after a successful save. `ConstanzaApp` installs no
   `BackHandler`, so system back finishes the Activity.
3. **That is survivable from the habit list and unacceptable from onboarding.** From the list, the
   user chose to open the editor and back-exits-app is the app's uniform existing behaviour. From
   onboarding, the user did not choose it — and "the app closes" is the worst available ending to a
   first run.
4. **So the escape hatch belongs to the gate, not the editor.** A `BackHandler` active only for the
   onboarding-seeded editor entry, routing to `Today`. Roughly five lines in `MainActivity.kt`, no
   change to `HabitEditorRoute`'s signature, and no `habit-management` spec delta. Onboarding is
   already done at that point, so Today is correct and final.
5. **The editor's missing cancel affordance for every other entry point stays out of scope** and is
   proposed as a new carried-forward item, `habit-editor-has-no-cancel-affordance`, owner-conditioned
   on the same change that resolves `today-has-no-add-habit-affordance` — both are Today/editor
   navigation gaps and should be decided together.

### Shared component question

**No new shared component.** `core/ui/component/` holds exactly one member (`HabitColorDot`) because
it has exactly one cross-package consumer. Onboarding's page scaffold, its primary-action slot and
its pager indicator have one consumer each, both inside the onboarding package. Build them locally
as private composables in `onboarding/`. That is also where D5's future third screen will appear, so
the local scaffold is the one that will actually be reused. Promote to `core/ui/component/` when a
second package needs it, not before.

### Reference-implementation defects that must not be ported

| Defect | Constanza's answer |
|---|---|
| `bebe-agua-android` `OnboardingScreen.kt:492` has no permanent-denial handling: after two denials the launcher silently no-ops and the "Enable" button is dead. | `NotificationPermission.decide` already returns `BLOCKED`. Onboarding's permission screen MUST render the `BLOCKED` state with an `ACTION_APP_NOTIFICATION_SETTINGS` deep link, the same gesture `TodayBanners` uses. This must be a spec scenario, not a code comment. |
| Its layout hardcodes `bottom = 160.dp` across three pages because the primary button floats over the pager. | Use a real bottom slot (a `Scaffold` bottom bar or a `Column` weight split), so page content never reserves space for a sibling by guesswork. All spacing from `Spacing`/`Dimens` (`core/ui/theme/Dimens.kt`), no raw `.dp`. `ConstanzaColors.Accent` is chrome-only by spec and must not colour onboarding content. |

## Capabilities

### New Capabilities

- `onboarding`: first-run gating, the two-screen flow and its API-level conditionality, the
  notification ask and its four permission states, the finish handoff, and the once-only guarantee.

### Modified Capabilities

- `reminder-response`: delta on the `Notification Permission Scope` requirement, to name onboarding
  as the primary point at which the permission is requested and Today's banner as the fallback.

**Why the delta is mandatory.** The latch (`requested_notification_permission`) will have two
writers. If only the new `onboarding` spec described the ask, two documents would each imply
ownership of one latch — the exact ambiguity this repository's failure-mode catalogue exists to
prevent, and the shape that produced `post-notifications-never-requested` (logic written, owner
named in prose, never wired). One requirement must name both writers and say which is primary.
`reminder-response`'s existing scenarios (API 33+ denial, API 31 no-prompt) stay valid and are not
rewritten.

## Approach

1. **Flag.** Add `onboarding_done` to `ReminderSettingsStore` with a `Flow<Boolean>` read and a
   suspend write, alongside the existing latch.
2. **Gate.** In `MainActivity.setContent`, wrap `ConstanzaApp()` in a composable collecting the flag
   via `collectAsState(initial = null)`. `null` holds a blank themed surface (no spinner, no flash),
   `false` shows onboarding, `true` shows `ConstanzaApp()`. Derived state, so no `Saver` and no
   rotation hazard; `ConstanzaRoute` is untouched.
3. **Flow.** Screen 1 (what the app does) → screen 2 (why notifications, then the real
   `RequestPermission()` launcher, with `GRANTED`/`SHOULD_REQUEST`/`BLOCKED` handled and
   `NOT_APPLICABLE` skipping the screen entirely). Either answer proceeds.
4. **Finish.** Write `onboarding_done = true`, then seed `ConstanzaApp`'s start route to
   `HabitEditor(habitId = null)` with the scoped `BackHandler` from D10.
5. **Safety net.** No change to `TodayViewModel`, `TodayBanners` or `TodayScreen`. Their role changes;
   their code does not.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `app/src/main/kotlin/.../onboarding/` | New | Screens, local page scaffold, ViewModel. |
| `core/ui/MainActivity.kt` | Modified | Gate wrapper, seeded start route, scoped `BackHandler`, KDoc for the second top-level state holder. |
| `reminding/ReminderSettingsStore.kt` | Modified | New `onboarding_done` key + accessors. |
| `tracking/{TodayViewModel,TodayBanners,TodayScreen}.kt` | Unchanged | Role becomes safety net. Logic untouched. |
| `res/values/strings.xml` | Modified | Onboarding copy. |
| `e2e/CoreFlowE2ETest.kt` (391 lines) | Modified | All four tests; one new scenario. |
| `e2e/CoreFlowTestFixture.kt` (177 lines) | Modified | Net-new DataStore seeding + reset. |
| `openspec/specs/reminder-response/spec.md` | Modified | Delta at archive. |
| `openspec/config.yaml` | Modified | New carried-forward item (D10.5). |

## Test Strategy

### Device-free matrix (binding, `testing.instrumented.device_free_matrix`)

Both legs must pass with nothing plugged in, via
`./gradlew :app:emulatorMatrixGroupDebugAndroidTest`:

| Leg | Must prove |
|---|---|
| `api37` | The permission screen appears, its control drives the real `com.android.permissioncontroller` dialog through UiAutomator, and granting leaves Today with no banner. |
| `api31` | The permission screen never renders (`NOT_APPLICABLE`), onboarding completes on screen 1 alone, and Today shows no banner. |

### `CoreFlowE2ETest` rework

| Test | Change |
|---|---|
| `:141` grant-through-real-dialog | Rewritten to walk onboarding: drive onboarding's own permission control, accept the real dialog **inside onboarding**, then assert Today renders **no** banner. Asserting the grant on Today is meaningless once onboarding owns the ask. |
| **new** deny-during-onboarding | Currently untested. Deny the real dialog during onboarding, reach Today, assert the safety net renders the `BLOCKED` variant (`today_notification_permission_open_settings`). This is the D1 safety-net contract made concrete. |
| `:182` API-below-33 | Walks onboarding (nothing to grant), then keeps its existing absence assertions unchanged. |
| `:220` create-habit-and-answer-reminder | Pre-seed `onboarding_done = true` before `ActivityScenario.launch`. Otherwise unchanged. |
| `:261` remove-habit | Same pre-seed. Otherwise unchanged. |

`launchApp()` (`:313-316`) awaits `today_title` immediately after launch and is the single point all
four break at; it grows a pre-seeded and a walk-through variant.

`MethodSorters.NAME_ASCENDING` ordering and the "permission grants are a one-way door within an
installation" constraint documented in the class KDoc still hold and must be re-read when naming the
two boundary tests, since the deny scenario must run before the grant scenario.

### Net-new seeding infrastructure (the largest hidden cost)

`CoreFlowTestFixture.reset()` (`:61-64`) clears Room and cancels notifications only; the
`reminder_settings` DataStore file has never been reset by any test. Required:

- A helper opening the same-named Preferences DataStore from `androidTest` and writing
  `onboarding_done` before `ActivityScenario.launch`.
- A matching clear, so the two walk-through tests get a genuinely un-onboarded process.
- An explicit statement of which `@Before` writes it, in `CoreFlowTestFixture` beside the Room reset
  rather than in a sibling file — one fixture, one reset discipline.

### Other levels

- **Unit:** onboarding ViewModel state machine (four permission states, API-conditional screen 2,
  finish writes the flag), and the gate's tri-state mapping. `./gradlew :app:testDebugUnitTest`.
- **Unaffected, verified:** every other Compose instrumented test sets content directly on a
  presentational screen and bypasses the Activity. `DarkChromeInstrumentedTest.kt:139` reads
  window-inset-controller state set in `onCreate()` before `setContent`, so a gate above the content
  cannot change what it observes.

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Size overruns the 800-line budget (see forecast). | High | Forecast recorded now, with a named split point, so `sdd-tasks` decides deliberately instead of discovering it. |
| DataStore seeding proves flaky — the file is process-shared and not transactional across an `ActivityScenario.launch` boundary. | Medium | Write and flush before launch, never during. If flaky, fall back to clearing the file in `@Before` and asserting the read-back before launching. |
| The `BLOCKED` state is hard to reach on the matrix (needs two denials in one install). | Medium | Assert the state mapping in unit tests; assert one denial's Today-side consequence instrumented. Do not attempt a two-denial instrumented walk. |
| Blank-hold frame is visible as a flicker on a slow first read. | Low | Blank themed surface at the app background colour, so worst case is an imperceptible extension of the cold-start window, not a visible flash. |
| Onboarding copy drifts from what the app does as features grow. | Low | Screen 1 is deliberately generic; strings live in `strings.xml`. |
| The `reminder-response` delta is skipped and latch ownership stays ambiguous. | Low | Named as mandatory in Capabilities with its reasoning. |

## Size Forecast — 800-line review budget

**Verdict: likely to exceed 800 changed lines in a single PR. Medium-High.**

| Slice | Est. changed lines |
|---|---|
| Onboarding package (2 screens, scaffold, ViewModel) | 220–260 |
| `MainActivity` gate + seeded route + `BackHandler` + KDoc | 45–60 |
| `ReminderSettingsStore` + strings | 35–45 |
| Unit tests (ViewModel + gate) | 70–100 |
| `CoreFlowE2ETest` rework + new scenario | 100–140 |
| `CoreFlowTestFixture` seeding + reset | 50–70 |
| **Code + tests subtotal** | **520–675** |
| OpenSpec artifacts (this proposal, `onboarding` spec, `reminder-response` delta, design, tasks) | 250–380 |
| **Total** | **770–1055** |

Delivery strategy is cached as `single-pr`, so this is a forecast, not a re-decision. Reviewers
should expect the upper half of that range. If the budget must hold, the natural split is
**code + unit tests** (slice A, ~370–465) and **instrumented rework + seeding fixture** (slice B,
~150–210), because slice B is exactly the work that has no production consumer until slice A lands
and is independently verifiable by the matrix.

## Rollback Plan

Scheduling and persisted data are both touched, so `rules.proposal` requires this section.

- **Persisted data.** The only persistence change is one additive boolean key in an existing
  Preferences DataStore. No Room schema change, no migration. Reverting the code leaves an orphaned
  key that nothing reads; DataStore Preferences tolerates unknown keys, so no cleanup is required
  and no data is lost.
- **Scheduling.** Untouched. No alarm, worker or channel behaviour changes; onboarding only affects
  whether the permission that gates delivery has been requested. A revert returns the app to the
  Today-banner ask, which still works and is still the only writer of the latch.
- **Code.** Revert the PR. The gate is a wrapper, so removing it restores `setContent { ConstanzaApp() }`
  exactly; `ConstanzaRoute` was never modified.
- **Users mid-state.** A user who onboarded before the revert keeps `requested_notification_permission = true`,
  so Today correctly shows `GRANTED` or `BLOCKED` rather than re-asking. This is why D4 keeps
  `TodayViewModel`'s write path.

## Dependencies

- None external. No new libraries. `ReminderSettingsStore`, `NotificationPermission` and the
  device-free matrix all exist and are green.
- Sequencing: `today-has-no-add-habit-affordance` should be sequenced **after** this change, per its
  own owner condition.

## Success Criteria

- [ ] A clean install shows onboarding before Today, and never shows it again after completion.
- [ ] The notification permission is requested inside onboarding, and either answer completes the flow.
- [ ] A denial leaves Today's safety-net banner rendering the `BLOCKED` variant with its settings deep link.
- [ ] On API 31-32 the permission screen never renders and no banner appears.
- [ ] Onboarding ends in the habit editor; system back there returns to Today rather than closing the app.
- [ ] `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` is green on both legs with nothing attached.
- [ ] `./gradlew check` is green (unit tests, lint, detekt).
- [ ] No raw `.dp` and no `ConstanzaColors.Accent` in onboarding content.
- [ ] Both latch writers are named in one requirement, with one stated as primary.
