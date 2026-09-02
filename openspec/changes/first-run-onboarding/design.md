# Design: First-Run Onboarding

Change: `first-run-onboarding` · Phase: `sdd-design` · Date: 2026-09-02
Inputs: `proposal.md` (binding, D1–D10), `exploration.md` (evidence), `openspec/config.yaml`
(`rules.design`), plus two mid-phase corrections from the orchestrator recorded in §2 and §8.

> Size note: this design exceeds the 800-word phase default, deliberately and for the same reason
> `proposal.md` did. Seven of its decisions are load-bearing correctness traps (the one-frame flash,
> the handoff seam, the four permission states, the API-31 page count, the DataStore seeding, the
> write ordering, and the E2E method ordering), and a design that asserted them without the argument
> would send `sdd-tasks` to re-derive each one. The archived `habit-tracking-mvp` design set the
> house precedent for depth over brevity here.

A tri-state wrapper composable resolves one DataStore boolean before anything renders, then either
holds the cold-start background, runs onboarding, or lets today's `ConstanzaApp()` through unchanged.
Onboarding's last act writes the flag and seeds `ConstanzaApp`'s start route to the habit editor,
tagged with where it came from, so both of the editor's exits land on Today rather than on a habit
list this user has never seen.

## Quick path for a reviewer

1. **§2 first.** Two of the proposal's premises changed after it was committed: D10's gate
   `BackHandler` is superseded by a separate editor change, and the proposal's `BLOCKED` reachability
   claim was wrong. Read what replaced them before the rest.
2. **§4** the gate. The whole change hangs on why the tri-state is a retained `StateFlow` and not
   `collectAsState(initial = null)`.
3. **§5** the handoff. One `EditorOrigin` field answers both editor exits and needs no `BackHandler`.
4. **§6** screen 2's four states, and why the flow's forward path never depends on the permission
   control.
5. **§8** the DataStore seeding — the change's largest hidden cost, and the one place the E2E suite's
   method ordering can silently break.

## 1. Stack reconciliation (`rules.design`)

`openspec/config.yaml`'s `stack:` section is **confirmed unchanged**. This change adds no library, no
module, no Gradle configuration and no schema. It uses only what is already declared and verified:
Compose + Material 3, Hilt (KSP), DataStore Preferences (`libs.androidx.datastore.preferences`,
already an `implementation` dependency), and the existing `emulatorMatrix` managed-device group.
`minSdk 31` / `targetSdk 37` are untouched and are precisely the boundary §7 designs around.

`rules.design` also requires "sequence diagrams for reminder/alarm and snooze flows". **Neither flow
is touched by this change** — no alarm, worker, channel, occurrence or snooze code changes, and
`TodayViewModel`/`TodayBanners`/`TodayScreen` are byte-unchanged (D1). The archived design's §9
diagrams remain current. §9 below supplies the diagram this change *does* owe: onboarding's own
control and write flow.

The clock-access ban (`config/detekt/detekt.yml` `ForbiddenMethodCall`) is satisfied trivially:
**nothing in this change reads time.** No `TimeProvider` injection is needed anywhere in the
onboarding package. Note the known gap already recorded in `app/build.gradle.kts` — that rule does
not fire in `:app` under AGP 9 — so this is a review-enforced property here, as it is everywhere else
in `:app`.

## 2. Two proposal premises that changed after it was committed

Recorded as supersessions rather than silently absorbed, because `proposal.md` is a committed
artifact that a future reader will find first.

### 2.1 D10's gate `BackHandler` is SUPERSEDED

D10 scoped a `BackHandler` into the gate on the explicit finding that "the editor currently has no
way out except saving". That finding was true when written and is being fixed directly: a separate,
standalone change gives `HabitEditorTopBar` a navigation icon, gives `HabitEditorRoute` an
`onBack: () -> Unit`, and installs the editor's own `BackHandler`.

**Consequence for this design:** no `BackHandler` anywhere in `MainActivity.kt`. Onboarding passes an
`onBack` destination like any other caller (§5). This is strictly better than D10's plan — it
special-cases nothing, adds no lines to the gate, and cannot produce the dead-control failure that a
gate-level handler risks (a handler that flips a seed `ConstanzaApp` has already consumed does
nothing at all; see §5.2).

**D10's substance survives intact.** Its actual requirement — *system back from the
onboarding-seeded editor must reach Today, not close the app* — is met, and the design that meets it
is smaller. What is discarded is only the mechanism.

**New dependency, stated plainly:** `first-run-onboarding` now **depends on the editor-cancel change
having landed first**. `ConstanzaApp` cannot pass an `onBack` to a `HabitEditorRoute` that does not
accept one. `sdd-tasks` must sequence this change behind it, and `sdd-apply` must not start against a
`HabitEditorRoute` that still has the two-parameter signature.

