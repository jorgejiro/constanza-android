# Exploration: A habit scheduled for specific days of the week

Origin: carried-forward open item `no-weekday-only-schedule-kind` in `openspec/config.yaml`
(starts line 1923). The maintainer's framing: "básicamente es como un hábito diario pero solo los
días laborables".

Artifact store is `hybrid`. The Engram half is `sdd/weekday-only-schedule/explore`. This file is the
filesystem half, written by the orchestrator because the `sdd-explore` agent ran with no write tool.
It reproduces that Engram observation and folds in the four maintainer decisions received after it
was written. Nothing here is invented; where the record is silent, this file says so.

## Legend

- **SETTLED** — decided by the maintainer (or, where stated, by the orchestrator). `sdd-propose`,
  `sdd-spec` and `sdd-design` take these as premises and MUST NOT re-open them.
- **SUPERSEDED** — this exploration's own earlier recommendation, kept for the record because the
  reasoning that replaced it only makes sense against it.

Nothing in this document is open. The `design_fork` the originating item posed — narrow `Weekdays`
kind versus general day-set kind — and its sub-decision — subsume `Weekly` or keep both — are both
closed below as settled constraints 1 and 2.

## Current state

`Schedule` (`domain/src/main/kotlin/com/jjrapps/constanza/domain/model/Model.kt:23-54`) is a sealed
interface with six kinds — `Daily`, `TimesPerDay`, `NTimesPerWeek(times)`, `Weekly(dayOfWeek)`,
`Monthly(dayOfMonth)`, `EveryNDays(n, anchor)` — each carrying `weekStart: DayOfWeek`
(default `MONDAY`).

`ScheduleEntity` (`app/src/main/kotlin/com/jjrapps/constanza/core/data/entity/Entities.kt:37-46`)
holds one nullable `dayOfWeek: Int?`; `kind` is a bare `String` column with **no CHECK constraint**
(verified against `app/schemas/com.jjrapps.constanza.core.data.AppDatabase/3.json`).

Room is at `version = 3` (`AppDatabase.kt:43`), never calls `fallbackToDestructiveMigration()`
(`AppDatabase.kt:18`), and every migration so far is a real hand-written `Migration` object whose
**first** statement is `PreMigrationSnapshotWriter.write(db)` (`AppMigrations.kt`,
`PreMigrationSnapshotWriter.kt`).

The kind's UI-facing discriminator is a separate `ScheduleKind` enum
(`app/src/main/kotlin/com/jjrapps/constanza/habit/HabitEditorViewModel.kt:278-285`) with a
matching `Schedule.kind` extension property (`:287-295`).

## Settled constraint 1 — a general `DaysOfWeek(Set<DayOfWeek>)` kind, not a narrow `Weekdays`

**SETTLED** by the maintainer: option **(b)**.

The maintainer chose the general mechanism so that future day combinations (Mon/Wed/Fri,
weekends-only) cost nothing later, rather than each arriving as a new parameterless kind and a new
`ScheduleKind` member.

**SUPERSEDED**: this exploration recommended option (a), the narrow parameterless `Weekdays` kind,
purely on cost grounds — it needed zero Room migration, zero backup-format change and zero new
picker UI, and answered exactly what was asked inside one small PR. That recommendation explicitly
deferred to the maintainer on whether more day-combination requests were expected soon. They are, so
(b) wins: paying one additive migration now beats paying two (once for `Weekdays`, again for
`DaysOfWeek` when the next combination lands).

## Settled constraint 2 — `DaysOfWeek` SUBSUMES `Weekly`; `Weekly` is removed

**SETTLED**, and this one is the **orchestrator's call**, not the maintainer's — recorded here in
full because the maintainer may want to revisit it.

`Weekly(dayOfWeek)` becomes `DaysOfWeek(setOf(day))`, a one-element set. `Weekly` is **removed from
the sealed hierarchy**, not kept alongside it.

Reasoning:

- Coexistence leaves two ways to express "every Monday" — a modelling smell — and forces the editor
  to offer both a "Weekly" entry and a "specific days" entry. That is worse UX, not more choice.
- The kind count stays at **six** (one kind replaced, not added), so the ratified `habit-scheduling`
  **Six Frequency Kinds** requirement keeps its shape; only the enumerated name changes.
- Subsumption's one real cost is migrating existing persisted `Weekly` rows, and that cost is
  **free right now**: the app has never been published, there are no real users, and on-device data
  is expendable.

### The data migration this adds — sized here, because the Engram exploration left it unsized

The Engram half deliberately left the subsume-or-coexist sub-decision open and therefore never sized
its migration. It is small:

