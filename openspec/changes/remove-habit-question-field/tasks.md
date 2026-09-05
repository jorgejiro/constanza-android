# Tasks: Remove the `question` field from the habit model

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | Slice 1: 80–120 · Slice 2: 90–130 · Slice 3: 224–334 (exits 1+2 applied) |
| 400-line budget risk | Medium |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 notification → PR 2 row+editor+D3 → PR 3 removal+migration |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: Medium

Sequential slices, sharing `NotificationPoster.kt`, `HabitListScreen.kt`, `HabitEditorScreen.kt`. Slice 3
cannot split further — Room derives `3.json` from the entity, so the migration must land with the field
removal in one commit. Exit 1 (D3 filename fix moved to Slice 2) and exit 2 (table-driven migration test
seed) are both applied below; with both, Slice 3 lands under 400 lines. Prerequisite: PR #79
(`fix/habit-list-back-navigation`) merges before Slice 2.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Notification shape, 3-arg `postReminder` | PR 1 | `:app:testDebugUnitTest --tests "*NotificationPoster*" --tests "*ReminderFireWorker*"` | `:app:emulatorMatrixGroupDebugAndroidTest` (API 31+37) | `git revert`, no persisted state |
| 2 | `HabitRow` overflow menu, editor notes, D3 fix | PR 2 | `:app:testDebugUnitTest --tests "*HabitEditorViewModel*"` | `:app:emulatorMatrixGroupDebugAndroidTest` | `git revert`, no persisted state |
| 3 | Field removal, Room v3 migration, child-row guard | PR 3 | `:app:testDebugUnitTest :app:compileDebugAndroidTestKotlin` | `:app:emulatorMatrixGroupDebugAndroidTest` | Forward-only `Migration(3,4)`; recovery via `pre-migration-v2.sql` |

## Phase 1: Notification Shape (PR 1)

Satisfies `reminder-response` → Notification Actions (3 scenarios).

- [x] 1.1 RED: rewrite `app/src/androidTest/kotlin/com/jjrapps/constanza/localization/SpanishColdProcessNotificationInstrumentedTest.kt:99,117-123` — assert `EXTRA_TITLE == "Seguimiento de hábitos"`, `EXTRA_TEXT`/`EXTRA_BIG_TEXT` == the Spanish habit name; keep the action/channel assertions.
- [x] 1.2 `app/src/main/res/values/strings.xml`, `values-es/strings.xml`: add `notification_reminder_title` (EN "Habit tracker", ES "Seguimiento de hábitos"); remove `notification_default_question`.
- [x] 1.3 `reminding/NotificationPoster.kt`: drop the `question` param from `postReminder`/`buildNotification`; `.setContentTitle(R.string.notification_reminder_title)`, `.setContentText(habitName)`, `.setStyle(BigTextStyle().bigText(habitName))`; update the KDoc.
- [x] 1.4 `scheduling/ReminderFireWorker.kt:52`: drop the `habit.question` argument.
- [x] 1.5 Fix `postReminder` arity at every call site: `NotificationPosterTest.kt:101`, `NotificationPosterInstrumentedTest.kt:90,119`, `NotificationActionWiringInstrumentedTest.kt:109`, `ReminderFireWorkerTest.kt:71,90`, `ReminderWorkerTestFixtures.kt:26,29`. (`ReminderWorkerTestFixtures.kt:26,29` is `insertHabitWithSchedule`'s `question` param on `HabitEntity`, unrelated to `postReminder`'s arity and out of Phase 1 scope — left untouched; the field itself is removed in Phase 3.)
- [x] 1.6 GREEN: run 1.1 plus the full instrumented matrix (Unit 1). Also required an unlisted fix: `e2e/CoreFlowE2ETest.kt:290-298` (`creatingAHabitThroughTheUiDeliversItsReminderAndRecordsTheAnswerTappedOnIt`) asserted `EXTRA_TITLE == REMINDED_HABIT`, which broke by the same construction as the headline test; repointed to assert `EXTRA_TITLE == notification_reminder_title` and added an `EXTRA_TEXT == REMINDED_HABIT` assertion. `:app:testDebugUnitTest` (221 tests), `:domain:test` (52 tests), `:app:detektMain`, and `:app:compileDebugAndroidTestKotlin` all green; `:app:emulatorMatrixGroupDebugAndroidTest` (api31 143 tests/3 skipped/0 failed, api37 143 tests/6 skipped/0 failed) green after the `CoreFlowE2ETest` fix — see apply-progress for the first, stale-build run that caught it.

