# Delta for Data Portability

## MODIFIED Requirements

### Requirement: Import

The system MUST support importing a previously exported file. Import MUST replace the entire
existing dataset with the file's contents: it MUST NOT merge with existing data, and MUST NOT
preserve any habit, schedule, slot, or entry that is absent from the imported file. Import MUST be
preceded by an explicit confirmation step that states the action is destructive and irreversible,
and MUST NOT proceed without that confirmation. Import MUST be atomic: a failed or rejected import
MUST leave the existing dataset exactly as it was, with no partial replacement. A malformed or
unreadable file MUST be rejected before any existing data is touched. Rejection feedback shown to
the user MUST render in the app's resolved language (see `app-localization`). Because the import
logic lives in a module deliberately kept Android-free with no `Context`, a rejection MUST be
represented as a typed failure value identifying which failure occurred plus any interpolated
arguments, never as a human-readable English message string carried in the failure itself; the
Compose layer alone maps that typed value to a localized string.
(Previously: covered replacement semantics, destructive confirmation, atomicity, and rejecting a
malformed file before touching data; said nothing about the language of rejection feedback or the
shape of the failure value.)

#### Scenario: Import into an empty app restores all data
- GIVEN an app with no habits
- WHEN the user confirms importing a previously exported file
- THEN every habit, schedule, slot, and entry from that file exists afterward, unchanged

#### Scenario: Replace over a non-empty dataset drops absent habits
- GIVEN an app with an existing habit not present in the file being imported
- WHEN the user confirms the import
- THEN that existing habit and its entries no longer exist afterward, and only the file's contents
  remain

#### Scenario: Declined confirmation changes nothing
- GIVEN an app with existing habits and entries
- WHEN the user is shown the destructive-import confirmation and declines it
- THEN no data is added, removed, or modified, and the import does not proceed

#### Scenario: Malformed file leaves data intact
- GIVEN an app with existing habits and entries
- WHEN the user selects a malformed or unreadable file for import
- THEN the file is rejected before any existing data is touched, and the existing dataset is
  unchanged

#### Scenario: Round-trip export then import restores byte-equivalent domain state
- GIVEN an app with several habits, schedules, slots, and entries
- WHEN the user exports the dataset and immediately imports that same file after confirming the
  replace
- THEN the resulting dataset is domain-equivalent to the dataset before export, record for record

#### Scenario: Rejection feedback renders in the resolved language
- GIVEN the language override is set to Español
- WHEN the user selects a malformed file for import
- THEN the rejection message shown on screen renders in Spanish, produced by mapping a typed
  failure value to a localized string rather than displaying a raw English message
