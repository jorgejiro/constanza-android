# Delta for Habit Management

## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: Habit Archiving

The system MUST support archiving a habit as a reversible flag. Archiving MUST NOT delete the habit,
its `Schedule`, its `ReminderSlot`s, or any `Entry` history: archiving preserves every record it
touches. Deleting a habit is a separate operation, defined by the Habit Deletion requirement, and
archiving MUST NOT be implemented as, and MUST NOT be conflated with, that deletion.

An archived habit MUST stop firing reminders and MUST be excluded from streak and compliance
calculations for any date on or after the archive date, while `Entry` history before that date MUST
remain intact and queryable. Un-archiving MUST resume reminder scheduling from the moment of
un-archival, without back-filling reminders for dates missed while archived.

(Previously: opened with "never a deletion", worded as if archiving forbade any delete operation
existing elsewhere in the system, rather than describing archiving's own non-destructive semantics.
Reworded so archiving's data-preserving behavior stays explicit while a separate delete operation is
introduced.)

#### Scenario: Archiving stops reminders
- GIVEN an active habit with an armed reminder
- WHEN the user archives it
- THEN the pending reminder is cancelled and no future reminder is armed

#### Scenario: Archived habit excluded from compliance going forward
- GIVEN a habit archived on 2026-09-10 with prior history
- WHEN compliance is computed for a window spanning before and after 2026-09-10
- THEN dates on or after 2026-09-10 are excluded from the calculation, and dates before remain
  included

#### Scenario: Un-archiving does not back-fill missed slots
- GIVEN a habit archived for 5 days
- WHEN the user un-archives it
- THEN reminders resume from now onward and no `Entry` is retroactively created for the archived
  window