## Phase 2: Habit Row + Editor + Snapshot Filename (PR 2)

Satisfies `habit-management` → Habit List Row Actions And Name Display (4 scenarios). Requires PR #79 merged first.

- [x] 2.1 RED: add `HabitListScreen` compose tests — overflow launcher only rendered, no always-visible buttons; opening it shows Progress, Archive/Un-archive, Delete; a name fitting two lines shows no ellipsis; a longer name ellipsizes at two lines. (`habit/HabitListRowMenuComposeTest.kt`, new file.)
- [x] 2.2 `habit/HabitListScreen.kt`: `HabitRow.trailingContent` → `Box { IconButton + DropdownMenu }` (Progress, Archive/Un-archive, Delete, each resetting `menuExpanded = false`); delete `supportingContent`; `headlineContent` gets `maxLines = 2, overflow = Ellipsis`; rewrite the KDoc to record the D2 supersession.
- [x] 2.3 Repoint menu-by-text sites to open the overflow first (reuse `HabitDeleteDialogComposeTest.kt:64`'s pattern): `e2e/CoreFlowE2ETest.kt:342` (line drifted from the plan's :336 — confirmed by grep before editing), `habit/HabitListArchiveComposeTest.kt` (archive click, un-archive click, and the reopen before the final `assertExists()`). Grepped every `habit_list_progress|archive|unarchive|delete|more_options` site across `androidTest`/`test`; `HabitListBackComposeTest.kt` already opened the overflow first and needed no change; no site the plan missed.
- [x] 2.4 `habit/HabitEditorScreen.kt`: notes `OutlinedTextField` gains `minLines = 3, maxLines = 5`.
- [x] 2.5 `core/data/migration/PreMigrationSnapshotWriter.kt`: derive the filename from `db.version` (`pre-migration-v${db.version}.sql`/`.tmp`), replacing the hardcoded `pre-migration-v1.sql` + `REPLACE_EXISTING` (design D3). Found and fixed a bug of my own making while doing this: `db.version` must be read INSIDE the existing `try`, not before it — reading it before would let a database-access failure escape `write()` uncaught, breaking property 1 of this class's own KDoc ("never throws for any recoverable cause"). `PreMigrationSnapshotWriterTest.kt` gained `every { db.version } returns 1` so it keeps testing `db.query`'s failure specifically, as its name says.
- [x] 2.6 GREEN: `:app:testDebugUnitTest` (221 tests), `:domain:test` (52 tests), `:app:detektMain`, `:app:compileDebugAndroidTestKotlin` all green. `:app:emulatorMatrixGroupDebugAndroidTest`: api31 151 tests/3 skipped/0 failed, api37 151 tests/6 skipped/0 failed — `AppDatabaseMigrationTest`'s existing 1→2 snapshot-path assertion is included in that count and stayed green untouched. One RED round on the way: the new `HabitListRowMenuComposeTest`'s ellipsis scenario first failed on real api31 hardware for a reason worth recording — see the apply-progress note on `TextLayoutResult.hasVisualOverflow`.

## Phase 3: Atomic Removal + Room v2→v3 Migration (PR 3)

Satisfies `habit-management` → Habit Creation (2 scenarios, regression-only) and `data-portability` →
Child Records Survive A Schema Migration (2 scenarios). Least reversible slice — lands last.