- A **data-only `UPDATE`** inside the same `Migration(3, 4)`: rewrite every row with `kind = 'WEEKLY'`
  to the new kind string and translate its single `dayOfWeek: Int?` into the new set column's
  encoding.
- It runs in the same migration as the additive `ALTER TABLE ... ADD COLUMN`, after that column
  exists.
- Like every migration in this repo, it MUST carry `PreMigrationSnapshotWriter.write(db)` as its
  first statement — structurally identical to `migration1To2`, which is itself a data-only `UPDATE`.
- `AppDatabaseMigrationTest`'s new 3→4 case MUST seed a `WEEKLY` row and assert it reads back as the
  new kind carrying the equivalent one-element set.

## Settled constraint 3 — "weekday" is literally Monday to Friday

**SETTLED** by the maintainer, and **evidenced**, not assumed.

Every reader of `weekStart` was grepped. It is used **exclusively** for `NTimesPerWeek` week-boundary
arithmetic: `WeekBoundary.startOfWeek()`, `StreakCalculator.weeklyStreaks`,
`ComplianceCalculator.weeklyRatio`, `ReminderFireWorker.currentWeekProgress`. It **never** classifies
a day as weekday versus weekend.

So `weekStart` and "is this day a working day" are orthogonal concepts in this codebase today. A
literal Mon–Fri reading does not contradict the model's locale-awareness, because that
locale-awareness is about **where a week starts**, not about which days are working days. The new
kind stays deliberately non-locale-derived and its KDoc MUST say so, exactly as `open_questions`
item 1 in `config.yaml` anticipated.

A locale-aware workweek (e.g. Friday–Saturday weekends in some locales) is a **separate, larger,
unscoped feature**. It MUST NOT be silently folded into this change.

## Settled constraint 4 — public holidays are out of scope

**SETTLED** by the maintainer. Not investigated further.

## Affected areas — the exhaustive `when (Schedule)` sites

Six sites are real `when` expressions over the sealed hierarchy with no `else`, so they **fail to
compile** the moment the hierarchy changes:

1. `domain/src/main/kotlin/com/jjrapps/constanza/domain/DueOn.kt:16` — the single due-authority.
   `OccurrencePlanner.kt` never matches on `Schedule` itself; it only calls `dueOn()`.
2. `app/src/main/kotlin/com/jjrapps/constanza/core/data/mapper/Mappers.kt:78` — `Schedule.toEntity()`.
3. `app/src/main/kotlin/com/jjrapps/constanza/habit/HabitEditorViewModel.kt:288` — the
   `Schedule.kind` property.
4. `app/src/main/kotlin/com/jjrapps/constanza/habit/HabitEditorViewModel.kt:217` —
   `defaultScheduleFor(kind, ...)`, exhaustive over the `ScheduleKind` enum (`:278-285`), which also
   needs its member renamed.
5. `app/src/main/kotlin/com/jjrapps/constanza/habit/ScheduleEditors.kt:60` — `ScheduleSection`'s
   `when (val schedule = state.schedule)`.
6. `app/src/main/kotlin/com/jjrapps/constanza/habit/ScheduleEditors.kt:144` — `ScheduleKind.labelRes`,
   exhaustive over `ScheduleKind`.

Plus `applyDayOfWeek` (`HabitEditorViewModel.kt:249-252`), which casts `state.schedule as?
Schedule.Weekly` — a *safe* cast, so it silently becomes a no-op rather than failing to compile once
`Weekly` is gone. It must be rewritten to a set-toggle against the new kind.

### The dangerous site is NOT compiler-enforced

`ScheduleEntity.toDomain()` (`Mappers.kt:53-76`) branches on the **`kind` string**, not on the sealed
type, and ends in `else -> error("Unknown schedule kind persisted: $kind")`. A forgotten update there
is a **runtime crash on database read**, not a build failure. Nothing forces this edit — it is a
deliberate, manual one, and it is a first-class risk of this change.

`StreakCalculator.kt` and `ComplianceCalculator.kt` only branch with `if (schedule is
Schedule.NTimesPerWeek)`, never an exhaustive `when` over `Schedule` — unaffected, since the new kind
is not week-quota-based.

## Storage and migration — additive, and this corrects the originating item's own `storage_note`

The item states "`ScheduleEntity.dayOfWeek` cannot hold a set... a Room migration — v3 to v4". True
for the chosen option (b). (It was **not** true for option (a): `kind` is unconstrained `TEXT`, so a
parameterless kind needed no migration at all. Moot now.)

