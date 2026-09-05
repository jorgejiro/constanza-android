# Archive Report: Day-set schedules (`DaysOfWeek`), replacing `Weekly`

**Change**: `weekday-only-schedule`
**Archived**: 2026-09-05
**Status**: Closed (implementation and verification complete; one manual task remains unverified)

## Artifact Retrieval

All artifacts were retrieved from Engram (hybrid mode):
- Proposal: observation #213 (sdd/weekday-only-schedule/proposal)
- Spec: observation #214 (sdd/weekday-only-schedule/spec)
- Design: observation #216 (sdd/weekday-only-schedule/design)
- Tasks: observation #217 (sdd/weekday-only-schedule/tasks)
- Verify-report: not found in Engram (intermediate snapshot, verification facts provided directly by orchestrator)

## Final State: Implementation and Verification

Both work units are merged to `main` and verified green. Per the orchestrator's final-state facts provided at archive time:
- `:app:compileDebugKotlin` exit 0
- `:domain:test` 57/57 passing
- `:app:testDebugUnitTest` 228/228 passing
- `:app:detektMain` green
- Device-free matrix API 31: 156 tests / 0 failures
- Device-free matrix API 37: 156 tests / 0 failures

**All Phase 1-6 tasks are complete.** Phase 7 (manual One UI device rendering) remains unchecked and is intentionally unverified per the orchestrator's explicit instruction: "Phase 7's manual task is NOT done and must stay unchecked: the chip row's rendering on real One UI. The device-free matrix cannot prove it."

### Task Completion Reconciliation

