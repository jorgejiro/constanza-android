# Habit Scheduling Specification

## Purpose

Defines the six supported frequency kinds, the reminder-slot model for multiple-times-per-day habits, and the pure occurrence-due predicate. Requirements here MUST be expressed framework-agnostically: no Room, AlarmManager, or Compose vocabulary.

## Requirements

### Requirement: Six Frequency Kinds

The system MUST support exactly these `Schedule` kinds: `DAILY`, `TIMES_PER_DAY`, `N_TIMES_PER_WEEK`, `WEEKLY`, `MONTHLY`, `EVERY_N_DAYS`.

`MONTHLY` MUST be a true calendar-month kind identified by day-of-month, distinct from `EVERY_N_DAYS`, which computes occurrences from a fixed day interval and an anchor date. Neither MUST be approximated by the other.

#### Scenario: MONTHLY on a day the month lacks
- GIVEN a MONTHLY schedule anchored to day-of-month 31
- WHEN the occurrence-due predicate evaluates a 30-day month
- THEN the occurrence falls on that month's actual last day, not day 31

#### Scenario: EVERY_N_DAYS is unaffected by month length
- GIVEN an EVERY_N_DAYS(30) schedule anchored 2026-01-01
- WHEN the predicate evaluates dates across a 28-day February
- THEN occurrences remain spaced exactly 30 days apart, ignoring month boundaries

### Requirement: Occurrence-Due Predicate

The `:domain` module MUST expose a pure function that, given a `Schedule` and a calendar date, returns whether that date is a due occurrence, with no I/O and no Android SDK dependency. This same function MUST be the sole authority for both arming a reminder and answering "is this due" for any other reader (e.g. a future digest).

#### Scenario: One predicate serves two readers identically
- GIVEN a schedule and a date it is due
- WHEN the reminder-arming path and an independent rollup query both call the predicate
- THEN both receive the same "due" result

### Requirement: Reminder Slots for TIMES_PER_DAY

A `TIMES_PER_DAY` schedule MUST define one or more explicit clock-time `ReminderSlot`s, each independently due and independently answerable. Every other frequency kind MUST have exactly one configurable reminder time, not per-slot times.

That single reminder time is **OPTIONAL** for every non-`TIMES_PER_DAY` kind (**ratified by the user 2026-09-01**, extending to all five kinds what this capability previously stated only for `N_TIMES_PER_WEEK`). A habit saved without one MUST NOT be rejected: the system MUST NOT fire any reminder for it, and it MUST remain fully trackable in-app through the today screen. "Exactly one" bounds how many times a non-slotted kind may have, not whether it must have any.

#### Scenario: A habit saved with no reminder time is accepted and stays trackable
- GIVEN a DAILY, WEEKLY, MONTHLY, EVERY_N_DAYS or N_TIMES_PER_WEEK habit being created
- WHEN it is saved with no reminder time set
- THEN the save succeeds, no reminder ever fires for it, and it still appears and is answerable on the today screen

#### Scenario: Three slots produce three independent occurrences
- GIVEN a TIMES_PER_DAY habit with slots at 08:00, 14:00, 20:00
- WHEN a due date arrives
- THEN three separate occurrences exist for that date, one per slot

#### Scenario: Non-slotted frequency has one reminder time
- GIVEN a DAILY habit
- WHEN its reminder is configured
- THEN exactly one clock time applies, with no slot concept

### Requirement: N_TIMES_PER_WEEK Reminder Semantics

(**RATIFIED by the user 2026-09-01.** This was an orchestrator assumption pending confirmation; the note survived four work units because nothing forced the question until task 6a.8 needed it to build the reminder-time editor. The behaviour below was already implemented and verified on the physical Pixel 10 during the API 37 delivery matrix — design.md §13.4's D8 quota-suppression result — so ratifying it required no rework.)

IF a reminder time is set for an `N_TIMES_PER_WEEK` schedule, the system MUST fire that reminder daily at the configured time until the habit's weekly quota is satisfied, THEN MUST remain silent for the remainder of that calendar week. IF no reminder time is set, the system MUST NOT fire any reminder, while the habit MUST remain trackable in-app.

#### Scenario: Quota met mid-week silences remaining reminders
- GIVEN an N_TIMES_PER_WEEK(3) habit with a reminder time and 3 completions already logged this week
- WHEN the next scheduled reminder time arrives
- THEN no reminder fires for the rest of that ISO week

#### Scenario: Quota unmet resets silently at week boundary
- GIVEN quota not yet met when the week ends
- WHEN a new ISO week begins
- THEN reminders resume from Monday without carrying over the prior week's shortfall

#### Scenario: No reminder time configured fires nothing
- GIVEN an N_TIMES_PER_WEEK habit with no reminder time set
- WHEN any day of the week elapses
- THEN no reminder ever fires for that habit

### Requirement: Week Boundary for N_TIMES_PER_WEEK

The week boundary for every `N_TIMES_PER_WEEK` schedule MUST be a fixed ISO-8601 week (Monday 00:00 local time as the start of the week), applied identically to every such habit. The boundary MUST NOT be derived from device locale or any other locale-dependent setting, and the MVP MUST NOT expose a user-configurable week-start setting. The `:domain` module MUST express the week start as an explicit parameter injected into its due/quota calculation, not as a constant hardcoded inside that calculation's logic, so a future configurable week start becomes a value change rather than a signature change.

#### Scenario: Week boundary is Monday regardless of device locale
- GIVEN a device whose locale's first day of week is Sunday
- WHEN an N_TIMES_PER_WEEK habit's weekly quota is evaluated
- THEN the week is computed as starting on Monday 00:00 local time, unaffected by the device locale

#### Scenario: No week-start setting exists in the MVP
- GIVEN the MVP settings surface
- WHEN the user looks for a week-start configuration option
- THEN no such setting is exposed, and Monday remains the only week start in effect

#### Scenario: Week start is an injected parameter, not a hardcoded constant
- GIVEN the `:domain` due/quota calculation for an N_TIMES_PER_WEEK schedule
- WHEN the calculation is invoked
- THEN the week-start value MUST be supplied as an explicit parameter to the function, not embedded as a literal inside its logic
