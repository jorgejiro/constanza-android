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
- [x] T2 Muted 12-colour palette: enum, colour band for a non-text dot, picker + custom dialog, DB v8 remap + backup schema 5 normalisation, tests, seeds, habit-management/data-portability specs. Route: delegated.
- [x] T3 Today screen: dot + neutral names, round answer buttons, answered glyphs, section headers, progress bar, reminder segments, FAB, notification accent; migrate tests. Route: delegated.
- [x] T4 Habit list: dot, schedule subtitle (new data flow), menu, archived switch; rewrite HabitNameColourComposeTest. Route: delegated.
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

### T2 — done, commit `5349520` (`feat(palette): muted 12-colour habit palette with v8 remap`)
Route: delegated writer (writer trigger). Verification (JAVA_HOME = Android Studio JBR):
- `:domain:test` → 57 tests, 0 failures (test-results XML).
- `:app:testDebugUnitTest` → 357 tests, 0 failures, 0 errors (test-results XML).
- `:app:detektMain` → pass. `:app:compileDebugAndroidTestKotlin` → pass.
- `:app:lintDebug` → only the 2 known `NewApi` errors at `tracking/TodayViewModel.kt:334` (`LocalDate.EPOCH`); no other errors.
- androidTest (incl. new `migration7To8RepaintsEveryRetiredPresetAndLeavesCustomColoursUntouched`) compiled, **not run** (emulator matrix is T6).

Palette: SAGE, BLUE, SAND, CLAY, LAVENDER (VISIBLE), TEAL, OLIVE, ROSE, INDIGO, LIME, TAN, GREY; DEFAULT = BLUE. 6.20–8.37:1 on Background, ≥5.27:1 on SurfaceRaised. CIE76 ΔE: global min 10.8 (SAND/TAN, floor 10), grid neighbours 17.5 (TEAL/GREY, floor 15), visible row 21.1 (floor 20).
Band decision: custom band against live `ConstanzaColors.Background` = [3.6:1, 9:1]. 3.6 because 3.0 on Background is only 2.55:1 on SurfaceRaised; 3.6 gives 3.06:1 there. Ceiling 9 sits just above the lightest preset (LIME 8.37). Saturation **not** capped (non-trivial: changes slider semantics/seeding); the ceiling removes the loudest picks. Old `[7,11]` clamp frozen as `clampToLegacyHabitBand` on `HabitBandGround` #110B06 (`LegacyHabitColorBand.kt`), used only by v5/v6 remaps and backup schema < 4.
Remap (`HabitColorMutedRemap`, Room 7→8 and backup schema < 5): nearest CIE76 ΔE, with 5 hue-family overrides (GREEN→SAGE, AMBER→SAND, MINT→TEAL, BLUE→BLUE, LILAC→LAVENDER). Others: VIOLET/PURPLE/LILAC→LAVENDER, RED→CLAY, LIGHT_BLUE/BLUE→BLUE, PINK/MAGENTA→ROSE, INDIGO→INDIGO, YELLOW/OLIVE→OLIVE, LIME/LIGHT_GREEN→LIME, TEAL/CYAN/MINT→TEAL, ORANGE/PEACH/AMBER→SAND, BROWN→TAN. Custom colours untouched (no clamp fallthrough). `8.json` identical to `7.json` except `version`. Backup `CURRENT_SCHEMA_VERSION` = 5; retone+retire hops now gated to schema < 4, muted hop to < 5.
Specs: visual-design-system (3:1 non-text floor for the dot, custom band, name in text colour, neutral chrome with destructive exception, all floors asserted by test), habit-management (12 muted, row of 5, design-system-repaint exception), data-portability (schema 5 scenarios, per-epoch gating).

Hand-off notes:
- **Deviation:** `core/di/DatabaseModule.kt` (outside the allowed surface) got one line to register `migration7To8`; without it Room cannot open v7 installs.
- **Deviation:** `ColorContrastTest`'s "status glyphs quieter than the habit palette floor" test was removed — the muted floor (6.20) is now below StatusCompleted (6.38) / StatusMissed (6.36) and the spec no longer reserves those tones. T3 retires the tokens.
- **T3/T4:** the spec now says names render in the text colour and answer controls/glyphs are neutral; code still paints names in the habit colour (`TodayScreen`, `HabitListScreen`) and uses StatusCompleted/StatusMissed. `HabitNameColourComposeTest` still uses the retired hex literals as arbitrary colours (T4 rewrites it).
- **T5:** `CustomColorDialog` sliders unchanged in UX; editing an old custom colour lighter than 9:1 seeds the slider at 1f and re-solves on confirm.
- Native review: not run by the writer (parent owns RDD assessment).