The new set needs one new column, e.g. a bitmask `INTEGER?`. That is
`ALTER TABLE schedules ADD COLUMN ...` — Room's **ordinary additive** migration, structurally like
the simple `migration1To2`, **not** the CREATE/INSERT/DROP/RENAME table rebuild that
`remove-habit-question-field`'s `Migration(2,3)` needed (SQLite on this minSdk cannot drop a column
in place). No table rebuild, **no FK cascade risk**, no child-row-count guard.

Room goes **v3 → v4** with a committed
`app/schemas/com.jjrapps.constanza.core.data.AppDatabase/4.json` (`exportSchema = true`;
`AppDatabase.kt`'s KDoc states every version is committed).

**The destructive-fallback framing does not arise here.** Neither option ever needed
`fallbackToDestructiveMigration()`. The `data-portability` spec's **Automatic Pre-Migration
Snapshot** requirement (`openspec/specs/data-portability/spec.md:113-132`) is satisfied **by
construction**, the same way `migration1To2` already satisfies it, and needs **NO delta**. Do not
write a delta that weakens it. `remove-habit-question-field`'s destructive-fallback rejection
reasoning stands unchanged and is not being revisited — it simply is not the applicable case, because
dropping a column and adding one are different operations.

The "user data is expendable" fact is true, but it is load-bearing only for settled constraint 2's
`Weekly`-row migration, not for the schema shape.

## Backup format impact

`BackupSchedule` (`app/src/main/kotlin/com/jjrapps/constanza/portability/BackupDto.kt:67-75`) already
carries `dayOfWeek: String?` for `Weekly`, following the same optional-field-per-kind shape every
other parameter uses. This change needs **one new nullable field**, e.g. `daysOfWeek: List<String>?`
(day names, mirroring the existing single-value `dayOfWeek: String?` convention), plus the
corresponding encode/decode lines in `BackupMapper.kt:47-66`.

`Round-Trip Fidelity` only guarantees round-tripping "a file exported by the current app version"
(`data-portability/spec.md:136-138`), so there is no back-compat burden for older exported files.
Whether the removed `dayOfWeek` field is dropped or kept as a tolerated legacy input is a
design-phase call.

## The schedule editor UI — this corrects the item's own "wall of checkboxes" worry

`ScheduleEditors.kt:232-250`'s `DayOfWeekPicker` is **already** a `FlowRow` of `FilterChip`s, one per
`DayOfWeek.entries`, single-select (`onClick` replaces the selected day). It is not a dropdown and
not a checkbox list.

Multi-select is a small, well-understood change to that same control: toggle set membership per chip
(`isSelected = day in selectedDays`) instead of replacing a single value. This directly refutes the
item's worry that a day-set picker "must present a set without becoming a wall of checkboxes" — the
existing idiom already **is** the right shape for a day-set picker.

The picker also reads its locale from `LocalConfiguration.current.locales[0]`, deliberately not
`LocalLocale` — see its own KDoc (`ScheduleEditors.kt:215-231`) and app-localization's Finding B.
That choice MUST survive the multi-select rewrite.

Empty-set handling needs a new validation error alongside `SaveValidationError.SlotsEmpty`
(`HabitEditorFormState.kt:59-82`), which is the exact existing precedent: a sealed
`SaveValidationError` object with its own message resource, returned from `validationError(state)`.

## Spec impact

`openspec/specs/habit-scheduling/spec.md:9-11`'s **Six Frequency Kinds** requirement literally
enumerates `DAILY, TIMES_PER_DAY, N_TIMES_PER_WEEK, WEEKLY, MONTHLY, EVERY_N_DAYS`. It needs a
`MODIFIED Requirements` delta: `WEEKLY` leaves the list and the new kind enters it.

`spec.md:41`'s scenario "A habit saved with no reminder time is accepted and stays trackable" also
names `WEEKLY` in its GIVEN, so the delta must carry that scenario too.

No `data-portability` delta (see Storage and migration).

## Test blast radius, named by file

- `domain/src/test/kotlin/com/jjrapps/constanza/domain/model/ModelTest.kt:34-45` — the test
  `Schedule has exactly the six ratified kinds and each carries weekStart` builds one instance per
  kind and hardcodes `assertEquals(6, schedules.size)`. The count stays 6 under subsumption; the
  `Schedule.Weekly(...)` line becomes the new kind.
- A new due-predicate test for `dueOn()`'s new branch. There is no dedicated `DueOnTest.kt` separate
  from `DueOnWeekQuotaTest.kt` (which is `NTimesPerWeek`-specific), so a new test method or file is
  the natural home. It must cover a five-day set, a one-day set (the subsumed `Weekly` case), and a
  non-member day.
- `app/src/test/kotlin/com/jjrapps/constanza/core/data/mapper/MappersTest.kt:63-66` — the round-trip
  table needs its `Weekly` case replaced.
- `app/src/test/kotlin/com/jjrapps/constanza/habit/HabitEditorViewModelTest.kt` — cases for
  `defaultScheduleFor`/`applyKindChange` with the renamed `ScheduleKind` member, and for the new
  set-toggle action replacing `applyDayOfWeek`.
- `app/src/androidTest/kotlin/com/jjrapps/constanza/habit/HabitScheduleKindComposeTest.kt` — its own
  KDoc mandates one test method per kind; the `WEEKLY` method is rewritten, not added to.
- `app/src/test/kotlin/com/jjrapps/constanza/portability/BackupImporterTest.kt`,
  `BackupImporterNormalizationTest.kt`, and
  `app/src/androidTest/kotlin/com/jjrapps/constanza/portability/BackupRoundTripTest.kt` — new-field
  cases.
- `app/src/androidTest/kotlin/com/jjrapps/constanza/core/data/AppDatabaseMigrationTest.kt` — a new
  3→4 case plus the generated `4.json`, following the existing `MigrationTestHelper` pattern
  (`:47-60` shows the shape). It must assert the new column, the snapshot firing, **and** the
  `WEEKLY` row's translation (settled constraint 2).
- `app/src/test/kotlin/com/jjrapps/constanza/scheduling/OccurrencePlannerTest.kt` and the androidTest
  fixtures that build `ScheduleEntity` by name (`ReconcileWorkerTest.kt`,
  `ReminderWorkerTestFixtures.kt`, `MidnightSweepWorkerTest.kt`) all use named constructor arguments,
  so an additive nullable column with a default does not force changes there — but a fixture/test
  case for the new kind's due-on behaviour is still warranted.
- `app/src/main/res/values/strings.xml:56-61` and `values-es/strings.xml:54-59` — the
  `schedule_kind_*` family. `StringResourceParityTest`
  (`app/src/test/kotlin/com/jjrapps/constanza/localization/StringResourceParityTest.kt`) enforces
  exact key-set symmetry between the two files, so every key change must land in both.
  `habit_editor_day_of_week_label` (EN "Day of week" / ES "Día de la semana") is now singular and
  needs a plural counterpart.

## Risks

- **`ScheduleEntity.toDomain()`'s `else -> error(...)` is not compiler-enforced.** It is the one
  site whose omission is a runtime crash on database read rather than a build failure. It needs a
  deliberate manual edit and a test that exercises the new kind through a real round trip.
- **Subsumption makes the legacy decode path load-bearing.** Once `Weekly` is gone from the sealed
  hierarchy, any `kind = 'WEEKLY'` row the migration failed to rewrite hits that same
  `error(...)` branch. The migration test's `WEEKLY`-row assertion is what actually catches this.
- **`habit-scheduling`'s Six Frequency Kinds requirement goes stale** the moment this ships, in both
  its enumerated list and one of its scenarios' GIVEN clauses.
- **The empty day-set is nonsense that the type system does not forbid.** `DaysOfWeek(emptySet())`
  would make a habit due never. Where it is rejected — domain validation, editor validation, or both
  — is a proposal/design decision, not an implementation detail.
- **Size.** This exploration estimated option (b) at roughly 350–500+ changed lines including tests
  and migration, against a 400-line review budget, and settled constraint 2 adds to that. Chained or
  stacked PR slices are likely required; `sdd-tasks` owns the final call under the 400-line guard.
- **The locale-aware-workweek door must stay shut.** The Mon–Fri reading is evidenced, but a future
  locale-aware workweek is a separate feature. If the new kind's KDoc does not say it is deliberately
  non-locale-derived, a future reader will assume it is a bug.
- **One UI rendering stays outside the automated matrix's proof**
  (`openspec/config.yaml`'s `testing.instrumented.device_free_matrix.limits`), so the multi-select
  chip row's real-device appearance remains a manual check.

## Ready for proposal

Yes. Nothing in this document is open.

Four settled constraints carry forward as premises:

1. a general `DaysOfWeek(Set<DayOfWeek>)` kind, not a narrow `Weekdays` kind
2. `DaysOfWeek` subsumes `Weekly`; `Weekly` is removed, one-element sets replace it
3. "weekday" is literally Monday to Friday, deliberately not locale-derived
4. public holidays are out of scope

Line numbers cited here were captured during exploration; treat the file plus symbol name as
authoritative and re-locate before editing.
