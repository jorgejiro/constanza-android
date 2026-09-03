# Archive Report: onboarding-exact-alarm-ask

**Change**: onboarding-exact-alarm-ask  
**Branch**: `feat/onboarding-exact-alarm-ask` (21 commits, base `main` at `8d4ca18`)  
**Archived**: 2026-09-03  
**Status**: CLOSED — PASS WITH WARNINGS

---

## Final State

**Completion**: Fully implemented and verified.  
**Task completion**: 32 of 32 tasks ticked across 8 phases (24 original + 5 first correction + 3 second correction).  
**Verification outcome**: PASS WITH WARNINGS — 0 CRITICAL, 1 WARNING, 1 NOTE.

**Test results** (per final-state facts provided at archive time):
- `:app:testDebugUnitTest`: 190 tests, 0 failures
- `detekt`/`detektMain`/`lintDebug`: clean
- `emulatorMatrixGroupDebugAndroidTest`: api31 103/0, api37 103/0

**Verification path**: Three verify runs with two correction rounds:
- Run 1 (2026-09-02): FAIL, 5 CRITICAL
- Run 2 (2026-09-02, after Phase 7 correction): FAIL, 2 CRITICAL remaining  
- Run 3 (2026-09-03, after Phase 8 correction): PASS WITH WARNINGS

---

## Artifact Observations

All artifacts persisted to Engram (hybrid mode). Observation IDs for traceability:

- **Proposal** (#95): `sdd/onboarding-exact-alarm-ask/proposal`
- **Spec** (#96): `sdd/onboarding-exact-alarm-ask/spec`
- **Design** (#97): `sdd/onboarding-exact-alarm-ask/design`
- **Tasks** (#98): `sdd/onboarding-exact-alarm-ask/tasks`
- **Verify Report** (#100): `sdd/onboarding-exact-alarm-ask/verify-report` (final run 3, observation #100 revision 3)

---

## Specs Merged Into Main

### Domain: onboarding

**Requirements changed**: 2 MODIFIED, 1 ADDED

**MODIFIED**: "Two-Screen Flow, API-Conditional" → "Two-Screen Flow, Applicability-Derived"
- The API-level gate was replaced with an applicability-derived rule: screen 2 exists when at least one of `POST_NOTIFICATIONS` or `SCHEDULE_EXACT_ALARM` applies and is unsatisfied.
- 2 old scenarios retained with updated text; 2 new scenarios added covering API 31 revoked-exact-alarms leg and API 37 granted-exact-alarms confirmation line.

**MODIFIED**: "Non-Blocking Permission Ask"
- Extended to cover both permission asks. Text clarified that neither blocks completion.
- 2 old scenarios retained; 2 new scenarios added for exact-alarm settings deep link paths.

**ADDED**: "Exact-Alarm Onboarding Row"
- New requirement specifying row copy, non-auto-launch, and live recomposition on grant.
- 3 scenarios: degradation text, no auto-launch, no restart on grant.

### Domain: reminder-delivery

**Requirements changed**: 1 MODIFIED, 1 ADDED

**MODIFIED**: "Exact-Alarm Permission States"
- Extended to name onboarding as the first offer surface and the Today banner as a standing fallback.
- Added note that declining the onboarding row costs nothing.
- 1 new scenario added: "Declining onboarding's offer costs nothing later".

**ADDED**: "Exact-Alarm Banner, Standing Fallback"
- New requirement specifying banner visibility, no latch (unlike notification permission), and no auto-launch.
- 4 scenarios: render when denied, disappear when granted, declining onboarding doesn't suppress, action deep-links only on tap.

---

## Key Findings

### 1. The spec was wrong, not the code

The initial delta spec scenario for "API 37 with exact alarms already granted" stated screen 2 renders "exactly one row, for notifications" when granted+undecided. The shipped code and Design Decision 2 correctly render both rows with the exact-alarm row as a confirmation line, matching the notification row's pattern for `GRANTED`. The verify phase discovered this and Phase 7 corrected the scenario text, not the code.

**Root cause**: `sdd-spec` and `sdd-design` ran in parallel and their outputs were never cross-checked before implementation began.

### 2. A checkbox asserted a test that did not exist

The Promise Coverage table in `tasks.md` claimed "no-restart-on-grant" had instrumented coverage (CRITICAL-2) that did not exist. The correction round (Phase 8) wrote a real instrumented test: `OnboardingComposeTest.theExactAlarmRowDropsItsAskAndShowsTheConfirmationOnTheSameCompositionWhenGrantedLive`, using a live `mutableStateOf` to drive recomposition without a new `setContent` call.

**Note**: The checkbox was marked done; the test was then written to match the checkbox. Correction applied retroactively to the tasks artifact.

### 3. The architectural guard needed hardening

The decision to assert "declining onboarding's offer costs nothing later" without a persisted flag was sound, but the initial proof was a repo-wide grep. When `gentle-ai sdd-verify-validate` rejected a passing verdict without runtime coverage (by its hard rule), Phase 8 wrote `NoExactAlarmAskPersistenceTest`, a reflection-based JVM unit test that enumerates public members of `AlarmScheduler` and `ReminderSettingsStore` and asserts none is persistence-shaped for exact alarms (name patterns: `record*/has*/is*/get*/set*` combined with completion nouns). The test was proven to bite by planting and reverting a `recordExactAlarmAsked()` method on `AlarmScheduler`.

### 4. Three "X does not exist" claims were made; one was false

During verification:
- `exactAlarmsAllowedScheduler()` was claimed to be phantom in the second verify report's WARNING-3, based on grepping `app/src/test`. The function exists at `TodayViewModelTestFactory.kt:76-80` (found in `androidTest`) and is used at line 45. Phase 7.4 corrected this.
- `NoExactAlarmAskPersistenceTest` was claimed not to exist before Phase 8.1 wrote it; that claim was correctly resolved by writing the test.
- No other negative claims remain in any persisted artifact.

---

## Warnings

**WARNING 1**: Emulator matrix reliability degraded during this change's verification.
- Two of three fresh `--rerun-tasks` matrix runs failed during Run 3, each with a `ComposeTimeoutException` in `TodaySlotRowComposeTest.awaitNodeWithText`.
- Failures were always in pre-existing, untouched code, never this change's own tests.
- Four distinct flaky methods now observed across two verify sessions (this and the prior).
- **Mitigation**: A retry-once policy or targeted quarantine of `TodaySlotRowComposeTest.awaitNodeWithText` is recommended; `openspec/config.yaml` carries this as a standing carried-forward item `today-slot-row-compose-test-timeout-flakiness`.

**NOTE 1**: Play policy for `SCHEDULE_EXACT_ALARM` was not verified during this phase (offline session with no network access). This change does not alter the manifest — it only adds a new surface for offering an already-declared permission. Policy verification is recommended before release.

---

## Changelog: Tasks Completion Summary

| Phase | Work | Ticked | Status |
|-------|------|--------|--------|
| 1 | Enum rename | 4/4 | Complete |
| 2 | ViewModel + applicability | 4/4 | Complete |
| 3 | Exact-alarm row UI | 4/4 | Complete |
| 4 | Instrumented tests | 4/4 | Complete |
| 5 | reminder-delivery confirmation | 2/2 | Complete |
| 6 | Full regression | 2/2 | Complete |
| 7 | Correction round (spec + tests) | 5/5 | Complete |
| 8 | Second correction (persistence guard) | 3/3 | Complete |
| **Total** | | **32/32** | **Complete** |

---

## Archive Contents

- ✅ proposal.md — 11,974 bytes
- ✅ specs/ — onboarding/spec.md, reminder-delivery/spec.md (delta specs, now archived for reference)
- ✅ design.md — 14,195 bytes
- ✅ tasks.md — 15,026 bytes (32/32 tasks ticked)
- ✅ apply-progress.md — 20,311 bytes (Phase 1-6 apply report)
- ✅ verify-report.md — 5,428 bytes (Run 3 final report)

---

## SDD Cycle Closed

This change has been fully planned, implemented, verified through three runs with two correction rounds, and archived. The spec store has been updated with all merged requirements. The change folder has been moved to `openspec/changes/archive/2026-09-03-onboarding-exact-alarm-ask/` and the source folder removed. Ready for the next change.
