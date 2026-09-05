# Design: Day-set schedules (`DaysOfWeek`), replacing `Weekly`

Premises are the exploration's settled constraints 1–4 and the proposal's three product decisions.
None is reopened here. This document decides only the four items the proposal left to `sdd-design`.

## Technical Approach

`Schedule.Weekly(dayOfWeek)` is replaced by `Schedule.DaysOfWeek(days: Set<DayOfWeek>)` carrying
`require(days.isNotEmpty())`, so "due never" is unrepresentable rather than validated. `dueOn()`
becomes `date.dayOfWeek in days`. The set is persisted as a **7-bit mask in a new nullable INTEGER
column**, encoded and decoded only inside `Mappers.kt` so `:domain` never learns the encoding
(design.md §4). Room goes v3 → v4 via one additive `ALTER TABLE ADD COLUMN` plus a data-only
`UPDATE` that rewrites `kind='WEEKLY'` rows, with `PreMigrationSnapshotWriter.write(db)` first.
The editor's existing `FlowRow` of `FilterChip`s becomes multi-select; the non-empty invariant is
enforced in the ViewModel reducer, not the composable.

## Architecture Decisions

### Decision 1: the set's column encoding

| Option | Tradeoff | Verdict |
|---|---|---|
| **Bitmask `INTEGER?`** (`daysOfWeekMask`, bit `n` = `DayOfWeek.value - 1`; Mon–Fri = `31`) | Fixed width, no parsing, no delimiter escaping, no duplicate/ordering ambiguity, indexable. Not human-readable in a raw dump. | **CHOSEN** |
| Delimited `TEXT?` (`"MONDAY,TUESDAY"`) | Readable in a dump, but introduces a parse step that can fail at read — new failure mode on the exact path that already crashes loudly (`toDomain`). Admits duplicates and orderings that mean the same set. | Rejected |

**Rationale from precedent, not taste.** `Entities.kt` has no list-valued or delimited column
anywhere; every column is a scalar. Both existing day-of-week columns — `ScheduleEntity.weekStart:
Int` and `ScheduleEntity.dayOfWeek: Int?` — already store a `DayOfWeek` as its `value` integer. A
bitmask is that same idiom widened; delimited text would be the first of its kind in this schema.

**Fate of `dayOfWeek: Int?`: left dead, still declared on `ScheduleEntity`.** Not dropped, and not
reused.

- *Not dropped*: SQLite on `minSdk = 31` is 3.32.2, below `ALTER TABLE ... DROP COLUMN`'s 3.35
  floor, so dropping means the CREATE/INSERT/DROP/RENAME rebuild `migration2To3` documents. That is
  ~40 lines of DDL plus the child-row guard in a change already at High budget risk, and it buys one
  unused nullable column back. A later cleanup change can drop it if it is ever worth the rebuild.
- *Not reused*: overloading `dayOfWeek` as the mask changes the column's value domain silently
  (`1..7` → `1..127`). An unmigrated row would then be **misread as a valid set** — `dayOfWeek = 3`
  (Wednesday) reads as mask `3` = Mon+Tue — which is silent data corruption. With a separate
  column, an unmigrated row reads `NULL`, and `requireNotNull` throws. **Fail loud beats fail
  silent**, which is the whole point of Decision 3's guard.
- The column stays declared so Room's `identityHash` validation still matches the entity. It is
  never written after v4: `emptyScheduleEntity` continues to pass `dayOfWeek = null`.

No CASCADE trap arises: there is no table rebuild. `remove-habit-question-field`'s child-row guard
is therefore **not** carried, and that is a consequence of not dropping, not an oversight.

### Decision 2: `BackupSchedule.dayOfWeek` is DELETED, replaced by `daysOfWeek: List<String>?`

