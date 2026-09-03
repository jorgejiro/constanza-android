# Tasks: Collapse an Answered Today Slot

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated lines, code-only | ~430–520 (prod ~190–260; tests ~230–260, one new file) |
| Estimated lines, all-in | ~790–880 (code-only + docs already committed: 99+199+65) |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | single-pr |
| Chain strategy | size-exception |

Decision needed before apply: Yes
Chained PRs recommended: No
Chain strategy: size-exception
400-line budget risk: Medium

Code-only is what this repo's PR is judged against; docs land in separate commits. It fits the session's 800-line budget but exceeds the skill's 400-line default, so `single-pr` still needs maintainer `size:exception`. All-in nears 800 if a reviewer counts the whole branch diff.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Collapse/reopen end to end (model + VM + UI + tests) | PR 1 (size:exception) | `./gradlew :app:testDebugUnitTest --tests "*Today*"` | `./gradlew :app:emulatorMatrixGroupDebugAndroidTest`, API 31+37, `TodayAnsweredSlotComposeTest` | Revert branch; state is in-memory only |

## Phase 1: Foundation
- [x] 1.1 Add `TodaySlotKey(habitId: Long, slotId: Long?)` + `TodaySlot.keyIn(habitId)` to `app/src/main/kotlin/com/jjrapps/constanza/tracking/TodayModel.kt`.
- [x] 1.2 Add `today_slot_change`, `today_slot_change_a11y` to `app/src/main/res/values/strings.xml`.

## Phase 2: ViewModel — reopen state
- [x] 2.1 Add `private val reopenedSlots = MutableStateFlow<Set<TodaySlotKey>>(emptySet())` to `TodayViewModel.kt`.
- [x] 2.2 Bundle `expandedHabitIds` + `reopenedSlots` into one combine source, mirroring `permissionBanners` — `combine` is already at its 5-source limit.
- [x] 2.3 Add `fun requestChange(key: TodaySlotKey)` in `TodayViewModel.kt`, adding `key` to `reopenedSlots`.
- [x] 2.4 In `TodayViewModel.answer()`, remove the slot's key from `reopenedSlots` before the write coroutine launches.
- [x] 2.5 Add `val reopenedSlots: Set<TodaySlotKey> = emptySet()` to `TodayUiState` in `TodayViewModel.kt`.

## Phase 3: UI — SlotRow branch and Change control
- [x] 3.1 Add private `SlotActions(reopenedKeys, onRequestChange, onAnswer)` in `TodayScreen.kt`, built with `remember(onRequestChange, onAnswer, state.reopenedSlots)` in `TodayContent`.
- [x] 3.2 Thread `SlotActions` through `TodayContent`→`HabitRollupRow`→`SlotRow`, replacing `onAnswer`; swap `habitId: Long` for `TodayHabitRow`. Both composables stay at 5 params — no new suppression.
- [x] 3.3 Extract the time-prefix logic out of `slotStatusText` into a helper, so the answered branch never evaluates the snooze check at `TodayScreen.kt:250`.
- [x] 3.4 In `SlotRow`, branch on `slot.status == UNKNOWN || key in actions.reopenedKeys`: `AnswerButtons` (unchanged) vs. `slotStatusLabel` text + one `ChangeButton`.
- [x] 3.5 Add `ChangeButton` in `TodayScreen.kt`: `TextButton` labelled `today_slot_change`, `contentDescription` from `today_slot_change_a11y` (habit name + answered status text), calling `actions.onRequestChange(key)`.

## Phase 4: Tests
- [x] 4.1 [RED→GREEN] Create `TodayAnsweredSlotComposeTest.kt` (`app/src/androidTest/.../tracking/`) using `TodayScreenWaits.kt`'s waits, no per-class `waitUntil`. Seed a 3-slot habit, answer each slot (Yes, No, Skip); assert buttons vanish, status text names the answer with no snooze wording even when snoozed, Change reopens only that slot, and re-answering re-collapses it. Covers the sibling-independence and answered-text scenarios.
- [x] 4.2 Same file: test `TodaySlotKey(habitId, null)` — a no-reminder-time habit's slot reopens/re-collapses correctly (null-slotId independence scenario).
- [x] 4.3 Same file: test each `ChangeButton`'s `contentDescription` differs from its sibling's (gesture-free, per-slot label scenario).
- [x] 4.4 Run all four existing Today Compose tests unmodified; confirm they stay green — proves, not assumes, zero regressions (sibling-untouched, independent-rows scenarios).
- [x] 4.5 Run `./gradlew :app:detekt`; confirm `LongParameterList` stays under its default 6 for `SlotActions`, `SlotRow`, `HabitRollupRow` with no new suppression.
- [x] 4.6 Run `./gradlew :app:emulatorMatrixGroupDebugAndroidTest`, API 31 + 37, nothing attached. A `TodaySlotRowComposeTest` failure is now a real regression, not the closed flake.

## Phase 5: Close-out
- [x] 5.1 Confirm via 4.4 that the three `:domain` rollup-precedence scenarios still hold — `:domain`-only, untouched by this change.
- [x] 5.2 Leave the identical-accessible-label edge case (two same-named, no-reminder-time habits) as a documented limitation in `design.md`; no fix scheduled.
