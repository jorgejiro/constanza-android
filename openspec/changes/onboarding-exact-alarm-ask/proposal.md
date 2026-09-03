# Proposal: Ask For Exact Alarms During Onboarding

## Intent

Constanza asks for one of its two reminder permissions during onboarding and surfaces the other
only later, as a Today banner reading "Reminders may arrive a few minutes late on this device."
behind a button labelled **Fix**. A user who has not yet created a habit cannot know what is being
fixed. Three sibling apps (Bebe Agua, Sleep Noise, Aquí Hay Tomate) ask for both up front, on one
screen.

This is the common path, not an edge case: the app declares `SCHEDULE_EXACT_ALARM` (never
`USE_EXACT_ALARM`) and targets API 37, and Android 14+ denies that permission by default for apps
targeting API 33+. Every new user on a modern device meets the banner. Confirmed on the
maintainer's Galaxy S25.

## The asymmetry this proposal is built on

| Permission | When denied | Ask shape | Repeatable |
|---|---|---|---|
| `POST_NOTIFICATIONS` | **No reminder arrives at all.** The app is mute | one-shot runtime dialog | No — spent once |
| `SCHEDULE_EXACT_ALARM` | Reminders **still arrive**, degraded to a 10-minute window (`AlarmScheduler.kt:29-38`) | deep link into system settings | Yes — indefinitely |

The second is worth asking for, but never worth pressing. Nothing is spent by not asking now.

## Recommendation, argued rather than assumed

**Do it — and here is what it costs.** The exact-alarm ask is not a runtime dialog. It is
`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`: a full context switch out of the app into a system settings
screen, before the user has created a single habit. That is a heavier interruption than the
notification prompt it will sit beside.

Against that: a button labelled "Fix" that explains nothing is worse. And the degradation is
otherwise silent — nothing in the product ever tells the user their reminders are approximate.

So: **one explanatory row on the existing screen 2 — never an auto-launched intent, never a gate,
and rendered only while eligibility is actually denied.**

## Scope

### In Scope
- A second permission row on onboarding screen 2 for `SCHEDULE_EXACT_ALARM`, sibling to the
  existing notification row, reusing `OnboardingPermissionAction`'s state-driven shape.
- Screen-2 existence derived from applicability instead of API level (see Approach).
- `OnboardingViewModel` reads `AlarmScheduler.canScheduleExactAlarms()` and re-reads it on
  `ON_RESUME`, since the deep link leaves the app.
- Copy alignment so the onboarding row and `ExactAlarmBanner` say the same thing; retire "Fix".
- Spec deltas for `onboarding` and `reminder-delivery`.

### Out of Scope
- Any `requested_exact_alarm_permission` latch, `ReminderSettingsStore` field, or DataStore
  migration — see "What happens to the banner".
- Removing, gating, or relocating `ExactAlarmBanner`.
- `NotificationPermission`'s Activity-free `BLOCKED` approximation (carried-forward item
  `notification-permission-blocked-after-one-ask`).
