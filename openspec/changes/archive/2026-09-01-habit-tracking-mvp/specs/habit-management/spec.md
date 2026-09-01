# Habit Management Specification

## Purpose

Defines creation, editing, and archiving of `Habit` records and their attached `Schedule`, independent of persistence or UI technology.

## Requirements

### Requirement: Habit Creation

The system MUST allow creating a `Habit` with a name, an optional guiding question, optional colour, optional notes, and exactly one attached `Schedule` of any supported frequency kind.

#### Scenario: Create a daily habit
- GIVEN no existing habits
- WHEN the user creates a habit named "Drink water" with a DAILY schedule
- THEN a new `Habit` exists with that name and a DAILY `Schedule`

#### Scenario: Creation requires a name
- GIVEN the user leaves the name field empty
- WHEN they attempt to save the habit
- THEN the system MUST reject the save and MUST NOT create a `Habit`

### Requirement: Habit Editing

The system MUST allow editing a habit's name, colour, notes, and attached `Schedule` (including changing frequency kind) without deleting existing `Entry` history.

Editing the `Schedule` MUST be treated as an in-app schedule edit and MUST trigger an immediate reminder reschedule (see `reminder-delivery`).

#### Scenario: Editing the schedule reschedules reminders
- GIVEN a habit with a WEEKLY schedule and pending reminders
- WHEN the user changes it to DAILY
- THEN existing pending reminders for the old schedule are cancelled and new ones are armed for DAILY

#### Scenario: Editing preserves past entries
- GIVEN a habit with 10 days of recorded entries
- WHEN the user edits its name
- THEN all 10 `Entry` records remain unchanged and queryable

### Requirement: Habit Archiving

The system MUST support archiving a habit as a reversible flag, never a deletion.

An archived habit MUST stop firing reminders and MUST be excluded from streak and compliance calculations for any date on or after the archive date, while `Entry` history before that date MUST remain intact and queryable. Un-archiving MUST resume reminder scheduling from the moment of un-archival, without back-filling reminders for dates missed while archived.

#### Scenario: Archiving stops reminders
- GIVEN an active habit with an armed reminder
- WHEN the user archives it
- THEN the pending reminder is cancelled and no future reminder is armed

#### Scenario: Archived habit excluded from compliance going forward
- GIVEN a habit archived on 2026-09-10 with prior history
- WHEN compliance is computed for a window spanning before and after 2026-09-10
- THEN dates on or after 2026-09-10 are excluded from the calculation, and dates before remain included

#### Scenario: Un-archiving does not back-fill missed slots
- GIVEN a habit archived for 5 days
- WHEN the user un-archives it
- THEN reminders resume from now onward and no `Entry` is retroactively created for the archived window