**D10.5 is also superseded.** It proposed adding a carried-forward item for the editor's missing
cancel affordance; that item already exists in `openspec/config.yaml` as
`habit-editor-has-no-cancel-affordance` (status `open`), and the editor change closes it. **This
change must not touch that item** — closing another change's item is exactly the silent-close the
`carried_forward_open_items` note forbids.

**One point deliberately left open, and designed around:** whether backing out of an editor with
unsaved edits confirms first belongs to the editor change. This design is indifferent to the answer,
by construction: `onBack` is a *destination*, invoked by the editor once the editor has decided to
leave. A confirmation dialog changes when `onBack` fires, never whether the destination is right.
**Invariant: onboarding supplies the destination; the editor owns the decision to leave.**

### 2.2 The proposal's `BLOCKED` reachability claim was WRONG

`proposal.md`'s risk table says `BLOCKED` "needs two denials in one install" and routes it away from
the matrix. That is false, and inheriting it would have under-tested the safety net.

`decideNotificationPermission` (`reminding/NotificationPermission.kt:43-52`) is:

```kotlin
sdkInt < 33        -> NOT_APPLICABLE
hasPermission      -> GRANTED
hasRequestedBefore -> BLOCKED
else               -> SHOULD_REQUEST
```

`BLOCKED` is `hasRequestedBefore && !hasPermission` — **one recorded ask plus no grant.** Two denials
is the *Android system's* no-more-prompting rule, not this table's. The class KDoc (`:34-42`) is
explicit that the flag deliberately *approximates* the system state so that no `Activity` reference is
needed.

**Consequence:** `BLOCKED` is reachable in one instrumented step on the API 37 leg — deny the real
dialog once during onboarding, and Today renders the `BLOCKED` variant. §10 routes that scenario to
the matrix. §8.3 shows why this collides with the grant scenario, and how the seeding fixture resolves
it.

### 2.3 Recorded and accepted: Constanza spends the user's second system prompt

Because the app flips to `BLOCKED` after **one** recorded ask, a user who denies once during
onboarding immediately loses the second native prompt Android would still have shown them, and is sent
to system settings instead. This conservatism predates the change; onboarding is what makes it the
common path rather than a rare one.

**Decision: accept it here. Do not fix it in this change.**

| Argument | Detail |
|---|---|
| The fix is out of scope by construction | Telling "never asked" from "permanently denied" needs `shouldShowRequestPermissionRationale`, which needs an `Activity`. `NotificationPermission`'s KDoc names avoiding that as the reason the flag exists. Changing it rewrites a shipped, spec-backed class with its own scenarios. |
| The error direction fails safe | The approximation can only ever **under**-prompt, never over-prompt. Over-prompting is the Play-policy risk; under-prompting costs one extra tap. |
| The consequence is not a dead end | `BLOCKED` always offers `ACTION_APP_NOTIFICATION_SETTINGS`, now on both Today and onboarding screen 2. The user is never stuck, only routed the longer way. |
| It is now visible | Previously it hid behind a banner most users never tapped. Onboarding makes it a real product behaviour, so it is recorded here rather than discovered later. |

**Recommendation for `sdd-tasks` (not performed here):** add a carried-forward item
`notification-permission-blocked-after-one-ask`, owner-conditioned on any change that is already
touching `NotificationPermission`'s Activity-free contract.

## 3. Architecture decisions

| # | Decision | Rejected alternative | Cost of the alternative |
|---|---|---|---|
| A1 | The gate's tri-state is a **retained `StateFlow<Boolean?>` on a `FirstRunGateViewModel`**, read with plain `collectAsState()`. | `collectAsState(initial = null)` over a cold `Flow` obtained per-composition, as the proposal's Approach step 2 sketched. | A cold flow re-holds `null` on **every** Activity recreation. The one-frame blank would move from cold start (where it is invisible, §4.2) to every rotation mid-session (where it is a visible flash on a fully-loaded screen). That is the exact defect D2 exists to prevent, relocated rather than fixed. |
| A2 | **Two state holders**: `FirstRunGateViewModel` (one flag, read-only) and `OnboardingViewModel` (flow state + both writes). | One combined ViewModel. | The gate's correctness would then depend on the flow's state machine. The gate must be provable by one question — "what does the flag say?" — and a combined holder makes its unit test carry the whole flow. Both are Activity-scoped either way, so the second holder costs nothing at runtime. |
| A3 | The editor's return destinations are carried **in the route** via `ConstanzaRoute.EditorOrigin`. | A `BackHandler` in the gate (D10); or ambient "came from onboarding" state in `ConstanzaApp`. | The gate handler is dead by construction (§5.2). Ambient state is wrong after the user later re-enters the editor from the list, and needs clearing logic that has no natural trigger. |
| A4 | The onboarding **page list is computed once, at flow start**, from API applicability alone. The live permission decision drives only screen 2's *content*. | Deriving the page list from the live decision. | Granting the permission would delete the page the user is standing on: index out of bounds, or a silent jump, and the "last page" label changing under the user's finger. §7. |
| A5 | Test seeding writes through the **app's own singleton `DataStore`**, obtained by a Hilt `@EntryPoint`. | A second `preferencesDataStore` delegate in `androidTest`; or deleting the backing file. | A second `DataStore` over one file in one process throws `IllegalStateException: There are multiple DataStores active for the same file` — androidTest runs in the app's process, so this is guaranteed, not a risk. Deleting the file hardcodes DataStore's internal path and would also wipe the permission latch, breaking the one-way-door discipline (§8.2). |
| A6 | Every pre-existing install **sees onboarding once** after updating. No migration. | A one-shot migration inferring prior use from `requested_notification_permission`. | §11. |
| A7 | No new shared component, no new M3 colour role, one new `Dimens` token. | Promoting the page scaffold to `core/ui/component/`. | Settled by the proposal; §12 records the one token and the exact role inventory so nothing is introduced silently. |

