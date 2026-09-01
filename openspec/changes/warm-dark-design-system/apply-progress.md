# Apply Progress: Warm-Dark Design System

## Unit 1 — Theme Foundation (PR A) — `feat/warm-dark-theme-foundation`

Status: **done**, tasks 1.1–1.12 complete. Task 1.13 is a manual device check and is intentionally
left unchecked — no automated harness can verify it (design.md decision 9).

### What landed

- `core/ui/theme/ConstanzaColors.kt` (created as `ConstanzaColors.kt`, not `Color.kt` — see Deviation
  below): the ten warm-neutral tokens, oklch in KDoc, hex via named `..._ARGB` constants.
- `core/ui/theme/HabitPalette.kt`: `HabitColor` enum (six ratified colours, `argb: Int` spine),
  `HabitPalette` object (`ORDERED`/`ARGB`/`DEFAULT`), `HabitColor.composeColor` extension.
- `core/ui/theme/Type.kt`: `ConstanzaTypography`, seven roles pinned to `FontFamily.Default`.
- `core/ui/theme/Shape.kt`: `ConstanzaShapes` (M3 baseline shape scale — no ratified value diverges).
- `core/ui/theme/Dimens.kt`: `Spacing` (xs/sm/md/lg/xl/xxl) and `Dimens` (`HabitDot`, `HabitDotSlot`,
  `Swatch`, `SwatchBorder`).
- `core/ui/theme/Theme.kt`: dark-only `ConstanzaTheme(content)`, `darkColorScheme(...)` built from
  `ConstanzaColors`, `ConstanzaTypography`, `ConstanzaShapes`. No `darkTheme` param, no
  `isSystemInDarkTheme()`, no `lightColorScheme()`.
- `res/values/colors.xml` (new): `window_background` = `#110B06`.
- `res/values/themes.xml`: `Theme.Constanza` parent is now `android:Theme.Material.NoActionBar`
  (dark), `android:windowBackground` points at `@color/window_background`.
- `core/ui/MainActivity.kt`: `enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(TRANSPARENT),
  navigationBarStyle = SystemBarStyle.dark(TRANSPARENT))` before `setContent`.
- `app/src/test/kotlin/.../core/ui/theme/ColorContrastTest.kt`: 8 test methods, 14 contrast-floor
  assertions (6 habit colours + accent, each against `Background` and `SurfaceSelected`, all ≥4.5:1)
  plus 4 text-token legibility assertions (`OnBackground`, `OnBackgroundVariant`, `OnBackgroundMuted`
  on `Background`; `OnAccent` on `Accent`).

### Deviation from the task-stated file path

Task 1.1 names the file `core/ui/theme/Color.kt`. Detekt's `MatchingDeclarationName` rule (active by
default under `buildUponDefaultConfig = true`, not called out anywhere in design.md/tasks.md) requires
a file with exactly one top-level declaration to be named after that declaration. `Color.kt` holding
only `object ConstanzaColors` triggers it. Fixed by renaming the file to `ConstanzaColors.kt` — same
directory, same object name and members, no consumer-visible change (Kotlin resolves by symbol, not
file name). This is the same class of problem design.md pre-solved for `MagicNumber` (the `..._ARGB`
const fallback, also applied here), just a rule design didn't anticipate. Flagged loudly rather than
silently improvised.

### Verification (real numbers, `--rerun-tasks` used throughout)

