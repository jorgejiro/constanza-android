# Tasks: Habit Deletion

## Review Workload Forecast

Code-only (what the PR is judged against — planning docs land in their own commits per repo
convention): **≈350–450 lines** (~120 production, ~230 test). Against the cached
`review_budget_lines: 800` for this session, that is Low risk with wide margin, well inside 800.

All-in, for context only, not the judged figure: proposal.md (105), spec delta (94), design.md
(220) are already committed = 419 doc lines + 350–450 code = **≈769–869 lines**. This is close to
or slightly over 800 if ever counted together, but repo convention excludes already-committed
planning docs from the code PR's review budget, so it is not the operative number.

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

Single PR. No `size:exception` needed — code-only stays under budget on any threshold in play.

### Suggested Work Units

| Unit | Goal | PR | Focused test | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| 1 | Full change (data, VM, UI, tests) | PR 1 | Repository + Compose instrumented tests (Phase 4) | `./gradlew :app:emulatorMatrixGroupDebugAndroidTest`, API 31+37 | Revert the single commit set; no schema change, no migration to unwind |

## Spec Conformance (carried forward from design.md)

| Scenario | Mechanism | Proven by |
|---|---|---|
| Deleting a habit with history removes it and all records | 4× `ForeignKey.CASCADE` inside one transaction | 4.1 |
| Deleting a habit with no history behaves the same | No count branch; same `delete` path | 4.4 |
| Confirmation states exact recorded-answer count | Resident `entryCounts` map + `<plurals>` | 4.5 |
| Declining changes nothing | Nothing runs before confirm | 4.6 |
| No reminder fires for a deleted habit | Cancel post-commit (repo) + null-guard (handler) | 4.2 (cancel) + 4.9 (handler) |
| Deletion does not affect archiving | `delete` never touches `archived`; no `replanAll()` | 4.3 |

## Phase 1: Test Infrastructure (blocking — must land before Phase 4 tests)

- [ ] 1.1 `app/src/androidTest/kotlin/com/jjrapps/constanza/habit/HabitRepositoryTestFixture.kt`: hoist
      the relaxed `AlarmScheduler` mock (currently built inline inside `OccurrencePlanner`
      construction) to a shared field used by both `OccurrencePlanner` and `HabitRepository`, so
      `verify { alarmScheduler.cancel(id) }` becomes possible. Blocks 4.2.
- [ ] 1.2 `app/src/androidTest/kotlin/com/jjrapps/constanza/e2e/CoreFlowTestFixture.kt`: add a bounded
      *negative* wait, `assertNoNotificationPosted(id)`, alongside the existing
      `awaitPostedNotification`. Blocks 4.9.

## Phase 2: Data Layer (Design D1, D3)

- [ ] 2.1 `app/src/main/kotlin/com/jjrapps/constanza/core/data/dao/Daos.kt`: add
      `data class HabitEntryCount(val habitId: Long, val count: Int)` and
      `EntryDao.observeCountsByHabit(): Flow<List<HabitEntryCount>>`
      (`SELECT habitId, COUNT(*) AS count FROM entries GROUP BY habitId`); in the same task, fold it
      into `HabitListViewModel`'s existing `combine`, exposing
      `entryCounts: Map<Long, Int>` on `HabitListUiState` — query and fold are one deliverable, not
      two, because `combine` withholds emission until every source has emitted, which is what makes
      `state.entryCounts[habit.id] ?: 0` honest.
- [ ] 2.2 `HabitDaos`: add `reminderOccurrenceDao` field (5th field).
- [ ] 2.3 `app/src/main/kotlin/com/jjrapps/constanza/habit/HabitRepository.kt`: constructor gains
      `alarmScheduler: AlarmScheduler` (6 params, under the unconfigured `LongParameterList`
      threshold of 7); add `suspend fun delete(habitId: Long)` — snapshot armed occurrence ids via
      `reminderOccurrenceDao.findByHabitId`, delete the habit row inside `database.withTransaction`,
      then `alarmScheduler.cancel(id)` per snapshotted id after commit. No `replanAll()`.

## Phase 3: ViewModel + UI Wiring (Design D2, D4)