## 4. The gate

### 4.1 Shape

`MainActivity.onCreate` changes by one word inside `setContent`:

```kotlin
setContent {
    ConstanzaTheme {
        FirstRunGate()
    }
}
```

```kotlin
/**
 * The app's SECOND top-level state holder, above [ConstanzaApp]'s hoisted route (proposal D2).
 *
 * Tri-state and not a [ConstanzaRoute] member: [ConstanzaApp]'s `rememberSaveable` initial-value
 * producer runs synchronously and cannot reflect a suspend DataStore read, so any synchronous
 * default renders the wrong screen for one frame.
 */
@Composable
private fun FirstRunGate(viewModel: FirstRunGateViewModel = hiltViewModel()) {
    val onboardingDone by viewModel.onboardingDone.collectAsState()
    // Write-once. Set synchronously inside onFinished, BEFORE the flag write is requested (§9).
    // rememberSaveable, not remember: a rotation in the frame between onFinished and the flag
    // emission would otherwise reset the seed and drop the user on Today instead of the editor.
    var startRoute by rememberSaveable { mutableStateOf<ConstanzaRoute>(ConstanzaRoute.Today) }
    when (onboardingDone) {
        // The blank hold. Emitting nothing is deliberate — see §4.2.
        null -> Unit
        false -> OnboardingRoute(
            onFinished = {
                startRoute = ConstanzaRoute.HabitEditor(
                    habitId = null,
                    origin = ConstanzaRoute.EditorOrigin.Onboarding,
                )
            },
        )
        true -> ConstanzaApp(startRoute = startRoute)
    }
}
```

```kotlin
@HiltViewModel
class FirstRunGateViewModel @Inject constructor(
    settingsStore: ReminderSettingsStore,
) : ViewModel() {
    /** `null` only while the first DataStore read is in flight. Retained across configuration
     *  change, so the blank hold happens at most once per process, not once per rotation. */
    val onboardingDone: StateFlow<Boolean?> =
        settingsStore.onboardingDone.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
```

### 4.2 Why the blank hold cannot flash

The `null` branch renders **nothing** rather than a themed `Surface`. That is not laziness, and it is
strictly better than painting a matching colour: with no composable emitted, the pixels on screen are
still the ones `android:windowBackground` already painted.

Verified, not assumed: `res/values/themes.xml:12-13` sets
`android:windowBackground="@color/window_background"`, and `res/values/colors.xml:4-6` states that
this colour "must match `ConstanzaColors.Background` exactly, or the cold-start frame flashes a
different colour than the first Compose frame (spec `Cold-Start Window Background And System Bar
Icons`)". The blank hold is therefore an *extension of the cold-start window*, not a new visual state.
Worst case is an imperceptibly longer cold start; there is no frame at which a different colour is
drawn.

Painting a `Surface(color = MaterialTheme.colorScheme.background)` instead would be *approximately*
the same colour and would introduce a real composition — a second thing that must be kept in sync with
the window background forever. Rejected for that reason.

`SharingStarted.Eagerly` matters here: the upstream read starts when the ViewModel is constructed, not
when the first collector subscribes, so the read is already in flight while Compose is doing its first
layout pass.

## 5. The handoff into the habit editor

### 5.1 The route carries its own return target

`ConstanzaApp` gains one parameter and `ConstanzaRoute.HabitEditor` gains one defaulted field:

```kotlin
private sealed interface ConstanzaRoute : java.io.Serializable {
    data object Today : ConstanzaRoute
    data object HabitList : ConstanzaRoute

    /** The editor is reachable from two places that must leave to DIFFERENT screens: the habit
     *  list, which is its own caller, and the end of onboarding, whose user has never seen the
     *  list — and cannot reach Today from it, since [HabitListRoute] has no back route at all. */
    enum class EditorOrigin { HabitList, Onboarding }

    data class HabitEditor(
        val habitId: Long?,
        val origin: EditorOrigin = EditorOrigin.HabitList,
    ) : ConstanzaRoute
    data class Progress(val habitId: Long) : ConstanzaRoute
    data object Settings : ConstanzaRoute
}

@Composable
private fun ConstanzaApp(startRoute: ConstanzaRoute = ConstanzaRoute.Today) {
    var route by rememberSaveable { mutableStateOf(startRoute) }
    when (val current = route) {
        // ...
        is ConstanzaRoute.HabitEditor -> {
            val leaveTo = when (current.origin) {
                ConstanzaRoute.EditorOrigin.HabitList -> ConstanzaRoute.HabitList
                ConstanzaRoute.EditorOrigin.Onboarding -> ConstanzaRoute.Today
            }
            HabitEditorRoute(
                habitId = current.habitId,
                onDone = { route = leaveTo },
                onBack = { route = leaveTo },
            )
        }
    }
}
```

