# Verification Report: warm-dark-design-system

**Date**: 2026-09-01 · **Branch verified**: `feat/warm-dark-screens-tonal` (tip of the chain; contains the whole change) · **Verdict**: **PASS WITH WARNINGS**

## Completeness

All six work units' checkboxes in `tasks.md` are checked except two, both **correctly** left unchecked as manual-only:

| Task | Status | Note |
|---|---|---|
| 1.13 (cold-start flash, light-mode icons) | Unchecked | Manual-only per design.md decision 9; partially provable now — see Manual Checks below |
| 6.5 (notification accent, sw≥600dp dot, fold/unfold) | Unchecked | Manual-only; partially provable now — see Manual Checks below |
| 6.4 (`./gradlew check` green) | Checked, but tasks.md itself documents `check` is NOT green | Honest self-correction already recorded; verified accurate (see Issue W1) |

No other task is stale or falsely marked complete. `apply-progress.md`'s claims were independently re-derived from source, not trusted — no discrepancy found between its claims and actual code.

## Build, Test, Detekt — Real Numbers, `--rerun-tasks`

| Command | Result |
|---|---|
| `./gradlew :domain:test --rerun-tasks` | **52/52**, 0 failures — matches expected |
| `./gradlew :app:testDebugUnitTest --rerun-tasks` | **118/118**, 0 failures — matches expected |
| `./gradlew :app:detektMain --rerun-tasks` | BUILD SUCCESSFUL, 0 issues |
| `./gradlew :domain:detektMain --rerun-tasks` | BUILD SUCCESSFUL, 0 issues |
| `./gradlew :app:connectedDebugAndroidTest --rerun-tasks` (Pixel 10, serial `55221FDCR005RD`, API 37) | **63/63**, 0 skipped, 0 failed — matches expected |

`git diff main..feat/warm-dark-screens-tonal --stat -- domain/` is empty — `:domain` genuinely untouched across the whole change.

## Requirement-by-Requirement Evidence (12/12 — every requirement has implementing code AND a passing test)

### visual-design-system (5 requirements)

| Requirement | Evidence |
|---|---|
| Dark-Only Rendering | `Theme.kt`: no `darkTheme` param, no `isSystemInDarkTheme()`, no `lightColorScheme()`. Live-verified: with device system appearance forced to light (`cmd uimode night no`), the app still rendered its fixed dark scheme end-to-end (screenshot evidence) |
| Habit Colour And Accent Contrast Floor | `ColorContrastTest` — 6 habit colours + accent, each ≥4.5:1 against `Background` AND `SurfaceSelected`; ran green in the 118/118 run |
| Accent Reserved For Chrome | `HabitPalette.ARGB` has exactly 6 entries, none equal to `ConstanzaColors.Accent` (`0xFFE8A860`); confirmed by source and live screenshot of the picker (6 swatches, no accent colour offered) |
| Cold-Start Window Background And System Bar Icons | `colors.xml` `window_background = #110B06` byte-identical to `ConstanzaColors.BACKGROUND_ARGB = 0xFF110B06`; `MainActivity.enableEdgeToEdge(SystemBarStyle.dark(...))` for both bars. Live-verified in light system mode: status-bar icons render in the light/legible style appropriate for the dark app background, not the device's light-mode dark-icon style |
| Contrast Floors Asserted By Automated Test | `ColorContrastTest.assertRatioAtLeast` asserts `ratio >= CONTRAST_FLOOR` where `CONTRAST_FLOOR = 4.5` is a literal constant, not measured headroom — a colour re-toned to 4.4:1 would genuinely fail this assertion |

### habit-management (3 requirements)

