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

### Requirement: Two-Screen Flow, API-Conditional

Onboarding MUST present exactly two screens on API 33+: an app-explanation screen, then a
notification-context screen that requests `POST_NOTIFICATIONS`. On API 31-32, the second screen
MUST NOT exist, since the permission does not exist below API 33 and a screen asking for it would
be dishonest about what the tap does.

#### Scenario: API 37 shows both screens
- GIVEN a fresh install on API 37
- WHEN the user completes screen 1
- THEN screen 2 renders and requests the notification permission

#### Scenario: API 31 shows only screen 1
- GIVEN a fresh install on API 31
- WHEN the user completes screen 1
- THEN onboarding finishes without ever rendering a permission screen

### Requirement: Non-Blocking Permission Ask

The notification permission ask MUST NOT block onboarding completion. Onboarding MUST complete
regardless of whether the permission is granted or denied, and the app MUST remain a usable manual
tracker afterward.

#### Scenario: Denial still completes onboarding
- GIVEN the user is on screen 2 on API 37
- WHEN they deny the system notification dialog
- THEN onboarding still completes and hands off into habit creation

#### Scenario: Grant completes onboarding
- GIVEN the user is on screen 2 on API 37
- WHEN they grant the system notification dialog
- THEN onboarding completes and hands off into habit creation

### Requirement: Permission Screen Never Offers A Prompt The System Will Silently Refuse

WHEN the permission is already permanently denied (`BLOCKED`) on entry to screen 2, the screen MUST
render the blocked variant with a deep link to `ACTION_APP_NOTIFICATION_SETTINGS` and MUST NOT
re-invoke the runtime prompt. Reaching `BLOCKED` requires two denials within the same install and is
not reachable on the device-free instrumented matrix, because the harness cannot script two real
system-dialog denials in one continuous automated run; this state mapping is verified by a unit test
instead.

#### Scenario: Blocked state on entry offers the settings deep link
- GIVEN the permission is already `BLOCKED` before onboarding starts
- WHEN the user reaches screen 2
- THEN it renders the blocked variant with a settings deep link instead of the runtime prompt

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
