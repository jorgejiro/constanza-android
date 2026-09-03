# Reminder Response Specification

## Purpose

Defines the three notification actions, the API-33+ scope of the notification permission, snooze configuration, and origin-date crediting.

## Requirements

### Requirement: Notification Actions

Every reminder notification MUST offer exactly three actions: Yes, No, Snooze. `SKIPPED` MUST NOT be offered as a notification action. Each action MUST write, or update per the provisional-missed rule, the `Entry` for that exact occurrence without requiring the app to be opened. The channel name, the notification body, and all three action labels MUST render in the app's resolved language (see `app-localization`), including when the notification is posted from a cold process with no Activity ever created.

#### Scenario: Answering Yes from the drawer completes the habit
- GIVEN a delivered reminder notification
- WHEN the user taps "Yes"
- THEN the corresponding `Entry` becomes `COMPLETED` without opening the app

#### Scenario: A cold-process notification renders in the overridden language
- GIVEN the language override is set to Español and the app process has been killed
- WHEN a scheduled reminder fires with no Activity created since the kill
- THEN the channel name, the notification body, and all three action labels ("Sí", "No",
  "Aplazar") render in Spanish

### Requirement: Notification Permission Scope

On API 33 and above, notification delivery MUST be gated by the runtime `POST_NOTIFICATIONS` permission. On API 31–32, notifications MUST be delivered with no runtime prompt, since that permission does not exist below API 33. WHEN `POST_NOTIFICATIONS` is denied on API 33+, the today screen MUST remain fully usable for answering habits in-app.

Onboarding (see `onboarding`) is the primary requester of this permission, asked once inside the first-run flow. Today's notification-permission banner is the safety net, not a second primary ask: it covers installs where onboarding has not recorded the ask, whether because onboarding predates the install's update or because the latch was otherwise never written. The `requested_notification_permission` latch has exactly two writers — onboarding's permission screen and `TodayViewModel` — and MUST be written exactly once per system dialog actually shown, by whichever surface showed it. Neither writer MUST write the latch without having shown the dialog. The banner MUST NOT re-invoke the runtime prompt once the decision is `BLOCKED`; it MUST render the blocked variant with a settings deep link instead, so it never offers a prompt the system will silently refuse to show.
(Previously: described only the API-level gating and the in-app usability guarantee; did not name onboarding, did not name the latch's two writers, and did not state which write is primary.)

#### Scenario: API 33+ denial still allows in-app answering
- GIVEN a device on API 33+ that denied `POST_NOTIFICATIONS`
- WHEN a habit becomes due
- THEN no notification is delivered, but the today screen still allows answering it

#### Scenario: API 31 device gets notifications with no prompt
- GIVEN a device on API 31
- WHEN the app requests to show a reminder
- THEN the notification is delivered without any runtime permission dialog

#### Scenario: Onboarding's ask writes the latch, and Today does not re-write it
- GIVEN a fresh install reaching onboarding's permission screen on API 37
- WHEN the user answers the system dialog
- THEN `requested_notification_permission` becomes true, written by onboarding, and Today does not
  write it again for that same decision

#### Scenario: The banner is the fallback where onboarding never wrote the latch
- GIVEN an install where `onboarding_done` is true but `requested_notification_permission` was
  never recorded
- WHEN Today loads and the decision is `SHOULD_REQUEST`
- THEN the banner renders and its own request writes the latch

#### Scenario: The banner never re-prompts once blocked
- GIVEN Today's decision is `BLOCKED`
- WHEN the user taps the banner
- THEN it opens `ACTION_APP_NOTIFICATION_SETTINGS` and does not invoke the runtime permission dialog

### Requirement: Snooze Configuration and Re-arm

Snooze default MUST be 20 minutes; the user MUST be able to change it to one of 10, 20, 30 minutes or 1, 2, 3, 4 hours. Snoozing MUST be unlimited — the system MUST NOT cap how many times a single occurrence may be snoozed. Each snooze MUST re-arm a new reminder at now + the configured duration for the same occurrence.

#### Scenario: Default snooze re-arms 20 minutes later
- GIVEN a reminder with default snooze settings
- WHEN the user taps "Snooze"
- THEN a new reminder for the same occurrence fires 20 minutes later

#### Scenario: Unlimited re-snoozing
- GIVEN an occurrence already snoozed five times
- WHEN the sixth snoozed reminder fires
- THEN "Snooze" is still offered and functions identically

### Requirement: Origin-Date Crediting

Regardless of the calendar date on which a snoozed reminder is finally answered, the resulting `Entry` MUST be attributed to the occurrence's originally-scheduled date, per the provisional-missed requirement in `habit-entry-tracking`.

#### Scenario: Answer given after midnight credits the prior day
- GIVEN a slot originally due 2026-09-01, snoozed past midnight
- WHEN the user answers at 2026-09-02 00:40
- THEN the resulting `Entry` is recorded against 2026-09-01, not 2026-09-02