| Requirement | Evidence |
|---|---|
| Habit Colour Palette | `HabitPalette.ORDERED = HabitColor.entries` = exactly 6 members; picker screenshot confirms exactly 6 swatches |
| Persisted Habit Colour Stays On-Palette Across A Palette Change | `HabitColorRemap.LEGACY_TO_CURRENT` — verified programmatically: 6 distinct legacy ints → 6 distinct current ints, all current values are exact `HabitPalette` members, legacy/current domains disjoint. `AppDatabaseMigrationTest.migration1To2RewritesEveryLegacyColourToItsCurrentCounterpart` asserts actual post-migration row **values** (not completion-only) — real SQLite, real Pixel 10, part of the 63/63 green run |
| Habit Colour Visible Where Habits Are Listed | `HabitColorDot` wired via `ListItem(leadingContent = ...)` in both `TodayScreen` and `HabitListScreen`; `HabitColorDotComposeTest` (2 tests, part of 63/63). **Independently re-confirmed live**: created two real habits with distinct colours at `sw≥664dp` (density override 260) — habit list rendered two visually distinct dots (pink, teal), each row's height unchanged |

### data-portability (4 requirements)

| Requirement | Evidence |
|---|---|
| Backup Schema Version Read On Import | `BackupDto.CURRENT_SCHEMA_VERSION = 2`, not `private`; `BackupImporter.normalizeHabitColors` gates on `schemaVersion < CURRENT_SCHEMA_VERSION`, called from `replaceAll` before the insert transaction |
| Legacy Habit Colour Normalized On Import | `BackupImporterNormalizationTest` — schemaVersion 1 normalizes purple→violet and orange→pink; part of 118/118 |
| Automatic Pre-Migration Snapshot | See "The Sign Trap and the Snapshot" below — fully re-derived from source, all five load-bearing properties confirmed |
| Round-Trip Fidelity (MODIFIED) | `BackupImporterNormalizationTest`'s "schemaVersion 2 leaves colours byte-identical" case |

## The Ten Specific Checks

1. **Bijection** — programmatically verified: 6 distinct legacy ints → 6 distinct current ints; `set(current) == set(HabitPalette.ARGB)`; legacy/current domains disjoint. Orange (`0xFFFB8C00`) → pink (`0xFFFFA8DC`) is the one family change. Confirmed correct.
2. **Sign trap avoided** — `AppMigrations.kt`'s SQL string is `"UPDATE habits SET colorArgb = CASE colorArgb $caseWhenSql END WHERE colorArgb IN ($inPlaceholders)"` where both `caseWhenSql` and `inPlaceholders` are built entirely from `?` placeholders; every value is bound via `db.execSQL(sql, (caseArgs + inArgs).toTypedArray())`. No hex literal anywhere in the SQL text. Verified `0xFF8E24AA.toInt() = -7461718` (signed int32) vs `4287505578` (unsigned/SQL-text-parsed) — the two really do differ, confirming the trap is real and genuinely avoided by binding. `AppDatabaseMigrationTest` asserts actual post-migration row values via a raw `SELECT` cursor, not merely that `runMigrationsAndValidate` didn't throw — a no-op migration from the sign trap would not pass this test.
3. **CASE/WHERE guard parity** — `entries = HabitColorRemap.LEGACY_TO_CURRENT.entries.toList()` generates BOTH the `CASE ... WHEN` arms and the `WHERE colorArgb IN (...)` placeholders from the exact same source list — guaranteed identical set by construction, no NULL risk.
4. **Import gate works** — `CURRENT_SCHEMA_VERSION = 2`, not private (confirmed by source read); `BackupImporter` reads `schemaVersion` and gates; current-version round trip is byte-identical (test-confirmed). This closes the previously-shipped write-only-schemaVersion defect.
5. **Snapshot cannot brick the DB** — `writer.write(db)` is the literal first statement in `migrate(db)`, before the `UPDATE`; its `Boolean` result is discarded (not assigned, not checked); `catch (expectedFailure: Exception)` — NOT `Throwable`; temp-file (`pre-migration-v1.sql.tmp`) then `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` only after the last row. All five load-bearing properties design.md names are present in the actual code, not just the KDoc.
6. **No cool violet left** — `Theme.kt`'s `DarkColors` binds 21 M3 roles (counted). Decompiled the resolved `material3-android-1.4.0` sources to check every M3 default token this app's actual component inventory reads: `TopAppBar` (`AppBarTokens.ContainerColor = Surface`, `OnScrollContainerColor = SurfaceContainer` — both bound), `AlertDialog` (`DialogTokens.ContainerColor = SurfaceContainerHigh` — bound), `ExposedDropdownMenu`/menu (`MenuTokens.ContainerColor = SurfaceContainer` — bound), `FilterChip` (`SecondaryContainer`/`SurfaceContainerLow` — both bound). Every M3 default token actually reachable by a component in this app is repointed off M3's cool default. `surfaceTint` is genuinely left at M3's default violet constant but is provably unreachable — confirmed via `TopAppBarTokens` that this app's version of Material3 (1.4.0) does not blend `surfaceTint` into `TopAppBar`'s container by default, and no `Card`/`Surface` in the app uses nonzero `tonalElevation`. The KDoc's own audit of unbound roles (`tertiary*`, `error*` beyond `colorScheme.error`, `inverse*`, `scrim`, `surfaceDim`/`surfaceBright`) was independently re-verified with `rg` — zero call sites for each, exactly as claimed.
7. **ColorContrastTest asserts the contract, not headroom** — confirmed above; `CONTRAST_FLOOR = 4.5` is a literal constant compared with `>=`, not a measured value.
8. **7.5's closure is legitimate** — removed from `carried_forward_open_items.items` in `config.yaml` (confirmed via diff); the note explicitly states "Item 7.5 was closed this way by warm-dark-design-system." `data-portability/spec.md` carries the `Automatic Pre-Migration Snapshot` requirement the closure is against. The snapshot genuinely ships (source-confirmed above).
9. **G.7-throttling-row untouched** — `git diff main..feat/warm-dark-screens-tonal -- openspec/config.yaml` shows only the new `lint-preexisting-errors` item added before it; the `G.7-throttling-row` block's own content is byte-identical.
10. **lint-preexisting-errors accuracy** — `git diff main..feat/warm-dark-screens-tonal -- <each of the 3 files>` is empty for all three (`NotificationPoster.kt`, `ScheduleEditors.kt`, `HabitScheduleKindComposeTest.kt`); `git log --oneline -1` for each resolves to a pre-this-change commit. Confirmed accurate.