| Command | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest --tests "*.ColorContrastTest"` | BUILD SUCCESSFUL — `ColorContrastTest`: 8 tests, 0 failures, 0 errors |
| `./gradlew :app:testDebugUnitTest` (full suite) | BUILD SUCCESSFUL — 105 tests, 0 failures (baseline 97 + 8 new = 105, exact match, no regression) |
| `./gradlew :domain:test` | BUILD SUCCESSFUL (baseline 52, unchanged — no `:domain` file touched in this unit) |
| `./gradlew :app:detektMain` | BUILD SUCCESSFUL, 0 issues (after the `ConstanzaColors.kt` rename + `..._ARGB` const fallback) |
| `./gradlew :domain:detektMain` | BUILD SUCCESSFUL, 0 issues |

`JAVA_HOME` had to be pointed at Android Studio's bundled JBR
(`/Applications/Android Studio.app/Contents/jbr/Contents/Home`) — no system-wide JDK is installed on
this machine. Not a code change; noted for the next unit's apply run.

### Outstanding — cannot be automated (task 1.13)

Manual device check, not run by this agent (explicitly out of scope for `sdd-apply`):

- No white cold-start flash on launch.
- System-bar icons stay legible (dark-background style) with the device's system-wide appearance set
  to light.
- Devices: Pixel 10 (API 37), Galaxy Z Fold 7 (SM-F966B, API 36).

### Changed-line footprint

`git diff --shortstat` against tracker branch `feat/warm-dark-design-system`: **10 files changed, 340
insertions(+), 14 deletions(-)** — 354 total changed lines, inside the 430-line stop threshold and
inside the unit's 300–430 forecast.

### Boundaries respected

- `habit/HabitEditorViewModel.kt` (`HabitColorPalette`) — untouched, confirmed via `git diff --name-only`.
- `AppDatabase`, `DatabaseModule`, any migration, `BackupDto`, `BackupImporter` — untouched.
- No screen restyled (units 4–6 own that).
- No font dependency added; `FontFamily.Default` only.

## Unit 2 — Palette + Data Migration (PR B, part 1) — `feat/warm-dark-palette-migration`

Status: **done**, tasks 2.1–2.13 complete. All landed as a single commit (design.md's own rule: a
commit with `version = 2` and no committed schema, or a migration and no test, bricks a device on
checkout).

### What landed

- `core/data/migration/HabitColorRemap.kt`: `internal object HabitColorRemap`, `LEGACY_TO_CURRENT`
  (6 entries, both sides literal ints, never `HabitColor.X.argb`), `normalize(argb: Int): Int`.
- `core/data/migration/AppMigrations.kt`: `internal object AppMigrations { val MIGRATION_1_2 }` — one
  parameterized `CASE colorArgb WHEN ? THEN ? … END WHERE colorArgb IN (?,?,?,?,?,?)`, all 18 args
  bound from `HabitColorRemap.LEGACY_TO_CURRENT` (never inlined hex), KDoc carries the rollback
  recipe (`Migration(2,3)` inverting the bijection).
- `core/data/AppDatabase.kt`: `version = 2`.
- `core/di/DatabaseModule.kt`: `.addMigrations(AppMigrations.MIGRATION_1_2)` registered on the
  `Room.databaseBuilder(...)` call (C4 hard blocker resolved).
- `app/schemas/com.jjrapps.constanza.core.data.AppDatabase/2.json`: generated via
  `./gradlew :app:kspDebugKotlin`, committed. `identityHash` confirmed byte-identical to `1.json`
  (`5adafec4244c3539d5378634993b6649`) — data-only change, asserted not assumed.
- `habit/HabitEditorViewModel.kt`: `object HabitColorPalette` deleted; `HabitEditorUiState.colorArgb`
  default now `HabitPalette.DEFAULT`.
- `habit/HabitEditorScreen.kt`: `ColorSwatchRow` now iterates `HabitPalette.ARGB` (one import + one
  call-site swap — the only edit needed to keep the codebase compiling after the `HabitColorPalette`
  deletion; `SWATCH_SIZE`/`SWATCH_BORDER` and every other tonal concern untouched, still unit 5's job).
- `portability/BackupDto.kt`: `CURRENT_SCHEMA_VERSION = 2`, `private` dropped (C5 resolved).
- `portability/BackupImporter.kt`: new top-level pure `normalizeHabitColors(habits, schemaVersion)`
  (mirrors the existing `remapEntrySlotId` pattern in the same file for testability without mocking
  five collaborators); `replaceAll` calls it before the insert transaction.
- `app/src/test/kotlin/.../core/data/migration/HabitColorRemapTest.kt`: 4 tests (bijection,
  orange→pink, unmapped passthrough, freeze-drift guard against `HabitPalette`).
- `app/src/test/kotlin/.../portability/BackupImporterNormalizationTest.kt`: 3 tests
  (`schemaVersion=1` normalizes purple and orange; `schemaVersion=2` byte-identical passthrough).
- `app/src/androidTest/kotlin/.../core/data/AppDatabaseMigrationTest.kt` **extended** (not recreated,
  C2): new test `migration1To2RewritesEveryLegacyColourToItsCurrentCounterpart` seeds all six legacy
  colours plus one unmapped colour via bound-arg `INSERT`, runs
  `runMigrationsAndValidate(TEST_DB_NAME, 2, true, AppMigrations.MIGRATION_1_2)`, then reads the
  post-migration rows back via a raw `SELECT` cursor and asserts the actual `colorArgb` VALUES — not
  merely that the run didn't throw.
- `openspec/config.yaml:216`: corrected to the real schema path (C1 resolved).

### Deviations, flagged loudly

1. **`AppMigrations` is an `object`, not a `class`.** `val MIGRATION_1_2: Migration` as a member of a
   `class` fails detekt's `VariableNaming` rule (camelCase-only for a class property). Declared
   directly inside an `object`, the applicable rule is `ObjectPropertyNaming`, whose default pattern
   permits `SCREAMING_SNAKE_CASE` — same class of issue as unit 1's `Color.kt` → `ConstanzaColors.kt`
   rename, not anticipated by `tasks.md`/`design.md`. A `@Suppress` was rejected for the same reason
   decision 1 rejected one for `MagicNumber`. **Consequence for unit 3**: `PreMigrationSnapshotWriter`
   needs `filesDir` injected at the migration's construction site (task 3.3); since `AppMigrations` is
   now an `object`, that will need a factory method taking the writer/directory as a parameter, not a
   constructor parameter. Documented here so unit 3's apply run isn't surprised by it.
2. **`habit/HabitEditorScreen.kt` was touched in unit 2**, though design.md's own File Changes table
   lists it as a unit 5 file. Unavoidable: task 2.6 deletes `HabitColorPalette`, and this screen was
   its only other consumer (`ColorSwatchRow`). The edit is the minimal possible one — an import and a
   single `.SWATCHES` → `.ARGB` symbol swap — and does not touch anything unit 5 owns (dimensions,
   tonal colours, `SWATCH_SIZE`/`SWATCH_BORDER`).

### Verification (real numbers, `--rerun-tasks` used throughout)

| Command | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest --tests "*.HabitColorRemapTest" --tests "*.BackupImporterNormalizationTest"` | BUILD SUCCESSFUL — 4 + 3 = 7 tests, 0 failures |
| `./gradlew :app:testDebugUnitTest` (full suite) | BUILD SUCCESSFUL — 112 tests, 0 failures (baseline 105 + 7 new, exact match) |
| `./gradlew :domain:test` | BUILD SUCCESSFUL — 52 tests, 0 failures (untouched, exact match to baseline) |
| `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.jjrapps.constanza.core.data.AppDatabaseMigrationTest` (device: Galaxy Z Fold 7, SM-F966B, API 36, serial `RFCY720PJKV`) | BUILD SUCCESSFUL — 2 tests, 0 failures, 0 errors. Confirms the migration rewrites real device SQLite rows, not merely an in-memory JVM assertion. |
| `./gradlew :app:detektMain` | BUILD SUCCESSFUL, 0 issues (after the `AppMigrations` object fix) |

Instrumented-test note: `./gradlew :app:connectedDebugAndroidTest --tests "..."` rejects `--tests` for
this AGP version (`Unknown command-line option '--tests'`) — the correct flag is
`-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`. Recorded so the next unit's apply run
doesn't rediscover this.

### Changed-line footprint — reported per the stop-and-report threshold, not silently absorbed

`git diff --shortstat` against `feat/warm-dark-theme-foundation`: **13 files changed, 734
insertions(+), 29 deletions(-)** — 763 raw changed lines. This is over unit 2's own 380-line stop
threshold and over the attempt's combined 580-line ledger for units 2+3 together.

