# Design: Warm-Dark Design System

Phase: `sdd-design` · Change: `warm-dark-design-system` · Artifact store: hybrid
Inputs: `proposal.md` (settled), `explore.md`, Engram decision #47, `specs/` (written in parallel by
`sdd-spec`), `openspec/config.yaml`. Reference conventions borrowed from the user's verified sibling
app `sleep-noise-android` (`ui/theme/Color.kt`, `Theme.kt`, `test/.../ColorContrastTest.kt`).

## Corrections to the ratified inputs (verified against code, stated loudly)

Every ratified input held except these. None reopens a decision; each changes *how*.

| # | Claim as given | What the code says | Consequence |
|---|---|---|---|
| C1 | Schema committed at `app/schemas/1.json` (proposal, `config.yaml:216`) | Actual path is `app/schemas/com.jjrapps.constanza.core.data.AppDatabase/1.json` | The v2 export lands in that same package-named folder, not `app/schemas/2.json` |
| C2 | A `MigrationTestHelper` test must be added | It already exists: `app/src/androidTest/.../core/data/AppDatabaseMigrationTest.kt` (task 3.7), `room-testing` is already a dependency (`app/build.gradle.kts:148`), and androidTest assets already expose `$projectDir/schemas` (`:80`) | **Extend that file**, do not create one. The proposal's "no Gradle change" holds — verified, not assumed |
| C3 | Habit colour just needs new composables | `HabitListScreen.HabitRow` already receives the full `:domain` `Habit`, which has `colorArgb`. But `TodayHabitRow` (`tracking/TodayModel.kt:22`) carries only `habitId/habitName/dayStatus/slots` | Today needs **UI-state plumbing** (a new field + 8 positional call sites across 3 files), the habit list needs none. Neither explore nor propose says this |
| C4 | Room builder just needs `version = 2` | `DatabaseModule.kt:26` is `Room.databaseBuilder(...).build()` with **no `addMigrations(...)`** | The migration must be registered there or Room throws `IllegalStateException: A migration from 1 to 2 was required but not found` at first open. Unstated anywhere upstream |
| C5 | `BackupDto.kt:19` `CURRENT_SCHEMA_VERSION = 1` becomes `2` | It is `private const` and referenced only by the `BackupFile` default | `private` must be dropped (or a sibling public constant added) before the importer can gate on it. Changing the value alone does not build the gate |
| C6 | — | `openspec/changes/.../specs/data-portability/spec.md` has **no requirement for the 7.5 snapshot** | Work unit 3 ships behaviour that `sdd-verify` has no spec requirement to check. Flagged as a risk, not fixed here (`sdd-spec` owns specs) |

Confirmed exactly as stated: `AppDatabase.kt:30` `version = 1`/`exportSchema = true`, no
`fallbackToDestructiveMigration()` anywhere; `HabitColorPalette.SWATCHES`
(`HabitEditorViewModel.kt:341-348`) holds the six Material 2 ints with `DEFAULT = SWATCHES.first()`;
`themes.xml:8` parent is `android:Theme.Material.Light.NoActionBar`; `MainActivity` has no
`enableEdgeToEdge`; `Theme.kt` is `lightColorScheme()`/`darkColorScheme()` + `isSystemInDarkTheme()`;
`BackupImporter.parseAndValidate` (line 42) gates `formatVersion` only; `BackupMapper.kt:41` passes
`colorArgb` through unvalidated; `NotificationPoster.kt:93` is `.setColor(colorArgb)`; table `habits`,
column `colorArgb INTEGER NOT NULL`; `core/ui/theme/` holds only `Theme.kt`; no `res/values/colors.xml`
exists.

## Technical Approach

Three layers, in dependency order.

    core/ui/theme/  (tokens: colours, habit palette, type, shape, dimens)
          │                    │
          │                    └──→ core/ui/component/HabitColorDot.kt
          │                                    │
          ├──→ Theme.kt ──→ MainActivity ──→ every screen ──┘
          │
    core/data/migration/  (HabitColorRemap ── frozen v1→v2 map, pure Kotlin)
          │                    │
          ├──→ Migration_1_2 ──┤            (registered in core/di/DatabaseModule)
          │        │           │
          │        └──→ PreMigrationSnapshotWriter (work unit 3, failure-isolated)
          │                    │
          └──→ portability/BackupImporter ──┘  (same map, gated on schemaVersion < 2)