Day names, mirroring the existing single-value convention (`BackupMapper.kt:51,61`). Keeping it as
"tolerated legacy input" was rejected because **it would tolerate nothing.** Verified:
`BackupSchedule.kind` is a raw `String` passed straight through `BackupMapper.toEntity` into
`ScheduleEntity.kind` (`BackupMapper.kt:59`), and `BackupImporter.validateHabit` (`:108-117`)
validates *only* slot references — never `kind`. So a legacy file's `kind='WEEKLY'` row imports
successfully and then detonates at `ScheduleEntity.toDomain()`'s `else -> error(...)` on the next
read. Honouring `dayOfWeek` without also translating `kind` buys a crash; translating `kind` would
be an unpromised back-compat guarantee the proposal puts out of scope.

**So the importer must reject it loudly instead**: a new `ImportFailure.UnsupportedScheduleKind(
habitId, kind)` raised from `validateHabit` when `kind` is not one of the six current constants.
`parseAndValidate` does no database I/O, so rejection leaves data intact — exactly
`data-portability`'s *Malformed file leaves data intact*. Costs one sealed variant, one
`portability_import_error_*` string in both locales, and one `DataPortabilityScreen` mapping.
`formatVersion` is **not** bumped: the gate only rejects *newer* files, so a bump cannot help an
older one.

### Decision 3: the migration — `migration3To4`, additive plus a data rewrite

New factory in `AppMigrations` (**actual path**: `core/data/migration/AppMigrations.kt`, not
`core/data/` as the proposal states), registered in `DatabaseModule.provideAppDatabase`'s
`.addMigrations(...)`. Needs `private const val SCHEMA_VERSION_4 = 4` for the same detekt
`MagicNumber` reason `SCHEMA_VERSION_3` exists.

```kotlin
writer.write(db)                                     // first statement, result discarded
db.execSQL("ALTER TABLE `schedules` ADD COLUMN `daysOfWeekMask` INTEGER")
db.execSQL(
    "UPDATE schedules SET daysOfWeekMask = 1 << (dayOfWeek - 1), kind = 'DAYS_OF_WEEK' " +
        "WHERE kind = 'WEEKLY' AND dayOfWeek IS NOT NULL",
)
val stragglers = /* SELECT COUNT(*) FROM schedules WHERE kind = 'WEEKLY' */
check(stragglers == 0) { "migration3To4 left $stragglers un-rewritten WEEKLY rows" }
```

The `check` is the design's answer to the coupling the risk register names: because `dayOfWeek` is
nullable and `kind` has no CHECK constraint, a `WEEKLY` row with a NULL day is representable. Such a
row would survive the `UPDATE` and crash at the next read. Failing the migration instead leaves an
intact, rolled-back database and a written snapshot. **The guard lives in the migration, not only in
its test.**

`4.json` is committed (`exportSchema = true`). Its `identityHash` genuinely differs from `3.json`'s,
since a column was added — unlike `2.json` vs `1.json`.

**Doc conflict to fix in the same edit**: `AppMigrations`' KDoc currently reserves `Migration(3, 4)`
for a colour-remap rollback recipe. That slot is now consumed; the note must be rewritten to start
from version 5, exactly as it was rewritten when `migration2To3` consumed `(2,3)`.

### Decision 4: the multi-select picker and how refusal presents

`DayOfWeekPicker(selected: DayOfWeek, …)` becomes `DayOfWeekPicker(selectedDays: Set<DayOfWeek>, …)`
with `isSelected = day in selectedDays` and `onToggleDay(day)`. The `LocalConfiguration.current
.locales[0]` read and its whole KDoc survive verbatim — that KDoc is the record of why `LocalLocale`
is wrong.

**Refusal is silently inert, and it lives in the reducer.** The chip stays `enabled = true` and
stays visually selected; the tap is simply a no-op. `applyDayOfWeekToggle` returns `state`
unchanged when `days == setOf(day)`. Rejected: `enabled = false` on the last chip, which greys it
and reads as "unavailable" rather than "already the only one"; and an error message, which the
proposal's product decision explicitly rules out. Putting the refusal in the reducer rather than the
composable makes it a plain unit test with no Compose harness, and leaves the `require` in the model
as a second, independent line of defence (`copy()` runs `init`, so `copy(days = emptySet())` throws
too).

