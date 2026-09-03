# Archive Report: Habit Deletion

**Change**: habit-deletion  
**Status**: COMPLETE — PASS WITH WARNINGS  
**Closed**: 2026-09-03  
**Branch**: feat/habit-deletion (6 commits, base main@34029dc)

## Completion Status

| Metric | Value |
|--------|-------|
| Requirements | 2/2 compliant (1 ADDED: Habit Deletion, 1 MODIFIED: Habit Archiving) |
| Scenarios | 9/9 compliant (6 new Habit Deletion, 3 preserved Habit Archiving) |
| Implementation tasks | 20/20 complete |
| Verification verdict | PASS WITH WARNINGS |
| Blockers | 0 |
| Critical findings | 0 |
| Warnings | 2 |

## Artifacts Collected

| Artifact | Source | ID | Captured |
|----------|--------|-----|----------|
| Proposal | Engram `sdd/habit-deletion/proposal` | 106 | Yes |
| Spec delta | Engram `sdd/habit-deletion/spec` | 107 | Yes |
| Design | Engram `sdd/habit-deletion/design` | 108 | Yes |
| Tasks | Engram `sdd/habit-deletion/tasks` | 109 | Yes |
| Verify-report | Engram `sdd/habit-deletion/verify-report` | 114 | Yes |

## Spec Merger

### Main Spec Changes

**File**: `openspec/specs/habit-management/spec.md`

**Requirements (7 total):**
- Habit Creation (unchanged)
- Habit Editing (unchanged)
- Habit Deletion (ADDED — 1 requirement with 6 scenarios)
- Habit Archiving (MODIFIED — description reworded, 3 scenarios unchanged)
- Habit Colour Palette (unchanged)
- Persisted Habit Colour Stays On-Palette Across A Palette Change (unchanged)
- Habit Colour Visible Where Habits Are Listed (unchanged)

**Delta Applied**:
- **ADDED**: "Requirement: Habit Deletion" (6 scenarios: history removal, no-history parity, confirmation count, confirmation decline, reminder silencing, archiving unaffected)
- **MODIFIED**: "Requirement: Habit Archiving" opening paragraph now explicitly names deletion as a separate operation instead of implicitly forbidding it; all 3 scenarios preserved unchanged

**Spec Conformance Mapping** (from design.md):
- Deleting with history removes all records → 4× ForeignKey.CASCADE, one transaction, deletion confirmed in HabitRepository.kt:137-141
- No-history deletion behaves identically → same delete path, no count branch
- Confirmation states exact count → resident entryCounts map + `<plurals>` in strings.xml
- Declining changes nothing → no deletion runs before user confirms
- No reminder fires for deleted habit → snapshot armed ids before cascade (repo layer, HabitRepositoryDeleteTest 4.2), handler null-guard after (e2e CoreFlowE2ETest 4.9)
- Deletion does not affect archiving → delete never reads/writes archived flag, no replanAll() call

## Design Decisions (D1-D5) — Verified in Source

### D1: Alarm Ordering

**Implementation**: HabitRepository.delete(habitId) follows BackupImporter.replaceAll's proven pattern: snapshot armed occurrence ids BEFORE the transaction, cascade inside withTransaction, cancel AFTER commit.

**Evidence** (HabitRepository.kt:137-141):
- Line 137-138: `reminderOccurrenceDao.findByHabitId(habitId).map { it.id }` — reads before writing
- Line 139: `database.withTransaction { daos.habitDao.deleteById(habitId) }` — SQLite cascades schedules, slots, entries, occurrences
- Line 140-141: `armedIds.forEach { alarmScheduler.cancel(it) }` — after commit

**Rationale**: Replaces archiving's replanAll() (which works only because the archived row survives to be re-queried). Snapshot-then-cascade-then-cancel avoids the failure mode of cancelling inside the transaction: if process dies between cancel and commit, a live habit sits disarmed (invisible to user). The chosen order fails benignly: an orphan armed alarm whose fire finds no occurrence and returns silently (ReminderFireWorker.fire:39-41 has two null-guards).

**Why NOT replanAll()**: Would re-couple deletion to every other habit's plan. No other habit changes; no replan needed. D1 rejects the alternative as explicitly stated in design.md§D1.

### D2: Affordance

**Implementation**: HabitRow.trailingContent keeps two TextButtons (Progress, Archive) + adds trailing IconButton(Icons.Filled.MoreVert) opening DropdownMenu with Delete item.

**Rationale**: Irreversible action (delete) gets equal visual weight to reversible one (archive) via overflow menu, not equal prominence as a third button in an already-constrained row. ConstanzaControlDefaults obligation does not arise: DropdownMenu draws no border, and OutlinedIconButton would have required explicit `border = ...` if chosen.

### D3: Entry Count as Resident State