The theme layer is the lower layer and depends on nothing. The frozen remap depends on nothing and is
consumed by both the migration and the importer. No capability package is depended on by `core/`.

---

## Decision 1 — Token architecture and where the habit palette lives

**Choice.** Six files under `core/ui/theme/`, plus one shared component package.

| File | Owns | Visibility |
|---|---|---|
| `Color.kt` | `object ConstanzaColors`: `Background`, `Surface`, `SurfaceRaised`, `SurfaceSelected`, `Outline`, `Accent`, `OnAccent`, `OnBackground`, `OnBackgroundVariant`, `OnBackgroundMuted`. oklch in each KDoc, hex as the computed conversion | `internal` to `:app` |
| `HabitPalette.kt` | `enum class HabitColor(val argb: Int)` — RED, PINK, VIOLET, BLUE, TEAL, GREEN — plus `object HabitPalette { val ORDERED: List<HabitColor>; val ARGB: List<Int>; val DEFAULT: Int }` and, separately in the same file, `val HabitColor.composeColor: Color` | public |
| `Type.kt` | `ConstanzaTypography = Typography(...)` overriding only `titleLarge`, `titleMedium`, `bodyLarge`, `bodyMedium`, `bodySmall`, `labelLarge`, `labelMedium`. `FontFamily.Default` — size/weight/lineHeight/letterSpacing only | `internal` |
| `Shape.kt` | `ConstanzaShapes = Shapes(...)` | `internal` |
| `Dimens.kt` | `object Spacing` (xs 4, sm 8, md 12, lg 16, xl 24, xxl 32) and `object Dimens` (`HabitDot`, `HabitDotSlot`, `Swatch` 40, `SwatchBorder` 3) | public |
| `Theme.kt` | dark-only `ConstanzaTheme(content)` | public |
| `core/ui/component/HabitColorDot.kt` | the shared dot+halo composable (decision 6) | public |

**The habit palette lives in `core/ui/theme/`, split by type, not by file.** `HabitColor.argb: Int` is
the Compose-free spine; `HabitColor.composeColor` is a derived extension. `HabitEditorViewModel`
imports only `HabitPalette.ARGB` / `.DEFAULT` (`Int`), so **no Compose type crosses into a ViewModel**
and `HabitEditorUiState.colorArgb: Int` is unchanged.

**Rejected: keep the palette in `habit/`** (today's `object HabitColorPalette` inside
`HabitEditorViewModel.kt`). `ColorContrastTest` must measure habit colours against theme surfaces, so
`core/ui/theme/` would import a capability package — core depending on a feature, the wrong direction.
The palette is also read by two capabilities (editor, Today/list) and one test; single-owner placement
in the lower layer is the only arrangement where nobody reaches sideways.

**Rejected: move the palette to `:domain`.** `Habit.colorArgb: Int` is legitimately domain data, but
*which six values are offered* is a design-system decision bound to contrast floors and to a surface
colour, neither of which `:domain` can express (`kotlin("jvm")`, no Android types). It would put an
accessibility contract in a module that cannot name the background it is measured against.

**Rejected: a `Color`-typed palette consumed directly by the ViewModel.** Cheapest to write and the
smell the brief asks about: it would make `HabitEditorViewModel` — a JVM-unit-tested class — depend on
`androidx.compose.ui.graphics`, and would push `Color`→`Int` conversion into the save path where the
persisted representation is decided. The Int stays authoritative all the way to Room and to
`NotificationPoster.setColor()`.

**Note for apply.** `:app:detektMain` runs with `buildUponDefaultConfig = true` and no `MagicNumber`
entry in `config/detekt/detekt.yml`. Today's `HabitColorPalette` hex ints pass, which is the precedent —
but if the new token files do trip `MagicNumber`, the fix is `const val …_ARGB = 0xFF110B06.toInt()`
(constant declarations are exempt by default) with `Color(...)` derived from it. Not a `@Suppress`.

## Decision 2 — Spacing/dimension tokens: define them, convert almost nothing

**Choice.** Define `Spacing`/`Dimens` in unit 1. Then one rule for every `.dp` literal in the
~1,232 lines of screen code:

> **A `.dp` literal becomes a token only if its value changes, or if the code is new.**

