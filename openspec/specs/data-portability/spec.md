# Data Portability Specification

## Purpose

Defines manual export and import to a file. Export/import is in MVP scope as the documented recovery path for a failed Room migration: without it, a bad migration means unrecoverable loss of history. This assumption is user-confirmed.

## Requirements

### Requirement: Export

The system MUST support exporting all `Habit`, `Schedule`, `ReminderSlot`, and `Entry` records to a single user-chosen file, on demand.

#### Scenario: Export produces one complete file
- GIVEN an app with several habits and entry history
- WHEN the user triggers export
- THEN one file is produced containing every habit, schedule, slot, and entry currently stored

### Requirement: Import

The system MUST support importing a previously exported file. Import MUST replace the entire existing dataset with the file's contents: it MUST NOT merge with existing data, and MUST NOT preserve any habit, schedule, slot, or entry that is absent from the imported file. Import MUST be preceded by an explicit confirmation step that states the action is destructive and irreversible, and MUST NOT proceed without that confirmation. Import MUST be atomic: a failed or rejected import MUST leave the existing dataset exactly as it was, with no partial replacement. A malformed or unreadable file MUST be rejected before any existing data is touched. Rejection feedback shown to the user MUST render in the app's resolved language (see `app-localization`). Because the import logic lives in a module deliberately kept Android-free with no `Context`, a rejection MUST be represented as a typed failure value identifying which failure occurred plus any interpolated arguments, never as a human-readable English message string carried in the failure itself; the Compose layer alone maps that typed value to a localized string.

#### Scenario: Import into an empty app restores all data
- GIVEN an app with no habits
- WHEN the user confirms importing a previously exported file
- THEN every habit, schedule, slot, and entry from that file exists afterward, unchanged

#### Scenario: Replace over a non-empty dataset drops absent habits
- GIVEN an app with an existing habit not present in the file being imported
- WHEN the user confirms the import
- THEN that existing habit and its entries no longer exist afterward, and only the file's contents remain

#### Scenario: Declined confirmation changes nothing
- GIVEN an app with existing habits and entries
- WHEN the user is shown the destructive-import confirmation and declines it
- THEN no data is added, removed, or modified, and the import does not proceed

#### Scenario: Malformed file leaves data intact
- GIVEN an app with existing habits and entries
- WHEN the user selects a malformed or unreadable file for import
- THEN the file is rejected before any existing data is touched, and the existing dataset is unchanged

#### Scenario: Round-trip export then import restores byte-equivalent domain state
- GIVEN an app with several habits, schedules, slots, and entries
- WHEN the user exports the dataset and immediately imports that same file after confirming the replace
- THEN the resulting dataset is domain-equivalent to the dataset before export, record for record

#### Scenario: Rejection feedback renders in the resolved language
- GIVEN the language override is set to Español
- WHEN the user selects a malformed file for import
- THEN the rejection message shown on screen renders in Spanish, produced by mapping a typed
  failure value to a localized string rather than displaying a raw English message

### Requirement: Backup Schema Version Read On Import

Import MUST read the backup file's declared schema version and use it to decide whether legacy
data normalization is required. The declared version MUST NOT be write-only: it MUST also gate
import-time behaviour.

#### Scenario: Legacy schema version is read and acted on
- GIVEN a backup file whose declared schema version predates the current version
- WHEN the user imports that file
- THEN the system reads the declared version and applies the normalization required for that
  version before the imported data replaces the existing dataset

#### Scenario: Current schema version needs no normalization
- GIVEN a backup file whose declared schema version matches the current version
- WHEN the user imports that file
- THEN no colour normalization is applied and the file's colours are imported unchanged

### Requirement: Legacy Habit Colour Normalized On Import

A backup file whose declared schema version predates a habit colour palette change MUST have
every off-palette habit colour normalized to the current palette, using the same one-to-one
mapping applied to already-persisted data by that palette change, before the imported data
replaces the existing dataset. A backup exported before a palette change MUST NOT re-introduce an
off-palette habit colour when imported afterwards.

#### Scenario: Pre-change export re-imported after the palette change
- GIVEN a file exported before the warm-dark palette change, containing a habit with the old
  purple colour
- WHEN the user imports that file after the app has moved to the warm-dark palette
- THEN the imported habit's colour is normalized to the corresponding current-palette colour, not
  the original off-palette value

#### Scenario: Legacy orange normalizes to pink on import
- GIVEN a file exported before the warm-dark palette change, containing a habit with the old
  orange colour
- WHEN the user imports that file after the app has moved to the warm-dark palette
- THEN the imported habit's colour is normalized to pink

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

### Requirement: Automatic Pre-Migration Snapshot

When a schema migration runs, a snapshot of the pre-migration data MUST be written before any
schema or data modification takes effect, so that a user who never exported by hand still has a
recovery artifact if the migration corrupts their data. Writing the snapshot MUST NOT be able to
prevent the app from opening: a failure to write the snapshot MUST be isolated from the migration
itself, so the migration still proceeds and the database remains usable even when the snapshot
could not be written.

#### Scenario: Snapshot is written before the migration modifies data
- GIVEN a user upgrades to a version whose database requires a schema migration
- WHEN the migration runs
- THEN a snapshot of the pre-migration data is written before any schema or data modification
  takes effect

#### Scenario: Snapshot failure does not block the migration or the app opening
- GIVEN a schema migration is running and the snapshot cannot be written
- WHEN the migration continues
- THEN the migration still completes, the app still opens afterward, and the user's data remains
  intact and reachable

### Requirement: Round-Trip Fidelity

Export followed by wipe followed by import MUST restore every habit, schedule, slot, and entry
unchanged, including archived habits and their pre-archive history. This guarantee applies to a
file exported by the current app version. It does NOT require preserving an off-palette colour
value carried in a file exported by a prior version: the import path is instead required to
normalize that value (see "Legacy Habit Colour Normalized On Import"). This is a clarification of
scope, not a relaxation — a current-version export MUST still restore identically, including its
colour values.

#### Scenario: Export, wipe, import preserves archived history
- GIVEN a dataset containing an archived habit with entries before its archive date
- WHEN the user exports, wipes all app data, and imports the same file
- THEN the archived habit, its archive state, and its full entry history are restored identically

#### Scenario: Current-version round trip preserves colour exactly
- GIVEN a dataset containing a habit with a colour from the current warm-dark palette
- WHEN the user exports, wipes all app data, and imports that same file
- THEN the habit's colour is restored exactly as exported, unchanged