- [ ] 3.1 RED: extend `app/src/androidTest/kotlin/com/jjrapps/constanza/core/data/AppDatabaseMigrationTest.kt` with a table-driven 2→3 case (one seed helper + four child-table inserts, exit 2) — two habits, each with a schedule, reminder slot, entry, and reminder occurrence. Assert `question` gone from `PRAGMA table_info(habits)`, `pre-migration-v2.sql` written, and all four child rows survive per habit, unmixed. Keep the 1→2 case and the v1 seed SQL at line 114 as-is.
- [ ] 3.2 `domain/src/main/kotlin/com/jjrapps/constanza/domain/model/Model.kt`: drop `Habit.question`.
- [ ] 3.3 `core/data/entity/Entities.kt`: drop `HabitEntity.question`.
- [ ] 3.4 `core/data/mapper/Mappers.kt`: drop both `question = question` lines.
- [ ] 3.5 `core/data/migration/AppMigrations.kt`: add `migration2To3(writer)` — `CREATE TABLE _new_habits` (CREATE string copied verbatim from generated `app/build/generated/ksp/.../AppDatabase_Impl.kt` (read-only), name substituted) → `INSERT…SELECT` → `DROP TABLE habits` → `RENAME`; `writer.write(db)` runs first, its `Boolean` discarded. Add the child-row count guard: query all four child tables before the DROP and again after the RENAME, throw on mismatch (design D2). Re-point the rollback KDoc from `Migration(2,3)` to `Migration(3,4)` (design D7).
- [ ] 3.6 `core/data/AppDatabase.kt`: `version = 3`; KDoc gains the v3 rebuild note.
- [ ] 3.7 `core/di/DatabaseModule.kt:36`: `.addMigrations(migration1To2(writer), migration2To3(writer))`, one shared `PreMigrationSnapshotWriter`.
- [ ] 3.8 `portability/BackupDto.kt`, `BackupMapper.kt`: drop `BackupHabit.question` and both mapping lines.
- [ ] 3.9 `habit/HabitEditorScreen.kt`, `HabitEditorFormState.kt`, `HabitEditorViewModel.kt`: delete the question `OutlinedTextField`, `FIELD_QUESTION`, `onQuestionChange`, and both `question = ...` mapping lines.
- [ ] 3.10 `res/values/strings.xml`, `res/values-es/strings.xml`: remove `habit_editor_question_label` from both.
- [ ] 3.11 `habit/HabitRepositoryCrudTest.kt`: delete lines 63/69 (`question` clauses); add a `colorArgb` (non-null INTEGER) assertion to `updatingAHabitChangesItsStoredFields` (design D6) — `notes` at line 70 already carries the "field actually changed" proof.
- [ ] 3.12 DELETE `HabitEditorViewModelTest.kt:434-443` (`onQuestionChange`-only test); repoint lines 101,107,118,144,606 to drop the `onQuestionChange` call and every `.question`/`question = ...` line.
- [ ] 3.13 Mechanical `question` fixture drops across the remaining unit/instrumented test and seed files named in `exploration.md`'s test blast radius (`ModelTest`, `MappersTest`, `HabitListViewModelTest`, `ProgressViewModelTest`, `OccurrencePlannerTest`, `TodayViewModelTest`, `BackupImporterTest`, `BackupImporterNormalizationTest`, `HabitRepositoryDeleteSlotTest`, `HabitListArchiveComposeTest:47`, `HabitRepositoryTestFixture`, `EntryDaoUniqueConstraintTest`, `HabitColorDotComposeTest`, `ReconcileWorkerTest`, `MidnightSweepWorkerTest`, `PortabilityTestFixture`, `LiveSnoozeAcrossMidnightSeed`, `ImminentReminderSeed`).
- [ ] 3.14 GREEN: run `./gradlew check`, `:app:compileDebugAndroidTestKotlin`, and the full instrumented matrix (Unit 3); confirm `AppMigrations.kt`'s KDoc names `Migration(3,4)` and `3.json` is committed.

## Phase 4: Cleanup / Final Verification

- [ ] 4.1 Confirm no `question` identifier remains in `domain/`, `app/src/main`, `app/src/test`, or `app/src/androidTest`, except the v1 seed SQL at `AppDatabaseMigrationTest.kt:114`.
- [ ] 4.2 Run `:app:detektMain` (not the full `:app:detekt`, already red on unmodified `main` `TodayViewModelTest.kt`, unrelated to this change).
- [ ] 4.3 Manual, non-matrix step: notification template and list-row rendering on a real device under One UI (`testing.instrumented.device_free_matrix.limits`).
