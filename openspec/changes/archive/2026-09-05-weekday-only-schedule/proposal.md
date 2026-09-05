# Proposal: Day-set schedules (`DaysOfWeek`), replacing `Weekly`

## Intent

A habit cannot be scheduled for working days only. The nearest fit is `Daily`, which fires on
Saturday and Sunday too, so a weekday habit **records weekend misses it never earned** — corrupting
streak and compliance figures, not merely annoying the user. Requested by the maintainer 2026-09-05.

Add one general kind, `Schedule.DaysOfWeek(Set<DayOfWeek>)`, which expresses Mon–Fri, weekends-only,
Mon/Wed/Fri and "every Monday" through one mechanism instead of a new kind per combination.

## Scope

### In Scope

- `Schedule.DaysOfWeek(days: Set<DayOfWeek>)` with a non-empty-set invariant; `Weekly` **removed**
  (settled: subsumption — `Weekly(d)` becomes `DaysOfWeek(setOf(d))`).
- `dueOn()` branch: `Due.Required` iff `date.dayOfWeek in days`, else `Due.NotDue`.
- Room v3→v4: additive `ALTER TABLE schedules ADD COLUMN` + data-only `UPDATE` rewriting `WEEKLY`
  rows; `PreMigrationSnapshotWriter` first; committed `4.json`.
- Multi-select day chips in the editor; one new `BackupSchedule` field; EN/ES strings.
- `habit-scheduling` spec delta.

### Out of Scope

- Public holidays (settled).
- Locale-derived workweek — `weekStart` is evidenced as `NTimesPerWeek`-only, never a
  weekday/weekend classifier. Separate, unscoped feature.
- Backward compatibility for previously exported backup files.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `habit-scheduling`: **Six Frequency Kinds** — `WEEKLY` leaves the enumerated list, `DAYS_OF_WEEK`
  enters it (count stays six); the "saved with no reminder time" scenario's GIVEN also names `WEEKLY`.
- `data-portability`: **None.** Automatic Pre-Migration Snapshot is satisfied by construction, as
  `migration1To2` already satisfies it. Do not write a delta weakening it.

## Product decisions

| Question | Decision | Why |
|---|---|---|
| What the picker offers | One entry replaces "Weekly": `schedule_kind_days_of_week` — EN **"Specific days of the week"**, ES **"Días concretos de la semana"**. `habit_editor_day_of_week_label` becomes plural `habit_editor_days_of_week_label` — EN "Days of week", ES "Días de la semana". | Two entries for one concept is worse UX, not more choice. No live regression: the app is unpublished. |
| Empty set | **The model forbids it** (`require(days.isNotEmpty())`), so the editor cannot construct it: the last selected chip refuses to deselect, with no error text. Deliberately unlike `SaveValidationError.SlotsEmpty`. | "Due never" is nonsense no other kind can express. Slots are legitimately empty mid-edit on a new habit; a day set never is. Unrepresentable beats validated. |
| A "weekdays" preset | **No separate control.** `defaultScheduleFor(DAYS_OF_WEEK)` defaults to **Mon–Fri**, so the requested habit costs zero extra taps; other combinations cost chip taps. | Answers the actual request without new UI. **Maintainer's call** — a preset row is cheap to add later but needs a real render to judge (project convention: never offer a layout choice unrendered). |

## Approach