Enums are `Serializable`, so `rememberSaveable`'s default saver handles the route exactly as it does
today. `HabitEditorRoute`'s signature gains nothing from *this* change — `onBack` arrives with the
editor change (§2.1), and `origin` never leaves `MainActivity.kt`.

**`onDone` branches too, and that is a finding, not a flourish.** Leaving save at `HabitList` for the
onboarding entry would strand a brand-new user: `HabitListRoute` takes no `onBack` and has no route to
Today. `CoreFlowE2ETest.relaunchApp()`'s own KDoc (`:318-320`) records this — "The habit list has no
route back to Today ... so re-opening the app is how a person gets back there". A first run that ends
by requiring an app relaunch is worse than the empty Today it was routing around. Both exits go to
Today, and one field decides both.

### 5.2 Why the gate cannot own a `BackHandler` (the mechanism D10 assumed)

Worth recording, because it is not obvious and it justifies §2.1 independently of the editor change.
`ConstanzaApp` seeds `rememberSaveable { mutableStateOf(startRoute) }` **once**. A gate-level
`BackHandler` that flipped `startRoute` back to `Today` would recompose `ConstanzaApp` with a new
parameter that its already-initialised state ignores — a back press that visibly does nothing. Making
it work needs `key(startRoute) { ConstanzaApp(startRoute) }` to discard and rebuild the whole
composition, which is a large hammer for a one-shot escape. The editor's own `onBack` avoids all of it.

### 5.3 Back behaviour, stated per case

| Where the user is | System back | Mechanism |
|---|---|---|
| Onboarding screen 1 or 2 | Finishes the Activity (today's uniform behaviour) | No handler is installed. Onboarding does not intercept back; there is nothing behind it. |
| Editor, entered from onboarding | Returns to **Today** | Editor's own `BackHandler` → `onBack` → `EditorOrigin.Onboarding` → `Today`. |
| Editor, entered from the habit list | Returns to the habit list | Same path, `EditorOrigin.HabitList`. Unchanged by this design. |
| Today | Finishes the Activity | Unchanged. |

The onboarding-seeded escape is one-shot by construction: once the user is on Today, the origin is
gone with the route, and every subsequent editor entry is an ordinary list entry.

## 6. Screen 2 — the four permission states

Screen 2 consults `NotificationPermission.decide(hasRequestedBefore)` exactly as `TodayViewModel` does,
and re-reads it on `ON_RESUME` through the same `DisposableEffect` + `LifecycleEventObserver` idiom
`TodayScreen.kt:55-61` already uses. **The re-read is load-bearing, not symmetry:** the `BLOCKED`
action leaves the app for system settings, and without it the user grants the permission there, comes
back, and screen 2 still says they are blocked.

| Decision | Page present? | Permission control | Primary (bottom-slot) action |
|---|---|---|---|
| `NOT_APPLICABLE` | **No.** The page does not exist in the list at all (D5). | — | — |
| `SHOULD_REQUEST` | Yes | "Allow" → `rememberLauncherForActivityResult(RequestPermission())` on `POST_NOTIFICATIONS`. On return, whatever the answer, `recordRequestedNotificationPermission()` then re-read. | Enabled, "Finish" |
| `GRANTED` | Yes | No ask. A confirmation line stating reminders will arrive; **no button at all**, because a button whose only honest action is "nothing" is the defect being avoided. | Enabled, "Finish" |
| `BLOCKED` | Yes | "Open settings" → `Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(EXTRA_APP_PACKAGE, packageName)` — byte-for-byte the `TodayBanners.kt:82-89` gesture. **Never the launcher.** | Enabled, "Finish" |

**The structural answer to the reference app's dead-button defect** (`bebe-agua-android`
`OnboardingScreen.kt:492`) is not only that `BLOCKED` has a real action. It is that
**the flow's forward path never routes through the permission control.** The bottom-slot primary
action is a sibling of the permission control, always present and always enabled, in all four states.
That is D9's non-blocking guarantee expressed in layout rather than in a comment: even a permission
control that somehow no-opped could not trap the user, because it was never the way forward.

The launcher and the `Intent` live in a private composable in the onboarding package, each with its own
`LocalContext`, so the page bodies stay presentational — the reason `TodayBanners.kt:26-34` gives for
keeping them out of `TodayScreen`.

## 7. API 31-32 divergence — one source of truth for the page count

Applicability is decided **once**, from a value that cannot change during the process:

```kotlin
// decide()'s first branch is `sdkInt < 33 -> NOT_APPLICABLE`, which ignores the flag entirely, so
// passing `false` here is sound: the flag only ever discriminates SHOULD_REQUEST from BLOCKED, and
// both of those are "applicable". Same argument TodayViewModel.kt:72-78 already records for its seed.
private val includesPermissionPage =
    notificationPermission.decide(hasRequestedBefore = false) != NotificationPermissionDecision.NOT_APPLICABLE

val pages: List<OnboardingPage> = buildList {
    add(OnboardingPage.Intro)
    if (includesPermissionPage) add(OnboardingPage.Notifications)
}
```

Everything the divergence can break is then **derived from `pages`, never restated**:

```kotlin
data class OnboardingUiState(
    val pages: List<OnboardingPage>,
    val index: Int,
    val permission: NotificationPermissionDecision,
) {
    val page: OnboardingPage get() = pages[index]
    val isLastPage: Boolean get() = index == pages.lastIndex   // never `index == 1`
    val showsProgress: Boolean get() = pages.size > 1
}
```

- **Primary action label** comes from `isLastPage`: "Continue" when false, "Finish" when true. On API
  31-32 screen 1 *is* the last page and reads "Finish". Any code comparing against a literal `1`
  breaks silently on the api31 leg — that is the trap, and `lastIndex` is the whole fix.
- **Progress indicator** renders only when `showsProgress`. A one-of-one indicator is not merely
  redundant; it tells the user there is somewhere else to go when there is not.
- Because both read the same list, they **cannot disagree**. There is no second place recording "how
  many screens are there".

Adding D5's future third screen is one `add(...)` and one page body. No existing page changes.

## 8. Test seeding infrastructure (the largest hidden cost)

### 8.1 The mechanism

`androidTest` runs in the app's own process, so it must not construct a second `DataStore` over
`reminder_settings` (A5). It borrows the app's own instance:

```kotlin
// app/src/androidTest/kotlin/com/jjrapps/constanza/e2e/CoreFlowTestFixture.kt
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ReminderSettingsDataStoreEntryPoint {
    fun reminderSettingsDataStore(): DataStore<Preferences>
}
```

`EntryPointAccessors.fromApplication(context, ...)` yields the exact instance Hilt hands
`ReminderSettingsStore`. Three properties follow, and all three are why this beats poking the file:

1. A write is immediately visible to the app, because it *is* the app's `DataStore`.
2. There is no second instance, so the multiple-active-DataStores guard is never tripped.
3. The keys are shared, not retyped. `ReminderSettingsStore`'s `private companion object` becomes
   **`internal`**, so the fixture references `ONBOARDING_DONE_KEY` and
   `REQUESTED_NOTIFICATION_PERMISSION_KEY` directly. A rename in production then breaks the test at
   compile time instead of silently seeding a key nobody reads.

Classpath is not a new risk: main's `implementation` dependencies are already on androidTest's compile
classpath in this module — `CoreFlowE2ETest` imports `androidx.core.content.ContextCompat` (from
`androidx.core.ktx`, `implementation`) and compiles today. `hilt.android` and
`androidx.datastore.preferences` arrive the same way. No new `androidTestImplementation` entry.

Production gains exactly one write method, deliberately parameterless:

```kotlin
val onboardingDone: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_DONE_KEY] ?: false }

@Suppress("RedundantSuspendModifier")   // same detekt/AAR type-resolution gap as its three siblings
suspend fun setOnboardingDone() { dataStore.edit { it[ONBOARDING_DONE_KEY] = true } }
```

There is no production way to un-onboard. Tests write `false` through the shared `DataStore`, so the
"reset" capability never leaks into the app's API surface.

### 8.2 Reset discipline — one fixture, and one thing it must NOT reset

`CoreFlowTestFixture.reset()` gains exactly one line, beside the Room clear:

```kotlin
suspend fun reset() {
    database.habitDao().deleteAll()
    notificationManager.cancelAll()
    settings.edit { it[ReminderSettingsStore.ONBOARDING_DONE_KEY] = false }   // NEW
}
```

The default is **un-onboarded**, which is the state a real clean install is in. Tests that do not care
about onboarding opt *out* by seeding `true` after reset. Defaulting the other way would make the
product's actual first-run state the one no test ever exercises.

`reset()` deliberately **does not touch `requested_notification_permission`.** Granting
`POST_NOTIFICATIONS` is a one-way door within an installation (`CoreFlowE2ETest` class KDoc,
`:84-99`): the permission cannot be revoked without killing the instrumentation process. Blanket-
resetting the latch would leave the app believing it should prompt while the system will never show a
dialog again — desynchronising the approximation from reality and hanging whichever test ran next.
Only one test resets it, explicitly, and §8.3 says why it is allowed to.

### 8.3 Ordering across `ActivityScenario.launch`, and the deny/grant collision

