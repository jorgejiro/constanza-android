# Tasks: Day-set schedules (`DaysOfWeek`), replacing `Weekly`

## Review Workload Forecast

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High (unsplit ≈530–610; per-unit <400)

Suggested split: PR1 (WU1, ~350–380 measured) → PR2 (WU2, ~240–280), stacked-to-main.

**Slice-boundary correction (post-WU1-apply)**: the original split cut at the storage/domain
architectural layer, leaving `HabitEditorViewModel.kt`/`ScheduleEditors.kt` referencing the
removed `Schedule.Weekly` — WU1 alone did not compile. This repeats a lesson this repo already
recorded (`remove-habit-question-field`: "Kotlin field removal is atomically compile-bound, so
slice boundaries must follow compilability rather than architectural layers"). Corrected: the
mechanical, behavior-preserving call-site repoint in those two files (plus their tests) moved from
WU2 into WU1 as task 2.4, so every PR in the stack compiles and ships on its own.
`ScheduleKind.WEEKLY` and its user-visible strings stay unchanged through WU1 — the editor still
offers a single day under the current label, backed by the new type; WU2 still owns turning that
into real multi-select.

### Work Units

- WU1 (PR1): domain+storage+migration+mechanical editor repoint; compiles and ships standalone, no user-visible change. Test: `:domain:test :app:testDebugUnitTest`. Harness: `:app:emulatorMatrixGroupDebugAndroidTest` (API31+37). Rollback: revert WU1; v3 schema unaffected, `DaysOfWeek` unresolved forces compile error.
- WU2 (PR2, stacked on WU1): editor multi-select+backup+strings ships the feature. Test: `:app:testDebugUnitTest`. Harness: same (full suite). Rollback: revert WU2 only; v4 schema stays, harmless.

Verify (both units): `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest :domain:test :app:detektMain :app:compileDebugAndroidTestKotlin` — counts from JUnit XML, never Gradle's summary. `:app:detektMain` only; full `:app:detekt` already red on `main`, out of scope.

## Work Unit 1 — Domain + Storage + Migration (PR 1)

### Phase 1: Domain (`:domain`, strict TDD — test first)
- [x] 1.1 RED (`:domain`, test-first): `ModelTest.kt:34-45` six-kinds case uses `DaysOfWeek(setOf(MONDAY))`, empty-set throws; `DueOnTest.kt:35` five/one-day sets + non-member day→`NotDue`, non-contiguous Mon/Wed/Fri. GREEN: `Model.kt` `Weekly(dayOfWeek)`→`DaysOfWeek(days:Set<DayOfWeek>)`,`require(days.isNotEmpty())`,KDoc literal Mon–Fri; `DueOn.kt:16` set-membership `date.dayOfWeek in days`. [R1;R3/weekdays,not-due,single-day,noncontiguous,empty-set]

### Phase 2: Storage (`:app`)
- [x] 2.1 `Entities.kt` add nullable `daysOfWeekMask:Int?` (`dayOfWeek` stays declared, unused); `MappersTest.kt:63-66` round-trip one-day/five-day mask, kind `DAYS_OF_WEEK`; implement in `Mappers.kt:53-78` — kind constant, `toMask()`/`toDaySet()`, both directions.
- [x] 2.2 REPOINT (mechanical constructor swap): `StreakCalculatorTest.kt:72`, `DayRollupTest.kt:96`, `TodayViewModelTest.kt:229-232`, `HabitRepositoryCrudTest.kt:44-86`, `LanguageOverrideComposeTest.kt:136`, `DatabaseStateReport.kt:212`.
- [x] 2.3 REWRITE `OccurrencePlannerTest.kt:68-69,133`: `weekly(dayOfWeek)` fixture → `daysOfWeek(mask)`.
- [x] 2.4 MECHANICAL, moved from WU2 (slice boundary follows compilability, not architectural layers — see forecast note above): `HabitEditorViewModel.kt:221,250-251,292` and `ScheduleEditors.kt:76-77` repoint `Schedule.Weekly(day)`→`Schedule.DaysOfWeek(setOf(day))` at those exact sites only; `HabitEditorViewModelTest.kt` same repoint. `ScheduleKind.WEEKLY`, its label, and single-day behavior stay exactly as they are — no multi-select, no new strings, no backup change. This is what makes `:app:compileDebugKotlin` exit 0 for WU1 alone.

### Phase 3: Migration
- [x] 3.1 `AppMigrations.kt`: `migration3To4` — snapshot first, `ALTER TABLE schedules ADD COLUMN daysOfWeekMask INTEGER`, `UPDATE...kind='DAYS_OF_WEEK'`, `check(stragglers==0)`; KDoc restarts at v5. `AppDatabase.kt` `version=4`+KDoc; `DatabaseModule.kt` registers `migration3To4(writer)`; generate+commit `4.json`.
- [x] 3.2 `AppDatabaseMigrationTest.kt`: seed v3 `kind='WEEKLY',dayOfWeek=3`; migrate; assert `kind='DAYS_OF_WEEK'`, `daysOfWeekMask=4`, snapshot exists, `toDomain()`→`DaysOfWeek(setOf(WEDNESDAY))`. [R3; migration guard]
- [x] 3.3 `AppDatabaseMigrationTest.kt`: separate case — seed `kind='WEEKLY',dayOfWeek=NULL`; migrate; `check(stragglers==0)` throws, migration fails, no partial write. [migration guard]
- [x] 3.4 Run verify command + emulator matrix (API31+37). [R1;pre-existing]

## Work Unit 2 — Editor + Backup + Strings (PR 2, stacked on PR 1)

### Phase 4: Editor
Note: WU1's task 2.4 already repointed the mechanical call sites onto `Schedule.DaysOfWeek` as a
single-day set (`ScheduleKind.WEEKLY` name and label unchanged). WU2 converts that single-day
plumbing into the real multi-select feature.
- [ ] 4.1 `HabitEditorViewModel.kt`: `:278-285` rename `ScheduleKind.WEEKLY`→`DAYS_OF_WEEK`; `:217` `defaultScheduleFor`→`DEFAULT_DAYS_OF_WEEK`(Mon–Fri); `:288` `Schedule.kind` branch; `applyDayOfWeek`(WU1's task 2.4 repoint) → `applyDayOfWeekToggle(state,day)` — no-op when `days==setOf(day)`.
- [ ] 4.2 `HabitEditorViewModelTest.kt` (10 sites, already on `Schedule.DaysOfWeek` since WU1's 2.4): default Mon–Fri; toggle asserts set **contents**; last-chip removal unchanged — via 4.1. [R2/no-reminder;R3]
- [ ] 4.3 `ScheduleEditors.kt:60` `when` branch (already on `Schedule.DaysOfWeek` since WU1's 2.4); `:144` `labelRes`→`schedule_kind_days_of_week`; `DayOfWeekPicker(selectedDays:Set<DayOfWeek>,onToggleDay)` — keep `LocalConfiguration.current.locales[0]` KDoc verbatim. `HabitScheduleKindComposeTest.kt:105-109` renamed `weeklyKindIsPersisted`→`daysOfWeekKindIsPersisted`.

### Phase 5: Backup
- [ ] 5.1 `BackupDto.kt:67` `-dayOfWeek:String?`→`+daysOfWeek:List<String>?`; `BackupMapper.kt:47-66` both directions; `BackupImporter.kt` adds `ImportFailure.UnsupportedScheduleKind(habitId,kind)`, `validateHabit` rejects `kind` outside six constants (no DB I/O); `DataPortabilityScreen.kt` maps to `portability_import_error_*`, both locales.
- [ ] 5.2 `BackupImporterTest.kt`, `BackupImporterNormalizationTest.kt`: `kind='WEEKLY'` file rejected, writes nothing. `BackupRoundTripTest.kt:156`: multi-day habit round trips.

Exit if over budget: split the importer guard (≈60 lines) into stacked Work Unit 3.

### Phase 6: Strings + Verification
- [ ] 6.1 Both `strings.xml`: add `schedule_kind_days_of_week`,`habit_editor_days_of_week_label`,`portability_import_error_*`; drop `schedule_kind_weekly`,`habit_editor_day_of_week_label` — `StringResourceParityTest` enforces parity.
- [ ] 6.2 Run verify command + emulator matrix (API31+37) — proves add-habit, chip toggle, reminder flow via `CoreFlowE2ETest`. [R2;pre-existing]

## Phase 7: Manual (device-free matrix cannot prove this)
- [ ] 7.1 On a real One UI device, render the multi-select chip row; confirm layout/selection.