**Not converting**, deliberately: every `.dp` literal whose number is unchanged by the tonal pass.
That is most of the 16/8/4/32 literals in `TodayScreen`, `HabitListScreen`, `ScheduleEditors`,
`ProgressScreen`, `SnoozeSettingsScreen`, `DataPortabilityScreen`.

**Rejected: convert all call sites.** A pure rename across six files, several hundred changed lines of
zero behavioural difference, inside a change already forecast at 2–3x its 700-line budget. It buys
consistency for a reader while spending review attention that the migration and the new Today
composables need more. Worse, it makes every screen diff mixed — a reviewer cannot tell a real padding
change from a mechanical substitution.

**Rejected: no tokens at all.** `SWATCH_SIZE = 40.dp` / `SWATCH_BORDER = 3.dp` already sit privately in
`HabitEditorScreen.kt:239-240`, and decision 6 adds a dot size the editor swatch and both list screens
must agree on. Without a shared home, that number gets copied three times.

The rule's payoff is that the changed-line count in units 4–6 stays proportional to the design change,
and every touched line means something.

## Decision 3 — Migration shape

**Choice.** `core/data/migration/HabitColorRemap.kt` — a pure Kotlin object, no Android, no Compose:

```kotlin
/** FROZEN. The v1→v2 mapping, with both sides as literal ints on purpose — see rationale. */
internal object HabitColorRemap {
    val LEGACY_TO_CURRENT: Map<Int, Int> = mapOf(/* teal→teal, blue→blue, red→red,
        purple→violet, green→green, orange→PINK */)
    fun normalize(argb: Int): Int = LEGACY_TO_CURRENT[argb] ?: argb
}
```

**Both sides are literal ints, not `HabitColor.VIOLET.argb`.** A migration is a frozen historical
artifact: if a future v2→v3 re-tone changes `HabitColor.VIOLET`, a palette-referencing
`Migration(1,2)` would silently start writing a value that never existed at version 2, and
`AppDatabaseMigrationTest` would still pass while the two migrations disagreed about what v2 means.
A JVM test asserts every right-hand value is a current `HabitPalette` member, so the freeze cannot
drift silently while the palette is current.

**SQL shape: one parameterized `CASE` statement.**

```kotlin
db.execSQL(
    "UPDATE habits SET colorArgb = CASE colorArgb " +
        "WHEN ? THEN ? ".repeat(6) + "END WHERE colorArgb IN (?,?,?,?,?,?)",
    bindArgs, // 12 mapping ints + the 6 legacy keys, all from LEGACY_TO_CURRENT
)
```

Two reasons the args are **bound, never inlined**. First, `0xFF8E24AA.toInt()` is the *negative*
`-7461206` in the column, while `0xFF8E24AA` written inside SQL is parsed by SQLite as `4287103146`
and matches nothing — a migration that runs green and rewrites zero rows. Binding lets Kotlin's
`Int` conversion be the only place the sign is decided. Second, the map becomes the single source: no
number is hand-transcribed into SQL at all.

**Rejected: six sequential `UPDATE … WHERE colorArgb = ?` statements.** They are safe here only because
the legacy and current int sets happen to be disjoint (checked: `00897B/1E88E5/E53935/FB8C00/8E24AA/
43A047` vs `FF9FA8/FFA8DC/CBB2FF/8FC5FF/5DD6C7/8BDB95`), so no statement can re-map a row a previous
one already wrote. A single `CASE` is collision-proof by construction rather than by an accident of the
chosen palette, and it is one table scan instead of six.

**Unmapped values.** `WHERE colorArgb IN (…)` means a non-palette int is not merely left equal — it is
never written. Never destructive, as the proposal requires, and cheap to assert.

**Two structurally identical schema JSONs.** The palette change is data, not schema, so
`…AppDatabase/2.json` will differ from `1.json` only in `"version"`; `identityHash`
(`5adafec4244c3539d5378634993b6649`) is unchanged. Room permits this: it requires a `Migration(1,2)`
to *exist* for the version step, and post-migration validation compares the live schema against
`2.json`, which passes trivially when nothing structural moved. Room's own open helper rewrites
`room_master_table`'s identity hash after migrating. **This is asserted, not trusted**:
`runMigrationsAndValidate(TEST_DB_NAME, 2, true, MIGRATION_1_2)` in the extended
`AppDatabaseMigrationTest` is what proves it, which is precisely why that test cannot be split from the
migration.

`DatabaseModule.provideAppDatabase` gains `.addMigrations(AppMigrations.MIGRATION_1_2)` (correction C4).