- [ ] 3.1 `HabitListViewModel.kt`: add `delete(habitId: Long)` calling `repository.delete`.
- [ ] 3.2 `HabitListScreen.kt`: add trailing `IconButton(Icons.Filled.MoreVert)` with a `DropdownMenu`
      (single item: Delete) to `HabitRow.trailingContent`, beside the existing Progress/Archive
      `TextButton`s.
- [ ] 3.3 `HabitListScreen.kt`: `DeleteHabitDialog` (M3 `AlertDialog`, matching
      `HabitEditorScreen`'s `DiscardChangesDialog` shape) naming the habit and
      `entryCounts[id] ?: 0`; `pendingDeleteId` as `rememberSaveable { mutableStateOf<Long?>(null) }`,
      resolved by id from `state.habits`; `HabitListActions.onDeleteHabit` wired through.
- [ ] 3.4 `res/values/strings.xml`: menu label, dialog title, `<plurals>` body (`one`/`other`),
      confirm label; dismiss reuses `action_cancel`.

## Phase 4: Tests

- [ ] 4.1 Repository instrumented test: delete a habit with a schedule, reminder slots, and entries;
      assert all four child tables (`schedules`, `reminder_slots`, `entries`,
      `reminder_occurrences`) are empty. Proves: cascade (spec scenario 1).
- [ ] 4.2 Repository instrumented test, using the hoisted mock from 1.1:
      `verify { alarmScheduler.cancel(id) }` once per snapshotted armed occurrence id. Proves: the
      **first** line of defence — cancellation actually ran — not that a later broadcast is ignored
      (that's 4.9).
- [ ] 4.3 Repository instrumented test: two habits, one archived; delete the active one; assert the
      archived habit's `archived`, `archivedAt`, and entries are unchanged. Proves: deletion does not
      affect archiving.
- [ ] 4.4 Repository instrumented test: delete a zero-entry habit; same outcome as 4.1 minus entries.
      Proves: no-history parity.
- [ ] 4.5 Compose test (`HabitRepositoryTestFixture.register`): overflow opens; dialog shows habit
      name and "7 recorded answers"; a zero-history habit shows "0 recorded answers". Proves: D3/D4
      count rendering.
- [ ] 4.6 Compose test: declining the dialog leaves every table unchanged.
- [ ] 4.7 Manual/visual check on the API 31 emulator: confirm two `TextButton`s + the new 48dp
      `IconButton` in `HabitRow.trailingContent` fit and remain tappable at 360dp width; adjust
      spacing via `Spacing`/`Dimens` if the row is visibly cramped. No raw `.dp`.
- [ ] 4.8 `CoreFlowE2ETest.kt`: rewrite the `:309-317` KDoc rationale for
      `removingAHabitThroughTheUiTakesItOffTodayAndOutOfTheSchedule` — the row survives because
      archiving is the *reversible* gesture, not because no delete exists. The assertion itself
      (habit row survives, archived, stamped) stays unchanged. Split `REMOVED_HABIT` into
      `ARCHIVED_HABIT` and `DELETED_HABIT`; reword the `:337` assertion message accordingly.
- [ ] 4.9 `CoreFlowE2ETest.kt`: new
      `deletingAHabitThroughTheUiRemovesItAndItsHistoryAndSilencesItsReminder`. Read
      `fixture.latestArmedOccurrenceFor(habit.id)` before deleting, delete through the UI, then
      `fixture.fireArmedAlarmFor(occurrence)` and assert `assertNoNotificationPosted` (from 1.2).
      State in the test KDoc that this proves only the **second** line of defence (handler's
      null-guard finds no occurrence/habit) — not that `AlarmScheduler.cancel` ran, which 4.2 proves.

## Phase 5: Verification

- [ ] 5.1 `./gradlew check` (no instrumented coverage; confirms compile + unit/lint gates).
- [ ] 5.2 `./gradlew :app:emulatorMatrixGroupDebugAndroidTest`, API 31 + API 37, nothing attached.
      Known flake: `TodaySlotRowComposeTest` times out intermittently via `awaitNodeWithText`
      (filed as `today-slot-row-compose-test-timeout-flakiness`, untouched by this change) — re-run
      before attributing an unrelated failure to this work.