Domain first: new sealed member, `dueOn` branch, `ScheduleKind.DAYS_OF_WEEK` + `Schedule.kind`.
Then storage: new column, `Mappers.kt` both directions, `Migration(3,4)`. Then the editor:
`DayOfWeekPicker` becomes multi-select by toggling set membership (`isSelected = day in days`) —
already a `FlowRow` of `FilterChip`s, so no new control — and `applyDayOfWeek` becomes a toggle
action. Then backup field and strings.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `domain/.../model/Model.kt` | Modified | `Weekly` → `DaysOfWeek(Set<DayOfWeek>)` + invariant |
| `domain/.../DueOn.kt:16` | Modified | Set-membership branch |
| `core/data/entity/Entities.kt:37` | Modified | New nullable set column |
| `core/data/mapper/Mappers.kt:53,78` | Modified | Both directions + kind constant |
| `core/data/AppMigrations.kt`, `AppDatabase.kt`, `DatabaseModule.kt`, `app/schemas/4.json` | New/Modified | v3→v4 |
| `habit/HabitEditorViewModel.kt:217,249,278,288` | Modified | Enum member, default set, toggle action |
| `habit/ScheduleEditors.kt:60,144,232` | Modified | Multi-select picker, label map |
| `habit/HabitEditorFormState.kt` | Modified | Non-empty-set enforcement |
| `portability/BackupDto.kt:67`, `BackupMapper.kt:47` | Modified | One new field |
| `res/values{,-es}/strings.xml` | Modified | Key-set parity enforced by test |
| `openspec/specs/habit-scheduling/spec.md:9-11,41` | Modified | Delta |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `ScheduleEntity.toDomain()`'s `else -> error(...)` is **not** compiler-enforced — a missed edit crashes at database read, not at build | High | Named as a first-class task; migration test asserts a real `WEEKLY`-row round trip |
| Any un-rewritten `kind='WEEKLY'` row hits that same `error(...)` after subsumption | Med | The `UPDATE` and its migration-test assertion are one work unit |
| Exceeds the 400-line review budget (exploration sized (b) at 350–500+; subsumption adds) | High | Slice boundaries below; `sdd-tasks` owns the final chain |
| Multi-select chip row's real-device appearance | Med | One UI stays outside the automated matrix — manual check |
| Multi-select rewrite drops `LocalConfiguration` locale reading | Low | Its KDoc states why `LocalLocale` is wrong; preserve verbatim |

## Sizing and slice boundaries (recommendation, not a decision)

`400-line budget risk: High`. Recommended two slices, split where the seam is natural — the storage
contract:

1. **Domain + storage + migration**: `Model.kt`, `DueOn.kt`, `Entities.kt`, `Mappers.kt`,
   `Migration(3,4)`, `4.json`, `ModelTest`, `MappersTest`, `dueOn` tests, `AppDatabaseMigrationTest`.
   Autonomous: compiles and migrates with no UI change.
2. **Editor + backup + strings + spec delta**: `ScheduleEditors.kt`, `HabitEditorViewModel.kt`,
   `HabitEditorFormState.kt`, `BackupDto`/`BackupMapper`, both `strings.xml`, spec delta and their
   tests.

Slice 1 cannot ship the feature alone (no way to pick the kind), so this is a **stacked** chain, not
two independent PRs. `sdd-tasks` decides the final chain under the review guard.

## Rollback Plan

Required by `rules.proposal` (this touches scheduling **and** persisted data).

- **Code**: revert the slice commits; the six exhaustive `when` sites make an incomplete revert a
  compile error, not a silent one.
- **Data**: Room has **no automatic downgrade**. Reverting the app onto a device already migrated to
  v4 means `version = 3` meets a v4 file and throws at open — a crash loop, not a wipe. Recovery is
  uninstall/reinstall plus importing the snapshot `PreMigrationSnapshotWriter` wrote before the
  migration ran. Acceptable: the app is unpublished and on-device data is expendable.
- **Spec**: the `habit-scheduling` delta is unarchived with the change.

## Treated as still unratified

- The set's **column encoding** (bitmask `INTEGER` vs. a delimited `TEXT`) — `sdd-design`'s call.
- Whether `BackupSchedule.dayOfWeek` is deleted or kept as a tolerated legacy input — `sdd-design`.
- `dueOn`'s `NTimesPerWeek` branch remains self-documented as "Provisional (OA-3, unconfirmed)"; this
  change does not touch or ratify it.
- Settled constraint 2 (subsumption) is the **orchestrator's** call, not the maintainer's, and is
  recorded in `exploration.md` with its full reasoning so it can be revisited.

## Dependencies

None external. Requires the exploration's settled constraints 1–4.

## Success Criteria

- [ ] A habit saved with Mon–Fri is due on weekdays and `Due.NotDue` on Saturday and Sunday.
- [ ] The kind picker shows six entries, one of them the day-set entry, in EN and ES.
- [ ] The last selected day chip cannot be deselected; no empty-set habit is representable.
- [ ] A v3 database with a `WEEKLY` row migrates to v4 and reads back as a one-element day set, with
      the pre-migration snapshot written first.
- [ ] Export/import round-trips a multi-day habit.
- [ ] `habit-scheduling` still enumerates exactly six kinds, `WEEKLY` no longer among them.
- [ ] `./gradlew :app:testDebugUnitTest :domain:test detektMain compileDebugAndroidTestKotlin` and
      `:app:emulatorMatrixGroupDebugAndroidTest` green.