## Decision 4 — Failure isolation for the 7.5 pre-migration snapshot

This is the most dangerous path in the change. A throw inside `migrate()` rolls the migration back and
leaves a database the shipped app can never open again — the data is not corrupted, but it is
unreachable, and the app crash-loops on launch. The design below exists entirely to make that
impossible.

**Choice.** `core/data/migration/PreMigrationSnapshotWriter.kt`:

```kotlin
internal class PreMigrationSnapshotWriter(private val targetDir: File) {
    /** Never throws for any recoverable cause. Returns false when no snapshot was written. */
    fun write(db: SupportSQLiteDatabase): Boolean = try {
        writeOrThrow(db); true
    } catch (e: Exception) {          // SQLiteException, IOException, RuntimeException
        Log.w(TAG, "pre-migration snapshot skipped", e)
        temp.delete(); false
    }
}
```

Five properties, each load-bearing:

1. **First statement in `migrate(db)`**, before the `UPDATE`. The only correct placement `config.yaml`
   item 7.5 identified, and the only one where a "before" state still exists.
2. **Catches `Exception`, not `Throwable`.** `Error` — `OutOfMemoryError` above all — is allowed to
   propagate. Swallowing a `VirtualMachineError` to keep writing to a database mid-migration is worse
   than the abort: Room's migration transaction rolls back cleanly and the file stays a valid v1.
   Stated as a deliberate line, not an omission.
3. **Temp file plus atomic rename.** Writes `pre-migration-v1.sql.tmp` in
   `filesDir/pre-migration/`, renames to `pre-migration-v1.sql` only after the last row. A partial dump
   can never masquerade as a complete snapshot, and the temp file is deleted on the failure path.
4. **Streamed row by row** through a `BufferedWriter`, every `Cursor` in `use {}`. Nothing accumulates
   in memory, so a large database degrades in time rather than in `OutOfMemoryError`.
5. **Fixed filename, no clock.** No `System.currentTimeMillis()` — that call is banned by
   `config/detekt/detekt.yml`'s `ForbiddenMethodCall` and the project's `TimeProvider` convention, and
   injecting a `TimeProvider` into a Room `Migration` to name a file that is written exactly once per
   install is ceremony. Also gives the file a single, documentable path.

**Format: replayable SQL.** `sqlite_master.sql` verbatim for each table, then one `INSERT INTO` per row
with values escaped by `Cursor.getType()` (`NULL`, numeric verbatim, `''`-doubled text, `x'…'` blobs).
Skips `sqlite_%`, `sqlite_sequence`, `android_metadata` and `room_master_table` — replaying a stale
identity hash would be actively harmful. Uses the `db` handed to `migrate()`; never opens a second
connection.

**Rejected: dump as JSON.** The app's own JSON is the *backup* shape, not a table dump, so a JSON dump
would be neither replayable by `sqlite3` nor importable by `BackupImporter` — the worst of both. SQL is
useful to exactly the audience that will ever read this file.

**How failure surfaces to the user: it does not.** Logged at `WARN`, nothing else. No toast, no
notification, no persisted flag, no crash — success is equally silent. Three reasons: nothing the user
could do mid-migration would help; a warning *after* a successful migration would frighten without
offering a remedy; and this artifact is honestly a forensic last resort for a support conversation
(the user cannot run `sqlite3` on the phone), not a self-service restore. **Rejected:** a DataStore
`snapshotFailed` flag surfaced on `DataPortabilityScreen`. Named here as the follow-up if 7.5 is ever
extended into a real recovery UI; it is scope inflation while no recovery UI exists.

## Decision 5 — Rollback, and where it is written down

Rollback after unit 2 is **version-shaped, not commit-shaped**. Reverting `AppDatabase` to
`version = 1` makes Room refuse to open an already-upgraded file: the user's data becomes unreachable.
The real exit is to roll *forward*.

| State | Exit |
|---|---|
| Before unit 2 merges | Revert the theme units. No persisted data was touched |
| After unit 2 | Ship `Migration(2, 3)` inverting `LEGACY_TO_CURRENT` (a bijection, so it inverts exactly) and revert the palette the picker offers. Unmapped ints were never written, so they need no inverse |
| Unit 3 only | Revert its single commit; `Migration(1,2)` survives untouched — the reason it is a separate commit |
| Unit 1 chrome | `themes.xml`, `colors.xml` and `MainActivity` revert independently of everything else |

