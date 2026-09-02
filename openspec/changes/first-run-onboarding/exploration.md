# Exploration: first-run-onboarding

Artifact store: hybrid. Engram topic key `sdd/first-run-onboarding/explore`.

## Scope note

The survey of `MainActivity`, the DataStore inventory, the notification-permission decision table,
the Today banners, the design tokens and the `bebe-agua-android` reference implementation was
carried out by the orchestrator and is treated as ratified input, not re-derived here. This
document covers only the questions that remained genuinely open. The same convention was used by
`warm-dark-design-system`.

**Ratified product decision, not re-litigated:** onboarding owns the notification ask; the Today
banner becomes the safety net for users who deny it and for installs that predate onboarding.

## Q1 — Where the gate goes

`ConstanzaApp()` (`MainActivity.kt:82-110`) seeds its route with
`rememberSaveable { mutableStateOf<ConstanzaRoute>(Today) }`. That initial-value producer runs
**synchronously**, and the onboarding-done flag needs a suspend DataStore read, which cannot
resolve in time.

Two shapes were weighed:

1. **Add `Onboarding` to `ConstanzaRoute`.** Rejected. The synchronous initial value cannot reflect
   an async read without either an always-wrong default (a one-frame flash of the wrong screen) or
   a nested tri-state gate anyway — at which point the sealed member buys nothing, while adding a
   route with no back-target to a type built for rotation-survival of mid-edit screens.
2. **Wrapper gate composable in `setContent`** — *recommended*. Tri-state `Boolean?`
   (`null` → blank hold, `false` → onboarding, `true` → `ConstanzaApp()`), mirroring the proven
   shape at `bebe-agua-android`'s `NavGraph.kt:51-59`. No rotation hazard: the state is derived via
   `collectAsState`, so no `Saver` is needed and `ConstanzaRoute`'s contract is untouched. The cost
   is a second top-level state holder, which must be documented.

## Q2 — Test blast radius

**Correction to the exploration's stated evidence.** Four androidTest files mention `MainActivity`,
not two: `e2e/CoreFlowE2ETest.kt`, `core/ui/DarkChromeInstrumentedTest.kt`,
`scheduling/ReplanOnResumeObserverTest.kt` and `seed/ImminentReminderSeed.kt`. Verified: the last
two mention it only in KDoc prose (`ReplanOnResumeObserverTest.kt:25-28`,
`ImminentReminderSeed.kt:66`) and never launch it. The conclusion therefore stands — only two files
launch the Activity — but the count as originally written was wrong.

- **Unaffected:** every other Compose instrumented test sets content directly on a presentational
  screen and bypasses the Activity (`TodayComposeTest.kt:46`, `TodayAdaptiveComposeTest.kt:59`,
  `EntryWriteParityTest.kt`, the habit editor/list tests).
- **Unaffected:** `DarkChromeInstrumentedTest.kt:139` reads window-inset-controller state set in
  `onCreate()` before `setContent`, so a gate above the content does not change what it observes.
- **Breaks 4/4:** `CoreFlowE2ETest.kt`. Its `launchApp()` (`:313-316`) awaits `today_title`
  immediately after launch, so every test fails at the gate.
  - `:141` `allowingNotificationsThroughTheRealSystemDialogClearsTheTodayBanner` currently proves
    the fresh-install `SHOULD_REQUEST` banner and the real dialog grant **on Today**. Once
    onboarding owns the ask this assertion is meaningless there. Replacement: drive onboarding's own
    permission control, accept the real system dialog inside onboarding, then assert Today shows no
    banner. Add the sibling scenario that is **currently untested** — deny the dialog during
    onboarding and assert Today's safety net renders the `BLOCKED` variant
    (`today_notification_permission_open_settings`).
  - `:182` `apiBelow33ShowsNoNotificationBannerBecauseThePermissionDoesNotExist` must walk through
    onboarding first (there is nothing to grant on API 31-32) and then keep its absence assertions.
  - `:220` and `:261` need the onboarding-done flag pre-seeded before `ActivityScenario.launch`.

