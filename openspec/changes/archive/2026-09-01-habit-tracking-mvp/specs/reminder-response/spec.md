# Reminder Response Specification

## Purpose

Defines the three notification actions, the API-33+ scope of the notification permission, snooze configuration, and origin-date crediting.

## Requirements

### Requirement: Notification Actions

Every reminder notification MUST offer exactly three actions: Yes, No, Snooze. `SKIPPED` MUST NOT be offered as a notification action. Each action MUST write, or update per the provisional-missed rule, the `Entry` for that exact occurrence without requiring the app to be opened.

#### Scenario: Answering Yes from the drawer completes the habit
- GIVEN a delivered reminder notification
- WHEN the user taps "Yes"
- THEN the corresponding `Entry` becomes `COMPLETED` without opening the app

### Requirement: Notification Permission Scope

On API 33 and above, notification delivery MUST be gated by the runtime `POST_NOTIFICATIONS` permission. On API 31–32, notifications MUST be delivered with no runtime prompt, since that permission does not exist below API 33. WHEN `POST_NOTIFICATIONS` is denied on API 33+, the today screen MUST remain fully usable for answering habits in-app.

#### Scenario: API 33+ denial still allows in-app answering
- GIVEN a device on API 33+ that denied `POST_NOTIFICATIONS`
- WHEN a habit becomes due
- THEN no notification is delivered, but the today screen still allows answering it

#### Scenario: API 31 device gets notifications with no prompt
- GIVEN a device on API 31
- WHEN the app requests to show a reminder
- THEN the notification is delivered without any runtime permission dialog

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