**Written down in the file someone reverting will actually open**: a KDoc block on
`AppMigrations.MIGRATION_1_2` stating the inverse recipe, mirrored here. **Rejected: design.md only.**
This change exists partly because `carried_forward_open_items` had to be invented for exactly that
failure — the archived change folder nobody browses. **Rejected: a new `docs/ROLLBACK.md`** — no such
convention exists in this repo, and a lone doc file is the same forgotten-artifact failure with a new
path.

## Decision 6 — Habit colour as identity on Today and the habit list

**Choice.** One shared composable, `core/ui/component/HabitColorDot.kt`: a `Box` of
`Dimens.HabitDotSlot` (24.dp) carrying a circle of `Color(argb).copy(alpha = 0.16f)` — the tinted halo
— with a solid `Dimens.HabitDot` (12.dp) circle of `Color(argb)` centred inside it.

| Screen | Placement | Height impact |
|---|---|---|
| `HabitListScreen.HabitRow` | `ListItem(leadingContent = { HabitColorDot(habit.colorArgb) })` | **None.** M3's `ListItem` already reserves a leading slot sized for a 24dp icon inside its 56/72dp minimum height |
| `TodayScreen.HabitRollupRow`, multi-slot branch | same `ListItem(leadingContent = …)` | **None**, same reason |
| `TodayScreen.HabitRollupRow`, single-slot branch | the name `Text` becomes a `Row(verticalAlignment = CenterVertically)` with the dot | ~0 — `bodyLarge` line height is already ≈24dp |
| `HabitEditorScreen.ColorSwatchRow` | unchanged shape, `SWATCH_SIZE`/`SWATCH_BORDER` move to `Dimens` | none |

**Using `leadingContent` rather than prepending a `Row` is the whole geometry argument.** It is why the
tonal pass can add habit identity to two list screens without moving a single measured row boundary.

**`TodayAdaptiveComposeTest` at `sw = 600dp` is structurally safe here, and that is verified rather
than hoped.** Its assertion is `morningBounds.bottom <= eveningBounds.top` between two *slot* time
labels inside one expanded habit. The dot lands on the habit *header*, so even if the header grew, both
slot labels shift down together and the inequality holds. The assertion only breaks if the two slot
rows are reflowed side by side — which nothing here does, and which `ui-adaptive-layout`'s
single-responsive-layout rule forbids anyway. Separately: that test uses `createComposeRule()`, not
`createAndroidComposeRule<MainActivity>()`, so unit 1's `enableEdgeToEdge` never executes in it.

**State plumbing (correction C3).** `TodayHabitRow` gains `colorArgb: Int` **with no default value**,
and `buildTodayHabitRow` fills it from the `Habit` it already holds. 8 positional call sites across
`TodayModel.kt` (3), `TodayViewModel.kt` (1) and `TodayViewModelTest.kt` (4) get updated in the same
unit. **Rejected: a default of `HabitPalette.DEFAULT`** — it keeps fixtures compiling and would let a
forgotten mapping render every habit in the same colour while every test still passed. The compiler
should be the one to notice.

**Accessibility.** The dot carries no `contentDescription`. Colour is a *secondary* recognition
channel; the habit name is the primary label and is already there, so announcing the colour would add
screen-reader noise without adding information. This is also why the change never makes colour the
only channel for anything.

## Decision 7 — Tonal surfaces: exactly one new structural component

`explore.md` found zero `Card`/`Surface`/`Divider` call sites in the whole UI tree, so the tonal
direction is structural work rather than a retint. The temptation is a `Card` per list row. Rejected.

**Choice.** Exactly **one** new structural surface: a `Surface` wrapping `TodayScreen.ExactAlarmBanner`
(tonal container, `ConstanzaColors.SurfaceRaised`, `ConstanzaShapes.medium`). Everything else is
token-driven, structure-preserving:

| Surface | Mechanism | Structural change |
|---|---|---|
| `ExactAlarmBanner` | new `Surface` + `Shape` + padding | yes — one site |
| every `ListItem` row | `ListItemDefaults.colors(containerColor = …)` | none |
| `TopAppBar` | `TopAppBarDefaults.topAppBarColors(...)` | none |
| `Scaffold` | `containerColor = ConstanzaColors.Background` | none |
| selection states (swatch border, radio, switch) | `colorScheme.primary` = accent, already read at `HabitEditorScreen.kt:246` | none |