Editor default `Mon–Fri` lives in `HabitEditorViewModel.kt` as a private `DEFAULT_DAYS_OF_WEEK`, not
in `:domain`. Deliberate: a `WEEKDAYS` constant in the domain model would imply a workweek concept
and reopen the locale-aware-workweek door settled constraint 3 closes. The new kind's KDoc MUST
state it is literal Mon–Fri and deliberately not derived from `weekStart`.

## Data Flow

    editor chip tap ──→ ScheduleParamAction.ToggleDayOfWeek ──→ applyDayOfWeekToggle
                                                                      │ (refuses last removal)
                                                                      ▼
                                                          Schedule.DaysOfWeek(days)
                                                          require(days.isNotEmpty())
                             ┌────────────────────────────────────────┤
                             ▼                                        ▼
                 dueOn(): date.dayOfWeek in days          Schedule.toEntity(): days → mask
                                                          ScheduleEntity.toDomain(): mask → days
                                                                      │
                                          Room `schedules.daysOfWeekMask INTEGER?` (v4)

## The two sites without compiler enforcement — and a correction

**The briefing is wrong about the second one.** `HabitEditorViewModel.kt:250` reads
`state.schedule as? Schedule.Weekly ?: return state`. Deleting the nested `Weekly` class makes
`Schedule.Weekly` an **unresolved reference**, and `schedule.copy(dayOfWeek = …)` on the next line
fails too. That site is a hard compile error, not a silent no-op. A safe cast only goes silent under
a *rename* that keeps a compatible shape, which subsumption is not. Verified by reading the file,
not assumed.

The genuinely unenforced sites are these, and one of them is not in the briefing:

| Site | Failure mode | Test that catches it |
|---|---|---|
| `Mappers.kt:55-75` `ScheduleEntity.toDomain()` — `when (kind)` over strings, `else -> error()` | Runtime crash on database read | `MappersTest` round trip **plus** `AppDatabaseMigrationTest`'s 3→4 case reading a seeded real `WEEKLY` row back through the mapper |
| **`BackupMapper.kt:59` + `BackupImporter.validateHabit`** — an unvalidated `kind` string reaches `ScheduleEntity.kind` | Import succeeds, next read crashes | New `BackupImporterTest` case: a file with `kind='WEEKLY'` is rejected by `parseAndValidate` with `UnsupportedScheduleKind` and writes nothing |
| Raw-string fixtures, e.g. `OccurrencePlannerTest.kt:68-69` `ScheduleEntity(kind = "WEEKLY", …)` | Compiles; the row crashes on read | Already loud — those tests fail at `error(...)`. REWRITE, not a latent risk |

`applyDayOfWeekToggle`'s residual (small) risk is different from the one the briefing names: the
`as? … ?: return state` idiom makes a *wrong-kind* dispatch a no-op, so a test asserting only "did
not crash" passes. Mitigation: every toggle test asserts the resulting `DaysOfWeek.days` set
contents, never merely that state changed.

## File Changes