**Ordering is guaranteed by `edit`'s suspend contract, not by timing.** `DataStore.edit` does not
return until the write is durable, so:

```kotlin
runBlocking { fixture.seedOnboardingDone() }     // returns only once committed
scenario = ActivityScenario.launch(MainActivity::class.java)
```

The gate's ViewModel is constructed after `launch`, and its first upstream emission reads the
committed value. This is the proposal's "write and flush before launch, never during", with the
reason named. No polling, no `awaitValue`.

**The collision §2.2 creates.** The deny scenario records the latch (`BLOCKED`), and the grant
scenario that sorts after it then finds onboarding rendering "Open settings" — no dialog, and a test
that times out for a reason unrelated to what it asserts. Resolution: the grant test, and only the
grant test, calls `fixture.seedNotificationPermissionUnasked()` before launching.

That is not cheating, and the argument is the one the class KDoc already makes. The latch is the app's
*approximation* of the system's remaining-prompt budget. After exactly one denial the system still
permits one more dialog; the app has simply given up early (§2.3). Clearing the latch restores the
approximation to the system's real state, which is exactly the state the grant test needs to observe.
It is also the only place in the suite that does this, and it is one line with a comment.

**Fallback if the second system dialog does not appear on the api37 image** (assumed from Android's
two-denial rule, not measured): keep the deny scenario as the real-dialog one, and reduce the grant
scenario to seeding the latch unasked on a *fresh* method with no prior denial — i.e. swap which of the
two owns the real dialog. Both cannot own it in one installation, and the deny scenario is the one that
is currently untested, so it gets first claim.

### 8.4 Method ordering

`MethodSorters.NAME_ASCENDING` is load-bearing and the class KDoc explains why. Two scenarios are added
and the existing names no longer sort correctly among them (`deny...` > `allowing...`). Rename the
boundary tests with explicit ordinal prefixes rather than relying on a second-letter accident:

| Order | Name | Leg | Seeds |
|---|---|---|---|
| 1 | `a1DenyingTheOnboardingPromptLeavesTodayOfferingNotificationSettings` | api37 | onboarding not done (reset default) |
| 2 | `a2AllowingTheOnboardingPromptLeavesTodayWithNoNotificationBanner` | api37 | not done + latch unasked (§8.3) |
| 3 | `a3ApiBelow33SkipsTheOnboardingPermissionScreenEntirely` | api31 | not done |
| 4 | `creatingAHabitThroughTheUi...` | both | `onboarding_done = true` |
| 5 | `removingAHabitThroughTheUi...` | both | `onboarding_done = true` |