**Rejected: `Card` (or `Surface`) per `LazyColumn` row.** It is the largest possible diff — every
`ListItem` becomes nested — and it changes each repeated row's measured height and padding, which is
exactly the geometry decision 6 spent its budget keeping still. A row's tonal separation is achievable
through `ListItemDefaults.colors` with no structural change at all.

**Rejected: `HorizontalDivider` between rows.** Adds per-row height for the same visual job the tonal
container colour already does, on the one screen with a geometry-sensitive test.

`ExactAlarmBanner` earns its exception because it is a single `LazyColumn` `item`, not a repeated row;
because a banner without a container does not read as a banner; and because its `weight(1f)` fix
(`TodayScreen.kt:132-136`, found by a manual device matrix) must survive the change — the `Surface`
wraps the existing `Row`, it does not replace it.

## File Changes

| File | Action | Description |
|---|---|---|
| `core/ui/theme/Color.kt` | Create | Warm neutral ramp + accent; oklch in KDoc, hex computed |
| `core/ui/theme/HabitPalette.kt` | Create | `HabitColor` enum (argb spine) + `HabitPalette` + `composeColor` |
| `core/ui/theme/Type.kt` | Create | Type scale; `FontFamily.Default`, no packaged font |
| `core/ui/theme/Shape.kt` | Create | `ConstanzaShapes` |
| `core/ui/theme/Dimens.kt` | Create | `Spacing` + `Dimens` |
| `core/ui/theme/Theme.kt` | Modify | Dark-only: drop `lightColorScheme`, `darkTheme` param, `isSystemInDarkTheme()` |
| `core/ui/component/HabitColorDot.kt` | Create | Dot + tinted halo |
| `res/values/colors.xml` | Create | `window_background` = `ConstanzaColors.Background` |
| `res/values/themes.xml` | Modify | Dark `NoActionBar` parent + `android:windowBackground` |
| `core/ui/MainActivity.kt` | Modify | `enableEdgeToEdge(SystemBarStyle.dark(...))` for both bars |
| `app/src/test/.../core/ui/theme/ColorContrastTest.kt` | Create | Contrast floors as JVM assertions |
| `core/data/migration/HabitColorRemap.kt` | Create | Frozen v1→v2 map + `normalize()` |
| `core/data/migration/AppMigrations.kt` | Create | `MIGRATION_1_2`, KDoc carrying the rollback recipe |
| `core/data/migration/PreMigrationSnapshotWriter.kt` | Create | Unit 3 — failure-isolated dump |
| `core/data/AppDatabase.kt` | Modify | `version = 2` |
| `core/di/DatabaseModule.kt` | Modify | `.addMigrations(...)`; pass `filesDir` for unit 3 |
| `app/schemas/com.jjrapps.constanza.core.data.AppDatabase/2.json` | Create | Generated, committed (C1) |
| `habit/HabitEditorViewModel.kt` | Modify | Delete `HabitColorPalette`; point at `HabitPalette` |
| `habit/HabitEditorScreen.kt` | Modify | Swatch sizes to `Dimens`; tonal pass (unit 5) |
| `habit/ScheduleEditors.kt` | Modify | Tonal pass (unit 5) |
| `portability/BackupDto.kt` | Modify | `CURRENT_SCHEMA_VERSION = 2`, drop `private` (C5) |
| `portability/BackupImporter.kt` | Modify | Gate on `schemaVersion < 2` → `HabitColorRemap.normalize` |
| `tracking/TodayModel.kt`, `TodayViewModel.kt` | Modify | `TodayHabitRow.colorArgb` (C3) |
| `tracking/TodayScreen.kt` | Modify | Dot in both branches; tonal banner `Surface` |
| `habit/HabitListScreen.kt` | Modify | Dot via `leadingContent` |
| `progress/ProgressScreen.kt`, `reminding/SnoozeSettingsScreen.kt`, `portability/DataPortabilityScreen.kt` | Modify | Tonal pass (unit 6) |
| `app/src/test/.../HabitColorRemapTest.kt`, `.../PreMigrationSnapshotWriterTest.kt`, `.../BackupImporterNormalizationTest.kt` | Create | JVM |
| `app/src/androidTest/.../core/data/AppDatabaseMigrationTest.kt` | Modify | Extend the existing harness (C2) |
| `app/src/androidTest/.../HabitColorDotComposeTest.kt` | Create | Dot present on Today and the list |