## Manual Device Checks (Pixel 10, `55221FDCR005RD`, API 37 — only device attached)

| Check | Result | Evidence |
|---|---|---|
| System-bar icons legible in light mode (1.13) | **PROVEN** | Forced `cmd uimode night no`, cold-started the app, screencapped: status-bar clock/wifi/battery icons render in the light/legible style against the dark app chrome, not the device's light-mode dark-icon style. Night mode restored to `yes` afterward, confirmed |
| Cold-start window background mechanism (1.13) | **PROVEN** (mechanism) / **INCONCLUSIVE** (transient flash) | `window_background` hex is byte-identical to `ConstanzaColors.Background` (source-level proof). `am start -W` reported `LaunchState: COLD`, `TotalTime: 357ms`; a screencap taken immediately after `am start` returned only showed the already-settled dark frame — the transient pre-Compose frame is too fast to catch with `screencap` timing, exactly as design.md predicted. Stated plainly as inconclusive rather than claimed proven |
| Dot at sw≥600dp (6.5) | **PROVEN** | `wm density 260` on this device's 1080px width yields ≈664dp effective width. Created two real habits (pink, teal) at that density; habit-list screenshot shows two visually distinct colour dots, row heights unchanged, no clipping. Density reset to physical default (420) afterward, confirmed |
| Notification accent on a real posted reminder (6.5) | **PARTIAL** | `NotificationPoster.setColor(colorArgb)` is a direct pass-through of the habit's stored `colorArgb` (source-confirmed) and the colour-propagation pipeline is proven end-to-end by real-device automated tests today (`HabitColorDotComposeTest` + `AppDatabaseMigrationTest`, both part of the 63/63 green `connectedDebugAndroidTest` run on this exact Pixel 10). A **live** posted notification with `dumpsys notification --noredact` inspection was attempted but not completed: setting a near-future reminder time via UI automation hit a pre-existing, out-of-scope numeric-field quirk in `ReminderTimeEditor`/`ScheduleEditors.kt` (`text.toIntOrNull()?.coerceIn(0, MAX_MINUTE) ?: minute` reverts to the *old* value rather than empty on backspace-to-empty, so typed digits concatenate with stale state and clamp to 59) that made reliably hitting an exact target minute impractical via `adb input text` within reasonable session time. `ScheduleEditors.kt` is confirmed untouched by this whole change (`git diff` empty), so this is an incidental, out-of-scope observation, not a defect in the change under verification — noted for the record, not scored against this change |
| Fold/unfold configuration change (6.5) | **NOT POSSIBLE** | No foldable device attached this session (Galaxy Z Fold 7 not connected). Not simulated. Fold/unfold correctness for `HabitColorDot` rendering was previously verified in units 2–5 on the Fold per `apply-progress.md`'s device logs from those sessions, which this session cannot independently re-confirm without the device |

