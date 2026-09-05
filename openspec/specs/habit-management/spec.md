# Habit Management Specification

## Purpose

Defines creation, editing, and archiving of `Habit` records and their attached `Schedule`, independent of persistence or UI technology.

## Requirements

### Requirement: Habit Creation

The system MUST allow creating a `Habit` with a name, optional colour, optional notes, and
exactly one attached `Schedule` of any supported frequency kind.

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

### Requirement: Habit Deletion

The system MUST support permanently deleting a `Habit` together with its `Schedule`, all its
`ReminderSlot`s, all its `Entry` records, and all its reminder occurrence records. Deletion MUST be
irreversible in-app: none of the deleted records MUST be recoverable through any in-app action after
deletion completes. The only recovery path is importing a previously exported backup, per the
`data-portability` `Import` requirement's full-replace guarantee.

Before deletion completes, the user MUST be shown a confirmation that names the habit and states the
exact number of recorded `Entry` records that will be destroyed, stating zero explicitly when the
habit has no history. Deletion MUST NOT proceed without confirmation, and declining the confirmation
MUST leave the habit and all its records unchanged.

After a habit is deleted, no reminder MUST fire for that habit again, regardless of whether a reminder
was armed for it at the moment of deletion.

Deletion MUST behave identically for a habit with entry history and a habit with none: only the
confirmation's stated count differs, never the outcome.

Deletion is a distinct operation from archiving. Deleting a habit MUST NOT read or modify the archived
flag, and MUST NOT alter archiving's behavior for any other habit.

#### Scenario: Deleting a habit with history removes it and all its records
- GIVEN a habit with a schedule, reminder slots, and 10 recorded entries
- WHEN the user confirms deletion
- THEN the habit, its schedule, its reminder slots, its entries, and its reminder occurrences no
  longer exist

#### Scenario: Deleting a habit with no history behaves the same as one with history
- GIVEN a newly created habit with zero recorded entries
- WHEN the user confirms deletion
- THEN the habit, its schedule, and its reminder slots no longer exist, and the confirmation shown
  before the user confirmed stated 0 recorded answers

#### Scenario: Confirmation states the exact recorded-answer count
- GIVEN a habit with 7 recorded entries
- WHEN the user opens the delete confirmation for that habit
- THEN the dialog names the habit and states exactly 7 recorded answers will be destroyed

#### Scenario: Declining the confirmation changes nothing
- GIVEN the delete confirmation is shown for a habit
- WHEN the user declines it
- THEN the habit, its schedule, its entries, and its reminder occurrences all remain unchanged

#### Scenario: No reminder fires for a deleted habit
- GIVEN a habit with a reminder armed for a future occurrence
- WHEN the user deletes that habit
- THEN no reminder for that habit is delivered afterward

#### Scenario: Deletion does not affect archiving
- GIVEN two habits, one archived and one active
- WHEN the active habit is deleted
- THEN the archived habit's archived state and entry history remain unchanged, and it continues to
  be excluded from compliance exactly as before

### Requirement: Habit Archiving

The system MUST support archiving a habit as a reversible flag. Archiving MUST NOT delete the habit,
its `Schedule`, its `ReminderSlot`s, or any `Entry` history: archiving preserves every record it
touches. Deleting a habit is a separate operation, defined by the Habit Deletion requirement, and
archiving MUST NOT be implemented as, and MUST NOT be conflated with, that deletion.

An archived habit MUST stop firing reminders and MUST be excluded from streak and compliance
calculations for any date on or after the archive date, while `Entry` history before that date MUST
remain intact and queryable. Un-archiving MUST resume reminder scheduling from the moment of
un-archival, without back-filling reminders for dates missed while archived.

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

### Requirement: Habit Colour Palette

The system MUST offer exactly six colours in the habit colour picker, and every offered colour
MUST be a member of the current warm-dark palette (see `visual-design-system`).

#### Scenario: Picker offers exactly six colours
- GIVEN the colour picker shown during habit creation or editing
- WHEN the user opens it
- THEN exactly six colours are shown, matching the current warm-dark palette values

### Requirement: Persisted Habit Colour Stays On-Palette Across A Palette Change

When the offered habit colour palette changes, every already-persisted habit's colour MUST be
rewritten so it remains a member of the current palette; the system MUST NOT leave any habit
holding a colour absent from the current palette. The rewrite MUST be a one-to-one mapping (no two
distinct previous colours collapse onto the same new colour). Where a previous colour's hue has no
same-hue counterpart in the new palette, it MUST map to the one remaining unclaimed colour in the
new palette rather than collapse onto another habit's colour.

#### Scenario: Existing habit keeps a same-family colour
- GIVEN a habit persisted with a colour from the old palette that has a same-hue counterpart in
  the new palette (e.g. teal, blue, red, purple, or green)
- WHEN the palette change is applied
- THEN the habit's colour is rewritten to the corresponding new-palette colour of the same family

#### Scenario: Orange changes colour family to pink
- GIVEN a habit persisted with the old orange colour, whose hue has no counterpart in the new
  palette because that hue is reserved for the accent
- WHEN the palette change is applied
- THEN the habit's colour is rewritten to pink — the one colour in six whose family changes — and
  it remains distinguishable from the other five habit colours

### Requirement: Habit Colour Visible Where Habits Are Listed

A habit's colour MUST be rendered as a visible identity marker on every screen where habits are
listed, not only within the habit editor.

#### Scenario: Colour visible on the today screen
- GIVEN a habit with an assigned colour and a due occurrence today
- WHEN the user views the today screen
- THEN that habit's colour is visibly rendered alongside its entry

#### Scenario: Colour visible on the habit list
- GIVEN two habits with different assigned colours
- WHEN the user views the habit list screen
- THEN each habit's row visibly displays its own colour, distinguishing the two habits from each
  other

### Requirement: Habit List Row Actions And Name Display

The habit list row's trailing content MUST show only one overflow menu launcher; it MUST NOT
render Progress, Archive/Un-archive, or Delete as always-visible buttons. Opening the menu MUST
offer Progress, Archive or Un-archive (matching the habit's current state), and Delete as items.
The habit's name MUST wrap across up to two lines before truncating, and MUST be ellipsized only
once both lines are full.

#### Scenario: The row shows only the overflow launcher
- GIVEN a habit list row
- WHEN it renders
- THEN Progress, Archive/Un-archive, and Delete are not shown as always-visible buttons, and the
  row shows a single overflow menu launcher

#### Scenario: The overflow menu offers all three actions
- GIVEN a habit list row with the overflow menu closed
- WHEN the user opens it
- THEN Progress, Archive or Un-archive, and Delete all appear as menu items

#### Scenario: A long name wraps onto a second line before truncating
- GIVEN a habit name long enough to need two lines at the row's rendered width but no more
- WHEN the row renders
- THEN the full name is visible across two lines with no ellipsis

#### Scenario: A name exceeding two lines is ellipsized
- GIVEN a habit name that would need more than two lines at the row's rendered width
- WHEN the row renders
- THEN the name is capped at two lines and ends with an ellipsis
