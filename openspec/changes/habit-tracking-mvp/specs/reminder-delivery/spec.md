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

The system MUST check exact-alarm scheduling eligibility before every scheduling call, not only once at app start. WHEN eligibility is denied, the system MUST degrade to an inexact window of at least 10 minutes rather than silently failing to schedule. WHEN eligibility is revoked while reminders are already scheduled, the system MUST detect the revocation and reschedule remaining occurrences using the inexact fallback without requiring the user to reopen the app.

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