## Q3 — Test state isolation

**No DataStore reset infrastructure exists.** `CoreFlowTestFixture.reset()` (`:61-64`) clears Room
(`database.habitDao().deleteAll()`) and cancels notifications, and nothing else. The
`reminder_settings` DataStore file (`DataStoreModule.kt:16`) is never reset between test methods or
classes. This is consistent with `CoreFlowE2ETest`'s own KDoc describing permission grants as a
"one-way door within an installation" and ordering its methods around that fact.

Consequence: pre-seeding `onboarding_done` is **net-new infrastructure**, with its own reset
discipline to design. This is the single largest hidden cost in the change.

## Q4 — Latch relocation

`requested_notification_permission` has exactly one production writer today:
`TodayViewModel.recordNotificationPermissionRequested()` (`:138-143`), reached from the launcher
callback at `TodayBanners.kt:67-69`. Reads are at `TodayViewModel.kt:130-132` and `:145-148`.

Minimal change: onboarding's permission screen gets its own launcher and calls the **same**
`reminderSettingsStore.recordRequestedNotificationPermission()`. `ReminderSettingsStore` is an
unqualified `@Singleton`, so no new store and no Hilt qualifier are required. `TodayViewModel`'s
write path is **kept**, not deleted — it remains the safety net's own write for installs that
predate onboarding.

Only one genuinely new key is needed (`onboarding_done`), and it belongs in the existing DataStore
file per the precedent its own module states (`DataStoreModule.kt:18-21`).

## Q5 — What first run actually shows today

`TodayScreen.kt:136-138` renders a single bare string, `today_empty` = "Nothing due today."
(`strings.xml:65`). The only route to habit creation is the TopAppBar "Manage habits" action
(`:99-104`) leading to `HabitListScreen.kt:80-81`'s FAB.

This is already tracked as the open item `today-has-no-add-habit-affordance`
(`openspec/config.yaml:102`). Onboarding must not depend on fixing it, but onboarding is what makes
this empty state the guaranteed first thing every new user sees, so the finish destination is a
real decision this exploration surfaces without resolving.

## Q6 — Scope boundary

The archived design (`openspec/changes/archive/2026-09-01-habit-tracking-mvp/design.md:1011-1017`)
explicitly rejects unsolicited first-launch system-settings prompts of the battery/exact-alarm
class as "poor UX and Play-policy discouraged". That reasoning is unchanged and keeps exact-alarm
out of onboarding's two-screen scope, on its existing contextual `ExactAlarmBanner`. Nothing found
here reopens it.

## Affected areas

| Path | Nature |
| --- | --- |
| `core/ui/MainActivity.kt` | Gate insertion (Q1) |
| `reminding/ReminderSettingsStore.kt` | New `onboarding_done` key; second call site for the existing latch write (Q4) |
| `tracking/{TodayViewModel,TodayBanners,TodayScreen}.kt` | Logic unchanged; role becomes safety net |
| `e2e/{CoreFlowE2ETest,CoreFlowTestFixture}.kt` | Rework plus net-new seeding infrastructure (Q2, Q3) |
| New onboarding package | Does not exist yet |

## Risks

1. `CoreFlowE2ETest` breaks in all four tests. Strategy needs sign-off before `sdd-tasks`.
2. No DataStore test-seeding infrastructure exists; this is net-new work.
3. `today-has-no-add-habit-affordance` is adjacent scope creep. The finish destination must be an
   explicit decision, not an implicit default.
4. Two documented defects in the reference implementation must not be ported: its missing
   permanent-denial handling (`bebe-agua-android` `OnboardingScreen.kt:492`, a dead button after two
   denials — Constanza's `BLOCKED` state already solves this and must not lose it), and its
   hardcoded layout offsets (`bottom = 160.dp` repeated across three pages).

## Open decisions for sdd-propose

1. Pre-seed versus walk-through for `CoreFlowE2ETest`.
2. Where `onboarding_done` lives, and where its test-seeding helper goes.
3. Onboarding's finish destination, given the open Today-affordance gap.
