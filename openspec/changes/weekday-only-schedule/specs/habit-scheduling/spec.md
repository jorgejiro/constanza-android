# Delta for Habit Scheduling

## MODIFIED Requirements

### Requirement: Six Frequency Kinds

The system MUST support exactly these `Schedule` kinds: `DAILY`, `TIMES_PER_DAY`, `N_TIMES_PER_WEEK`, `DAYS_OF_WEEK`, `MONTHLY`, `EVERY_N_DAYS`.

`MONTHLY` MUST be a true calendar-month kind identified by day-of-month, distinct from `EVERY_N_DAYS`, which computes occurrences from a fixed day interval and an anchor date. Neither MUST be approximated by the other.
(Previously: enumerated `WEEKLY` in place of `DAYS_OF_WEEK`.)

#### Scenario: MONTHLY on a day the month lacks
- GIVEN a MONTHLY schedule anchored to day-of-month 31
- WHEN the occurrence-due predicate evaluates a 30-day month
- THEN the occurrence falls on that month's actual last day, not day 31

#### Scenario: EVERY_N_DAYS is unaffected by month length
- GIVEN an EVERY_N_DAYS(30) schedule anchored 2026-01-01
- WHEN the predicate evaluates dates across a 28-day February
- THEN occurrences remain spaced exactly 30 days apart, ignoring month boundaries

### Requirement: Reminder Slots for TIMES_PER_DAY

A `TIMES_PER_DAY` schedule MUST define one or more explicit clock-time `ReminderSlot`s, each independently due and independently answerable. Every other frequency kind MUST have exactly one configurable reminder time, not per-slot times.

That single reminder time is OPTIONAL for every non-`TIMES_PER_DAY` kind (ratified by the user 2026-09-01, extending to all five kinds what this capability previously stated only for `N_TIMES_PER_WEEK`). A habit saved without one MUST NOT be rejected: the system MUST NOT fire any reminder for it, and it MUST remain fully trackable in-app through the today screen. "Exactly one" bounds how many times a non-slotted kind may have, not whether it must have any.
(Previously: the scenario below's GIVEN clause named `WEEKLY` in place of `DAYS_OF_WEEK`.)

#### Scenario: A habit saved with no reminder time is accepted and stays trackable
- GIVEN a DAILY, DAYS_OF_WEEK, MONTHLY, EVERY_N_DAYS or N_TIMES_PER_WEEK habit being created
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

## ADDED Requirements

### Requirement: Day-Set Due Behavior for DAYS_OF_WEEK

A `DAYS_OF_WEEK` schedule MUST carry a set of one or more distinct days of the week and MUST NOT be constructible with an empty set. A date is due under this schedule iff that date's day of week is a member of the set; every other date MUST evaluate to `Due.NotDue` — the same non-due outcome every other frequency kind already produces for a date it does not occupy, never a missed occurrence. Membership MUST be evaluated literally against the calendar day of week and MUST NOT be derived from `weekStart` or any other locale-dependent setting.

#### Scenario: Monday-to-Friday set is due only on weekdays
- GIVEN a DAYS_OF_WEEK schedule set to Monday through Friday
- WHEN the occurrence-due predicate evaluates each day of one calendar week
- THEN Monday through Friday are due and Saturday and Sunday are not

#### Scenario: A day outside the set is not due, never missed
- GIVEN a DAYS_OF_WEEK schedule that does not include Saturday
- WHEN a Saturday is evaluated
- THEN it is `Due.NotDue`, and no missed occurrence is ever recorded for that Saturday

#### Scenario: A single-day set behaves like the former WEEKLY kind
- GIVEN a DAYS_OF_WEEK schedule containing only Monday
- WHEN the predicate evaluates a full calendar week
- THEN only Monday is due, matching the prior single-day-per-week behavior

#### Scenario: A non-contiguous set is due only on its member days
- GIVEN a DAYS_OF_WEEK schedule containing Monday, Wednesday, and Friday
- WHEN the predicate evaluates a full calendar week
- THEN only Monday, Wednesday, and Friday are due

#### Scenario: An empty day set cannot be constructed
- GIVEN an attempt to construct a DAYS_OF_WEEK schedule with no days selected
- WHEN construction is attempted
- THEN it fails, and no schedule due on no days is ever representable