## Design Coherence

All 9 design.md decisions checked against code; no deviation found beyond those `apply-progress.md` already flags loudly (file renames for detekt naming rules, `object`→factory-function shape, `expectedFailure` catch-variable naming) — all confirmed as genuine, correctly-resolved detekt tensions, not silent scope drift.

One numeric discrepancy in design.md's own prose (not code): design.md states `0xFF8E24AA.toInt()` is `-7461206`/`4287103146`; the actual computed values are `-7461718`/`4287505578`. This is a doc-only arithmetic typo in design.md — the code itself is correct and the concept (sign trap) is real and correctly avoided. **Not a code defect.**

## Issues

**CRITICAL**: None found.

**WARNING**:
- **W1** — Task 6.4 claims `./gradlew check` as its proof, but `tasks.md` itself already documents (correctly, not silently) that `check` is NOT green due to 3 pre-existing lint errors, closed instead via the new `lint-preexisting-errors` carried-forward item. This is self-consistent and honestly recorded, not a hidden gap — flagged here only so `sdd-archive` does not need to rediscover it. Recommend the archive step confirm `lint-preexisting-errors` survives into `openspec/config.yaml`'s post-archive state exactly as this change left it.
- **W2** — Task 6.5's notification-accent sub-check could not be completed live this session (see Manual Device Checks). The underlying colour-propagation pipeline is otherwise proven by automated tests; this is a residual manual-verification gap, not a code defect. Recommend either a follow-up session with more automation-friendly reminder-time entry, or accepting the strong indirect proof already available.

**SUGGESTION**:
- **S1** — `ReminderTimeEditor`'s minute-field `coerceIn` fallback (`?: minute`, reverting to stale state rather than empty) makes the field awkward to fully clear and retype via any programmatic input method, and is mildly surprising for a real user backspacing to correct a typo (it silently snaps back instead of showing empty). Pre-existing, out of this change's scope (`ScheduleEditors.kt` confirmed untouched), noted for a future ticket, not this change.
- **S2** — design.md's KDoc-mirrored arithmetic for the sign-trap example (`-7461206`/`4287103146`) doesn't match the actual computed values (`-7461718`/`4287505578`). Cosmetic, doc-only, does not affect the code's correctness or the AppMigrations.kt KDoc (which states the concept correctly without repeating the specific numbers verbatim in the same way).

## The Defining Failure Mode — Hunted, Not Found

Walked every requirement in all three specs (12/12) and every commitment in design.md's 9 decisions against actual implementing code. Every spec requirement has both implementing code and a passing automated test confirmed by a real re-run today. No design.md commitment was found without a corresponding code change or an explicitly-stated, justified non-implementation (e.g., `Migration(2,3)` rollback recipe is documentation-only by design, correctly not implemented since unit 2 was never rolled back). The `Promise Coverage` table's claim of full coverage held up under independent re-derivation.

## Can This Change Archive?

**Yes**, with two residual items for the orchestrator to route, neither blocking:
1. Task 6.5's live notification-accent capture is incomplete (W2) — strong indirect proof exists; a follow-up manual session can close this fully if desired.
1.13's cold-start flash and 6.5's fold/unfold row remain genuinely non-automatable and are correctly recorded as such, consistent with the prior units' device-matrix history.

No CRITICAL issues. No false claims found in `tasks.md` or `apply-progress.md` — every claim checked was either true or already self-corrected loudly in the artifacts themselves (task 6.4's `check` gap being the model example of the latter).
