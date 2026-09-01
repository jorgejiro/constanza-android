# Tasks: Warm-Dark Design System

Deviation note: this artifact exceeds the skill's 530-word soft budget. Justified by explicit
orchestrator instructions for this change — full promise-to-task traceability, per-unit stop
thresholds, chain mapping, and concrete verification commands are mandatory here because the prior
change (`habit-tracking-mvp`) shipped 10 unowned promises. Brevity was sacrificed for coverage.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 1,480–2,200 (design decision 8; measured 2x multiplier on UI units carried forward from `habit-tracking-mvp`) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | Tracker → PR A (unit 1) → PR B (units 2+3) → PR C (unit 4) → PR D (unit 5) → PR E (unit 6) |
| Delivery strategy | auto-chain |
| Chain strategy | feature-branch-chain |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

**Measured history applied**: every UI work unit in `habit-tracking-mvp` roughly doubled its forecast; non-UI (migration/data) units tracked their forecast closely. Units 4 and 5 here are UI-heavy and are where an overrun is most likely to recur.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| 1 | Theme foundation: tokens, dark-only `Theme.kt`, cold-start chrome, contrast test | PR A | `./gradlew :app:testDebugUnitTest --tests "*.ColorContrastTest"` | Manual — no automated harness reaches pre-Compose window (see "Cannot Be Automatically Verified") | Revert `core/ui/theme/*`, `colors.xml`, `themes.xml`, `MainActivity.kt`; no persisted data touched |
| 2 | Palette + migration: `HabitColorRemap`, `MIGRATION_1_2`, `version=2`, `2.json`, `addMigrations`, picker swap, import gate | PR B (part 1) | `./gradlew :app:testDebugUnitTest --tests "*.HabitColorRemapTest" --tests "*.BackupImporterNormalizationTest"` | `./gradlew :app:connectedDebugAndroidTest --tests "*.AppDatabaseMigrationTest"` | Ship `Migration(2,3)` inverting the bijection; never downgrade `version` |
| 3 | 7.5 pre-migration snapshot, failure-isolated | PR B (part 2, separate commit) | `./gradlew :app:testDebugUnitTest --tests "*.PreMigrationSnapshotWriterTest"` | `./gradlew :app:connectedDebugAndroidTest --tests "*.AppDatabaseMigrationTest"` | Revert this single commit only; `MIGRATION_1_2` survives untouched |
| 4 | Habit colour identity: dot, Today state plumbing, both list screens, tonal banner | PR C | `./gradlew :app:testDebugUnitTest --tests "*.TodayViewModelTest"` | `./gradlew :app:connectedDebugAndroidTest --tests "*.HabitColorDotComposeTest" --tests "*.TodayAdaptiveComposeTest"` | Revert `HabitColorDot.kt`, the 8 call sites, `TodayScreen`/`HabitListScreen` dot wiring |
| 5 | `HabitEditorScreen` + `ScheduleEditors` tonal pass | PR D | `./gradlew :app:testDebugUnitTest` (regression) | `./gradlew :app:connectedDebugAndroidTest` (existing `HabitEditor*` suites) | Revert the two files; no data/schema touched |
| 6 | `ProgressScreen`, `SnoozeSettingsScreen`, `DataPortabilityScreen` tonal pass | PR E | `./gradlew check` (aggregate) | Manual device matrix (final gate) | Revert the three files independently |

## PR Chain — Feature Branch Chain

- **Tracker branch**: `feat/warm-dark-design-system` — draft PR targeting `main`, never merged until every child is integrated.
- **PR A**: `feat/warm-dark-theme-foundation` → base `feat/warm-dark-design-system` — Unit 1. 📍 current unit.
- **PR B**: `feat/warm-dark-palette-migration` → base `feat/warm-dark-theme-foundation` — Units 2+3.
- **PR C**: `feat/warm-dark-habit-color-identity` → base `feat/warm-dark-palette-migration` — Unit 4.
- **PR D**: `feat/warm-dark-editor-tonal-pass` → base `feat/warm-dark-habit-color-identity` — Unit 5.
- **PR E**: `feat/warm-dark-remaining-tonal-pass` → base `feat/warm-dark-editor-tonal-pass` — Unit 6. Only after E lands does the tracker PR merge to `main`.

