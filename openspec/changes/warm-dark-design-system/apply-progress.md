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