## Decision 8 — Work-unit boundaries, forecasts, dependency order

The proposal's six units are **confirmed**, with revised forecasts and one sequencing change.

| # | Unit | Forecast | Proposal said | Why revised |
|---|---|---|---|---|
| 1 | Theme foundation: 5 new token files, dark-only `Theme.kt`, `colors.xml`, `themes.xml`, `enableEdgeToEdge`, `ColorContrastTest` | **300–430** | 250–350 | Six token files + a 13-assertion contrast test measured larger than the estimate |
| 2 | Palette + data: `HabitColorRemap`, `MIGRATION_1_2`, `version = 2`, `2.json`, `addMigrations`, picker swap, import normalization, `CURRENT_SCHEMA_VERSION = 2`, all three data tests | **250–380** | 250–350 | Confirmed. The UI 2x multiplier is **not** applied here — it was measured on UI units, and applying it to migration code would misread the evidence |
| 3 | 7.5 snapshot, failure-isolated, on top of unit 2 | **120–200** | 120–180 | Confirmed |
| 4 | Habit colour identity: `HabitColorDot`, `TodayHabitRow.colorArgb` + 8 call sites, both list screens, tonal `ExactAlarmBanner` | **320–460** | 320–460 | Confirmed |
| 5 | `HabitEditorScreen` + `ScheduleEditors` tonal pass | **360–520** | 360–520 | Confirmed |
| 6 | `ProgressScreen`, `SnoozeSettingsScreen`, `DataPortabilityScreen` tonal pass | **130–210** | 130–210 | Confirmed |

Total **1,480–2,200** against a 700-line budget. `delivery_strategy = auto-chain`.

**Sequencing change vs the proposal.** `HabitPalette.kt` (the definition) lands in **unit 1**, because
`ColorContrastTest` cannot assert the floors without it. But the **consumption** swap — deleting
`HabitColorPalette` and pointing `HabitEditorViewModel` at `HabitPalette` — stays in **unit 2**,
alongside the migration, in the same commit. Reason: if the picker changed before the migration
shipped, a habit created between the two merges would carry a new-palette int that the already-run
`Migration(1,2)` never sees, and vice versa a habit created after the migration but before the picker
swap would get a legacy int nothing will ever rewrite. Picker and persisted data must move together.

**Dependency edges.** `1 → 2`, `1 → 4`, `1 → 5`, `1 → 6`, `2 → 3`, `4 → 5` (both touch the dot
language; 4 defines it). `5` and `6` are independent of each other.

**Must not be split.** Unit 2's `MIGRATION_1_2` + `HabitColorRemap` + `HabitColorRemapTest` +
the extended `AppDatabaseMigrationTest` + `2.json` + `version = 2` are **one commit**. A commit with
`version = 2` and no committed schema, or with a migration and no test, is a commit that bricks a
device on checkout.

**Must be separate.** Unit 3 is its own commit so it reverts alone (rollback row 3).

**Chained PRs.**

| PR | Units | Forecast |
|---|---|---|
| A | 1 | 300–430 |
| B | 2 + 3 | 370–580 |
| C | 4 | 320–460 |
| D | 5 | 360–520 |
| E | 6 | 130–210 |

D and E are separate because `5 + 6` reaches 730 at the high end. If unit 5's actual lands under 400,
E may fold into D.

**The measured control, restated as an instruction for `sdd-tasks`/`sdd-apply`.** In
`habit-tracking-mvp` every UI work unit roughly doubled its own forecast, and the single most effective
countermeasure was an explicit *stop and report* threshold. So: **each unit stops and reports on
crossing the top of its own forecast range above**, rather than finishing and presenting the overrun.
Units 4 and 5 are where this has historically fired.

**Archive note (surfaced by `sdd-spec`).** The archived `habit-tracking-mvp` change's `specs/` folders
are full spec copies, not `ADDED`/`MODIFIED` deltas, so this repo has **no precedent for applying a
delta**. This change's `data-portability` and `habit-management` deltas will need the archive step to
merge them into `openspec/specs/**` by hand and carefully. Not this design's artifact; recorded so
`sdd-archive` does not discover it cold.

## Decision 9 — Verification strategy

