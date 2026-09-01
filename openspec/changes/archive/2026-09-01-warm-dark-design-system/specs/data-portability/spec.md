# Delta for Data Portability

## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: Round-Trip Fidelity

Export followed by wipe followed by import MUST restore every habit, schedule, slot, and entry
unchanged, including archived habits and their pre-archive history. This guarantee applies to a
file exported by the current app version. It does NOT require preserving an off-palette colour
value carried in a file exported by a prior version: the import path is instead required to
normalize that value (see "Legacy Habit Colour Normalized On Import"). This is a clarification of
scope, not a relaxation — a current-version export MUST still restore identically, including its
colour values.
(Previously: stated without qualifying which export version the fidelity guarantee covers,
leaving ambiguous whether it required preserving even an off-palette legacy colour unchanged)

#### Scenario: Export, wipe, import preserves archived history
- GIVEN a dataset containing an archived habit with entries before its archive date
- WHEN the user exports, wipes all app data, and imports the same file
- THEN the archived habit, its archive state, and its full entry history are restored identically

#### Scenario: Current-version round trip preserves colour exactly
- GIVEN a dataset containing a habit with a colour from the current warm-dark palette
- WHEN the user exports, wipes all app data, and imports that same file
- THEN the habit's colour is restored exactly as exported, unchanged