| File | Action | Description |
|---|---|---|
| `domain/…/model/Model.kt` | Modify | `Weekly` → `DaysOfWeek(days)`, `require`, non-locale KDoc |
| `domain/…/DueOn.kt` | Modify | Set-membership branch (line 19-20) |
| `app/…/core/data/entity/Entities.kt` | Modify | `+ val daysOfWeekMask: Int?`; `dayOfWeek` stays dead |
| `app/…/core/data/mapper/Mappers.kt` | Modify | Kind constant, both directions, mask encode/decode helpers |
| `app/…/core/data/migration/AppMigrations.kt` | Modify | `migration3To4` + KDoc rollback-slot fix |
| `app/…/core/data/AppDatabase.kt` | Modify | `version = 4` + KDoc paragraph |
| `app/…/core/di/DatabaseModule.kt` | Modify | Register `migration3To4(writer)` |
| `app/schemas/…/4.json` | Create | Generated, committed |
| `app/…/habit/HabitEditorViewModel.kt` | Modify | Enum member, `Schedule.kind`, `ToggleDayOfWeek` action, `DEFAULT_DAYS_OF_WEEK`, toggle reducer |
| `app/…/habit/ScheduleEditors.kt` | Modify | `when` branch, `labelRes`, multi-select `DayOfWeekPicker` |
| `app/…/portability/BackupDto.kt` | Modify | `-dayOfWeek`, `+daysOfWeek: List<String>?` |
| `app/…/portability/BackupMapper.kt` | Modify | Both directions |
| `app/…/portability/BackupImporter.kt` | Modify | `UnsupportedScheduleKind` + kind validation |
| `app/…/portability/DataPortabilityScreen.kt` | Modify | One failure→string mapping |
| `app/src/main/res/values{,-es}/strings.xml` | Modify | `schedule_kind_days_of_week`, `habit_editor_days_of_week_label`, one import-error string; drop `schedule_kind_weekly`, `habit_editor_day_of_week_label` |

`HabitEditorFormState.kt` needs **no** change — the invariant is in the model and the reducer, so no
`SaveValidationError` variant is added. This deviates from the exploration's expectation and matches
the proposal's ratified "unrepresentable beats validated".

## Interfaces / Contracts

```kotlin
// :domain — no encoding knowledge, no workweek concept.
/** Literal calendar days. Membership is NOT derived from [weekStart] or any locale setting. */
data class DaysOfWeek(
    val days: Set<DayOfWeek>,
    override val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) : Schedule {
    init { require(days.isNotEmpty()) { "A DaysOfWeek schedule must carry at least one day." } }
}

// :app, Mappers.kt only — bit n == DayOfWeek.value - 1.
private fun Set<DayOfWeek>.toMask(): Int = fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
private fun Int.toDaySet(): Set<DayOfWeek> =
    DayOfWeek.entries.filterTo(mutableSetOf()) { this and (1 shl (it.value - 1)) != 0 }
```

## Testing Strategy

| File | Action | What it must prove |
|---|---|---|
| `domain/…/model/ModelTest.kt:39` | REWRITE | Still exactly 6 kinds; **new** case: `DaysOfWeek(emptySet())` throws, and so does `copy(days = emptySet())` |
| `domain/…/DueOnTest.kt:35` | REWRITE | Five-day set, one-day set (subsumed `Weekly`), non-member day → `NotDue`. **This file exists** (the exploration claimed it did not) |
| `app/…/mapper/MappersTest.kt:64` | REWRITE | Round trip through the mask, incl. a one-day and a five-day set |
| `app/androidTest/…/AppDatabaseMigrationTest.kt` | REWRITE (add case) | Seed a real v3 `kind='WEEKLY', dayOfWeek=3` row → assert `kind='DAYS_OF_WEEK'`, `daysOfWeekMask=4`, snapshot `pre-migration-v3.sql` exists, and the row reads back through `toDomain()` as `DaysOfWeek(setOf(WEDNESDAY))` |
| `app/…/habit/HabitEditorViewModelTest.kt` (×10 sites) | REWRITE | Default is Mon–Fri; toggle adds/removes and **asserts set contents**; last-chip removal leaves the set unchanged |
| `app/androidTest/…/HabitScheduleKindComposeTest.kt:105-109` | REWRITE | `weeklyKindIsPersisted` → `daysOfWeekKindIsPersisted` (one method per kind, per its KDoc) |
| `app/…/scheduling/OccurrencePlannerTest.kt:68-69,133` | REWRITE | `weekly(dayOfWeek)` fixture → `daysOfWeek(mask)` with the new kind string |
| `app/…/portability/BackupImporterTest.kt`, `BackupImporterNormalizationTest.kt` | REWRITE | New field; a `kind='WEEKLY'` file is rejected and writes nothing |
| `app/androidTest/…/BackupRoundTripTest.kt:156` | REWRITE | Multi-day habit round trips |
| `domain/…/StreakCalculatorTest.kt:72` | REPOINT | Mechanical constructor swap |
| `domain/…/DayRollupTest.kt:96` | REPOINT | Mechanical |
| `app/…/tracking/TodayViewModelTest.kt:229-232` | REPOINT | Mechanical |
| `app/androidTest/…/HabitRepositoryCrudTest.kt:44,46,53,86` | REPOINT | Mechanical |
| `app/androidTest/…/LanguageOverrideComposeTest.kt:136` | REPOINT | Mechanical |
| `app/androidTest/…/seed/DatabaseStateReport.kt:212` | REPOINT | Print `daysOfWeekMask` alongside `dayOfWeek` |

