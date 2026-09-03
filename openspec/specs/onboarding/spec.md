# Onboarding Specification

## Purpose

Defines the first-run flow that introduces the app and requests the notification permission at a
moment with context, then hands off into creating the user's first habit.

## Requirements

### Requirement: Once-Per-Install Onboarding Gate

The system MUST show onboarding before any other screen on a fresh install, and MUST NOT show it
again once completed on that install.

#### Scenario: Fresh install shows onboarding first
- GIVEN a fresh install with `onboarding_done` unset
- WHEN the app launches
- THEN onboarding renders before Today or any other screen

#### Scenario: Completed onboarding never reappears
- GIVEN onboarding already completed on this install
- WHEN the app is relaunched
- THEN the app opens directly to the main flow and onboarding does not render

### Requirement: Two-Screen Flow, Applicability-Derived

Onboarding MUST present an app-explanation screen first. A second screen MUST exist WHEN AND ONLY
WHEN at least one of the two permission asks — `POST_NOTIFICATIONS` and `SCHEDULE_EXACT_ALARM` —
currently applies to the device and is not already satisfied. WHEN the second screen exists, it
MUST render exactly the rows that apply and MUST order the notification row before the exact-alarm
row, reflecting delivery severity (a denied notification silences the app; a denied exact alarm
only widens the delivery window) — not a ranking of the two asks, which carry equal visual and
interactive weight. WHEN neither ask applies and is unsatisfied, the second screen MUST NOT exist
and onboarding MUST finish after screen 1.
(Previously: titled "Two-Screen Flow, API-Conditional"; screen 2's existence was decided by API
level alone — present on API 33+, absent below it — because `POST_NOTIFICATIONS` does not exist
below API 33. That justification no longer covers `SCHEDULE_EXACT_ALARM`, which exists from API 31,
so screen 2's existence is now derived from applicability instead.)

#### Scenario: API 37 fresh install shows both rows, notifications first
- GIVEN a fresh install on API 37
- WHEN the user completes screen 1
- THEN screen 2 renders both rows, with the notification row above the exact-alarm row

#### Scenario: API 31 fresh install has nothing to ask
- GIVEN a fresh install on API 31 where notifications are `NOT_APPLICABLE` and exact alarms are
  granted by default (pre-Android-14)
- WHEN the user completes screen 1
- THEN screen 2 does not exist and onboarding finishes directly

#### Scenario: API 31 with exact alarms revoked shows one row
- GIVEN an API 31 install where the user has revoked `SCHEDULE_EXACT_ALARM` and notifications
  remain `NOT_APPLICABLE`
- WHEN the user completes screen 1
- THEN screen 2 renders exactly one row, for exact alarms

#### Scenario: API 37 with exact alarms already granted shows a confirmation, not an ask
- GIVEN an API 37 device where `SCHEDULE_EXACT_ALARM` is already granted and `POST_NOTIFICATIONS`
  is still undecided
- WHEN the user completes screen 1
- THEN screen 2 renders both rows: the notification row is the only one still asking for action,
  and the exact-alarm row renders its granted confirmation line, with no button

### Requirement: Non-Blocking Permission Ask

Neither permission ask MUST block onboarding completion. Onboarding MUST complete regardless of
whether `POST_NOTIFICATIONS` is granted or denied, and regardless of whether the user grants,
denies, or simply returns from the exact-alarm settings deep link without changing anything. The
app MUST remain a usable manual tracker afterward.
(Previously: covered only the notification permission ask; did not mention the exact-alarm deep
link.)

#### Scenario: Notification denial still completes onboarding
- GIVEN the user is on screen 2 on API 37
- WHEN they deny the system notification dialog
- THEN onboarding still completes and hands off into habit creation

#### Scenario: Notification grant completes onboarding
- GIVEN the user is on screen 2 on API 37
- WHEN they grant the system notification dialog
- THEN onboarding completes and hands off into habit creation

#### Scenario: Returning from exact-alarm settings without granting still completes onboarding
- GIVEN the user is on screen 2 and taps the exact-alarm row, deep-linking into settings
- WHEN they return without granting the permission
- THEN onboarding still completes normally

#### Scenario: Granting exact alarms and returning still completes onboarding
- GIVEN the user is on screen 2 and taps the exact-alarm row, deep-linking into settings
- WHEN they grant the permission and return
- THEN onboarding completes and hands off into habit creation

### Requirement: Permission Screen Never Offers A Prompt The System Will Silently Refuse

WHEN the permission is already permanently denied (`BLOCKED`) on entry to screen 2, the screen MUST
render the blocked variant with a deep link to `ACTION_APP_NOTIFICATION_SETTINGS` and MUST NOT
re-invoke the runtime prompt. `BLOCKED` is one recorded ask plus no grant — not two denials — so it
is reachable in a single instrumented step: deny the real dialog once during onboarding, and the
state is observable on relaunch.

#### Scenario: Blocked state on entry offers the settings deep link
- GIVEN the permission is already `BLOCKED` before onboarding starts
- WHEN the user reaches screen 2
- THEN it renders the blocked variant with a settings deep link instead of the runtime prompt

### Requirement: Exact-Alarm Onboarding Row

WHEN the exact-alarm row is present on screen 2, its copy MUST state that reminders still arrive,
only degraded to a wider delivery window, and MUST NOT imply delivery stops. It MUST NOT be
presented, visually or textually, as required to proceed. Tapping it MUST deep-link to
`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`; the screen MUST NOT auto-launch that intent on its own. The
row's visibility MUST re-derive from live eligibility on `ON_RESUME`, since the deep link leaves
the app, and it MUST disappear without an app restart once the permission is granted.

#### Scenario: Row copy states degradation, not silence
- GIVEN the exact-alarm row renders on screen 2
- WHEN its text is read
- THEN it states that reminders still arrive, only less precisely timed, and does not say they
  will stop

#### Scenario: Row never auto-launches the settings intent
- GIVEN screen 2 renders with the exact-alarm row
- WHEN the row first appears
- THEN no settings intent launches until the user deliberately taps the row

#### Scenario: Granting via the deep link updates the screen without restart
- GIVEN the user taps the exact-alarm row and grants the permission in settings
- WHEN they return to onboarding, triggering `ON_RESUME`
- THEN the exact-alarm row disappears without restarting onboarding

### Requirement: Completion Commits At Handoff, Never On A Content Outcome

The system MUST commit `onboarding_done = true` at the moment onboarding hands off to habit
creation, never gated on whether a habit is subsequently created or saved.

#### Scenario: Leaving the editor without saving does not reopen onboarding
- GIVEN onboarding just handed off to the habit editor
- WHEN the user presses back without saving a habit
- THEN a subsequent relaunch does not show onboarding again

### Requirement: Finish Handoff Into Habit Creation With A Back Escape

Onboarding MUST finish by opening the habit editor for a new habit. Pressing system back from that
onboarding-seeded editor entry MUST navigate to Today, not close the app.

#### Scenario: Finishing onboarding opens the editor
- GIVEN the user completes screen 2 (or screen 1 alone on API 31-32)
- WHEN onboarding finishes
- THEN the habit editor opens for a new habit

#### Scenario: Back from the seeded editor entry reaches Today
- GIVEN the onboarding-seeded habit editor is open
- WHEN the user presses system back
- THEN Today renders and the app does not close
