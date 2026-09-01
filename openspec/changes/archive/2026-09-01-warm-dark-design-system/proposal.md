# Proposal: Warm-Dark Design System

## Intent

Constanza ships M3's implicit defaults: a cool violet dark scheme nothing in the app actually reads
(4 `MaterialTheme` references across the whole Compose tree), a shipped habit palette whose purple
`#8E24AA` measures **2.64:1** on the dark surface — below the WCAG 3:1 floor for a non-text UI
component — and a light pre-Compose window background (`themes.xml:8` parent
`android:Theme.Material.Light.NoActionBar`) that flashes white on every cold start. Habit colour is
persisted, exported and pushed into notifications, yet it is invisible on Today, the habit list and
Progress: it is stored identity that never identifies anything.

This change installs the ratified warm-dark visual language (accent `#E8A860`, warm neutral ramp,
six accessible habit colours) **and starts reading it**, so habit colour becomes visible identity
and the contrast floors become asserted tests instead of a table in a PR.

## The three decisions

### 1. Persisted `colorArgb` is remapped — `AppDatabase` goes to `version = 2`

Confirmed: `AppDatabase.kt:30` is `version = 1`, `exportSchema = true`,
`fallbackToDestructiveMigration()` nowhere; `HabitColorPalette.SWATCHES`
(`HabitEditorViewModel.kt:341-348`) holds the six Material 2 ints. Swapping the constants recolours
only the picker, so a `Migration(1, 2)` rewrites `habits.colorArgb` old→new.

**Two corrections to the assumed "each hue maps to its own hue".** It holds for five of six —
teal 174°→173°, blue 209°→211°, red 354°→354°, purple 285°→violet 260°, green 122°→128°. **Orange
`#FB8C00` (33°) has no counterpart**, because ratified decision 4 gives that hue to the accent.
Mapping it by hue would collapse it onto red, destroying distinctness between two habits that were
distinguishable. So orange maps to the palette's one unclaimed slot, **pink `#FFA8DC`** — the
mapping stays a **bijection** (six distinct in, six distinct out). Exactly one habit in six changes
colour family, and that is forced, not incidental.

**Unmapped values.** `BackupMapper.kt:41` passes `colorArgb` through with **no validation**, and
`BackupImporter.parseAndValidate` gates only `formatVersion` — so import is an unvalidated ingress,
and a pre-change export re-injects `#8E24AA` at 2.64:1 *after* the migration. A migration alone does
not close the hole. Therefore: the migration leaves any non-palette int **untouched** (never
destructive), and the same pure six→six map runs on import **only when the file's `schemaVersion`
< 2** — `BackupDto.kt:19`'s `CURRENT_SCHEMA_VERSION` is a stale hardcoded `1` and becomes `2`. A
current export still round-trips byte-identically; only legacy files are normalized.

### 2. The habit colour dot on Today and the habit list is NEW behaviour, in scope

Habit colour reaches a composable in exactly two lines, both in `HabitEditorScreen.kt` (160, 251).
Today, the habit list and Progress render none. The dot + tinted halo is **in scope** — the chosen
direction's premise is habit colour as identity, and without it the palette work has nowhere to
appear — and it is declared as **new user-visible behaviour with a spec delta**, not styling.

### 3. Carried-forward item 7.5 — brought IN SCOPE

Decision 1 creates the only correct placement config.yaml already names. Cost weighed honestly, and
one premise is wrong: it is **not untestable**. `testing.design_intent.frameworks.migrations`
already commits to Room `MigrationTestHelper` against the committed `app/schemas/1.json`, which runs
`Migration(1,2)` for real and can assert the dump exists and contains the pre-migration rows.

Constraints: the dump is a version-agnostic raw-SQL enumeration (`sqlite_master` + `Cursor`) as the
**first** statement in `migrate(db)`, before any write; app-private storage (no SAF, no permission,
no user interaction inside a migration); and **best-effort with failure isolation** — a throw inside
`migrate()` aborts the migration and traps the user's data, so a failed dump must never fail the
migration. It lands as its own commit on top of the migration so it reverts alone.