**Implementation**: EntryDao.observeCountsByHabit() emits Flow<List<HabitEntryCount>> (GROUP BY habitId); HabitListViewModel folds into existing combine, exposing entryCounts: Map<Long, Int> on HabitListUiState. Dialog reads state.entryCounts[habit.id] ?: 0 at render time (a map lookup, no I/O).

**Rationale**: combine emits nothing until every source emits, so a rendered row implies counts arrived. No window renders a stale zero, and `?: 0` is honest: a habit absent from GROUP BY result is absent because it has zero entries.

### D4: Confirmation Dialog

**Implementation**: M3 AlertDialog (same shape as HabitEditorScreen's DiscardChangesDialog). Title and body name the habit and exact entry count via strings.xml `<plurals>` (one/other branches). Zero falls to `other`, rendering literally "0 recorded answers" — honest, not guessed.

**Rationale**: Confirm button labeled with the verb (Delete), not recolored to error (error-on-dialog contrast not in audited set; accent is chrome-only). Pending state via rememberSaveable mutableStateOf<Long?>(null), resolved by id from state.habits.

### D5: CoreFlowE2ETest Rationale Rewrite

**Rationale Changed**: The test at CoreFlowE2ETest:309-317 claimed "nothing anywhere deletes a habit row, by design". That rationale is now false. The assertion (row survives archiving) is still correct, but the reason changed: the row survives archiving because archiving is the reversible gesture and preserves every record it touches — not because deletion does not exist.

**Assertion**: Unchanged — archived habit row still survives, archived flag true, archivedAt not null.

**Test Split**: REMOVED_HABIT split into ARCHIVED_HABIT and DELETED_HABIT. New sibling test deletingAHabitThroughTheUiRemovesItAndItsHistoryAndSilencesItsReminder (CoreFlowE2ETest:370-402) carries the counterpart: delete is irreversible, recovery is backup import.

## Verification Evidence

### Per verify-report.md (ID 114, 2026-09-03 11:41:21)

**Verdict**: PASS WITH WARNINGS (0 CRITICAL, 2 WARNING)

**Test Results**:
- Unit tests: 190 passed / 0 failed / 0 skipped
- Detekt/lintDebug: clean
- Instrumented matrix API 31: 111/111 passed, 2 skipped (permission-gated)
- Instrumented matrix API 37: 111/111 passed (all habit-deletion tests), 1 skipped; unrelated Today-screen compose test failed on ComposeTimeoutException both runs (pre-existing flakiness, not this change)

**Spec Compliance**: 9/9 scenarios COMPLIANT (verified by real, runtime-passing tests at named file:line, not checkbox claims alone)

| Scenario | Test | Result |
|----------|------|--------|
| Deleting with history removes all records | HabitRepositoryDeleteTest:44 | COMPLIANT |
| No-history deletion same as history | HabitRepositoryDeleteTest:125 + HabitDeleteDialogComposeTest:98 | COMPLIANT |
| Confirmation states exact count | HabitDeleteDialogComposeTest:70 | COMPLIANT |
| Declining confirmation changes nothing | HabitDeleteDialogComposeTest:109 | COMPLIANT |
| No reminder fires for deleted habit | HabitRepositoryDeleteTest:76 (cancel ran) + CoreFlowE2ETest:370 (handler null-guard) | COMPLIANT |
| Deletion does not affect archiving | HabitRepositoryDeleteTest:90 | COMPLIANT |
| Archiving stops reminders | HabitRepositoryArchiveTest (preserved, unmodified) | COMPLIANT |
| Archived excluded from compliance | Pre-existing coverage (preserved, unmodified) | COMPLIANT |
| Un-archiving doesn't back-fill | HabitRepositoryArchiveTest (preserved, unmodified) | COMPLIANT |

**Correctness (Re-derived from Source)**:

1. **Alarm ordering (D1)**: Snapshot armed ids before cascade, cancel after transaction. No replanAll(). HabitRepository.kt:137-141 matches pattern, does not contain the archiving mistake.

2. **Two-test division for "no reminder fires"**: Each test proves only its own half. Repo test (4.2) verifies cancel() ran; E2E test (4.9) verifies handler's null-guard. Each test's KDoc disclaims the other half; no test claims both.

3. **Entry count query and fold**: confirmEntryCount resident in state before dialog opens (combine withholds emission until every source emits); no slow query between tap and dialog.

4. **Archiving unchanged**: HabitRepository.setArchived (lines 111-122) untouched; all HabitRepositoryArchiveTest and HabitListArchiveComposeTest pass unmodified on both matrix legs both runs.

5. **CoreFlowE2ETest:309-317 rationale rewritten, assertion kept**: Comment now explains row survives because archiving is reversible. Assertions at lines 344-349 (archived, archivedAt, occurrences empty) unchanged.

6. **No Room migration**: app/schemas/** untouched. All four cascade FKs pre-existed. Change adds only DAO query and projection class; Room's schema identity hash (computed from entities and views, not DAO methods) is unchanged.

### Warnings from verify-report

**WARNING 1**: Instrumented matrix api37 failed on unrelated pre-existing Today-screen compose tests (TodayAddHabitComposeTest and TodaySlotRowComposeTest) both runs via ComposeTimeoutException at exactly 15000ms. Per launch brief, this is the known `today-slot-row-compose-test-timeout-flakiness` item (openspec/config.yaml carried_forward_open_items id: "today-slot-row-compose-test-timeout-flakiness", status: open, escalation_2026_09_03 recorded). Every habit-deletion-authored test (4 HabitRepositoryDeleteTest, 3 HabitDeleteDialogComposeTest, 2 CoreFlowE2ETest habit-deletion methods) passed on both legs both runs with zero variance. Neither test file was modified by this change.

**WARNING 2**: apply-progress.md self-reports 608 changed code-only lines against tasks.md's forecast ceiling of 350-450 (and an apply-attempt self-imposed 600 ceiling exceeded by 8 lines). Still within session's 800-line review budget. Already disclosed by apply agent with size:exception recommendation. Carried forward for maintainer/orchestrator to reconcile before delivery.

## Key Learnings from the Change

The four points the launch brief required the archive report to capture:

### 1. Alarm Ordering: Snapshot → Cascade → Cancel (Prevents Silent Disarming)

The only defect a user would actually notice — that an armed alarm silently never fires — is prevented by correct ordering. HabitRepository.delete snapshots occurrence ids BEFORE the transaction, cascades inside it, and cancels alarms AFTER commit. This is BackupImporter.replaceAll's proven pattern, not setArchived's replanAll-inside-transaction. Had the ordering been reversed (cancel before cascade), a process death between steps would leave a live habit silently disarmed (invisible failure). The chosen order fails benignly: an orphan armed alarm finds no row and returns (ReminderFireWorker.fire:39-41 has two null-guards).

### 2. Two-Test Proof: Repository Cancel + E2E Null-Guard

The "no reminder fires" proof is deliberately split into two tests proving non-overlapping halves, not one test claiming both. HabitRepositoryDeleteTest.kt:76 drives repository.delete() and asserts `verify { alarmScheduler.cancel(id) }` per snapshotted id — proves the FIRST line of defence (cancellation ran), never touches ReminderFireReceiver. CoreFlowE2ETest.kt:370 deletes through the real UI, fires a snapshotted occurrence into the handler via fixture.fireArmedAlarmFor(), and asserts assertNoNotificationPosted — proves only the SECOND line of defence (handler finds no occurrence/habit, returns null). Each test's KDoc states which half it proves and disclaims the other. A single test claiming both would be false coverage: this codebase produced exactly that shape a day earlier (as noted in the apply phase report).

### 3. HabitDao.deleteById Had Zero Production Callers Until Now

`HabitDao.deleteById` already existed at core/data/dao/Daos.kt:34-35 with ZERO production consumers — only two androidTest seed helpers called it. This is the fifteenth catalogued instance of this repository's documented failure mode: "logic written, never wired". This change is what finally gives the four-year-old method its first production caller. The fact that it did not already have one is visible on the property that protects this code from silent defects: if HabitRepository.delete had been implemented as `habitDao.deleteAll()` instead of `habitDao.deleteById(id)`, or if the cascade had silently not worked, both mistakes would have compiled, passed the unit and instrumented tests, and shipped as silent data corruption. The cascade was verified against the schema, not just assumed. The DAO method had a test asserting the cascade works. But "does HabitDao.deleteById work?" was an unchecked box in production until the one call site landed.

### 4. CoreFlowE2ETest Product Decision Reversal: Archive ≠ Delete

The test at CoreFlowE2ETest:309-317 encoded "there is no hard delete… by design" as both prose and an assertion. That prose is now false. The assertion (habit row survives archiving) is still correct, but the reason changed: archiving survives because it is reversible and data-preserving, not because deletion is forbidden. The maintainer's decision to introduce deletion is explicit, and the rationale in the test was rewritten while the assertion it justified was kept. The trade-off the original decision named — "a deleted habit takes its history with it" — was accepted knowingly. This is exactly the shape a design reversal should take: rationale changes, assertion stays if still correct, the decision owner is named.

## Specification as Source of Truth

The openspec/specs/habit-management/spec.md is now the authoritative statement of this app's habit management behavior. All seven requirements stand. Six scenarios across Habit Deletion and Habit Archiving are non-overlapping and each maps to a named test at a file:line. Three scenarios for Habit Archiving are preserved unchanged from before this change.

## Handoff

This change is complete and verified. It is ready for review and delivery under ordinary repository policy. No schema migration, no data shape change, and the only new DAO method was wired with its first production caller.

---

**Archive date**: 2026-09-03  
**Observation IDs**: proposal(106), spec(107), design(108), tasks(109), verify-report(114)
