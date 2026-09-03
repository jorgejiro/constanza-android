# Archive Report: today-midnight-rollover

**Archive Date**: 2026-09-03  
**Change Name**: today-midnight-rollover  
**Status**: Complete and archived

## Executive Summary

The change `today-midnight-rollover` has been fully planned, implemented, verified, and archived. All 19 implementation tasks completed successfully. The specification has been merged, and the backlog defect has been closed with verified resolution.

## Artifacts

### Change Artifacts Present
- ✅ proposal.md
- ✅ specs/habit-entry-tracking/spec.md (delta)
- ✅ design.md
- ✅ tasks.md

### Task Completion
- **19/19 tasks complete** (100%)
- All implementation tasks checked and verified
- No unchecked items in persisted tasks artifact

### Verification Status
- `sdd-verify` returned **PASS**
- **0 CRITICAL** issues
- **0 WARNING** issues
- Matrix testing: 115 tests per leg (api31 + api37)
  - api31: 0 failures, 0 errors, 2 skipped
  - api37: 0 failures, 0 errors, 1 skipped

## Specifications Merged

### `openspec/specs/habit-entry-tracking/spec.md`

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| Total Requirements | 6 | 7 | +1 |
| Day-Level Rollup Scenarios | 6 | 9 | +3 |

#### Added Requirements
- **In-App Answer Date Attribution**: New requirement specifying that answers must be recorded against the current displayed date, not the date at construction. If midnight passes between construction and answer, the write targets the new date.
  - 2 scenarios added
  
#### Modified Requirements
- **Day-Level Rollup and Per-Slot Display**: Requirement widened to include date tracking across midnight. Now specifies that the today screen MUST track the current local date, cross local midnight with re-render, and correct the display on resume after backgrounding. The foregrounded timezone-travel gap is documented as an accepted boundary.
  - All 6 original scenarios preserved byte-identical
  - 3 new scenarios added:
    - Today screen rolls over at local midnight while displayed
    - A backgrounded app corrects the date on resume
    - Foregrounded timezone travel remains a known, accepted gap
  - Total: 9 scenarios

#### Preserved Requirements (Unchanged)
- Entry States (2 scenarios)
- Slot Independence (3 scenarios)
- **Midnight Transition** (3 scenarios) — preserved byte-identical
- Provisional-Missed Correction (4 scenarios)
- Abandoned Snooze Resolution (4 scenarios)

**Requirement count verification**: 
- Entry States (2) + Slot Independence (3) + Midnight Transition (3) + Provisional-Missed Correction (4) + Abandoned Snooze Resolution (4) + Day-Level Rollup (9) + In-App Answer Date Attribution (2) = **27 scenarios** across 7 requirements

## Backlog Item Closed

### `openspec/config.yaml`

**Item**: `today-never-rolls-over-at-midnight`
- **Previous Status**: open
- **Current Status**: resolved
- **Resolution**: The date became an observed source via a `CurrentDateSource` port emitting the local date at each local midnight. `TodayViewModel` keys `uiState` on it through `flatMapLatest`, maintaining exactly five typed sources in the combine. `ON_RESUME` pushes the current date for backgrounding-across-midnight recovery. `answer()` reads `uiState.value.date` instead of a clock read, fixing data corruption where after-midnight answers were written against yesterday. The tick loop re-reads the clock every iteration with a 1-second positive floor at exact midnight.
- **Resolution Verified**: 196 JVM unit tests (0 failures, 0 errors), detekt zero findings, all three source sets compile, matrix green on both legs 2026-09-03 (api31: 115 tests/0/0/2 skipped, api37: 115/0/0/1 skipped, skips complementary by design).
- **Note**: Timezone travel while continuously foregrounded tracked separately as `today-foregrounded-timezone-travel` (open, by design).

## Work Completed

### Phase 1: Core Foundation
- `millisUntilNextMidnight()` moved from `scheduling/WorkScheduler.kt` to `core/time/TimeProvider.kt`
- `CurrentDateSource` port created and wired via `TimeModule.kt`
- Test coverage for timer re-read, late wake-up, and midnight floor

### Phase 2: ViewModel Wiring
- `LocalDate` field added to `TodayUiState`
- `TodayViewModel` now injects `CurrentDateSource` instead of `TimeProvider`
- Existing five-source combine wrapped in `observedDate.flatMapLatest`
- `answer()` fixed to read `uiState.value.date`, addressing data corruption
- `ON_RESUME` integration to refresh date after backgrounding

### Phase 3: Test Fixture Audit
- `TodayViewModelTest` refactored with fake `CurrentDateSource`
- Rollover test: advancing date re-subscribes and updates display
- Answer attribution test: post-midnight answers write to new date only
- Resume test: stale `observedDate` corrected on return

### Phase 4: Config and Verification
- `openspec/config.yaml`: added `today-foregrounded-timezone-travel` item (carried forward, deliberately open)
- `:app:detektMain`: no `ForbiddenMethodCall` violations
- Matrix green on both legs (api31 + api37)

## Final State

- **Change folder**: Ready for archive move
- **Main specs**: Updated with merged delta
- **Backlog**: Defect closed and documented
- **All artifacts**: In place and verified
- **SDD Cycle**: Complete

This change is archived and closed. The app now correctly displays the current date on the today screen, updating at local midnight even when the app remains foregrounded, and correcting the display when resumed after backgrounding across midnight.