| Layer | What it proves | Command / mechanism |
|---|---|---|
| JVM unit | Every habit colour and the accent ≥ **4.5:1** against **both** `Background` and `SurfaceSelected` (14 assertions), plus the text tokens. WCAG relative-luminance helper ported from the sibling app's `ColorContrastTest` | `./gradlew :app:testDebugUnitTest` |
| JVM unit | Remap is a bijection (6 distinct in, 6 distinct out); orange→pink specifically; an unmapped int passes through unchanged; every target is a current `HabitPalette` member (guards the freeze) | same |
| JVM unit | Import gate: `schemaVersion = 1` normalizes, `schemaVersion = 2` leaves colours byte-identical (round-trip fidelity) | same |
| JVM unit | **Snapshot failure isolation** — a `SupportSQLiteDatabase` mocked (MockK) to throw does not propagate out of `write()`, and leaves no `pre-migration-v1.sql`. The cheapest possible place to prove the most dangerous property | same |
| Instrumented | The real `Migration(1,2)` against the checked-in `1.json`: seed all six legacy ints plus one unmapped int, then `runMigrationsAndValidate(TEST_DB_NAME, 2, true, MIGRATION_1_2)`; assert the six map, the unmapped survives, and the snapshot file exists containing the pre-migration rows. **Extends** `AppDatabaseMigrationTest.kt` | `./gradlew :app:connectedDebugAndroidTest` |
| Instrumented | The dot renders on Today and the habit list; `TodayAdaptiveComposeTest` still green at `sw = 600dp`; existing `HabitEditor*`/`HabitList*` suites unaffected | same |
| Aggregate | `:app:detektMain` clean (`MagicNumber` in the new token files is the one thing to watch) | `./gradlew check` |
| **Physical device only** | No white cold-start flash; system-bar icons legible with the device in light mode; one posted reminder shows the migrated accent (`NotificationPoster.setColor`); the dot at `sw ≥ 600dp` and across a fold/unfold configuration change | Manual — **Pixel 10 (API 37)** and **Galaxy Z Fold 7 (SM-F966B, API 36)** |

**Device gotcha, recorded so nobody debugs code instead.** `IllegalStateException: No compose
hierarchies found in the app` on either physical device means **no Activity resumed** — keyguard up or
the device dozing — not a Compose or dependency fault. Check `adb shell dumpsys window | rg
isKeyguardShowing` first; the Pixel is PIN-protected so `adb shell wm dismiss-keyguard` cannot clear
it, and `adb shell svc power stayon usb` keeps it unlocked while plugged in. Already documented at
`app/build.gradle.kts:178-186`.

Nothing here is a cold-start *automated* assertion: the first-frame window background cannot be
asserted by an instrumented Compose test, because the test harness launches its own
`ComponentActivity`, not `MainActivity` with `Theme.Constanza`. Stated rather than papered over.

## Threat Matrix

**N/A** — this change introduces no routing, shell command, subprocess, VCS/PR automation,
executable-file classification, or process integration.

One adjacent boundary, noted because it is genuinely untrusted input rather than dismissed: import
reads `colorArgb` from a user-selected file (`BackupMapper.kt:41`, historically unvalidated). This
change narrows it — `HabitColorRemap.normalize()` is a total `Map` lookup over `Int` with an identity
fallback, so no file content can produce a value outside `{legacy six → current six} ∪ {input}`, and
no code path derives control flow, a path, or a command from it.

## Migration / Rollout

Single mandatory `Migration(1, 2)`, applied lazily by Room at first open after upgrade (see
decisions 3, 4, 5). No feature flag: dark-only is ratified, and a `darkTheme` seam would be dead code
by construction. No phased rollout — `versionCode = 1`, no release channel exists yet.

## Open Questions

- [ ] **The 7.5 snapshot has no spec requirement** (correction C6). Unit 3 ships behaviour
      `sdd-verify` has nothing to verify against, and `config.yaml`'s `carried_forward_open_items`
      instruction is to close item 7.5 *explicitly*. Either `sdd-spec` adds a requirement to the
      `data-portability` delta, or the archive step closes 7.5 on design + test evidence alone. Needs a
      decision before archive, not before apply.
- [ ] `MagicNumber` behaviour on the new token files under `:app:detektMain` is precedent-backed
      (today's hex `SWATCHES` pass) but not proven for six new files. Unit 1 finds out; the fallback is
      already chosen (decision 1's note), so this is not a design fork.
