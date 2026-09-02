# Archive Report: First-Run Onboarding

**Change**: `first-run-onboarding`  
**Archived**: 2026-09-02  
**Archive Location**: `openspec/changes/archive/2026-09-02-first-run-onboarding/`  
**Artifact Store**: hybrid (both filesystem and Engram)

## Artifact Traceability

All artifacts have been persisted and are referenced by observation ID for Engram continuity:

| Artifact | Observation ID | Filesystem Path | Status |
|----------|---|---|---|
| Proposal | #70 | `openspec/changes/first-run-onboarding/proposal.md` | Archived |
| Spec | #71 | `openspec/changes/first-run-onboarding/specs/` | Archived |
| Design | #72 | `openspec/changes/first-run-onboarding/design.md` | Archived |
| Tasks | #73 | `openspec/changes/first-run-onboarding/tasks.md` | Archived |
| Verify Report | #77 | `openspec/changes/first-run-onboarding/verify-report.md` | Archived |

## Final State Summary

**Implementation Status**: Fully implemented and merged to `main`

- **PR #49 (Unit A)**: Onboarding gate, onboarding package, `onboardingDone` flag, 11 unit tests. Merged to `main` at commit `2500265`.
- **PR #50 (Unit B)**: DataStore seeding infrastructure, `CoreFlowE2ETest` rework, API37 measurement. Merged to `main` at commit `2500265`.
- **Current branch**: `chore/verify-and-archive-first-run-onboarding` (tracking applied changes post-merge)

**Task Completion**: 29/29 tasks complete (100%)

All implementation tasks in `tasks.md` are marked complete per `sdd-apply`. No unchecked tasks remain.

**Verification Result**: PASS

- **Verdict**: pass (per `verify-report` observation #77, verified 2026-09-02 13:50:45)
- **Critical findings**: 0
- **Warnings**: 1 (pre-existing test infra race in `TodayComposeTest`/`TodayAdaptiveComposeTest`, not introduced by this change)
- **Suggestions**: 2 (placement of `@EntryPoint` in production `core/di/` for androidTest consumer; Unit A line count 3 lines over budget)
- **Requirements verified**: 7/7
- **Scenarios verified**: 15/15 (6 from onboarding, 9 from reminder-response delta)
- **Unit tests**: 147 passed / 0 failed (`:app:testDebugUnitTest`)
- **Instrumented tests**: API 31 78 tests / 0 failures / 2 skipped; API 37 78 tests / 0 failures / 1 skipped
- **Build gates**: detekt, detektMain, lintDebug all clean

**Design Corrections Applied During Implementation**

Per the Final-State Authority section of the skill (stale snapshot claims are outranked by explicit final-state facts and verify-report evidence):

1. **§8.3 assumption about re-showing permission dialog on API37**: Designed assumption was that after one denial, if the app's latch were cleared, the system would re-show the `POST_NOTIFICATIONS` dialog once more. This assumption was **settled by measurement, not argument**, during Unit B's apply work. On the api37 instrumented image, after a single real denial, the system DID re-show the dialog exactly once when the app's latch was cleared. The designed fallback behavior (displaying a blocked-variant deep-link banner instead) was not needed on this device, but remains a safe defensively-correct posture for other OEM implementations.

2. **§8.1 `@EntryPoint` placement corrected during apply**: The original design placed the Hilt `@EntryPoint` in `androidTest`, but this threw `ClassCastException` when the test ran. Root cause: this app instruments the real `ConstanzaApplication` (no `HiltAndroidTest` test-harness), so KSP aggregates `SingletonComponent` from `main` sources only, not from androidTest. The entry point was moved to `core/di/DataStoreModule.kt` as an `internal` scoped method, making it visible to both main and androidTest. This is recorded as a design correction discovered during apply, fully documented in the file's own KDoc with the exact exception evidence that forced the move, and approved by the verify report's assessment (§Correctness, `ReminderSettingsDataStoreEntryPoint`'s placement).

## Specs Merged

**New capability**: `onboarding`
- Created `openspec/specs/onboarding/spec.md` (6 requirements, 10 scenarios)
- All requirements authored to capture the first-run flow, notification permission ask at context, and completion hand-off into habit creation

**Modified capability**: `reminder-response`
- Updated `openspec/specs/reminder-response/spec.md`'s "Notification Permission Scope" requirement to:
  - Name onboarding as the primary requester of POST_NOTIFICATIONS
  - Explain the two writers to `requested_notification_permission` latch (onboarding's permission screen and TodayViewModel)
  - Document the safety-net behavior of Today's banner where onboarding did not record the ask
  - Add 3 new scenarios: latch-coordination, fallback behavior, blocked-state reuse prevention
  - Preserve existing scenarios: API 33+ denial allows in-app answering, API 31 gets notifications with no prompt
- All other requirements in reminder-response (Notification Actions, Snooze Configuration, Origin-Date Crediting) preserved unchanged

**Carried-forward items**: No changes made to `openspec/config.yaml`'s `carried_forward_open_items`

Four items remain exactly as they were:
- `habit-editor-has-no-cancel-affordance` (open — PR #47 fixed only the editor's own entry points, design §2.1 deferred the gate's BackHandler)
- `notification-permission-blocked-after-one-ask` (open — deliberate deferral recorded by design §2.3, a future change will own this)
- `today-has-no-add-habit-affordance` (open)
- `compose-test-db-teardown-race` (open — pre-existing test infra issue, not introduced by this change)

Per the archive rule, stale checkboxes and closed items must never remain; all carried-forward items are genuinely open and recorded as such.

## Archive Verification

✅ Task Completion Gate: PASS (29/29 tasks checked)
✅ Verification Gate: PASS (0 CRITICAL, no blockers)
✅ Specs merged: onboarding (new) + reminder-response (modified)
✅ Archive folder moved: `openspec/changes/archive/2026-09-02-first-run-onboarding/`
✅ Diff readback: empty (byte-identical copy verified)
✅ Config validation: PASS (`openspec/config.yaml` parses)
✅ Spec count: 10 directories (was 9, now includes onboarding)
✅ Source directory removed: `openspec/changes/first-run-onboarding/` is absent

## SDD Cycle Complete

This change has successfully completed the full SDD lifecycle:
- **Proposed**: Intent and scope defined, risk forecast (medium, 595–800 lines), delivery strategy evaluated
- **Specified**: 7 total requirements across onboarding (new) and reminder-response (modified)
- **Designed**: Gate architecture, onboarding flow, DataStore seeding, test fixture strategy
- **Tasked**: 29 tasks defined in two implementation work units (Unit A: gate + onboarding package + unit tests; Unit B: instrumented rework + seeding)
- **Applied**: Both units merged to main via PR #49 and #50, all tasks complete
- **Verified**: PASS verdict, all requirements and scenarios verified, design corrections documented
- **Archived**: Specs merged into main source of truth, change folder moved to archive with full audit trail

Ready for the next change.