```
main
 └─ feat/warm-dark-design-system            (tracker, draft, no-merge)
     └─ feat/warm-dark-theme-foundation      (PR A · Unit 1) 📍
         └─ feat/warm-dark-palette-migration     (PR B · Units 2+3)
             └─ feat/warm-dark-habit-color-identity  (PR C · Unit 4)
                 └─ feat/warm-dark-editor-tonal-pass     (PR D · Unit 5)
                     └─ feat/warm-dark-remaining-tonal-pass  (PR E · Unit 6)
```

D and E may fold into one PR if unit 5's actual lands under 400 lines (design decision 8); do not
merge a fold decision silently — record it in PR D's description if it happens.

## Stop-and-Report Thresholds

Each unit stops and reports remaining scope on **crossing the top of its own forecast**, rather than
finishing and presenting the overrun. This was the single most effective control in
`habit-tracking-mvp` (5 overshoots converted into decisions, 2 clean in-budget stops).

| Unit | Stop at (changed lines) | Historically high-risk? |
|---|---|---|
| 1 | 430 | No (non-UI-dominant) |
| 2 | 380 (own commit); PR B cumulative stop at 580 with unit 3 | No |
| 3 | 200 (own commit, on top of unit 2) | No |
| 4 | 460 | **Yes** — UI unit |
| 5 | 520 | **Yes** — UI unit, largest single forecast |
| 6 | 210 | No |

## Unit 1 — Theme Foundation (PR A)

- [x] 1.1 Create `core/ui/theme/Color.kt`: `object ConstanzaColors` (`Background`, `Surface`, `SurfaceRaised`, `SurfaceSelected`, `Outline`, `Accent`, `OnAccent`, `OnBackground`, `OnBackgroundVariant`, `OnBackgroundMuted`); oklch in KDoc per Engram #47, hex as computed conversion. **Deviation**: file created as `ConstanzaColors.kt`, not `Color.kt` — detekt's `MatchingDeclarationName` rule (active by default, not anticipated by design.md) requires the file name to match its single top-level declaration. Same directory, same object name, mechanical rename only.
- [x] 1.2 Create `core/ui/theme/HabitPalette.kt`: `enum class HabitColor(val argb: Int)` (RED, PINK, VIOLET, BLUE, TEAL, GREEN — the ratified `#FF9FA8/#FFA8DC/#CBB2FF/#8FC5FF/#5DD6C7/#8BDB95`), `object HabitPalette { ORDERED; ARGB; DEFAULT }`, and `val HabitColor.composeColor: Color`. `argb: Int` is the Compose-free spine — no `androidx.compose.ui.graphics` import reaches `HabitEditorViewModel`.
- [x] 1.3 Create `core/ui/theme/Type.kt`: `ConstanzaTypography` overriding `titleLarge/titleMedium/bodyLarge/bodyMedium/bodySmall/labelLarge/labelMedium`, `FontFamily.Default` (no packaged font).
- [x] 1.4 Create `core/ui/theme/Shape.kt`: `ConstanzaShapes`.
- [x] 1.5 Create `core/ui/theme/Dimens.kt`: `object Spacing` (xs/sm/md/lg/xl/xxl) and `object Dimens` (`HabitDot` 12dp, `HabitDotSlot` 24dp, `Swatch` 40dp, `SwatchBorder` 3dp).
- [x] 1.6 Modify `core/ui/theme/Theme.kt`: dark-only `ConstanzaTheme(content)`. Drop `lightColorScheme()`, the `darkTheme` param, `isSystemInDarkTheme()`. Satisfies spec `Dark-Only Rendering`.
- [x] 1.7 Create `res/values/colors.xml`: `window_background` = `ConstanzaColors.Background` hex.
- [x] 1.8 Modify `res/values/themes.xml`: dark `NoActionBar` parent, `android:windowBackground` → `window_background`. Satisfies spec `Cold-Start Window Background`.
- [x] 1.9 Modify `core/ui/MainActivity.kt`: `enableEdgeToEdge(SystemBarStyle.dark(...))` for both status and navigation bars. Satisfies spec `System-bar icon appearance`.
- [x] 1.10 Create `app/src/test/kotlin/.../core/ui/theme/ColorContrastTest.kt`: WCAG relative-luminance helper (ported from sibling app `sleep-noise-android`); assert all 6 habit colours + accent ≥4.5:1 against `Background` AND `SurfaceSelected` (14 assertions) + text tokens. Satisfies spec `Habit Colour And Accent Contrast Floor` + `Contrast Floors Asserted By Automated Test`.
- [x] 1.11 Explicit boundary: do NOT touch `HabitEditorViewModel.kt`'s existing `HabitColorPalette` in this unit — the consumption swap is Unit 2, alongside the migration (design decision 8 sequencing rule). Confirmed untouched.
- [x] 1.12 Verify: `./gradlew :app:testDebugUnitTest --tests "*.ColorContrastTest"` green; `./gradlew :app:detektMain` clean (watch `MagicNumber` on the 5 new token files; fallback is `const val …_ARGB = 0xFF…toInt()`, not `@Suppress`, per design decision 1). Both green — see apply-progress.md for exact numbers.
- [ ] 1.13 **Cannot be automatically verified — manual device check only**: no white cold-start flash, system-bar icons legible with device set to light mode. Run on Pixel 10 (API 37) and Galaxy Z Fold 7 (SM-F966B, API 36). No automated assertion exists because the Compose test harness launches its own `ComponentActivity`, not `MainActivity` with `Theme.Constanza` (design decision 9, stated explicitly rather than papered over).

