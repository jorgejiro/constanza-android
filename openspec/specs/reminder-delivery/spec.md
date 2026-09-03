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
