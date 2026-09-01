# Archive Report: warm-dark-design-system

**Date Archived**: 2026-09-01 · **Status**: CLOSED · **Verdict**: **PASS WITH WARNINGS — Archive Approved**

## Executive Summary

The `warm-dark-design-system` change has been fully implemented, verified, and archived. All 12 spec requirements have implementing code and passing tests. The change introduces a warm-dark visual language with accessible habit colours, a database migration path for persisted colour data, and schema versioning for reliable import recovery. The first change in this repository to close with zero unowned promises.

## Scope and Artifacts

**Work Units**: 6 (unit 1 through unit 6, covering theme tokens, colour remapping, UI plumbing, migration rollback recipe, and pre-migration snapshot)

**Specs Synced**:
- `openspec/specs/visual-design-system/spec.md` — NEW (5 requirements: dark-only rendering, contrast floors, accent reservation, cold-start rendering, automated test assertion)
- `openspec/specs/habit-management/spec.md` — ADDED 3 requirements (colour palette, palette-change durability, colour visibility on list screens)
- `openspec/specs/data-portability/spec.md` — ADDED 3 + MODIFIED 1 (schema version read on import, legacy colour normalization, automatic pre-migration snapshot, clarified round-trip fidelity scope)

**All Artifacts Present**:
- proposal.md, explore.md, design.md, tasks.md, apply-progress.md, verify-report.md
- specs/ delta folder with 3 domains

## Verification Outcome

Per `verify-report.md` dated 2026-09-01:

**Verdict**: PASS WITH WARNINGS
- **CRITICAL Issues**: 0
- **WARNING Issues**: 2 (task 6.4 `./gradlew check` gap documented but already self-corrected in the artifacts; task 6.5 live notification-accent capture incomplete but indirect proof complete)
- **SUGGESTION Issues**: 2 (pre-existing `ReminderTimeEditor` quirk out of scope; design.md doc-only numeric typo, code is correct)

**Requirement Coverage**: 12/12 — every requirement in all three specs (5 + 3 + 4) has implementing code AND a passing automated test, re-confirmed with `--rerun-tasks` on Pixel 10 (API 37).

**Test Results** (Pixel 10, serial `55221FDCR005RD`, Android 17):
- `:domain:test` — 52/52 passed, 0 failures
- `:app:testDebugUnitTest` — 118/118 passed, 0 failures
- `:app:detektMain` — BUILD SUCCESSFUL, 0 issues
- `:domain:detektMain` — BUILD SUCCESSFUL, 0 issues
- `:app:connectedDebugAndroidTest` — 63/63 passed, 0 skipped, 0 failed

(`:domain` untouched across entire change; verified via `git diff main..HEAD -- domain/` empty)

**Design Coherence**: All 9 design decisions verified against actual code; no deviations found beyond those already flagged as correct (detekt naming rule compliance, object→factory-function shape, catch-variable naming).

## Task Completion

**All 6 work units marked complete** except two tasks deliberately left unchecked as non-automatable:

| Task ID | Status | Reason |
|---------|--------|--------|
| 1.13 | Unchecked | Manual-only: transient cold-start flash (mechanism proven, flash too fast for `screencap`); light-mode system-bar icons (mechanism proven via `enableEdgeToEdge(dark style)` source verification and live screenshot in forced light mode) |
| 6.5 | Unchecked | Manual-only: notification accent on live posted reminder (colour-propagation pipeline proven by automated tests but live `dumpsys` capture attempt hit pre-existing unrelated UI quirk in `ScheduleEditors.kt` not touched by this change); fold/unfold (Galaxy Z Fold 7 not connected this session but tested in units 2–5) |
| 6.4 | Checked | `./gradlew check` NOT green due to 3 pre-existing lint errors (confirmed pre-existing: `git diff main..HEAD` empty for all 3 files). Properly self-corrected in tasks.md itself with documented reason; no false claim |

**Confidence in Completion**: High. The defining-failure-mode hunt found zero unowned promises across all 12 requirements and 9 design decisions. Every requirement has both code and test; every design decision has corresponding code or explicit documentation of non-implementation. This is the first change to close with zero unowned promises (prior change `habit-tracking-mvp` shipped 10 unowned, 8 as production defects).

## Delivery and Review

**Delivery Strategy**: Feature-branch chain (auto-chain, 700-line review budget)

**PR Chain**: #36 (unit 1) → #37 (units 2+3) → #38 (unit 4) → #39 (unit 5) → #40 (unit 6) → #35 (tracker)

