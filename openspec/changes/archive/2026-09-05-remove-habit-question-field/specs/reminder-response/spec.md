# Delta for Reminder Response

## MODIFIED Requirements

### Requirement: Notification Actions

Every reminder notification MUST offer exactly three actions: Yes, No, Snooze. `SKIPPED` MUST NOT
be offered as a notification action. Each action MUST write, or update per the provisional-missed
rule, the `Entry` for that exact occurrence without requiring the app to be opened. The channel
name, the notification title, and all three action labels MUST render in the app's resolved
language (see `app-localization`), including when the notification is posted from a cold process
with no Activity ever created. The notification body carries the user's habit name verbatim and
MUST NOT be localized or otherwise translated: it is user-authored content, not application copy.
(Previously: guaranteed the language render on the notification body; the body now carries the
user's habit name verbatim, so the language guarantee moves to the title.)

#### Scenario: Answering Yes from the drawer completes the habit
- GIVEN a delivered reminder notification
- WHEN the user taps "Yes"
- THEN the corresponding `Entry` becomes `COMPLETED` without opening the app

#### Scenario: A cold-process notification renders in the overridden language
- GIVEN the language override is set to Español and the app process has been killed
- WHEN a scheduled reminder fires with no Activity created since the kill
- THEN the channel name, the notification title ("Seguimiento de hábitos"), and all three action
  labels ("Sí", "No", "Aplazar") render in Spanish, while the body still shows the habit's name
  exactly as entered

#### Scenario: The notification body is never localized
- GIVEN a habit and the app's resolved language set to any override
- WHEN a reminder fires for that habit
- THEN the notification body shows the habit's name unchanged, regardless of the resolved language