## Unit 2 — Palette + Data Migration (PR B, part 1 — ONE COMMIT)

Tasks 2.1–2.11 land as a **single commit**. Design's rule: a commit with `version=2` and no committed
schema, or a migration and no test, bricks a device on checkout.

- [x] 2.1 Create `core/data/migration/HabitColorRemap.kt`: `internal object HabitColorRemap` with `LEGACY_TO_CURRENT: Map<Int, Int>` — **both sides literal ints, never `HabitColor.X.argb`** (a frozen historical artifact must not drift if the palette re-tones later). Six entries: teal→teal, blue→blue, red→red, purple(`0xFF8E24AA`)→violet, green→green, orange(`0xFFFB8C00`)→pink. `fun normalize(argb: Int): Int`.
- [x] 2.2 Create `core/data/migration/AppMigrations.kt`: `MIGRATION_1_2`. **SQL shape: one parameterized `CASE colorArgb WHEN ? THEN ? … END WHERE colorArgb IN (?,?,?,?,?,?)`, all 12+6 args BOUND from `HabitColorRemap.LEGACY_TO_CURRENT`, never inlined as hex literals.** The sign trap: `0xFF8E24AA.toInt()` is `-7461718` in the column, but `0xFF8E24AA` written inside SQL text parses as `4287505578` — an inlined `CASE` compiles, runs, reports success, and rewrites zero rows. KDoc on `MIGRATION_1_2` carries the rollback recipe: `Migration(2,3)` inverting the bijection (design decision 5).
- [x] 2.3 Modify `core/data/AppDatabase.kt`: `version = 2`.
- [x] 2.4 Modify `core/di/DatabaseModule.kt`: `.addMigrations(AppMigrations.MIGRATION_1_2)` on the `Room.databaseBuilder(...)` call. **Hard blocker (C4)** — without this, Room throws `IllegalStateException` at first open on every existing install.
- [x] 2.5 Build to generate, then commit `app/schemas/com.jjrapps.constanza.core.data.AppDatabase/2.json` — **corrected path (C1)**, NOT `app/schemas/2.json`. `identityHash` unchanged from `1.json` (data-only change).
- [x] 2.6 Modify `habit/HabitEditorViewModel.kt`: delete `object HabitColorPalette`; import `HabitPalette.ARGB`/`.DEFAULT` (`Int` only — no Compose type). Picker now offers exactly the six current-palette colours. Satisfies spec `Habit Colour Palette` and `Accent Reserved For Chrome` (accent hue absent from the offered six).
- [x] 2.7 Modify `portability/BackupDto.kt`: `CURRENT_SCHEMA_VERSION = 2`, drop `private` (C5) so the importer can gate on it.
- [x] 2.8 Modify `portability/BackupImporter.kt`: gate on `schemaVersion < 2` → apply `HabitColorRemap.normalize()` to every imported `colorArgb`; `schemaVersion == 2` passes colours through unchanged. Satisfies spec `Backup Schema Version Read On Import` + `Legacy Habit Colour Normalized On Import`.
- [x] 2.9 Create `app/src/test/kotlin/.../HabitColorRemapTest.kt`: bijection (6 distinct in, 6 distinct out); orange→pink specifically; an unmapped int passes through unchanged; every right-hand value is a current `HabitPalette` member (freeze-drift guard).
- [x] 2.10 Create `app/src/test/kotlin/.../BackupImporterNormalizationTest.kt`: `schemaVersion=1` normalizes off-palette colours; `schemaVersion=2` leaves colours byte-identical (round-trip fidelity case). Satisfies spec `Round-Trip Fidelity` (MODIFIED) scenario "Current-version round trip preserves colour exactly".
- [x] 2.11 **Extend** (do not create — C2) `app/src/androidTest/kotlin/.../core/data/AppDatabaseMigrationTest.kt`: seed all six legacy ints plus one unmapped int into a v1 database built from the checked-in `1.json`; run `runMigrationsAndValidate(TEST_DB_NAME, 2, true, MIGRATION_1_2)`. **Assert the actual post-migration row VALUES equal the expected remapped ints — not merely that the migration completed** (a green-but-no-op migration from the sign trap would still pass a completion-only assertion). Assert the unmapped int survives unchanged. Satisfies spec `Persisted Habit Colour Stays On-Palette Across A Palette Change`.
- [x] 2.12 Fix `openspec/config.yaml:216` — `migrations:` design-intent line currently reads `app/schemas/1.json`; correct to `app/schemas/com.jjrapps.constanza.core.data.AppDatabase/1.json` (C1).
- [x] 2.13 Verify: `./gradlew :app:testDebugUnitTest --tests "*.HabitColorRemapTest" --tests "*.BackupImporterNormalizationTest"`; `./gradlew :app:connectedDebugAndroidTest --tests "*.AppDatabaseMigrationTest"`; `./gradlew :app:detektMain`.

