# Delta for Reminder Response

## MODIFIED Requirements

### Requirement: Notification Actions

Every reminder notification MUST offer exactly three actions: Yes, No, Snooze. `SKIPPED` MUST NOT
be offered as a notification action. Each action MUST write, or update per the provisional-missed
rule, the `Entry` for that exact occurrence without requiring the app to be opened. The channel
name, the notification body, and all three action labels MUST render in the app's resolved
language (see `app-localization`), including when the notification is posted from a cold process
with no Activity ever created.
(Previously: described only the three actions, the `SKIPPED` exclusion, and the app-free write
guarantee; did not state a language guarantee.)

#### Scenario: Answering Yes from the drawer completes the habit
- GIVEN a delivered reminder notification
- WHEN the user taps "Yes"
- THEN the corresponding `Entry` becomes `COMPLETED` without opening the app

#### Scenario: A cold-process notification renders in the overridden language
- GIVEN the language override is set to Español and the app process has been killed
- WHEN a scheduled reminder fires with no Activity created since the kill
- THEN the channel name, the notification body, and all three action labels ("Sí", "No",
  "Aplazar") render in Spanish
