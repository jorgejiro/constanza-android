# Reminder Delivery Specification

## Purpose

Defines exact-alarm scheduling, its degrade path, permission-state handling, mandatory reschedule triggers, and the missed-reminder sweep.

## Requirements

### Requirement: Exact-Alarm Scheduling

The system MUST attempt exact, wake-capable scheduling for every due occurrence's reminder time. Because `minSdk = 31`, the system MUST NOT implement any pre-API-31 exact-alarm fallback path.

#### Scenario: Reminder fires at the exact configured time
- GIVEN exact-alarm scheduling eligibility is granted
- WHEN a slot's reminder time arrives
- THEN the notification is delivered at that exact time

### Requirement: Exact-Alarm Permission States

The system MUST check exact-alarm scheduling eligibility before every scheduling call, not only
once at app start. WHEN eligibility is denied, the system MUST degrade to an inexact window of at
least 10 minutes rather than silently failing to schedule. WHEN eligibility is revoked while
reminders are already scheduled, the system MUST detect the revocation and reschedule remaining
occurrences using the inexact fallback without requiring the user to reopen the app. Onboarding
(see `onboarding`) is the first surface that offers this permission; the Today banner (see
"Exact-Alarm Banner, Standing Fallback" below) is the standing fallback that remains available
regardless of what the user chose during onboarding, since declining or ignoring the onboarding row
spends nothing — the settings deep link is not one-shot and the system never silently refuses it.
(Previously: described only the eligibility check, the inexact-window degradation, and the
mid-session revocation/re-grant reschedule; did not name onboarding or the banner, and did not
state that nothing is spent by declining the onboarding offer.)

#### Scenario: Denied before habit creation still delivers, inexactly
- GIVEN exact-alarm eligibility is denied
- WHEN a habit with a reminder is created
- THEN its reminder is scheduled in an inexact window of at least 10 minutes and still arrives

#### Scenario: Revoked mid-session degrades already-armed reminders
- GIVEN reminders already scheduled exactly
- WHEN the user revokes exact-alarm permission
- THEN pending reminders are rescheduled to the inexact fallback rather than dropped

#### Scenario: Re-granted permission upgrades pending reminders
- GIVEN reminders currently on the inexact fallback
- WHEN the user re-grants exact-alarm permission
- THEN pending reminders are rescheduled to exact timing

#### Scenario: Declining onboarding's offer costs nothing later
- GIVEN the user declined the exact-alarm row during onboarding and eligibility is still denied
- WHEN they later open exact-alarm settings through any other surface
- THEN the system honours the request exactly as if onboarding had never offered it

### Requirement: Five Mandatory Reschedule Triggers

The system MUST re-arm every not-yet-fired occurrence on each of: device boot (`BOOT_COMPLETED`), this app's own update (`MY_PACKAGE_REPLACED`), timezone change (`ACTION_TIMEZONE_CHANGED`), date/time change including DST (`ACTION_DATE_CHANGED`/`ACTION_TIME_CHANGED`), and any in-app schedule edit. No other event MAY substitute for these five.

#### Scenario: Reminders survive a device reboot
- GIVEN pending reminders before a reboot
- WHEN the device finishes booting
- THEN every pending reminder is re-armed

#### Scenario: Reminders survive an app update
- GIVEN pending reminders before an app update
- WHEN the update finishes installing
- THEN every pending reminder is re-armed

#### Scenario: Timezone change re-arms at the correct local time
- GIVEN a reminder scheduled for 08:00 in the prior timezone
- WHEN the device timezone changes
- THEN the reminder re-arms for 08:00 in the new local timezone

### Requirement: Missed-Reminder Sweep

The system MUST periodically detect an occurrence whose reminder should have fired but did not, and fire it late rather than lose it silently.

#### Scenario: A dropped reminder still arrives late
- GIVEN a reminder that failed to fire due to OEM throttling
- WHEN the periodic sweep next runs
- THEN the notification is delivered late instead of never

### Requirement: Already-Answered Slots Stay Silent

An occurrence MUST NOT be armed, and MUST NOT fire, for a `(habit, date, slot)` whose `Entry`
already records an answer. "Answered" means exactly `COMPLETED` or `SKIPPED`. `MISSED` is
deliberately NOT answered: `habit-entry-tracking`'s Provisional-Missed Correction names an import as
one of the three paths that MUST be able to correct a `MISSED` into a `COMPLETED`, so treating
`MISSED` as settled would close a route this suite requires to stay open. `SKIPPED` is the opposite
case — `habit-entry-tracking` restricts it to an explicit in-app user action, so re-asking would
override a decision the user made on purpose. An absent `Entry` (`UNKNOWN`) is not answered and MUST
still be armed.

