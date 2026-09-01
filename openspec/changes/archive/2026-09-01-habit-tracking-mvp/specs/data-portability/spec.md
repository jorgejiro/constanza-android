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

The system MUST support importing a previously exported file. Import MUST replace the entire existing dataset with the file's contents: it MUST NOT merge with existing data, and MUST NOT preserve any habit, schedule, slot, or entry that is absent from the imported file. Import MUST be preceded by an explicit confirmation step that states the action is destructive and irreversible, and MUST NOT proceed without that confirmation. Import MUST be atomic: a failed or rejected import MUST leave the existing dataset exactly as it was, with no partial replacement. A malformed or unreadable file MUST be rejected before any existing data is touched.

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

### Requirement: Round-Trip Fidelity

Export followed by wipe followed by import MUST restore every habit, schedule, slot, and entry unchanged, including archived habits and their pre-archive history.

#### Scenario: Export, wipe, import preserves archived history
- GIVEN a dataset containing an archived habit with entries before its archive date
- WHEN the user exports, wipes all app data, and imports the same file
- THEN the archived habit, its archive state, and its full entry history are restored identically