## Unit 3 — 7.5 Pre-Migration Snapshot (PR B, part 2 — SEPARATE COMMIT)

Must be its own commit so it reverts alone without touching `MIGRATION_1_2` (design decision 5,
rollback row "Unit 3 only").

- [ ] 3.1 Create `core/data/migration/PreMigrationSnapshotWriter.kt`: `internal class PreMigrationSnapshotWriter(targetDir: File)`, `fun write(db: SupportSQLiteDatabase): Boolean`. Catches `Exception`, NOT `Throwable` (lets `OutOfMemoryError`/`VirtualMachineError` propagate — Room's transaction rollback is safer than swallowing it mid-migration). Temp file `pre-migration-v1.sql.tmp` → atomic rename to `pre-migration-v1.sql` in `filesDir/pre-migration/` only after the last row; deletes temp on failure. Streams row by row via `BufferedWriter`, every `Cursor` in `use {}`. Fixed filename, no `System.currentTimeMillis()` (forbidden by `ForbiddenMethodCall` detekt rule + `TimeProvider` convention). Format: `sqlite_master.sql` per table + one `INSERT INTO` per row, values escaped by `Cursor.getType()`; skips `sqlite_%`, `sqlite_sequence`, `android_metadata`, `room_master_table`.
- [ ] 3.2 Modify `core/data/migration/AppMigrations.kt`: call `PreMigrationSnapshotWriter.write(db)` as the **first statement** in `MIGRATION_1_2.migrate(db)`, before the `UPDATE`. Never propagate its result as a migration failure — logged at `WARN` only, no toast/notification/persisted flag on either success or failure.
- [ ] 3.3 Modify `core/di/DatabaseModule.kt`: pass `filesDir` into `PreMigrationSnapshotWriter` construction at the migration call site.
- [ ] 3.4 Create `app/src/test/kotlin/.../PreMigrationSnapshotWriterTest.kt`: a MockK `SupportSQLiteDatabase` configured to throw during read does NOT propagate out of `write()`; `write()` returns `false`; no `pre-migration-v1.sql` file is left behind. Satisfies spec `Automatic Pre-Migration Snapshot` scenario "Snapshot failure does not block the migration".
- [ ] 3.5 **Extend** `app/src/androidTest/kotlin/.../core/data/AppDatabaseMigrationTest.kt` (separate commit from unit 2's edit to the same file): after `runMigrationsAndValidate`, assert `pre-migration-v1.sql` exists and contains the pre-migration (legacy) rows. Satisfies spec `Automatic Pre-Migration Snapshot` scenario "Snapshot is written before the migration modifies data".
- [ ] 3.6 Modify `openspec/config.yaml`: remove item `"7.5"` from `carried_forward_open_items.items` — this change closes it (snapshot shipped + `data-portability` spec now carries the `Automatic Pre-Migration Snapshot` requirement, correction C6). **Do NOT touch `G.7-throttling-row` in the same edit** — it stays open, unrelated to this change.
- [ ] 3.7 Verify: `./gradlew :app:testDebugUnitTest --tests "*.PreMigrationSnapshotWriterTest"`; `./gradlew :app:connectedDebugAndroidTest --tests "*.AppDatabaseMigrationTest"`.

## Unit 4 — Habit Colour Identity (PR C)

- [ ] 4.1 Create `core/ui/component/HabitColorDot.kt`: `Box(Dimens.HabitDotSlot)` with a tinted halo circle (`Color(argb).copy(alpha = 0.16f)`) and a solid `Dimens.HabitDot` circle centred inside. No `contentDescription` (colour is a secondary channel — design decision 6).
- [ ] 4.2 Modify `tracking/TodayModel.kt`: `TodayHabitRow.colorArgb: Int` with **no default value**; update `buildTodayHabitRow` to fill it from the `Habit` already held (3 call sites in this file, per correction C3).
- [ ] 4.3 Modify `tracking/TodayViewModel.kt`: update the 1 call site constructing `TodayHabitRow`.
- [ ] 4.4 Modify `app/src/test/kotlin/.../TodayViewModelTest.kt`: update the 4 call sites. No default value on `colorArgb` means the compiler catches any forgotten mapping — do not add a default here to make fixtures compile.
- [ ] 4.5 Modify `tracking/TodayScreen.kt`: `HabitRollupRow` multi-slot branch uses `ListItem(leadingContent = { HabitColorDot(...) })`; single-slot branch wraps the name `Text` in a `Row(verticalAlignment = CenterVertically)` with the dot. Wrap `ExactAlarmBanner` in a `Surface` (`ConstanzaColors.SurfaceRaised`, `ConstanzaShapes.medium`) around the existing `Row` — preserve the existing `weight(1f)` fix (`TodayScreen.kt:132-136`), do not replace the `Row`.
- [ ] 4.6 Modify `habit/HabitListScreen.kt`: `HabitRow` renders `ListItem(leadingContent = { HabitColorDot(habit.colorArgb) })`.
- [ ] 4.7 Create `app/src/androidTest/kotlin/.../HabitColorDotComposeTest.kt`: dot present on Today and on the habit list. Satisfies spec `Habit Colour Visible Where Habits Are Listed`.
- [ ] 4.8 Regression: run `TodayAdaptiveComposeTest` at `sw=600dp` unmodified — must stay green (dot lands on the habit header, not the slot rows the assertion measures).
- [ ] 4.9 Verify: `./gradlew :app:testDebugUnitTest --tests "*.TodayViewModelTest"`; `./gradlew :app:connectedDebugAndroidTest --tests "*.HabitColorDotComposeTest" --tests "*.TodayAdaptiveComposeTest"`; `./gradlew :app:detektMain`.

## Unit 5 — Editor Tonal Pass (PR D)

- [ ] 5.1 Modify `habit/HabitEditorScreen.kt`: move `SWATCH_SIZE`/`SWATCH_BORDER` (`:239-240`) to `Dimens.Swatch`/`Dimens.SwatchBorder`; apply `ListItemDefaults.colors`, `TopAppBarDefaults.topAppBarColors`, `Scaffold(containerColor = ConstanzaColors.Background)`; selection states already read `colorScheme.primary` (`:246`, already the accent) — no change needed there.
- [ ] 5.2 Modify `habit/ScheduleEditors.kt`: same tonal token rules.
- [ ] 5.3 Apply the `.dp` literal rule to both files: convert to a token only if the value changes or the code is new; leave every unchanged literal alone (design decision 2 — avoids a mixed diff a reviewer can't distinguish from a real padding change).
- [ ] 5.4 Verify: `./gradlew :app:testDebugUnitTest` (regression); `./gradlew :app:connectedDebugAndroidTest --tests "*.HabitEditor*"`; `./gradlew :app:detektMain`.

## Unit 6 — Remaining Screens Tonal Pass (PR E)

- [ ] 6.1 Modify `progress/ProgressScreen.kt`: tonal pass, same `.dp` literal rule.
- [ ] 6.2 Modify `reminding/SnoozeSettingsScreen.kt`: tonal pass, same rule.
- [ ] 6.3 Modify `portability/DataPortabilityScreen.kt`: tonal pass, same rule.
- [ ] 6.4 Aggregate verify: `./gradlew check` green (`:app:detektMain` clean; `:domain:detektMain`/`:domain:test` are N/A — no `:domain` file is touched anywhere in this change).
- [ ] 6.5 **Cannot be automatically verified — manual device matrix (final gate)**: notification accent shows the migrated colour on one posted reminder (`NotificationPoster.setColor`); dot renders correctly at `sw≥600dp` and across a fold/unfold configuration change. Pixel 10 (API 37) + Galaxy Z Fold 7 (SM-F966B, API 36). If `IllegalStateException: No compose hierarchies found` appears on either device, check `adb shell dumpsys window | rg isKeyguardShowing` first — it means no Activity resumed, not a code fault (`app/build.gradle.kts:178-186`).
- [ ] 6.6 Confirm no PR in this chain regressed the untouched `G.7-throttling-row` entry in `openspec/config.yaml` — it must still read exactly as unit 3 left it.

## Promise Coverage (every design/spec obligation → owning task)

| Obligation | Source | Task(s) |
|---|---|---|
| Dark-only rendering, no dynamic colour | spec `Dark-Only Rendering` | 1.6, 1.8, 1.9 |
| Habit/accent contrast ≥4.5:1 both surfaces | spec `Habit Colour And Accent Contrast Floor` | 1.10 |
| Accent excluded from habit picker | spec `Accent Reserved For Chrome` | 2.6 |
| Cold-start dark window, pinned system-bar icons | spec `Cold-Start Window Background And System Bar Icons` | 1.7, 1.8, 1.9, 1.13 (manual) |
| Contrast floor as automated JVM test | spec `Contrast Floors Asserted By Automated Test` | 1.10 |
| Exactly six habit colours offered | spec `Habit Colour Palette` | 2.6 |
| Persisted colour rewritten, bijective, orange→pink | spec `Persisted Habit Colour Stays On-Palette...` | 2.1, 2.2, 2.9, 2.11 |
| Colour visible on Today and habit list | spec `Habit Colour Visible Where Habits Are Listed` | 4.1–4.7 |
| Import reads schema version, gates behaviour | spec `Backup Schema Version Read On Import` | 2.7, 2.8 |
| Legacy colour normalized on import | spec `Legacy Habit Colour Normalized On Import` | 2.8, 2.10 |
| Pre-migration snapshot, failure-isolated | spec `Automatic Pre-Migration Snapshot` | 3.1–3.5 |
| Round-trip fidelity clarified for current exports | spec `Round-Trip Fidelity` (MODIFIED) | 2.10 |
| C1 — corrected schema path | correction | 2.5, 2.12 |
| C2 — extend, not create, migration test | correction | 2.11, 3.5 |
| C3 — Today state plumbing, 8 call sites | correction | 4.2, 4.3, 4.4 |
| C4 — `.addMigrations()` registration | correction | 2.4 |
| C5 — drop `private` on `CURRENT_SCHEMA_VERSION` | correction | 2.7 |
| C6 — 7.5 spec requirement | correction (resolved by spec phase) | satisfied by spec `Automatic Pre-Migration Snapshot`; closure task 3.6 |
| `config.yaml:216` wrong path | orchestrator flag | 2.12 |
| `carried_forward_open_items` 7.5 removal | orchestrator flag | 3.6 |
| `G.7-throttling-row` stays untouched | orchestrator flag | 3.6 (explicit exclusion), 6.6 (final confirmation) |
| Sign-trap bound-args requirement | orchestrator flag | 2.2, 2.11 |

## Verification Command Reference

| Layer | Command | Applies to |
|---|---|---|
| JVM unit | `./gradlew :app:testDebugUnitTest` | Units 1, 2, 3, 4 (regression 5, 6) |
| Instrumented | `./gradlew :app:connectedDebugAndroidTest` | Units 2, 3, 4 (regression 5) — device or emulator |
| Detekt | `./gradlew :app:detektMain` | All units |
| Detekt/test | `:domain:detektMain`, `:domain:test` | N/A — no `:domain` file changes in this design |
| Aggregate | `./gradlew check` | Unit 6 (final gate) |
| Manual — physical device | Pixel 10 (API 37), Galaxy Z Fold 7 (SM-F966B, API 36) | 1.13 (cold-start flash — no automated harness exists), 6.5 (notification accent, fold/unfold) |