**Why, and why this is not a hidden overrun of authored work**: 423 of the 734 insertions are the
generated `app/schemas/.../2.json` (task 2.5) — mechanically produced by
`./gradlew :app:kspDebugKotlin`, not hand-written, and its whole purpose is to be diffed automatically
against `1.json` to prove `identityHash` is unchanged (design.md decision 3: "**This is asserted, not
trusted**"). Excluding it: **340 authored changed lines** (311 insertions + 29 deletions), inside the
380-line stop threshold and inside the unit's 250–380 forecast.

**Stopping here rather than continuing into unit 3.** The attempt was acquired with
`--max-changed-lines 580` for units 2+3 combined. Unit 2 alone, in raw `git diff --shortstat` terms
(the same measure the ledger authority most plausibly tracks), already reaches 763 — over the combined
ceiling before unit 3 writes a single line. Per the explicit stop-and-report instruction ("not
advisory"), this is reported as a decision point rather than silently pushed through: unit 2 is
complete, coherent, committed as one atomic commit, and fully verified (JVM + real hardware); unit 3
(tasks 3.1–3.7, the pre-migration snapshot) is unstarted and needs its own attempt/authorization
decision, informed by whether the ledger counts the mandatory generated schema JSON or excludes it as
today's evidence suggests it should.

### Boundaries respected

- `habit/HabitListScreen.kt`, `tracking/TodayModel.kt`, `tracking/TodayViewModel.kt`,
  `tracking/TodayScreen.kt` — untouched (unit 4's scope).
- `habit/ScheduleEditors.kt`, `progress/ProgressScreen.kt`, `reminding/SnoozeSettingsScreen.kt`,
  `portability/DataPortabilityScreen.kt` — untouched (units 5/6's scope).
- No `PreMigrationSnapshotWriter` reference anywhere yet — unit 3 is genuinely unstarted, not
  partially begun.
- `:domain` — confirmed untouched (`./gradlew :domain:test` exact-matches baseline 52).

## Unit 3 — 7.5 Pre-Migration Snapshot (PR B, part 2) — `feat/warm-dark-palette-migration`

Status: **done**, tasks 3.1–3.7 complete. Lands as its own separate commit on top of unit 2's, so it
can revert alone without touching `MIGRATION_1_2` (design.md decision 5). The design explicitly names
this the single most dangerous path in the whole change — a throw inside `migrate()` would leave the
user's database unopenable — and every property below is built around never being that path.

### What landed

- `core/data/migration/PreMigrationSnapshotWriter.kt` (new): `internal class
  PreMigrationSnapshotWriter(targetDir: File)`, `fun write(db: SupportSQLiteDatabase): Boolean`.
  Dumps every user table (`sqlite_master.sql` verbatim `CREATE TABLE`, then one `INSERT INTO` per row,
  values escaped by `Cursor.getType()`) to `<targetDir>/pre-migration/pre-migration-v1.sql`, skipping
  `sqlite_%`, `android_metadata`, `room_master_table`. Writes `pre-migration-v1.sql.tmp` first, then
  `java.nio.file.Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` to the final name only after the last
  row; deletes the temp file on the failure path. Every `Cursor` is closed via `use {}`; rows stream
  through one `BufferedWriter`, nothing accumulates in memory. No `System.currentTimeMillis()` — the
  filename is fixed by design, so no `TimeProvider` injection is needed at all. `catch (Exception)`,
  never `Throwable`: an `OutOfMemoryError` propagates on purpose (Room's rollback is safer than a
  half-written snapshot), stated loudly in the class KDoc so a reviewer does not "fix" it.
- `core/data/migration/AppMigrations.kt`: `MIGRATION_1_2` (a `val`) became `migration1To2(writer:
  PreMigrationSnapshotWriter): Migration` (a factory function). `writer.write(db)` is now the literal
  first statement inside `migrate(db)`, before the `UPDATE`; its `Boolean` result is discarded on
  purpose — `write()` already logs its own WARN on failure and never throws for a recoverable cause.
- `core/di/DatabaseModule.kt`: `.addMigrations(AppMigrations.MIGRATION_1_2)` →
  `.addMigrations(AppMigrations.migration1To2(PreMigrationSnapshotWriter(context.filesDir)))` — the
  one call site with a real `Context`, so `filesDir` is supplied here.
- `app/src/test/kotlin/.../core/data/migration/PreMigrationSnapshotWriterTest.kt` (new): a MockK
  `SupportSQLiteDatabase` whose `query(...)` throws proves `write()` returns `false`, throws nothing,
  and leaves neither the temp nor the final file behind. Uses a real `Files.createTempDirectory` —
  `isReturnDefaultValues = true` (unit 4a's setting) means the `Log.w` call inside the catch block
  returns harmlessly instead of "Method ... not mocked.", so no Robolectric is needed.
- `app/src/androidTest/kotlin/.../core/data/AppDatabaseMigrationTest.kt` **extended again** (separate
  commit from unit 2's edit to the same file, per the orchestrator's explicit instruction): both
  existing tests now build their `Migration` via `AppMigrations.migration1To2(PreMigrationSnapshotWriter(targetFilesDir))`
  instead of the removed `MIGRATION_1_2` val; a new test,
  `migration1To2WritesAPreMigrationSnapshotContainingTheLegacyRows`, seeds one legacy colour, runs the
  real migration against the checked-in `1.json`, then asserts the snapshot file exists at
  `targetContext.filesDir/pre-migration/pre-migration-v1.sql` and that its contents contain the
  **legacy** colour value (not the post-migration one) — proving the dump happened before the rewrite,
  on real hardware, not merely at some point during the migration.
- `openspec/config.yaml`: item `"7.5"` removed from `carried_forward_open_items.items` (task 3.6).
  `G.7-throttling-row` confirmed byte-for-byte unchanged — diffed independently before commit.

### Deviation, flagged loudly (structural problem the orchestrator pre-identified)

**`AppMigrations` cannot take a constructor parameter because it stays an `object` (unit 2's own
flagged constraint).** Task 3.3 as written ("pass `filesDir` into `PreMigrationSnapshotWriter` at the
migration call site") assumed a `class`. Resolved with a factory function,
`AppMigrations.migration1To2(writer: PreMigrationSnapshotWriter): Migration`, closing over the
injected `writer` and returning a fresh `Migration(1, 2)` — `DatabaseModule` builds the writer with its
own `Context.filesDir` and hands it in. A function name is camelCase under `FunctionNaming`, so the
`VariableNaming`/`ObjectPropertyNaming` tension that forced the `object` shape in the first place never
applies to it. No `@Suppress` anywhere in this unit either — see the second deviation below for the one
new tension this unit hit on its own.

**Second, new deviation: detekt's `TooGenericExceptionCaught` on the mandated `catch (Exception)`.**
Not anticipated by `tasks.md`/`design.md`, and not visible under `:app:detektMain`'s known gap for
semantic rules (`ForbiddenMethodCall` — this is a PSI-only syntactic rule, so it does fire). Resolved
by naming the caught variable `expectedFailure` instead of `e`: the rule's own default
`allowedExceptionNameRegex` (`_|(ignore|expected).*`) is a configured escape hatch, not an annotation,
and the name also states the design intent plainly (this catch is for anticipated, recoverable
failures, deliberately not `Throwable`). No `@Suppress` used — same class of resolution decision 1
already established for `MagicNumber` and unit 2 established for `VariableNaming`.

### Verification (real numbers, `--rerun-tasks` used throughout; `JAVA_HOME` pointed at Android
Studio's bundled JBR as in units 1–2)

| Command | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest --tests "*.PreMigrationSnapshotWriterTest"` | BUILD SUCCESSFUL — 1 test, 0 failures |
| `./gradlew :app:testDebugUnitTest` (full suite) | BUILD SUCCESSFUL — 113 tests, 0 failures (baseline 112 + 1 new, exact match) |
| `./gradlew :domain:test` | BUILD SUCCESSFUL — 52 tests, 0 failures (untouched, exact match to baseline) |
| `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.jjrapps.constanza.core.data.AppDatabaseMigrationTest` (Galaxy Z Fold 7, SM-F966B, API 36, serial `RFCY720PJKV`; `mWakefulness=Awake`, `isKeyguardShowing=false` confirmed first) | BUILD SUCCESSFUL — 3 tests, 0 failures, 0 errors (the 2 existing + the new snapshot-content test) |
| `./gradlew :app:connectedDebugAndroidTest` (full instrumented suite, same device) | BUILD SUCCESSFUL — 61 tests, 0 failures, 0 errors (baseline 60 + 1 new, exact match) |
| `./gradlew :app:detektMain` | BUILD SUCCESSFUL, 0 issues (after the `expectedFailure` rename) |
| `./gradlew :domain:detektMain` | BUILD SUCCESSFUL, 0 issues |

The Pixel 10 was not connected; not waited on, per instructions. No device gotcha hit — keyguard/
wakefulness were verified green before running.

### Changed-line footprint — reported per the stop-and-report threshold, not silently absorbed

`git diff --cached --shortstat` (unit 3's files only, on top of unit 2's commit): **6 files changed,
245 insertions(+), 27 deletions(-)** — **272 raw changed lines**. Unlike unit 2, there is no
mechanically-generated file in this unit (the migration stays data-only; no schema regeneration was
needed), so **272 is also the authored figure** — there is no smaller "real" number hiding under it.

This crosses the unit's own 200-line stop threshold (and the 120–200 forecast) by 72 lines, about 36%
over the top of the range. Per the explicit stop-and-report instruction, this is reported as a decision
point rather than pushed through silently: all of tasks 3.1–3.7 are complete, tested (JVM + real
hardware), coherent, and committed as the required single atomic commit — nothing is left unstarted or
partially done. The overrun is driven by two things the forecast likely under-weighted: (1) the KDoc on
`PreMigrationSnapshotWriter` is unusually long because the orchestrator explicitly asked for the
`catch (Exception)` reasoning to be spelled out so a reviewer does not "fix" it into `catch (Throwable)`
— roughly 30 of the file's 127 lines are that one block of documentation; and (2) the structural
`object`→factory-function deviation (flagged in advance by unit 2, but still requiring a real KDoc
explanation, a `DatabaseModule` call-site change, and updates to both existing `AppDatabaseMigrationTest`
call sites) added lines beyond the writer + its own test that the forecast's "120–200" range was sized
before that constraint was known.

### Boundaries respected

- `MIGRATION_1_2`'s `UPDATE` SQL itself — byte-for-byte unchanged; only the enclosing shape (`val` →
  factory function) and the new first statement (`writer.write(db)`) changed.
- `HabitColorRemap.kt` — untouched.
- `openspec/config.yaml`'s `G.7-throttling-row` entry — confirmed untouched (diffed independently).
- No screen, no `:domain` file, no other migration file touched (units 4–6's scope; `Migration(2,3)`
  rollback recipe is documentation only, not implemented — correctly, per design.md decision 5, it
  ships only if unit 2 is ever actually rolled back).

## Unit 4 — Habit Colour Identity (PR C) — `feat/warm-dark-colour-identity`

Status: **done**, tasks 4.1–4.9 complete. Branch created off `feat/warm-dark-palette-migration`
(tip `543be10`, units 2+3), landing as one commit — this unit has no equivalent of unit 2/3's
"must not split" rule, so one atomic commit was chosen for the same reason: `TodayHabitRow.colorArgb`
with no default and its call sites move together, or the build breaks partway through history.

### What landed

- `core/ui/component/HabitColorDot.kt` (new package): `HabitColorDot(argb: Int, modifier: Modifier)`
  — a `Dimens.HabitDotSlot` `Box` containing a tinted-halo circle (`Color(argb).copy(alpha = 0.16f)`)
  and a solid `Dimens.HabitDot` circle, both `.clip(CircleShape).background(...)`, matching the
  existing `ColorSwatchRow` drawing convention in `HabitEditorScreen.kt`. Carries
  `Modifier.testTag(HABIT_COLOR_DOT_TEST_TAG)` and nothing else — no `contentDescription`, per
  design.md decision 6, stated loudly as deliberate in the KDoc so nobody "fixes" it later.
- `tracking/TodayModel.kt`: `TodayHabitRow` gains `colorArgb: Int` with no default value, positioned
  before `slots` (data class field order, no consumer relies on positional construction outside this
  file); `buildTodayHabitRow`'s one construction site now passes `habit.colorArgb`.
- `tracking/TodayScreen.kt`: `HabitRollupRow`'s single-slot branch wraps the habit-name `Text` in a
  `Row(verticalAlignment = CenterVertically)` with the dot; the multi-slot branch's `ListItem` gains
  `leadingContent = { HabitColorDot(row.colorArgb) }`. `ExactAlarmBanner`'s body is now wrapped in
  `Surface(color = ConstanzaColors.SurfaceRaised, shape = ConstanzaShapes.medium)` — the existing
  `Row` (and its load-bearing `weight(1f)` fix) is preserved verbatim inside it, not replaced.
- `habit/HabitListScreen.kt`: `HabitRow`'s `ListItem` gains
  `leadingContent = { HabitColorDot(habit.colorArgb) }`.
- `app/src/test/kotlin/.../TodayViewModelTest.kt`: `habit()` fixture gained a
  `colorArgb: Int = HABIT_COLOR_ARGB` parameter; one assertion added verifying `row.colorArgb`
  propagates from the fixture's habit.
- `app/src/androidTest/kotlin/.../core/ui/component/HabitColorDotComposeTest.kt` (new): two tests —
  the dot is present on the real `TodayRoute` for a habit due today, and two habits on the real
  `HabitListRoute` each render their own dot (`dots.size >= 2`). Both assert via
  `onAllNodesWithTag(HABIT_COLOR_DOT_TEST_TAG, useUnmergedTree = true)`, never a
  `contentDescription` — satisfies spec `Habit Colour Visible Where Habits Are Listed`.

### Corrections to the task-stated call-site counts — flagged loudly, per instruction

Before editing, `rg -n "TodayHabitRow\("` across the whole main source tree found exactly **one**
literal `TodayHabitRow(...)` construction (`TodayModel.kt`'s `buildTodayHabitRow` return), not the
"3 call sites in this file" task 4.2 states, and **zero** in `TodayViewModel.kt`, not the "1 call
site" task 4.3 states. Design.md's own correction C3 says "8 positional call sites across
`TodayModel.kt` (3), `TodayViewModel.kt` (1) and `TodayViewModelTest.kt` (4)" — the 4 in the test
file matches exactly (the file's 4 `buildTodayHabitRow(...)` invocations), so that count is real. The
3+1 in the two main-source files do not correspond to any literal `TodayHabitRow(...)` construction;
`buildTodayHabitRow`'s signature (`habit, schedule, slots, snapshot`) was already unchanged by adding
the field, since it derives `colorArgb` from the `Habit` it already receives. Task 4.3 required no
code change at all. This is reported as a real discrepancy in the task/design artifacts, not silently
reconciled by inventing edits to match the stated count.

### Deviation found during verification — test-only, not production code

`HabitColorDotComposeTest`'s habit-list assertion initially failed on the real device
(`AssertionError: each listed habit must render its own colour dot`, 0 nodes found) while the Today
assertion passed. Root cause: `HabitListScreen.HabitRow`'s `ListItem` sits under
`Modifier.clickable { onEditHabit(habit.id) }`, and `clickable` merges descendant semantics into one
accessibility node for the row — the default merged-tree finder `onAllNodesWithTag` searches cannot
see the dot's `testTag` once it is folded into that merge. The Today single-slot branch has no such
merging modifier, which is why it passed without the fix. Resolved with
`useUnmergedTree = true` on both assertions in the test; no production `HabitColorDot.kt` or screen
code changed to work around it — this is exactly the kind of accessibility grouping the app wants for
a real screen reader user, not a defect.

### Verification (real numbers, `--rerun-tasks` used throughout)

| Command | Result |
|---|---|
| `./gradlew :app:compileDebugKotlin --rerun-tasks` | BUILD SUCCESSFUL, no new warnings |
| `./gradlew :app:testDebugUnitTest --tests "*.TodayViewModelTest"` | BUILD SUCCESSFUL — 8 tests, 0 failures |
| `./gradlew :app:testDebugUnitTest` (full suite) | BUILD SUCCESSFUL — 113 tests, 0 failures (exact match to the stated baseline) |
| `./gradlew :domain:test` | BUILD SUCCESSFUL — 52 tests, 0 failures (exact match to baseline; `:domain` confirmed untouched) |
| `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=....HabitColorDotComposeTest` (device: Galaxy Z Fold 7, SM-F966B, API 36, serial `RFCY720PJKV`, `mWakefulness=Awake`, `isKeyguardShowing=false`, confirmed before running) | First run: 1 of 2 failed (the `useUnmergedTree` issue above). After the test-only fix: BUILD SUCCESSFUL — 2 tests, 0 failures |
| `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=....TodayAdaptiveComposeTest` (same device, run completely unmodified per the non-negotiable) | BUILD SUCCESSFUL — 1 test, 0 failures |
| `./gradlew :app:connectedDebugAndroidTest` (full instrumented suite, same device) | BUILD SUCCESSFUL — 63 tests, 0 skipped, 0 failed (baseline 61 + 2 new `HabitColorDotComposeTest` tests, exact match, no regression) |
| `./gradlew :app:detektMain` | BUILD SUCCESSFUL, 0 issues |
| `./gradlew :domain:detektMain` | BUILD SUCCESSFUL, 0 issues (untouched) |

Instrumented tests used `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>` (not `--tests`),
per unit 2's recorded AGP-version note.

### Changed-line footprint

`git diff --cached --shortstat` (all of this unit's files staged together): **6 files changed, 239
insertions(+), 24 deletions(-)** — 263 total changed lines, all authored (no generated artifact in
this unit). Well inside the unit's own 460-line stop threshold and inside the 320–460 forecast range
(at the low end of it) — no stop-and-report was triggered.

### Boundaries respected

- `habit/HabitEditorScreen.kt`, `habit/ScheduleEditors.kt` — untouched (unit 5's scope); `SWATCH_SIZE`/
  `SWATCH_BORDER` still private in `HabitEditorScreen.kt`, not yet moved to `Dimens` (that move is
  task 5.1, not this unit's).
- `progress/ProgressScreen.kt`, `reminding/SnoozeSettingsScreen.kt`,
  `portability/DataPortabilityScreen.kt` — untouched (unit 6's scope).
- No migration, `AppDatabase`, `DatabaseModule`, `BackupImporter`/`BackupDto` file touched.
- `:domain` — confirmed untouched (`./gradlew :domain:test` exact-matches baseline 52).
- `TodayAdaptiveComposeTest.kt` itself — confirmed byte-identical to before this unit
  (`git diff --stat` shows no change to that file); only its passing result is new evidence, not its
  source.

## Unit 5 — Editor Tonal Pass (PR D) — `feat/warm-dark-editor-tonal`

Status: **done**, tasks 5.1–5.4 complete. Branch created off `feat/warm-dark-habit-color-identity`
(tip `2c9c92c`, unit 4), landing as one commit — this is by far the smallest actual footprint of any
unit so far, well under the unit's own 520-line stop threshold and under design.md's 360–520 forecast,
the opposite of the "UI units roughly double their forecast" pattern the tracker flagged as
historically likely here.

### What landed

- `habit/HabitEditorScreen.kt`: `Scaffold(topBar = { HabitEditorTopBar(titleRes) }, containerColor =
  ConstanzaColors.Background)` — the `TopAppBar` itself was extracted into a new private
  `HabitEditorTopBar(titleRes: Int)` composable carrying `TopAppBarDefaults.topAppBarColors
  (containerColor = ConstanzaColors.Background)`, so the app bar reads as one seamless surface with
  the screen behind it instead of M3's default `surfaceContainer` role (which `Theme.kt` never
  repoints, so it would otherwise stay cool-toned). `SWATCH_SIZE`/`SWATCH_BORDER` (two private `val`s)
  deleted; `ColorSwatchRow` now reads `Dimens.Swatch`/`Dimens.SwatchBorder` (already defined in unit
  1's `Dimens.kt`, unused until now) — the one deliberate token-ownership move task 5.1 called for.
- `habit/ScheduleEditors.kt`: **no change** — see the correction below.

### Corrections to the task-stated scope — flagged loudly, per instruction

1. **`ListItemDefaults.colors` does not apply to `HabitEditorScreen.kt`.** Task 5.1 lists it alongside
   `TopAppBarDefaults`/`Scaffold`. `rg -n "ListItem"` against the file found zero matches — the editor
   is built entirely from `Column`/`Row`/`OutlinedTextField`/`Text`/`Button`, never a `ListItem`. There
   is nothing to apply the token to; not applied, rather than invented against a component that isn't
   there.
2. **The swatch selection border needed no change**, confirmed rather than assumed: it already reads
   `MaterialTheme.colorScheme.primary` at `HabitEditorScreen.kt:247` (line renumbered from the task's
   stated `:246` by the import additions below it — same file, same expression), which has been the
   warm accent since unit 1's `Theme.kt` repointed `primary` to `ConstanzaColors.Accent`.
3. **`habit/ScheduleEditors.kt` required zero production code changes.** Read in full (all 332 lines)
   before concluding this. Every colour it reads — `MaterialTheme.colorScheme.onSurfaceVariant`,
   `.error`, plus the implicit selection colours of `Switch` (`ReminderTimeEditor`), `FilterChip`
   (`DayOfWeekPicker`), and `Checkbox` (`ReminderSlotRow`) — already resolves through the
   `MaterialTheme.colorScheme` unit 1's `Theme.kt` built from `ConstanzaColors` (`primary`→Accent,
   `secondaryContainer`→SurfaceSelected, `onSurfaceVariant`→OnBackgroundVariant). The file has no
   `ListItem`, `TopAppBar`, `Scaffold`, or hardcoded `Color(...)` literal — none of design decision 7's
   tonal-surface rules have anything to touch here, and decision 7's own table independently predicts
   this ("selection states … none" structural change, already reading `colorScheme.primary`). `git
   diff` for this file is empty; this is reported as a real finding, not silently skipped.
4. **`ListItemDefaults`/`TopAppBarDefaults`/`Scaffold(containerColor = …)` genuinely only applied to
   `HabitEditorScreen.kt`, and even there only two of the three** (`TopAppBarDefaults`, `Scaffold`) —
   consistent with points 1–3 above.

### Deviation, flagged loudly (detekt, not design)

Inlining the `colors = TopAppBarDefaults.topAppBarColors(...)` argument directly into the existing
`Scaffold(topBar = { TopAppBar(...) })` lambda pushed `HabitEditorScreen`'s body from 60 lines (the
`LongMethod` threshold) to 66. `@Suppress` is ruled out by design. Resolved by extracting the whole
`TopAppBar(...)` call into the new private `HabitEditorTopBar(titleRes: Int)` composable — the same
extraction pattern the file already uses for `EditorNameField` — which returned the caller to a
single-line `Scaffold(...)` call and detekt to clean.

### Verification (real numbers, `--rerun-tasks` used throughout; `JAVA_HOME` pointed at Android
Studio's bundled JBR as in units 1–4)

| Command | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest` (full suite) | BUILD SUCCESSFUL — 113 tests, 0 failures (exact match to baseline) |
| `./gradlew :domain:test` | BUILD SUCCESSFUL — 52 tests, 0 failures (exact match to baseline; `:domain` confirmed untouched) |
| `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.jjrapps.constanza.habit.HabitEditorComposeTest,com.jjrapps.constanza.habit.HabitEditorRotationComposeTest` (device: Galaxy Z Fold 7, SM-F966B, API 36, serial `RFCY720PJKV`, `mWakefulness=Awake`, `isKeyguardShowing=false` confirmed before running) | BUILD SUCCESSFUL — 4 tests, 0 failures. Neither test's own source was modified — both ran exactly as they existed before this unit, per the non-negotiable on the focus-restoration machinery |
| `./gradlew :app:connectedDebugAndroidTest` (full instrumented suite, same device) | BUILD SUCCESSFUL — 63 tests, 0 skipped, 0 failed (exact match to baseline — this unit added no new instrumented test) |
| `./gradlew :app:detektMain` | First run FAILED — `LongMethod` on `HabitEditorScreen` (see deviation above). After the `HabitEditorTopBar` extraction: BUILD SUCCESSFUL, 0 issues |
| `./gradlew :domain:detektMain` | BUILD SUCCESSFUL, 0 issues (untouched) |

Instrumented `--tests` again rejected by this AGP/Gradle version (unit 2's recorded gotcha, confirmed
still true); used `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>,<FQCN>` instead.

### Focus-restoration machinery — confirmed untouched

`focusRestoring(fieldId, focusedFieldId)`, the `FIELD_NAME`/`FIELD_QUESTION`/`FIELD_NOTES` constants,
the single shared `rememberSaveable { mutableStateOf<String?>(null) }` in `HabitEditorScreen`, and
`hasInitialized`/`rememberSaveable(habitId)` in `HabitEditorRoute` are all byte-identical to before this
unit (`git diff` shows no touched lines in that region). Every text field's `.then(focusRestoring(...))`
survives in its original position in the `Modifier` chain — none of this unit's edits touched a text
field's `Modifier` chain at all, only the top bar and the swatch row.

### Changed-line footprint

`git diff --shortstat` against `feat/warm-dark-colour-identity` (this unit's only commit): **1 file
changed, 19 insertions(+), 6 deletions(-)** — 25 total changed lines, all authored, all in
`habit/HabitEditorScreen.kt`. `habit/ScheduleEditors.kt` has zero diff (see correction 3 above). This
is far inside the unit's 520-line stop threshold and far below the 360–520 forecast — the smallest
footprint of any unit in this change so far, because the tonal groundwork (tokenized `colorScheme`
roles, `Dimens.Swatch`/`Dimens.SwatchBorder`) was already laid by units 1 and 2, leaving unit 5 with
one real gap to close (the app bar's unrepointed `surfaceContainer` role) rather than a from-scratch
retint.

### Boundaries respected

- `progress/ProgressScreen.kt`, `reminding/SnoozeSettingsScreen.kt`,
  `portability/DataPortabilityScreen.kt` — untouched (unit 6's scope).
- No migration, `AppDatabase`, `DatabaseModule`, `BackupImporter`/`BackupDto`, `TodayModel.kt`,
  `TodayScreen.kt`, `HabitListScreen.kt`, or `HabitColorDot.kt` file touched.
- `:domain` — confirmed untouched (`./gradlew :domain:test` exact-matches baseline 52).
- `HabitEditorComposeTest.kt`/`HabitEditorRotationComposeTest.kt` — confirmed byte-identical to before
  this unit; only their passing result is new evidence, not their source.

## Unit 6 — Remaining Screens Tonal Pass (PR E) — `feat/warm-dark-screens-tonal`

Status: tasks 6.0, 6.1, 6.2, 6.3, 6.6 done. **6.4 partially proven — flagged loudly, not marked
complete.** 6.5 not run (manual-only, correctly left unchecked). Branch created off
`feat/warm-dark-editor-tonal` (tip `60cfcab`, unit 5). Two `feat`/`fix` commits, no PR opened
(orchestrator's job).

### Task 6.0 — the important one, done first as instructed

`core/ui/theme/Theme.kt`'s `DarkColors` went from 11 bound M3 roles to 21. The five-tone ramp
(`Background` < `Surface` < `SurfaceRaised` < `SurfaceSelected` < `Outline`) maps onto the five
`surfaceContainer*` roles, with `surfaceContainerLowest`/`Low` deliberately collapsing onto
`Background` (nothing in this app's component inventory — `ListItem`, `TopAppBar`,
`ExposedDropdownMenu`, `AlertDialog`, confirmed by `rg` — needs a container more recessed than the
screen itself):

| M3 role | Bound to | Reused from |
|---|---|---|
| `surfaceContainerLowest` | `Background` | already bound (unit 1) |
| `surfaceContainerLow` | `Background` | already bound (unit 1) |
| `surfaceContainer` | `Surface` | already bound (unit 1) |
| `surfaceContainerHigh` | `SurfaceRaised` | already bound as `surfaceVariant` (unit 1) |
| `surfaceContainerHighest` | `SurfaceSelected` | already bound as `secondaryContainer` (unit 1) |
| `outlineVariant` | `Outline` | already bound as `outline` (unit 1) |
| `primaryContainer` | `SurfaceSelected` | mirrors `secondaryContainer` |
| `onPrimaryContainer` | `OnBackground` | mirrors `onSecondaryContainer` |
| `secondary` | `Accent` | mirrors `primary` — one accent, per spec `Accent Reserved For Chrome` |
| `onSecondary` | `OnAccent` | mirrors `onPrimary` |

**`ConstanzaColors.Outline` deliberately NOT reused as a container fill.** A naive sixth ramp step
would suggest `surfaceContainerHighest = Outline`, but `FilterChip`'s unselected border already reads
`colorScheme.outline` — giving its fill the identical value would render an invisible border (fill
and border blending into one flat colour). Caught before writing the binding, not after.

**Audited and left at M3 default, each with a stated reason (full detail in `Theme.kt`'s own KDoc,
per the instruction that a silent omission is what caused this gap in the first place):**
`tertiary*` (zero call sites, `rg -i tertiary` confirms), `error*` beyond the already-consumed
`colorScheme.error` (M3's baseline error red isn't hue-derived from `primary`, so it needs no
repointing; no filled `errorContainer` exists), `inverseSurface`/`inverseOnSurface`/`inversePrimary`
(no `Snackbar` or inverse component anywhere, `rg -i snackbar` confirms), `scrim` (fixed
black-with-alpha, hue-independent, already appropriate), `surfaceTint` (M3's default is a fixed
violet constant NOT auto-derived from `primary`, but every `Surface`/`Card`/`TopAppBar`/`Scaffold` in
this app sets an explicit `containerColor` or reads a now-warm container role at zero
`tonalElevation`, so nothing currently blends it into a visible pixel — flagged as one to revisit if
a future `Card` introduces real tonal-elevation blending), `surfaceDim`/`surfaceBright` (zero call
sites).

**`HabitEditorTopBar`'s unit-5 pin to `Background` — REMOVED, decided explicitly as instructed.**
Its own KDoc justification ("`surfaceContainer` isn't one of the roles `ConstanzaColors` repoints")
is exactly the gap this task closes at the theme layer: `surfaceContainer` now resolves to
`ConstanzaColors.Surface` for every `TopAppBar` in the app, not just the editor's. Keeping a
per-screen override would have silently re-diverged the editor's bar from `ProgressScreen`'s and
`SnoozeSettingsScreen`'s (both left at M3 defaults, see 6.1/6.2 below) the moment either needed a
different tone, for no remaining reason once the root cause is fixed. The `HabitEditorTopBar`
composable itself is kept — it now exists solely to hold `titleRes`, same as `EditorNameField`.
`TopAppBarDefaults` import removed as now-unused.

**`ColorContrastTest` extended** with the two literal surface values newly reachable as an M3
container fill that were bound in unit 1 but never measured: `Surface` (via `surfaceContainer`,
where `ListItem`'s `HabitColorDot` and habit-name text land on `TodayScreen`/`HabitListScreen`) and
`SurfaceRaised` (via `surfaceContainerHigh`, backing `AlertDialog` and `ExactAlarmBanner`'s
`Surface`). 5 new test methods, 12 + 3 = new assertions (2 loop-based across all 6 habit colours,
3 single).

### Tasks 6.1–6.3 — the same finding, three times, flagged loudly each time

**Zero production code changes needed in `ProgressScreen.kt`, `SnoozeSettingsScreen.kt`, or
`DataPortabilityScreen.kt`.** Each file was read in full before concluding this, exactly the same
discipline unit 5 used for `ScheduleEditors.kt`. None contains a `Color(...)` literal, a `Card`, or a
`Divider`; every themed surface each file touches (`TopAppBar`, `Scaffold`, `RadioButton`,
`AlertDialog`, `TextButton`) already resolves through `MaterialTheme.colorScheme`, which task 6.0
just finished making warm end-to-end. This is precisely the outcome task 6.0's own KDoc predicted:
"if you do the screens first you will patch the same symptom three times locally" — doing the theme
first meant there was nothing left to patch. `git diff` for all three files is empty; confirmed via
`git diff --stat` after this unit's commits, not assumed from the read alone.

### Deviation, flagged loudly (detekt, not design) — `fix(test)` commit

`./gradlew check` is the first invocation of the full aggregate anywhere in this SDD change; every
prior unit's verification only ran `:app:detektMain`/`:domain:detektMain`, which do not analyze test
sources. The test-inclusive `:app:detekt` failed on first run: 3 `MaxLineLength` violations — one in
this unit's own `ColorContrastTest` extension, two pre-existing in `OccurrencePlannerTest.kt`
(last touched by the archived `habit-tracking-mvp` change) and `TodayViewModelTest.kt` (last touched
by this change's own unit 4, commit `290c727` — unrelated to the tonal-pass work here). All three are
mechanical line-wraps with zero logic change; fixed together in a separate `fix(test)` commit since
they block this unit's own explicit gate and the fix carries no behavioural risk.

### Correction to task 6.4's own stated proof — flagged loudly, not silently reconciled

**`./gradlew check` is NOT green.** After the `MaxLineLength` fix, `:app:detekt` and every test task
pass, but `:app:lintDebug` fails on 3 pre-existing errors, none introduced by this unit and none in a
file this whole change has ever touched:

| File:line | Lint rule | Last touched by |
|---|---|---|
| `reminding/NotificationPoster.kt:52` | `MissingPermission` | `c3e3172` (reminding capability, pre-this-change) |
| `habit/ScheduleEditors.kt:242` | `NonObservableLocale` (`Locale.getDefault()` in `DayOfWeekPicker`) | `f599c4a` (habit-scheduling, pre-this-change) |
| `androidTest/.../HabitScheduleKindComposeTest.kt:60` | `ViewModelConstructorInComposable` | `fb94a36` (habit-scheduling test, pre-this-change) |

Each traced via `git log --oneline -1 -- <file>` to a commit outside this SDD change's own history —
none of units 1–6 ever touched these three lines. Not fixed here: a `MissingPermission` runtime-crash
guard and a `NonObservableLocale` recomposition fix are genuine behavioural changes to unrelated
capabilities (notification posting, schedule editing), well outside a tonal-pass unit's rollback
boundary, and risk-inappropriate to fold into a colour-role change. **Task 6.4 is left unchecked**
because its stated proof (`check` green) did not pass — the honest state, not a silent downgrade of
the requirement. This needs its own scoped fix, routed by the orchestrator, before `./gradlew check`
can ever be green in this repository.

### Verification (real numbers, `--rerun-tasks` used throughout; `JAVA_HOME` pointed at Android
Studio's bundled JBR as in units 1–5)

| Command | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest` | BUILD SUCCESSFUL — 118/118, 0 failures (baseline 113 + 5 new `ColorContrastTest` methods) |
| `./gradlew :domain:test` | BUILD SUCCESSFUL — 52/52 (exact baseline match; `:domain` confirmed untouched) |
| `./gradlew :app:connectedDebugAndroidTest` (Galaxy Z Fold 7, serial `RFCY720PJKV`, `mWakefulness=Awake`, `isKeyguardShowing=false` confirmed before running; Pixel 10 not connected, not waited on) | BUILD SUCCESSFUL — 63/63, 0 skipped, 0 failed (exact baseline match — full suite run, not filtered, since task 6.0 changes colours app-wide) |
| `./gradlew :app:detektMain` | BUILD SUCCESSFUL, 0 issues |
| `./gradlew :domain:detektMain` | BUILD SUCCESSFUL, 0 issues (untouched) |
| `./gradlew check` | FAILED — `:app:lintDebug`, 3 pre-existing errors unrelated to this change (see table above). `:app:detekt` (test-inclusive) and every test task inside `check` passed |

### Changed-line footprint (against `feat/warm-dark-editor-tonal`, tip `60cfcab`)

`git diff --shortstat feat/warm-dark-editor-tonal..HEAD`: **5 files changed, 157 insertions(+),
16 deletions(-)** — 173 total changed lines. Split by commit:

- `8dc2b8e` (`feat(theme)`, task 6.0): 3 files, 125 insertions(+), 10 deletions(-) — 135 lines.
  Reported separately from the 210-line screens threshold, as instructed: task 6.0 was added after
  the unit's forecast was made, so its cost is unforecast.
- `9168a6d` (`fix(test)`, `MaxLineLength`): 3 files, 33 insertions(+), 7 deletions(-) — 40 lines.
- Tasks 6.1–6.3 (the three screens): **0 lines** — `git diff` empty for all three files, per the
  finding above.

Well inside the unit's own 210-line stop threshold for the screens (0 of 210 used) and a modest,
justified unforecast cost for task 6.0.

### Outstanding — not run, not claimed

- **Task 6.5, manual device matrix (final gate)**: notification accent on a real posted reminder
  (`NotificationPoster.setColor`) showing the migrated colour, and the habit-colour dot across a
  fold/unfold configuration change. Requires the Pixel 10 (not connected this session) alongside the
  Galaxy Z Fold 7. **Not run.** Recorded here as an outstanding manual check for the orchestrator to
  route — do not mark 6.5 complete without it actually running.
- **Unit 1 task 1.13** (cold-start flash, system-bar icons in light mode) — still unchecked from unit
  1, untouched this unit, left unchecked per instruction.
- **Task 6.4's lint gap** (table above) — needs its own scoped fix outside this change's rollback
  boundary before `./gradlew check` can be green.

### Task 6.6 — confirmed, not assumed

`git diff 543be10..HEAD -- openspec/config.yaml` is empty (`543be10` is unit 3's own
"docs(tasks): mark work unit 3 tasks complete, merge apply-progress" commit, the last commit to touch
this file). `G.7-throttling-row` reads byte-for-byte as unit 3 left it.