`launchApp()` splits into two named variants — `launchFirstRunApp()` (awaits onboarding's first page)
and `launchOnboardedApp()` (seeds the flag, awaits `today_title`). No default: every call site states
which world it is in.

## 9. Write ordering and the commit point

`onboarding_done` is committed at **handoff**, never on a content outcome (D10). The sequence, with
the ordering guarantee made explicit at the one site that owns it:

```
  user taps the bottom-slot primary action on the LAST page
        │
        │  (one synchronous click handler — Compose state writes are immediate,
        │   a suspend DataStore write cannot complete inside this frame)
        ├─1─▶ onFinished()            gate: startRoute = HabitEditor(null, Onboarding)
        └─2─▶ viewModel.finish()      viewModelScope.launch { store.setOnboardingDone() }
                    │
                    ▼
              3. DataStore.edit commits durably
                    │
                    ▼
              4. dataStore.data emits ──▶ FirstRunGateViewModel.onboardingDone = true
                    │
                    ▼
              5. gate recomposes ──▶ ConstanzaApp(startRoute = HabitEditor(null, Onboarding))
```

Step 1 before step 2 is a real contract, expressed at a single call site:

```kotlin
onPrimaryAction = {
    if (state.isLastPage) { onFinished(); viewModel.finish() } else viewModel.next()
}
```

If the emission could ever beat the seed, the gate would compose `ConstanzaApp(Today)` and
`rememberSaveable` would latch Today permanently — the seed is read exactly once. It cannot, because a
suspend write cannot complete before the click handler returns, but ordering the two calls at one
visible site removes the need to rely on that.

**What a crash leaves behind:**

| Crash window | State on next launch | Is that correct? |
|---|---|---|
| Anywhere before step 3 | `onboarding_done = false` → onboarding runs again from screen 1 | Yes. If the dialog was already answered, the latch is `true`, so screen 2 renders `GRANTED`/`BLOCKED` and the user is **not** re-prompted. No information is lost and nothing is asked twice. |
| After step 3, before or during the editor | `onboarding_done = true`, no habit exists → Today (empty) | Yes — precisely D10's intent. Onboarding never repeats, and the flag never depended on a habit existing. |
| Between the dialog answer and the finish tap | latch `true`, `onboarding_done` `false` | Onboarding replays; screen 2 shows the answered state; Continue → Finish. No second prompt, no lost answer. |

There is **no window in which `onboarding_done` is true but the flow was never shown**, because the
write is the flow's last action. The write is idempotent; a repeat is a no-op edit.

## 10. Testing strategy

| Layer | What | How |
|---|---|---|
| Unit | `FirstRunGateViewModel` tri-state: `null` before the first emission, then `false`/`true` | Turbine over the `StateFlow`, fake `ReminderSettingsStore` backed by a `MutableStateFlow`. This is the gate's testable seam; the composable's three-branch `when` needs none. |
| Unit | `OnboardingViewModel` page list: 2 pages when applicable, **1 when `NOT_APPLICABLE`** | MockK on `NotificationPermission` — mandatory, not preference: `Build.VERSION.SDK_INT` is `0` under `isReturnDefaultValues`, so an unmocked `decide()` would always answer `NOT_APPLICABLE`. |
| Unit | `isLastPage` / `showsProgress` at both page counts | Direct assertions on `OnboardingUiState`. This is the API-31 label trap's regression test. |
| Unit | All four permission states map to the right control, and `finish()` writes the flag | Fake store + mocked `NotificationPermission`. |
| Instrumented (api37) | Deny the **real** dialog inside onboarding → Today renders `today_notification_permission_open_settings` | `a1...`. Currently untested anywhere. This is the D1 safety-net contract and §2.2's corrected `BLOCKED` reachability, both made concrete. |
| Instrumented (api37) | Accept the real dialog inside onboarding → Today renders **no** banner | `a2...`. Replaces the old on-Today assertion, which is meaningless once onboarding owns the ask. |
| Instrumented (api31) | The permission page never renders, the single page's action reads **"Finish"**, Today shows no banner | `a3...`. Asserting the *label* as well as the absence is what proves §7 rather than merely proving the page is missing. |
| Instrumented (both) | Habit create/answer and habit removal, unchanged | Pre-seeded `onboarding_done = true`. |
| Unaffected, verified | Every other Compose test sets content directly on a presentational screen and never launches the Activity; `DarkChromeInstrumentedTest.kt:139` reads inset state set in `onCreate()` *before* `setContent`, so a gate above the content cannot change what it observes. | No change. |

Commands: `./gradlew :app:testDebugUnitTest` and `./gradlew check` for the JVM half;
`./gradlew :app:emulatorMatrixGroupDebugAndroidTest` for both legs, nothing attached
(`testing.instrumented.device_free_matrix`).

## 11. Migration / rollout — existing installs

**No migration. Every pre-existing install sees onboarding exactly once after updating** (A6).

The evidence that makes this cheap is recorded in `openspec/config.yaml` under
`release-build-never-produced`: there is no `signingConfigs` block and no release APK has ever been
assembled, so the entire population of existing installs is the maintainer's own debug builds — which
are reinstalled routinely anyway.

| | Accept (chosen) | One-shot migration (rejected) |
|---|---|---|
| Mechanism | `onboarding_done` absent → `false` | On first read: if `onboarding_done` is absent **and** `requested_notification_permission` is present, write `true` |
| Cost | One extra flow, once, for one person | A heuristic on a flag that this very change gives a **second writer** to. It must run exactly once and strictly before onboarding can write, so it needs its own ordering guarantee inside the gate's cold path — the one place this design keeps to a single scalar read and no branching. |
| Failure mode | Sees two screens they wrote | A wrong inference means a **real** user silently never sees onboarding — exactly the failure this change exists to prevent |

**D1's fallback framing stays coherent.** A pre-existing install walks onboarding, and screen 2 reads
its already-recorded state rather than re-asking:

- Already granted → `GRANTED`: no prompt, a confirmation line, Finish.
- Already denied → `BLOCKED`: the settings deep link, and Today's banner keeps its fallback role for
  that user afterwards, exactly as before.
- Banner never touched → `SHOULD_REQUEST`: onboarding asks, which is the desired outcome.

No permission state is disturbed in any case, and D9 means nobody is blocked.

**The sharpest cost of accepting, named rather than buried:** a pre-existing user with twelve habits is
handed an empty habit editor at the end. That is a genuine wart, and it is only tolerable because the
population is one — and because §5.1's `onBack` now makes the escape a single tap to Today rather than
the app closing. The two decisions interlock; accepting A6 without the editor change would have been
harder to defend.

**Rollback** is as `proposal.md` records it: one additive boolean key that a revert orphans, no Room
change, no scheduling change, and `setContent { ConstanzaApp() }` restored by removing a wrapper.

## 12. Design-token and colour-role inventory

| Concern | Value | Note |
|---|---|---|
| Page padding, gaps | `Spacing.lg`, `Spacing.xl`, `Spacing.sm` | No raw `.dp` anywhere in the onboarding package. |
| Pager dot size | **`Dimens.PagerDot = 8.dp`** — one new token | The only new token. Reusing `Spacing.sm` as a *size* is the category error `Dimens`'s own KDoc exists to prevent, and `Dimens.HabitDot` is semantically a habit dot. |
| Page background | `colorScheme.background` (Scaffold default) | Bound to `ConstanzaColors.Background` in `Theme.kt`. Audited. |
| Title / body text | `onBackground` / `onSurfaceVariant` | Both bound. Audited. |
| Progress dots | active `primary`, inactive `outlineVariant` | Both bound. A pager dot is a selection indicator — named chrome in `ConstanzaColors.Accent`'s own KDoc. |
| Primary action | `Button` → `primary` / `onPrimary` | A `Button` is a primary control, also named chrome. Same route `TodayBanners`' `TextButton` already takes. |
| Bottom slot container | `Scaffold(bottomBar = { Column { ... } })` — a plain `Column`, **not** `BottomAppBar` | Avoids `BottomAppBar`'s own container role and `surfaceTint` blending, neither of which `Theme.kt` audited for a filled bar. No new role is introduced by this change. |

**Enforceable rule, stated so it can be checked by `rg`: the onboarding package contains zero
references to `ConstanzaColors` and zero `.dp` literals.** Accent reaches the two chrome controls
through M3 roles, never as a content colour — that is the distinction `proposal.md`'s success
criterion is asking for.

**Layout, and the reference defect it avoids.** `Scaffold` with a real `bottomBar` slot; the page body
fills the content area and scrolls. No page reserves space for a sibling. The reference app's repeated
`bottom = 160.dp` across three pages exists because its button floats over the pager — a structural
choice this design does not make. `Scaffold` is chosen over a hand-rolled `Column` weight split because
the app is edge-to-edge (`enableEdgeToEdge` in `onCreate`) and `Scaffold` already applies window
insets, which a bare `Column` would have to redo by hand.

## 13. File changes

| File | Action | What |
|---|---|---|
| `core/ui/MainActivity.kt` | Modify | `FirstRunGate` + `FirstRunGateViewModel`, `ConstanzaApp(startRoute)`, `EditorOrigin` on `HabitEditor`, the `leaveTo` branch, KDoc for the second top-level state holder. **No `BackHandler`.** |
| `onboarding/OnboardingRoute.kt` | Create | Container: `hiltViewModel()`, `ON_RESUME` re-read, `onFinished` hoisted out, `onPrimaryAction`'s ordering contract (§9). |
| `onboarding/OnboardingScreen.kt` | Create | `OnboardingScaffold` (bottom slot + conditional progress) and the two page bodies. Presentational. |
| `onboarding/OnboardingPermissionAction.kt` | Create | The four-state control: its own `LocalContext`, launcher and settings `Intent`. Mirrors `TodayBanners`. |
| `onboarding/OnboardingViewModel.kt` | Create | `pages`, `index`, live decision, `recordRequestedNotificationPermission()`, `finish()`. |
| `reminding/ReminderSettingsStore.kt` | Modify | `onboardingDone` flow, `setOnboardingDone()`, `ONBOARDING_DONE_KEY`; companion `private` → `internal`. |
| `core/ui/theme/Dimens.kt` | Modify | `PagerDot`. |
| `res/values/strings.xml` | Modify | Onboarding copy, including the `GRANTED`/`BLOCKED` variants and Continue/Finish. |
| `e2e/CoreFlowTestFixture.kt` | Modify | `@EntryPoint`, seeding helpers, one line in `reset()`. |
| `e2e/CoreFlowE2ETest.kt` | Modify | Two renamed boundary tests, one new scenario, two `launchApp` variants, two pre-seeds. |
| `habit/HabitEditorScreen.kt` | **Not touched** | `onBack` arrives with the separate editor change (§2.1). |
| `tracking/*` | **Not touched** | D1: role changes, code does not. |
| `openspec/config.yaml` | **Not touched by this change** | `habit-editor-has-no-cancel-affordance` belongs to the editor change (§2.1). §2.3's recommended new item is for `sdd-tasks` to decide. |

## 14. Threat matrix

**N/A** — no shell command, no subprocess, no VCS/PR automation, no executable-file classification,
and no request/URL routing. Compose route state hoisted in one Activity is not a dispatch surface.

One inter-process boundary exists and is named rather than skipped: the outbound
`Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)` carrying `EXTRA_APP_PACKAGE`. It has a fixed
system-owned action, a single extra derived from `context.packageName`, no untrusted input, and it is
copied unchanged from shipped `TodayBanners.kt:82-89`. It introduces no new surface, so it produces no
matrix row and no RED test of its own.

## 15. Open questions

- [ ] **Blocking on sequencing, not on design:** the editor-cancel change must land before
      `sdd-apply` starts here (§2.1). If it slips, this change cannot compile against
      `HabitEditorRoute`.
- [ ] Unverified assumption (§8.3): that the api37 image shows the `POST_NOTIFICATIONS` dialog a
      second time after one denial. A fallback is designed; measure it in the first matrix run rather
      than reasoning about it further.
- [ ] For `sdd-tasks`, not for this phase: whether §2.3's accepted one-ask conservatism becomes a
      carried-forward item now or waits for a change that is already touching `NotificationPermission`.