The suppression MUST apply at BOTH planning time and fire time, because the two catch different
failures. Planning-time suppression MUST prevent the occurrence row from being created at all,
which is what keeps the midnight sweep (see `habit-entry-tracking`: Midnight Transition) from ever
seeing it. Fire-time suppression is the net for an occurrence armed before the answer existed;
WHEN it suppresses, it MUST leave the occurrence in a state the unresolved-occurrence scan excludes,
so that staying silent never costs the user their recorded answer.

The answered `Entry` MAY carry the no-slot sentinel rather than the slot's own id — an answer given
before the habit had any reminder time is stored that way, and a reminder time added afterwards
mints a different id. Matching on the concrete slot id alone MUST NOT be treated as sufficient.

This generalises a principle the design already accepted rather than introducing a new one: the one
pre-existing silence rule in the suite (`habit-scheduling`'s `N_TIMES_PER_WEEK` quota suppression)
is likewise entry-derived, just narrower.

(Previously: no requirement addressed staying silent at all. Every requirement here pushed toward
delivering — "MUST re-arm every not-yet-fired occurrence", "fire it late rather than lose it
silently" — so a reminder for an already-completed slot violated nothing.)

#### Scenario: A completed slot is never armed
- GIVEN a habit whose slot for today is already recorded `COMPLETED`, with a reminder time earlier
  than the current moment
- WHEN occurrences are planned or re-planned for any reason
- THEN no occurrence is created for that slot and date, and no alarm is scheduled for it

#### Scenario: A skipped slot is never armed
- GIVEN a habit whose slot for today is already recorded `SKIPPED`
- WHEN occurrences are planned or re-planned
- THEN no occurrence is created for that slot and date

#### Scenario: A missed slot is still armed
- GIVEN a habit whose slot for a date is recorded `MISSED`
- WHEN occurrences are planned or re-planned
- THEN that slot is armed exactly as an unanswered one would be, so the answer that corrects the
  `MISSED` can still arrive

#### Scenario: An occurrence armed before the answer never posts
- GIVEN an occurrence armed for a slot that is answered `COMPLETED` before its reminder time arrives
- WHEN that occurrence's reminder time arrives
- THEN no notification is posted, and the occurrence transitions to a state the unresolved scan
  excludes, so the recorded answer survives the following midnight untouched

#### Scenario: An answer stored without a slot suppresses a later-added reminder time
- GIVEN a habit saved with no reminder time whose slot for today is answered `COMPLETED` and stored
  under the no-slot sentinel
- WHEN a reminder time is added to that habit the same day, minting a new slot id
- THEN nothing is armed for that slot today

#### Scenario: Only the answered date is suppressed
- GIVEN a daily habit whose slot for today is answered `COMPLETED`
- WHEN occurrences are planned
- THEN tomorrow's occurrence is armed normally — the suppression is scoped to the answered date, not
  to the habit

### Requirement: Exact-Alarm Banner, Standing Fallback

The system MUST render a Today banner communicating degraded reminder delivery WHEN exact-alarm
eligibility is denied, and MUST NOT render it WHEN eligibility is granted. The banner's visibility
MUST be derived from live eligibility, re-read at least on `ON_RESUME`, and MUST NOT depend on any
persisted record of whether the user was previously asked — unlike `POST_NOTIFICATIONS`, there is
no "we have asked" latch for this permission, and none is needed, because the deep link is always
available and the system never silently refuses it. The banner MUST NOT auto-launch the settings
intent; its action control MUST deep-link to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` only on
deliberate user interaction, and that control's label MUST describe what tapping it does rather
than name an unqualified fix affordance.

#### Scenario: Banner renders whenever eligibility is denied
- GIVEN a device where exact-alarm eligibility is denied
- WHEN Today loads
- THEN the banner renders, communicating the degraded delivery window

#### Scenario: Banner disappears once granted, no restart needed
- GIVEN the banner is visible
- WHEN the user grants exact-alarm permission via the deep link and returns to Today
- THEN the banner no longer renders, without requiring an app restart

#### Scenario: Declining onboarding's ask does not suppress the banner
- GIVEN the user declined the exact-alarm row during onboarding and eligibility is still denied
  afterward
- WHEN Today loads
- THEN the banner renders exactly as it would if onboarding had never offered the row

#### Scenario: Banner action deep-links and never auto-launches
- GIVEN the banner is visible
- WHEN it first renders
- THEN no settings intent launches until the user taps the banner's action control