Nothing is DELETED. `StringResourceParityTest` needs no edit — it is key-set driven and will fail on
its own if either locale is missed.

### Sites found by grep that the briefing did not name

`StreakCalculatorTest.kt:72`, `DayRollupTest.kt:96`, `TodayViewModelTest.kt:229-232`,
`OccurrencePlannerTest.kt:68-69,133`, `HabitRepositoryCrudTest.kt:44-86`,
`LanguageOverrideComposeTest.kt:136`, `DatabaseStateReport.kt:212`, `BackupRoundTripTest.kt:156`,
and the existing `DueOnTest.kt`. Confirmed clean: `StreakCalculator.kt`, `ComplianceCalculator.kt`,
`ReminderFireWorker.kt`, `WeekBoundary.kt` and `Theme.kt` touch only `weekStart`/`date.dayOfWeek`,
never `Schedule.Weekly`. `ReconcileWorkerTest.kt:86`, `MidnightSweepWorkerTest.kt:81` and
`ReminderWorkerTestFixtures.kt:32` pass `dayOfWeek = null` by name, so the additive nullable column
does not force an edit.

## Threat Matrix

N/A — no routing, shell command, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. The persisted-data risk is covered by Decision 3's in-migration guard
and the pre-migration snapshot, not by that matrix.

## Migration / Rollout

v3 → v4, as Decision 3. No feature flag, no phased rollout: the app is unpublished. Rollback is the
proposal's plan unchanged — Room has no automatic downgrade, so reverting the app onto a migrated
device is a crash loop recoverable only by reinstall plus snapshot import. Accepted.

## Sizing

Both slices fit the 400-line budget on this design; neither needs an exception.

| Slice | Content | Authored estimate |
|---|---|---|
| 1 (stacked base) | `Model.kt`, `DueOn.kt`, `Entities.kt`, `Mappers.kt`, `AppMigrations.kt`, `AppDatabase.kt`, `DatabaseModule.kt`, `4.json` (generated, excluded), and every domain/storage/migration test above | ≈ 260–300 |
| 2 (stacked child) | `ScheduleEditors.kt`, `HabitEditorViewModel.kt`, both `strings.xml`, `BackupDto`/`BackupMapper`/`BackupImporter`/`DataPortabilityScreen`, and their tests | ≈ 270–310 |

Slice 1 compiles and migrates with no UI change and **cannot ship the feature alone** — there is no
way to pick the kind — so this stays a stacked chain, not two independent PRs.

**Exit if slice 2 drifts over budget**, named rather than absorbed: split the importer guard
(`ImportFailure.UnsupportedScheduleKind`, its validation, its two strings, its screen mapping and
its tests, ≈ 60 lines) into a third stacked slice. Do not drop the guard to fit — it is the only
thing standing between a legacy backup file and a read crash.

## Open Questions

None blocking. Two notes for `sdd-tasks`:

- The `AppMigrations` KDoc rollback-slot correction is easy to forget; make it part of the
  `migration3To4` work unit, not a separate cosmetic task.
- The multi-select chip row's One UI appearance stays outside the device-free matrix's proof
  (`testing.instrumented.device_free_matrix.limits`) — a manual check, already accepted as a risk.