## Capabilities

### New Capabilities

- `visual-design-system`: dark-only rendering with no light or dynamic scheme; asserted contrast
  floors; the accent-is-chrome-never-habit-identity rule; exactly six habit colours; habit colour
  rendered as identity wherever habits are listed. Warranted as a spec because the floors and the
  accent rule are testable accessibility invariants, not implementation detail. Exact hex/oklch
  values, token files and shape scale belong in `design.md`, not here.

### Modified Capabilities

- `habit-management`: the offered colour set changes and every persisted habit colour is rewritten
  once by migration (Habit Creation / Habit Editing).
- `data-portability`: legacy (`schemaVersion` < 2) palette values are normalized on import; a
  best-effort pre-migration snapshot is written before the first schema change (7.5). Round-Trip
  Fidelity is clarified, not weakened — current exports still restore identically.

**No delta:** `ui-adaptive-layout` (an unchanged constraint to honour, not a requirement change —
single responsive layout, no clipping at `sw≥600dp`), `habit-entry-tracking`, `habit-scheduling`,
`habit-progress`, `reminder-delivery`, `reminder-response`.

## Approach

Foundation first, then read it screen by screen, largest surface isolated.

| # | Work unit | Ends at | Forecast (2x history) |
|---|---|---|---|
| 1 | Theme foundation: `Color.kt` (oklch in comments), `Type.kt`, `Shape.kt`, dark-only `Theme.kt`, `themes.xml` parent, `enableEdgeToEdge`, `ColorContrastTest.kt` | Floors asserted green; no white cold-start flash | ~250–350 |
| 2 | Palette + data: new `SWATCHES`, `AppDatabase` v2, bijective `Migration(1,2)`, `schemas/2.json`, `MigrationTestHelper` test, import normalization, `CURRENT_SCHEMA_VERSION = 2` | No stored colour below the floor | ~250–350 |
| 3 | 7.5 pre-migration snapshot, failure-isolated, on top of unit 2 | Snapshot written before any `ALTER` | ~120–180 |
| 4 | Habit colour identity: dot + halo in `TodayScreen` and `HabitListScreen`, tonal `ExactAlarmBanner` | Colour visible where habits are listed | ~320–460 |
| 5 | `HabitEditorScreen` + `ScheduleEditors` tonal pass | Editor reads tokens throughout | ~360–520 |
| 6 | `ProgressScreen`, `SnoozeSettingsScreen`, `DataPortabilityScreen` tonal pass | Every screen reads tokens | ~130–210 |

**This change ENDS at unit 6.** Total ~1,430–2,070 against a 700-line budget → `auto-chain`, 3–4
chained PRs; units 4 and 5 each own a PR. Unit 1 gates 2–6; unit 3 gates on 2.

**Stack treated as unratified:** none reopened. `stack.sdk.open_risk` (targetSdk 37
behaviour-change list unverified) is untouched — this change adds no alarm, notification or
background-execution behaviour. `G.7-throttling-row` stays blocked, unchanged.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `core/ui/theme/` | New + Modified | `Color.kt`/`Type.kt`/`Shape.kt` greenfield; `Theme.kt` dark-only |
| `res/values/themes.xml`, `MainActivity.kt` | Modified | Dark window background; edge-to-edge, system-bar icons |
| `habit/HabitEditorViewModel.kt` | Modified | New six `SWATCHES` |
| `core/data/AppDatabase.kt`, `core/data/migration/` | Modified + New | `version = 2`, `Migration(1,2)`, snapshot |
| `app/schemas/2.json` | New | Committed schema |
| `portability/BackupDto.kt`, `BackupMapper.kt` | Modified | `schemaVersion = 2`, legacy colour normalization |
| `tracking/TodayScreen.kt`, `habit/HabitListScreen.kt` | Modified | New dot + halo composables |
| `habit/HabitEditorScreen.kt`, `habit/ScheduleEditors.kt`, `progress/`, `reminding/`, `portability/` screens | Modified | Tonal pass |
| `app/src/test/.../ColorContrastTest.kt`, migration tests | New | Floors and migration as tests |