### T3 — done, commits `9340811`, `81d8e56`, `dd63fa7`, `ae3b7fb`
Route: delegated writer (writer trigger). Commits (additions+deletions):
- `9340811` feat(today): graphite header with day progress line — 15 files, +494/-189 (683).
- `81d8e56` feat(today): round neutral answer buttons and glyphs — 14 files, +250/-255 (505).
- `dd63fa7` feat(today): dot-led rows with reminder segments — 14 files, +529/-538 (1067; over the ~900 budget because the old row code leaves `TodayScreen.kt` and the new rows land in `TodayHabitRows.kt` in the same unit — they cannot compile apart).
- `ae3b7fb` feat(reminding): neutral notification accent — 3 files, +35/-28 (63).
Each commit compiled and kept unit tests green (`:app:testDebugUnitTest :app:detektMain :app:lintDebug :app:compileDebugAndroidTestKotlin` run per commit).

Test-first: `TodayProgressTest` written first against stub `todayProgress`/`reminderProgress` → RED (3 of 5 failed), then implemented → GREEN.

Verification (JAVA_HOME = Android Studio JBR), on `ae3b7fb`:
- `:domain:test` → 57 tests, 0 failures (XML).
- `:app:testDebugUnitTest` → 361 tests, 0 failures/errors (XML; 357 − 2 retired status-glyph contrast tests + 1 neutral-glyph contrast test + 5 progress tests).
- `:app:detektMain` → pass. `:app:compileDebugAndroidTestKotlin` → pass.
- `:app:lintDebug` → 0 errors (the `LocalDate.EPOCH` NewApi is fixed via `LocalDate.ofEpochDay(0)`); 9 pre-existing warnings.
- `:app:emulatorMatrixGroupDebugAndroidTest` (full suite, same tree) → api31: 207 tests, 0 failures, 3 skipped; api37: 207 tests, 0 failures, 6 skipped (API-level assumption skips, pre-existing). Includes all Today/tracking classes, CoreFlowE2ETest, TodayAddHabitE2ETest, NotificationPosterInstrumentedTest, HabitNameColourComposeTest.
- Render: `TodayRealRenderTest` on api37 (board data, 360dp @2.625), PNG recovered from the pulled logcat → `/private/tmp/claude-501/-Users-jorge-dev-constanza-android/393ec5f2-5fc2-4458-93b1-0cc69a55357b/scratchpad/t3-today.png` (English; `ProvideAppLocale` only bites below API 33).

What changed: header (title, list/sliders icon buttons from new `core/ui/icons/ConstanzaIcons`, long date + 44dp chevrons trailing it, "Today" jump in the title row on a past day, "N of M" + 4dp bar, omitted when nothing is due); section headers labelMedium in OnBackgroundMuted, no divider (shared `SectionHeader`, so Settings headings change too); rows with `core/ui/component/HabitDot` (50% alpha in Answered), neutral names, time/snooze subtitle; round ✕/✓ 44dp buttons in 48dp targets (✓ filled in Now); neutral glyphs; multi-reminder segments (unanswered = SurfaceSelected) + "x of N · next HH:MM" with the day rollup in the accessible label; 44dp chevron expander; light-filled flat FAB; banners on Surface; notification accent #ECECEE. StatusCompleted/StatusMissed and AnswerPill* dims retired.

Deviations:
- "x of N" strings are plain strings, not plurals: neither EN nor ES inflects "N of M".
- Pending single-slot rows show the reminder time again as subtitle (board), reversing today-status-icons point 3; `TodaySlotRowComposeTest.theSlotTimeIsAbsentOnASingleSlotHabit` became `theSlotTimeIsTheSubtitleOfAPendingSingleSlotHabit`.
- `NotificationPoster.postReminder` keeps its now-unused `colorArgb` (suppressed) because `scheduling/ReminderFireWorker.kt` is outside the T3 surface.
- `TodayRealRenderTest` now logs the PNG as base64 chunks (GMD uninstalls `filesDir`).
- `dd63fa7` exceeds ~900 lines (see above).

Hand-off notes:
- **T4:** reuse `HabitDot` (exposes `HabitDotColor` semantics for tests) and `ConstanzaIcons`; the habit-list half of `HabitNameColourComposeTest` still asserts the habit colour on the name span and must flip to the dot. `HabitListScreen.kt:269` comment still cites the removed `TodayScreen.demotedSuffix`.
- **T5:** `SectionHeader` is already in the graphite label style (affects Settings sections). `SectionDivider` still exists for Settings.
- Out of surface, stale: `res/drawable/ic_launcher_foreground.xml` comment cites `ConstanzaColors.StatusCompleted` (token retired; colour value unchanged in the drawable); `ReminderFireWorker` still passes `habit.colorArgb`.
- Native review: not run by the writer (parent owns RDD assessment).