- Battery-optimisation and Doze exemption prompts. Different permission: the archived rejection of
  unsolicited first-launch prompts at `archive/2026-09-01-habit-tracking-mvp/design.md:1011-1017`
  is about `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, not exact alarms, and is not a blocker
  here.
- Changing which permissions the manifest declares.
- Porting Bebe Agua's implementation: it has no permanent-denial handling and hardcodes layout
  offsets.

## Approach

### The API conditional inverts

The published requirement says screen 2 MUST NOT exist on API 31-32 "since the permission does not
exist below API 33 and a screen asking for it would be dishonest about what the tap does".
`SCHEDULE_EXACT_ALARM` exists from API 31, so that justification no longer covers the whole screen.
Replace the API-level rule with a derived one:

> Screen 2 exists when at least one permission ask currently applies, and renders exactly the rows
> that apply.

| Leg | Notifications | Exact alarms | Screen 2 |
|---|---|---|---|
| API 37, fresh install | applicable | denied by default | both rows |
| API 31-32, fresh install | `NOT_APPLICABLE` | granted by default (pre-Android-14) | does not exist |
| API 31-32, revoked or cleared | `NOT_APPLICABLE` | denied | one row |

The API 31 outcome the current spec asserts survives — but as an observed consequence, not as a
constant. That distinction is the point: an API-level literal would now be wrong for row two.

### Non-blocking is already structural, and stays that way

The primary action is a `Scaffold` `bottomBar` sibling that never routes through any permission
control (`OnboardingScreen.kt:41-62`). The new row inherits that guarantee unchanged, and
`onboarding_done` still commits at handoff. Both permissions must be deniable with onboarding
still completing.

### What happens to the banner: it stays, unchanged and unlatched

The notification banner needed a "we have asked" latch because the runtime prompt is one-shot and
the app must not offer a prompt the system will silently refuse. **There is no exact-alarm
equivalent and none is needed.** The affordance is a settings deep link the system always honours,
so nothing can be spent. Its visibility already derives from live state
(`!state.canScheduleExactAlarms`, re-read on `ON_RESUME`), which is a strictly better signal than a
latch: it disappears the moment the permission is granted and returns the moment it is revoked.

Onboarding becomes the **first** offer; the banner remains the **standing** one. No persistence
change, no migration.

### Provable device-free

Both branches drive off one injectable boolean. `TodayViewModelTestFactory.exactAlarmsAllowedScheduler()`
already establishes the pattern — a MockK `AlarmScheduler` stubbed to `true` precisely because the
real default arms the banner and shifts unrelated assertions. Tests MUST drive the branch off that
injection, never off the emulator's default grant state, or they would assert the emulator's
configuration rather than the product's rule.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `onboarding`: `Two-Screen Flow, API-Conditional` → derived-applicability rule (retitled);
  `Non-Blocking Permission Ask` → covers both permissions. Plus one ADDED requirement: the
  exact-alarm row MUST state that reminders still arrive, MUST NOT be presented as required, and
  MUST NOT auto-launch the settings intent.
- `reminder-delivery`: `Exact-Alarm Permission States` → names onboarding as the first offer
  surface and the Today banner as the standing fallback. This relationship has no spec home today:
  `ExactAlarmBanner` is implemented but unspecified — no requirement in `openspec/specs/` mentions
  it.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `app/src/main/kotlin/.../onboarding/OnboardingViewModel.kt` | Modified | Inject `AlarmScheduler`; third state source; applicability-derived page list; `ON_RESUME` re-read |
| `app/src/main/kotlin/.../onboarding/OnboardingScreen.kt` | Modified | Screen 2 hosts two rows; page renamed away from `Notifications` |
| `app/src/main/kotlin/.../onboarding/OnboardingPermissionAction.kt` | Modified | Add the exact-alarm control beside the notification one |
| `app/src/main/kotlin/.../onboarding/OnboardingRoute.kt` | Modified | Refresh both permissions on `ON_RESUME` |
| `app/src/main/res/values/strings.xml` | Modified | New row copy; retire `today_exact_alarm_banner_action` = "Fix" |
| `app/src/main/kotlin/.../tracking/TodayBanners.kt` | Modified | Copy only — behaviour untouched |
| `app/src/test/.../onboarding/` | Modified | `OnboardingViewModelTest`, `OnboardingUiStateTest` |
| `app/src/androidTest/.../onboarding/` | New | First instrumented onboarding test; none exists today |
| `openspec/specs/onboarding`, `openspec/specs/reminder-delivery` | Modified | Delta specs |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| **Play policy unverified.** `SCHEDULE_EXACT_ALARM` is a restricted permission. This phase has no network access and could not check current Play Console policy. The repository's own last verification (2026-08-31, archived exploration) covers only *which* permission to declare, not whether offering it during onboarding carries a constraint. Stated as unknown rather than guessed. | Med | Verify against Play Console Help before release. Mitigating fact: this change alters nothing the manifest declares and adds no new declaration — only *when* the app offers an existing deep link. |
| Two consecutive permission asks on one screen read as nagging | Med | Explanation-first copy; no auto-launch; row hidden entirely when already granted |
| User leaves for settings and does not return; process death restarts onboarding at screen 1 | Med | `ON_RESUME` re-read already handles return. Process death is existing behaviour (`index` is a plain `MutableStateFlow`, no `SavedStateHandle`) — record as a known limit, do not silently widen scope to fix it |
| Screen 2 renders with no actionable row (both already granted) | Low | Derived rule excludes it; the existing `GRANTED` confirmation-line treatment covers the mid-flow grant case |
| PR exceeds the 800-line review budget | **High** | See forecast below — this needs a decision before `sdd-tasks` |

### Size forecast, measured

Measured against the nearest neighbour, `archive/2026-09-02-first-run-onboarding`, whose SDD
artifacts totalled **2,003 lines** (design 646, proposal 279, tasks 236, apply-progress 246,
verify-report 221, spec deltas 144, exploration 132, archive-report 99).

| Half | Estimate | Fits 800? |
|---|---|---|
| Production code + tests | 350–510 lines | Yes |
| SDD artifacts + spec deltas | 900–1,200 lines | No |
| **Total PR** | **1,250–1,700 lines** | **No** |

The code half is comfortable. The artifact half is what breaks the budget, and it is the half a
previous proposal undershot by 3×. This change introduces **no new architecture** — it reuses the
scaffold, the permission-control shape, the `ON_RESUME` idiom, and the injectable-scheduler test
pattern verbatim — so a 150–250 line `design.md` is defensible and the 646-line precedent is not a
target. Recommend capping design.md explicitly; the fallback is an accepted `size:exception` for
the artifact half only.

## Rollback Plan

Required by `rules.proposal` because this touches scheduling/alarms.

Revert is clean and carries no data risk: **no persisted state changes.** No `ReminderSettingsStore`
field, no DataStore key, no Room migration, no manifest change, and `AlarmScheduler` itself is not
modified — the change only *reads* `canScheduleExactAlarms()` from a second call site. Reverting the
commit restores the previous onboarding page list and leaves `ExactAlarmBanner` as the sole offer
surface, exactly as today. Users who granted the permission through the new row keep it, because the
grant lives in the system, not in the app.

## Dependencies

None. `AlarmScheduler` is already `@Inject`-constructed and already consumed by `TodayViewModel`.

## Success Criteria

- [ ] On API 37 fresh install, screen 2 renders both permission rows.
- [ ] The exact-alarm row states that reminders still arrive, degraded — it never implies they stop.
- [ ] The row never auto-launches settings; it requires a deliberate tap.
- [ ] Onboarding completes with either or both permissions denied.
- [ ] Granting exact alarms in settings and returning updates screen 2 without a restart.
- [ ] On API 31-32 with exact alarms granted and notifications not applicable, screen 2 does not exist.
- [ ] `ExactAlarmBanner` still renders on Today whenever eligibility is denied, including after
      onboarding declined it — no latch suppresses it.
- [ ] No new persisted field and no Room migration in the diff.
- [ ] `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` green on both legs, with both branches
      driven by the injected scheduler rather than the emulator's default grant state.