## Non-Goals

| Ruled out | Reason |
|---|---|
| Full colour picker beyond six swatches | Six is the accessibility-audited set; arbitrary colour reopens the contrast floor |
| Per-habit icons | Second identity channel; colour must earn its own first |
| A light theme "later" | Ratified dark-only; a `darkTheme` seam would be dead code |
| Tablet-specific layouts | Already out of MVP scope per `ui-adaptive-layout` |
| Animation / motion system | Independent of colour; would inflate every screen unit |
| 30-day compliance grid | Belongs to a different design direction and to `habit-progress` scope |
| Palette tokens in Room | Ratified decision 9: keep raw ARGB |
| New font family | Ratified decision 11: scale yes, family no |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A throw inside `migrate()` traps user data — the DB will not open | Med | Dump wrapped in failure isolation; `MigrationTestHelper` runs the real migration; snapshot is its own revertible commit |
| Change is 2–3x the 700-line budget | High | Six work units, 3–4 chained PRs, units 4/5 isolated |
| Import re-injects a sub-floor legacy colour | Med | `schemaVersion` < 2 normalization; unit-tested pure map |
| `TodayAdaptiveComposeTest.kt` `sw=600dp` no-overlap assertion breaks | Med | New dot/halo sized inside the existing single layout; no second branch |
| Today and habit list need new composables, not edits | Med | Own work unit with its own instrumented assertions |
| Notification accent shifts silently (`NotificationPoster.setColor`) | Low | Migration rewrites the source value once; manual check on one posted reminder |
| Tonal pass drifts into a redesign | Med | Per-screen units with a stated end state; no layout restructuring |

## Rollback Plan

Touches persisted data, so rollback is version-shaped, not commit-shaped.

1. **Before unit 2 merges** — revert the theme units; no data touched.
2. **After unit 2** — do NOT downgrade `AppDatabase` to 1: Room refuses to open a v2 file and the
   user's data is unreachable. Roll back by shipping `Migration(2, 3)` that maps the six new ints
   back to the six old ones (the mapping is a bijection, so it inverts exactly), plus reverting the
   `SWATCHES` constants. Unmapped values were never rewritten, so they need no inverse.
3. **7.5 snapshot** — revert its single commit; `Migration(1,2)` survives untouched.
4. **Cold-start / edge-to-edge** — `themes.xml` and `MainActivity` revert independently.

## Dependencies

- Unit 1 (tokens + contrast test) gates units 2, 4, 5, 6; unit 3 gates on unit 2.
- Ratified palette and accent from Engram decision "Constanza goes warm-dark" (#47) — input, not
  re-litigated here.
- No new library, no version bump, no Gradle change.

## Success Criteria

- [ ] `ColorContrastTest.kt` asserts every habit colour ≥4.5:1 and the accent ≥4.5:1 against the
      app background and the selected surface, and passes in `./gradlew :app:testDebugUnitTest`.
- [ ] No colour below 3:1 is reachable: migration rewrites stored values, import normalizes legacy
      files, and the picker offers only the audited six.
- [ ] A `MigrationTestHelper` test migrates `schemas/1.json` → v2, asserts the six→six bijection,
      and asserts an unmapped int survives unchanged.
- [ ] A habit's colour is visible on Today and the habit list, not only in the editor.
- [ ] No cold-start white flash; system-bar icons legible on the warm dark surface.
- [ ] `./gradlew check` and `:app:connectedDebugAndroidTest` stay green, including
      `TodayAdaptiveComposeTest` at `sw=600dp`.
- [ ] Item 7.5 is removed from `carried_forward_open_items` with the snapshot shipped.