### T4 — done, commits `eb01e05`, `f22637e`
Route: delegated writer (writer trigger). Commits (additions+deletions):
- `eb01e05` feat(habit): schedule summary subtitle on the habit list — 9 files, +358/-19 (377).
- `f22637e` feat(habit): graphite habit list with dots and neutral menu — 7 files, +547/-156 (703).

Test-first: `ScheduleSummaryTest` against a stub returning "" → RED (8/8 failed), implemented → GREEN. `HabitListViewModelTest."each row carries its own habit's schedule and reminder slots"` against a VM mapping `schedule = null` → RED (1 failed), wired → GREEN.

Verification (JAVA_HOME = Android Studio JBR), on `f22637e`:
- `:domain:test` → 57 tests, 0 failures (XML).
- `:app:testDebugUnitTest` → 370 tests, 0 failures/errors (XML; 361 + 8 summary + 1 VM mapping).
- `:app:detektMain` → pass. `:app:compileDebugAndroidTestKotlin` → pass.
- `:app:lintDebug` → 0 errors; 10 warnings, none in files T4 touched (the 3 new ES plurals carry `many`).
- `:app:api37DebugAndroidTest` with packages `habit`, `e2e`, `core.ui.component` → 73 tests, 0 failures, 1 skipped (XML). Includes HabitNameColourComposeTest (both halves), new HabitListSubtitleComposeTest (2), CoreFlowE2ETest, TodayAddHabitE2ETest, all habit-list/editor/repository classes. api31 not run in T4 (T6).
- Render: `HabitListRealRenderTest` on api37, Spanish via a configuration context, PNG from pulled logcat → `/private/tmp/claude-501/-Users-jorge-dev-constanza-android/393ec5f2-5fc2-4458-93b1-0cc69a55357b/scratchpad/t4-list.png`. Matches the "E · Hábitos" board (menu popup not captured: it is a separate window).

What changed: `ScheduleDao.observeAll`/`ReminderSlotDao.observeAll` (read-only, no schema change) → `HabitRepository.schedules` (`Map<habitId, HabitSchedule>`) → `HabitListViewModel` joins into `HabitListItem(habit, schedule?)` (`HabitListUiState.items` replaces `habits`). Pure `scheduleSummary(schedule, slots, strings, timeFormat, locale)` + `rememberScheduleSummaryStrings()` / `HabitScheduleSummary` in `habit/ScheduleSummary.kt`. Rows: `HabitDot` + name (bodyLarge, OnBackground, 2 lines ellipsis) + subtitle (bodyMedium, OnBackgroundVariant) + ⋮ menu (SurfaceRaised, 14dp, Divider hairline, Delete in `error`). Show-archived label in OnBackgroundVariant + divider; `ConstanzaControlDefaults.switchColors()` (new, reusable for T5 Settings). Top bar on background with `ConstanzaIcons.ChevronStart`; FAB light-filled flat like Today; empty states OnBackgroundVariant; delete dialog on SurfaceRaised with confirm in `error`.

Deviations:
- Several-times-a-day subtitle reads "3 reminders"/"3 recordatorios", not the board's "3 horas" (more natural; matches "Horas de recordatorio" wording elsewhere).
- `HabitRepository.schedules` is a cold-`Flow` property, not an `observe…()` function: the class sat at detekt's `TooManyFunctions` threshold.
- Days of week are ordered from the schedule's `weekStart`.
- Monthly ES copy: "Mensual, el día 5".

Hand-off notes:
- **T5:** `ConstanzaControlDefaults.switchColors()` exists for the Settings switches. `HabitEditorScreen`'s top bar still uses the Material arrow; the list now uses `ConstanzaIcons.ChevronStart` — align when restyling the editor.
- Native review: not run by the writer (parent owns RDD assessment).

### Native review log
- T1 `131117e`..`7f55a2a` (775 lines): granted → approved, acknowledged (lineage review-fca3c9fb01a9e39f). 2 WARNING + 1 SUGGESTION informational (migration test ground → fixed in T2; ReminderTimeField selected contrast → T5; spec floors unproved → covered in T2).
- T2 first attempt (2253 / 1835 lines): granted, stopped `lens_context_budget_exceeded`. Owner chose to split; T2 rewritten into `fa8b44b`, `5b3ad96`, `83e127c`, `e553317`, `a47ac1d`, `01154eb` (tree identical to `backup/graphite-t2`).
- `fa8b44b` (672): declined. `5b3ad96` (704): declined. `83e127c`..`a47ac1d` (905): declined. Reviewed boundary advances to `01154eb`.
- Owner raised the slicing budget to ~900 lines (2026-10-04). The native reviewer context budget is not configurable.

## Next step
T5 (after a docs(odd) commit recording T4).
