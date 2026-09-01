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
