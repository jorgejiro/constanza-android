# Delta for Data Portability

## ADDED Requirements

### Requirement: Child Records Survive A Schema Migration

A schema migration that rebuilds a table referenced by another table's foreign key MUST NOT
delete or lose any row in the referencing table as a side effect of the rebuild. Every `Schedule`,
`ReminderSlot`, `Entry`, and reminder occurrence record linked to a `Habit` MUST still exist,
linked to that same `Habit`, after the migration completes, regardless of the mechanism used to
prevent cascade deletion during the rebuild.

#### Scenario: A habit's child records survive a migration that rebuilds the habits table
- GIVEN a habit with a schedule, reminder slots, entries, and reminder occurrences, persisted at
  the prior schema version
- WHEN the app opens and runs the schema migration that rebuilds the `habits` table
- THEN the same habit's schedule, reminder slots, entries, and reminder occurrences all still
  exist, linked to that habit, after the migration completes

#### Scenario: Each habit's records survive independently
- GIVEN two habits, each with its own schedule, reminder slots, entries, and reminder occurrences,
  persisted at the prior schema version
- WHEN the schema migration that rebuilds the `habits` table runs
- THEN each habit still has exactly its own child records afterward, with none lost and none
  mixed between habits