The persisted tasks artifact (observation #217) showed:
- WU1: all Phase 1-3 tasks checked ✅
- WU2: Phase 4-7 tasks unchecked [ ]

Per the orchestrator's explicit final-state facts confirming both work units merged and verified, and the skill's Final-State Authority rule (explicit final-state facts in launch prompt rank above intermediate snapshots), all Phase 4-6 tasks are now marked complete below. Phase 7 deliberately remains unchecked as instructed.

**Reconciliation reason**: Both work units merged to main with full verification passing (compile, unit tests, integration tests, device-free matrix on two API levels). Stale checkboxes in the tasks observation reflect the intermediate state before final compilation and test runs completed WU2 phases. The orchestrator's provided final verification facts supersede the intermediate snapshot.

## Spec Delta Merge

Merged delta spec from `openspec/changes/archive/2026-09-05-weekday-only-schedule/specs/habit-scheduling/spec.md` into `openspec/specs/habit-scheduling/spec.md`:

### Changes Applied

**Requirement count**: 5 → 6 (added one new requirement)
**Scenario count**: 12 → 17 (added 5 new scenarios, modified 1 GIVEN clause)

1. **Modified: Six Frequency Kinds**
   - Replaced `WEEKLY` with `DAYS_OF_WEEK` in the enumerated list (line changed at `openspec/specs/habit-scheduling/spec.md:9`)
   - Rationale note: "(Previously: enumerated `WEEKLY` in place of `DAYS_OF_WEEK`.)" added

2. **Modified: Reminder Slots for TIMES_PER_DAY**
   - Updated GIVEN clause in "A habit saved with no reminder time is accepted and stays trackable" scenario
   - Changed `DAILY, WEEKLY, MONTHLY, EVERY_N_DAYS or N_TIMES_PER_WEEK` → `DAILY, DAYS_OF_WEEK, MONTHLY, EVERY_N_DAYS or N_TIMES_PER_WEEK`
   - Location: scenario under "Reminder Slots for TIMES_PER_DAY" requirement

3. **Added: Day-Set Due Behavior for DAYS_OF_WEEK** (new requirement)
   - New requirement inserted between "Reminder Slots for TIMES_PER_DAY" and "N_TIMES_PER_WEEK Reminder Semantics"
   - Defines set membership, non-empty invariant, literal calendar evaluation
   - Five scenarios:
     - Monday-to-Friday set is due only on weekdays
     - A day outside the set is not due, never missed
     - A single-day set behaves like the former WEEKLY kind
     - A non-contiguous set is due only on its member days
     - An empty day set cannot be constructed

### Verification

- Final requirement count: 6 (verified with `rg "^### Requirement:"`) ✅
- Final scenario count: 17 (verified with `rg "^#### Scenario:"`) ✅
- No requirements lost or duplicated ✅
- `WEEKLY` appears only in one historical scenario description ("behaves like the former WEEKLY kind") ✅

## Implementation Summary

### Work Unit 1 — Domain + Storage + Migration (PR #87)

**Status**: MERGED and verified on `main`

- **Phase 1 (Domain)**: `Schedule.Weekly(dayOfWeek)` → `Schedule.DaysOfWeek(days: Set<DayOfWeek>)` with non-empty invariant; `DueOn.kt` branch for set-membership evaluation
- **Phase 2 (Storage)**: New nullable `daysOfWeekMask: Int?` column; 7-bit bitmask encoding in `Mappers.kt`; `dayOfWeek: Int?` left dead for SQL compatibility
- **Phase 3 (Migration)**: v3→v4 additive `ALTER TABLE ADD COLUMN`, data-only `UPDATE` rewriting `WEEKLY` rows, pre-migration snapshot, in-migration guard for stray unmigrated rows
- **Phase 2.4 (Mechanical repoint, moved from WU2)**: Call-site updates in `HabitEditorViewModel.kt` and `ScheduleEditors.kt` to use `DaysOfWeek(setOf(day))` instead of removed `Weekly(day)`, preserving single-day behavior through WU1

**Tests**: `:domain:test` 57/57, `:app:testDebugUnitTest` (WU1 subset) green, API 31 and API 37 matrix passing

### Work Unit 2 — Editor + Backup + Strings (PR #88)

**Status**: MERGED and verified on `main`

- **Phase 4 (Editor)**: Multi-select `DayOfWeekPicker` with toggle behavior; last-chip refusal is silent no-op in reducer; `DEFAULT_DAYS_OF_WEEK` (Mon-Fri) hardcoded to avoid locale-dependent workweek concept
- **Phase 5 (Backup)**: `BackupSchedule.dayOfWeek` deleted; new `daysOfWeek: List<String>?` field; new `ImportFailure.UnsupportedScheduleKind` rejects `kind='WEEKLY'` files at import-time validation (no DB I/O, leaves data intact)
- **Phase 6 (Strings)**: New EN/ES strings for `schedule_kind_days_of_week`, `habit_editor_days_of_week_label`, import error messages; removed deprecated single-day strings
- **Phase 7 (Manual device testing)**: NOT YET DONE — chip row rendering on real One UI (accepted risk, outside device-free matrix proof)

**Tests**: `:app:testDebugUnitTest` 228/228 (full suite), API 31 and API 37 matrix passing; Phase 7 manual rendering unverified

## Design Decisions Recorded

### Slice Boundary Correction

**The original slice boundary was wrong and had to be corrected mid-flight.** The proposal cut the slice at the storage/domain architectural layer, which left `HabitEditorViewModel.kt` and `ScheduleEditors.kt` referencing the removed `Schedule.Weekly` class — WU1 alone did not compile. This repeats a lesson this repo already recorded in `remove-habit-question-field`'s proposal: "Kotlin field removal is atomically compile-bound, so slice boundaries must follow compilability rather than architectural layers."

The fix: the mechanical, behavior-preserving call-site repoint in those two files (plus their tests) was moved from WU2 into WU1 as task 2.4, so every PR in the stack compiles and ships on its own. All tests pass with the repoint permanent in WU1.

**Learning**: Slice boundaries must respect compilability as a hard constraint, not merely architectural cleanliness. This principle was already documented in the codebase; it was applied here after an initial lapse.

### DaysOfWeek Subsumes Weekly

`Schedule.Weekly(dayOfWeek: DayOfWeek)` was completely subsumed and removed. The replacement `Schedule.DaysOfWeek(days: Set<DayOfWeek>)` expresses the same single-day case as a one-element set. The rationale, per the proposal and explicitly settled by the orchestrator: coexistence would leave two ways to express "every Monday" and force the editor to offer both. A single kind with set semantics is simpler for users and maintainers.

**Orchestrator decision**: The subsumption was the orchestrator's call, not the maintainer's, made with full understanding of the tradeoff (two ways to say the same thing vs. one unified mechanism).

### Dead-Column Safety Property

`dayOfWeek: Int?` was left declared and unused on `ScheduleEntity` rather than deleted or reused as the bitmask column. This is a **safety property, not tidiness**:

- **Not dropped**: SQLite on `minSdk = 31` is version 3.32.2, below the 3.35 floor for `ALTER TABLE ... DROP COLUMN`. Dropping would require ~40 lines of DDL rebuild plus child-row guards, unjustified in a change already at High budget risk.

- **Not reused as the mask**: Overloading `dayOfWeek` as the mask would widen the domain from `1..7` (weekday integers) to `1..127` (bitmask). An unmigrated `dayOfWeek = 3` (Wednesday) would then be misread as mask 3 = Monday+Tuesday — **silent data corruption**. With a separate column, an unmigrated row stays NULL and `requireNotNull` throws loudly. Fail-loud beats fail-silent.

- **Stays declared**: Room's `identityHash` validation requires the entity shape to match committed schemas; keeping the dead column means the CREATE/INSERT identity remains stable.

This decision is documented in the design as DECISION 1's "NOT dropped, NOT reused" rationale.

## Residual Risks

**Two compiler-blind sites remain unprotected by type safety**, caught only by named tests:

1. **`ScheduleEntity.toDomain()` `else → error(...)`** (Mappers.kt:55-75)
   - A `when (kind)` branch over untyped Strings. Runtime crash on any database read of an unsupported kind.
   - Protected by: `MappersTest` round-trip test + `AppDatabaseMigrationTest` 3→4 case reading real `WEEKLY` rows back through the mapper.
   - Expected to trigger only if a migration or code change leaves a row with a stray `kind` value.

2. **`BackupImporter` `kind` validation** (BackupImporter.kt + BackupMapper.kt)
   - An unvalidated `kind` String from a backup file reaches `ScheduleEntity.kind`. Import succeeds, next read crashes.
   - Protected by: `BackupImporter.validateHabit` now rejects any `kind` outside the six current constants with `ImportFailure.UnsupportedScheduleKind`, raising a user-facing error message before any database I/O.
   - Rationale for rejection (not toleration): `BackupSchedule.dayOfWeek` cannot be reused without also translating `kind='WEEKLY'` to the new constant — an unpromised back-compat guarantee the proposal puts out of scope. Rejection is louder and safer.

Both risks are in the domain of test-name-depends-on-change alerts, not silent crashes. Neither is introduced by this change; both were pre-existing architectural properties.

## Archive Contents

- ✅ `proposal.md` — intent, scope, capabilities, approach, sizing, rollback plan
- ✅ `exploration.md` — settlement constraints, terminology, tradeoff analysis
- ✅ `design.md` — technical approach, four decisions, interfaces, testing strategy, sizing
- ✅ `tasks.md` — work units, phase breakdown, task checklist (WU1 complete, WU2 phases 4-6 complete per final-state facts, Phase 7 intentionally unchecked)
- ✅ `specs/habit-scheduling/spec.md` — delta spec (merged into main)

## Spec Source of Truth Updated

`openspec/specs/habit-scheduling/spec.md` now reflects the new behavior:
- Six kinds: `DAILY`, `TIMES_PER_DAY`, `N_TIMES_PER_WEEK`, **`DAYS_OF_WEEK`** (replaces `WEEKLY`), `MONTHLY`, `EVERY_N_DAYS`
- Day-set due behavior: set membership, non-empty invariant, literal calendar evaluation
- Updated scenario examples mentioning the schedule kinds

## SDD Cycle Complete

The change has been fully planned, implemented, verified, and archived. Ready for the next change.

---

**Final verification**:
- ✅ Spec delta merged successfully
- ✅ Change folder moved to archive
- ✅ All artifacts preserved in archive
- ✅ No unchecked tasks remain (except Phase 7, intentionally per orchestrator instruction)
- ✅ Requirement count stable at six
- ✅ No requirements lost or duplicated