**Merged to main**: commit `a91ae1e` (tracker PR #35)

**Scope**: 41 files changed, +3,744/−103 lines
- Forecast: 1,480–2,200 lines (utility model, given ratified stack)
- Actual: 3,744 (includes generated backup schema JSON ~424 lines, all SDD artifacts)

**Note on merge order**: Initial merge sequence was top-down (#36 → #37 → #38 → #39 → #40 → #35) per orchestrator instruction, but best practice is bottom-up (deepest child first). After unit 1 only, tracker PR #35 initially carried incomplete state. Corrected via one merge of `feat/warm-dark-editor-tonal` into tracker; final tree verified byte-identical to the verification branch. **Nothing incorrect ever reached `main`.** Documented as a process gap (order never written down), not an operator error.

## Configuration and Open Items

**`openspec/config.yaml` Verification**: PASS
- `carried_forward_open_items` contains exactly 2 items post-archive: `lint-preexisting-errors` and `G.7-throttling-row` ✓
- Item `7.5` legitimately closed by this change — correctly removed from `carried_forward_open_items.items` ✓
- Closure recorded in section note: "Item 7.5 was closed this way by warm-dark-design-system, which wrote the app's first Room migration and so met its owner condition." ✓
- `G.7-throttling-row` untouched (content byte-identical) ✓

**`lint-preexisting-errors`** (newly added, blocking `./gradlew check`):
- `MissingPermission` at `reminding/NotificationPoster.kt:52` (pre-existing, gated by `canPost()` but lint cannot prove it; needs annotation or inline check lint can see)
- `NonObservableLocale` at `habit/ScheduleEditors.kt:242` (pre-existing; reads locale in composable)
- `ViewModelConstructorInComposable` at `habit/HabitScheduleKindComposeTest.kt:60` (pre-existing, test-only)

All three verified pre-existing: `git diff main..HEAD` empty for all three files across the entire PR chain.

## What Worked Well

1. **Promise Coverage Table**: Explicit mapping of every design/spec obligation to a numbered task id. Two high-risk items (2.12 for wrong schema path correction, 3.6 for removing item 7.5) named up front with dedicated task ids — both completed correctly. This discipline is why zero unowned promises survived to close.

2. **Self-Correcting Artifacts**: When `./gradlew check` failed due to pre-existing lint issues, the tasks artifact documented this honestly and added the carried-forward item instead of silent workaround. Verification caught the self-correction and verified its accuracy independently.

3. **Byte-Identity Verification**: The sign-trap correction in `AppMigrations.kt` (using SQL parameter binding instead of hex literals) was verified with explicit proof of the sign-trap risk (`0xFF8E24AA.toInt() = -7461718` vs SQL-parsed `4287505578`). The actual `AppDatabaseMigrationTest` asserts post-migration row **values**, not just "test ran", catching any silent no-op migration.

4. **Palette Remapping Bijection**: The colour remap from legacy to current palette (6 distinct → 6 distinct, disjoint domains, verified programmatically) was proven end-to-end: legacy→current mapping, `HabitPalette` membership check, and one-to-one guarantees with the orange→pink family change documented and justified.

5. **Schema Versioning Gate**: The prior defect of write-only `CURRENT_SCHEMA_VERSION` is now fixed: it is public and read on import to gate normalization. Verified in `BackupImporter` as actually gating the normalization call path.

## Process Lessons

1. **Merge Order in Feature Chains**: Feature-branch chains must be merged bottom-up (deepest child first), not top-down. The initial top-down merge left the tracker carrying incomplete state until corrected with one merge of the composed branch. Documented as a process gap (the order was never written down), not an operator error — but worth recording for the next change.

2. **SDD Ledger Ceiling**: The `gentle-ai sdd-attempt` cumulative 400-line ledger was dropped after unit 3 by user decision — its ceiling was 400 lines, but the six-unit chain forecast 1,480–2,200. All three blocks it recorded were accounting artifacts (planning docs, generated JSON, cumulative total), not real problems. The control that actually held — 3 for 3 — was the per-unit stop-and-report threshold in each apply prompt. For the next six-unit change, the per-unit threshold should be the sole control.

3. **Database Migration Test Completeness**: A `MigrationTestHelper` test that asserts `runMigrationsAndValidate` succeeds is necessary but not sufficient. The AppDatabaseMigrationTest proved this by asserting actual post-migration row **values** via a raw `SELECT` cursor, catching hypothetical no-op migrations that dummy-validate.

## Archive Completeness Checklist

- [x] Main specs updated with all delta requirements (visual-design-system, habit-management, data-portability)
- [x] Change folder moved to `openspec/changes/archive/2026-09-01-warm-dark-design-system/`
- [x] Archive contains all original artifacts (proposal, specs, design, tasks, apply-progress, verify-report, explore)
- [x] Archived `tasks.md` has no stale unchecked implementation tasks (two tasks left unchecked are documented as non-automatable)
- [x] Active changes directory no longer has `warm-dark-design-system`
- [x] Mechanical copy/move verified via `diff -r` (empty diff confirms byte-identity)
- [x] `openspec/config.yaml` verified post-archive (exactly 2 open items, item 7.5 closed with note)

## Traceability

- **Proposal**: `sdd/warm-dark-design-system/proposal`
- **Spec**: `sdd/warm-dark-design-system/spec`
- **Design**: `sdd/warm-dark-design-system/design`
- **Tasks**: `sdd/warm-dark-design-system/tasks`
- **Verify Report**: `sdd/warm-dark-design-system/verify-report`
- **Archive Report**: `sdd/warm-dark-design-system/archive-report`

All artifacts persisted to both openspec filesystem and Engram observation store.

## SDD Cycle Status

**COMPLETE**. The warm-dark-design-system change has been:
1. Proposed (rationale, rollback plan, scope)
2. Specified (12 requirements across 3 domains, scenarios with Given/When/Then)
3. Designed (9 decisions, technical approach, corrections to inputs)
4. Tasked (6 work units, hierarchical, one-session completable)
5. Applied (6 units implemented, all checkboxes checked or documented as non-automatable)
6. Verified (12/12 requirements with code + tests, 0 CRITICAL, 2 WARNING, 2 SUGGESTION)
7. Archived (specs merged, change folder moved, report persisted)

Ready for the next change.
