# Graphite redesign (direction E)

## Objective
Replace the warm-dark, colour-heavy look with the approved "E · Grafito" direction across the whole app.

## Problem / why
The owner finds the app ugly: every habit name is painted in its own saturated colour and Today answers use green/red "Yes"/"No" pills. He wants a sober, elegant, readable app. Direction E was chosen and every screen was approved on the design canvas https://claude.ai/artifact/QiysguLZtogMJVsLm5Tetf (boards "E · Grafito con progreso", "E · Hábitos", "E · Editar hábito", "E · Progreso", "E · Ajustes", "E · Bienvenida", "E · Sistema").

## Scope (authorized 2026-10-04)
- Neutral graphite dark theme: bg #141416, surface #1C1C20, raised #232327, divider #26262B, control stroke #3A3A41, text #ECECEE, secondary #A1A1A9, label #8C8C95, destructive #E3A39A. Still dark-only.
- Geist font bundled in `res/font` (OFL, no downloadable fonts: F-Droid build has no Play Services).
- Habit colour shown only as an 8dp dot; names in the text colour.
- 12-colour muted habit palette replacing the 21 presets; persisted colours remapped to the nearest new preset (DB migration + backup normalisation), following the existing remap pattern.
- Today: progress bar "N de M" (habits fully answered / habits today), round 44dp ✕ / ✓ buttons (✓ filled for the "Now" section), neutral answered glyphs, multi-reminder habits show one segment bar per reminder plus "x de N · próxima HH:MM".
- Habit list: dot + schedule subtitle ("Diaria · 09:30"), overflow menu, archived switch.
- Editor, Progress, Settings (snooze as chips, day-review mode as a row), Onboarding restyled per canvas.
- Notification accent becomes neutral (#ECECEE). Notification layout itself is system-drawn and stays.

## Out of scope
- Launcher icon and notification small icon (settled, unchanged).
- Light theme. Store screenshots regeneration (separate follow-up).

## Constraints
- Keep every behaviour; this is a visual change plus the list subtitle data.
- Answer buttons stay reachable by tests and TalkBack (content descriptions kept; tests migrated where they looked up visible text).
- Specs under `openspec/specs/` updated where they contradict the redesign.
- Device-free verification only (emulator matrix).

## Delivery
Strategy: ask-on-risk resolved by owner policy (no process prompts) → work-unit commits on `feat/graphite-redesign`, sliced into chained PRs by compilability at delivery. Forecast well above 400 authored lines (~2500).

## Tasks
- [x] T1 Theme foundation: graphite tokens, M3 mapping, Geist typography, window background, contrast tests, visual-design-system spec. Route: delegated (writer trigger, 2+ non-trivial files).
- [ ] T2 Muted 12-colour palette: enum, colour band for a non-text dot, picker + custom dialog, DB v8 remap + backup schema 5 normalisation, tests, seeds, habit-management/data-portability specs. Route: delegated.
- [ ] T3 Today screen: dot + neutral names, round answer buttons, answered glyphs, section headers, progress bar, reminder segments, FAB, notification accent; migrate tests. Route: delegated.
- [ ] T4 Habit list: dot, schedule subtitle (new data flow), menu, archived switch; rewrite HabitNameColourComposeTest. Route: delegated.
- [ ] T5 Editor, Progress, Settings, Onboarding restyle. Route: delegated.
- [ ] T6 Full verification: `./gradlew check`, `assembleDebug`, `:app:emulatorMatrixGroupDebugAndroidTest`, rendered review on emulator. Route: delegated verifier.

## Checks per task
`./gradlew :domain:test :app:testDebugUnitTest :app:detektMain :app:compileDebugAndroidTestKotlin` (JAVA_HOME = Android Studio JBR).

## Progress / evidence
### T1 — done, commit `131117e` (`feat(theme): switch to graphite palette and Geist type`)
Route: delegated writer (writer trigger). Verification (JAVA_HOME = Android Studio JBR):
- `:domain:test` → 57 tests, 0 failures (build test-results XML).
- `:app:testDebugUnitTest` → 346 tests, 0 failures (build test-results XML).
- `:app:detektMain` → pass. `:app:compileDebugAndroidTestKotlin` → pass.
- `:app:lintDebug` → FAILS on 2 pre-existing `NewApi` errors at `tracking/TodayViewModel.kt:334` (`LocalDate.EPOCH`, API 34, minSdk 31). File untouched by T1; the same line exists at the base commit. Not fixed (outside T1 surface).

Final tokens: Background #141416, Surface #1C1C20, SurfaceRaised #232327, SurfaceSelected #2A2A2F, Divider #26262B, ControlStroke **#6C6C73** (design #3A3A41 measured 1.63:1 on bg / 1.39:1 on raised, below SC 1.4.11 3:1; #6C6C73 = 3.53 / 3.26 / 3.00), OnBackground #ECECEE, OnBackgroundVariant **#ADADB5** (design #A1A1A9 measured 6.62:1 on Surface, 6.10:1 on Raised; now 8.25 / 7.62 / 7.02), OnBackgroundMuted #8C8C95 (5.52 / 5.10 / 4.70), ChromeInteractive #ECECEE, OnChromeInteractive #141416, Destructive #E3A39A (M3 `error`, 8.74 / 8.07 / 7.44), StatusCompleted/StatusMissed unchanged.
M3 mapping: surfaceContainerLowest/Low = Background, surfaceContainer = Surface, High and Highest = SurfaceRaised; primary/secondary/tertiary = ChromeInteractive on OnChromeInteractive; primary/secondary/tertiaryContainer = SurfaceSelected; outline = ControlStroke; outlineVariant = Divider; error = Destructive.
Type: Geist variable TTF (`res/font/geist_variable.ttf`, OFL at `third_party/geist/OFL.txt`, credited in both READMEs) on all 15 roles; headlineMedium 30/600/-0.02em (Today title), titleLarge 22/600, bodyLarge 17/500, bodyMedium 14/400, labelMedium 12/500/0.08em, headlineSmall weight 600. Shapes: small 12dp, medium 16dp, large 18dp.

Hand-off notes:
- **Owner decision pending:** the board's control stroke #3A3A41 fails 3:1; T1 ships #6C6C73. If the owner prefers #3A3A41 for purely decorative borders (e.g. a filled field), T3/T5 should use Divider-like tones at the call site only where the control is otherwise identifiable.
- **T2:** `HabitColorBand.kt` now measures against a frozen `HabitBandGround` (#110B06, the retired background) instead of `ConstanzaColors.Background`, so the v6/v7 migrations and backup normalisation keep byte-identical outputs (with the live #141416 the GREEN preset fell to 6.62:1 and was rewritten). Tests switched to the same ground: `HabitColorBandTest` (all `ConstanzaColors.Background` refs) and `HabitPaletteTest` (band test). T2 owns redoing the band/palette. **`androidTest/.../core/data/AppDatabaseMigrationTest.kt:379` still measures the clamped ratio against `ConstanzaColors.Background`** (outside T1 surface, not compiled-broken but will fail on the emulator): point it at `HabitBandGround`.
- **T3:** FAB default `containerColor` is `primaryContainer` = SurfaceSelected; the design's light-filled FAB needs `containerColor = primary` at the call site. StatusCompleted/StatusMissed still exist for T3 to retire.
- **T5:** `habit/ReminderTimeField.kt` overrides `timeSelectorSelectedContentColor = primary`; primary now equals onSurface (#ECECEE), so the selected hour/minute half is distinguished only by its fill (SurfaceSelected vs SurfaceRaised, ~1.1:1). Restyle that selector. `OutlinedTextField` reads `shapes.extraSmall` (4dp); pass `shapes.small` for 12dp fields. Section-label uppercase stays at `SectionHeader` call site.
- Launcher `ic_launcher_background.xml` keeps #110B06 (out of scope); its comment still says it must match `ConstanzaColors.BACKGROUND_ARGB` — stale, outside T1 surface. No test binds them; spec now states the launcher is not bound.
- Native review: not run by the writer (parent owns RDD assessment).

## Next step
T2.
