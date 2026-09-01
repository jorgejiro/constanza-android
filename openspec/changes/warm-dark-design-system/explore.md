# Exploration — warm-dark-design-system

Phase: `sdd-explore` · Change: `warm-dark-design-system` · Artifact store: hybrid
(Engram topic `sdd/warm-dark-design-system/explore`, observation #48)

## Scope note

The theme, palette and sibling-app investigation was completed by the orchestrator before this
phase and is treated as ratified input (recorded in the Engram decision "Constanza goes warm-dark").
This exploration covers **only** the current Compose structure of the app's screens and what
applying the chosen "Tonal" direction costs there.

## The headline finding

**The theme layer is not wired to the UI.** Across all of `:app`, the entire Compose tree reads:

| Reference | Count | Where |
|---|---|---|
| `MaterialTheme.colorScheme.primary` | 1 | `habit/HabitEditorScreen.kt:246` (swatch selection border) |
| `MaterialTheme.colorScheme.onSurfaceVariant` | 1 | `habit/ScheduleEditors.kt:131` |
| `MaterialTheme.colorScheme.error` | 1 | `habit/ScheduleEditors.kt:289` |
| `MaterialTheme.typography.bodySmall` | 1 | `habit/ScheduleEditors.kt:130` |

Four references in total, three of them inside the habit editor. Every other `Text`, `ListItem`,
`TopAppBar` and control in the app takes M3's implicit defaults.

The consequence for this change: replacing the colour scheme in `Theme.kt` would, on its own,
change almost nothing on screen. **This change is not "swap the tokens" — it is "start reading
the tokens".** That is a larger and more mechanical job than a retint, and it is spread across
every screen rather than concentrated in the theme package.

## Current state

| Screen | File(s) | Lines (`wc -l`) |
|---|---|---|
| Today | `tracking/TodayScreen.kt` | 242 |
| Habit list | `habit/HabitListScreen.kt` | 166 |
| Habit editor | `habit/HabitEditorScreen.kt` + `habit/ScheduleEditors.kt` | 257 + 332 = 589 |
| Progress | `progress/ProgressScreen.kt` | 59 |
| Settings | `reminding/SnoozeSettingsScreen.kt` + `portability/DataPortabilityScreen.kt` | 81 + 95 = 176 |

Total across the six files: 1,232 lines.

The schedule section is composed inline into the editor's `Column`, not a separate route.
`DataPortabilitySection` is embedded as a `LazyColumn` item inside `SnoozeSettingsScreen`, also
without its own route. Note that "the progress/settings screen" resolves to **three** files, not
one — the design canvas artboards combined progress and settings into a single screen that does
not exist as such in the code.

### Zero M3 surface components anywhere

Grepped the whole UI tree for `Card`, `ElevatedCard`, `OutlinedCard`, `Surface`, `Divider`,
`HorizontalDivider`, `VerticalDivider` — **zero imports and zero call sites**. Every screen is
`Scaffold` + `TopAppBar` + `LazyColumn`/`Column` + `ListItem`/`Row`/`Text`/`TextButton`.

So the Tonal direction's tonal-surface treatment is **new structural work, not a token swap onto
existing Card/Surface usage**. There is nothing tonal to retint yet.

### Habit colour renders in exactly one place

`habit/HabitEditorScreen.kt`'s private `ColorSwatchRow` (lines 242–257): a `Row` of six 40dp
circles, `Color(swatch)` fill, `MaterialTheme.colorScheme.primary` selection border. The only two
lines in the app where a habit's colour reaches a composable are `HabitEditorScreen.kt:160` and
`:251`.

Neither `TodayScreen.kt` nor `HabitListScreen.kt` nor `ProgressScreen.kt` renders a habit's
`colorArgb` at all — no dot, no tint, nothing. **"Habit colour as a dot with a tinted halo" is a
new UI element on Today and the habit list, not a restyle of an existing one.** Those two screens
need new composables added, not existing ones recoloured.

### No token files exist

`core/ui/theme/` contains exactly one file: `Theme.kt`. **No `Type.kt`, no `Shape.kt`, no `Dimens`
object anywhere in the repository.** Spacing is raw `.dp` literals at every call site (16/8/4/32dp
predominating). `SWATCH_SIZE = 40.dp` and `SWATCH_BORDER = 3.dp` are private constants in
`HabitEditorScreen.kt:239-240`, the closest thing to a design token in the codebase.

### `HabitColorPalette` and persisted colour

`habit/HabitEditorViewModel.kt:340-350` holds `object HabitColorPalette` with the six Material 2
ARGB ints (teal first) and `DEFAULT = SWATCHES.first()`. `HabitEditorUiState.colorArgb` defaults
to it (line 324).

Swapping those six constants changes **only** the swatch picker and the default offered to a new
habit. It is a code default. Every already-created habit keeps its old ARGB int, because nothing
remaps persisted rows. If the product intent is "every habit renders in the new palette", that
needs an explicit data remap. This interacts with `AppDatabase` being `version = 1`,
`exportSchema = true`, schema committed at `app/schemas/1.json`, and
`fallbackToDestructiveMigration()` called nowhere.

## Per-screen non-themed sites

- **`tracking/TodayScreen.kt`** — no `colorScheme`/`typography` reads at all. Raw `.dp` literals.
  `ExactAlarmBanner` (lines 126–148) is a bare `Row` with no background surface; a tonal warning
  banner needs a new tonal container here. No colour dot in `HabitRollupRow`/`SlotRow`.
- **`habit/HabitListScreen.kt`** — no `colorScheme`/`typography` reads. Raw `.dp` literals. No
  colour dot in `HabitRow` (lines 141–166) despite rendering one `Habit` per row; this is where the
  dot + halo affordance must be **added**.
- **`habit/HabitEditorScreen.kt`** — one `colorScheme.primary` read (line 246). Hardcoded
  `SWATCH_SIZE`/`SWATCH_BORDER`. `ColorSwatchRow` is the site the tonal dot language extends from.
- **`habit/ScheduleEditors.kt`** — three theme-aware lines (130, 131, 289). Its six schedule-kind
  sub-editors (`ScheduleKindPicker`, `NumberStepper`, `DayOfWeekPicker`, `EveryNDaysEditor`,
  `ReminderSlotEditor`, `ReminderTimeEditor`) use plain M3 components with zero explicit colour or
  shape overrides, inheriting whatever `ConstanzaTheme` supplies. A tonal pass is mostly a token
  change here — but the file is 332 lines across six sub-composables, so shape/spacing consistency
  still touches many call sites.
- **`progress/ProgressScreen.kt`** — no theme reads, no hardcoded colours, pure `Text` list.
  Smallest surface by a wide margin.
- **`reminding/SnoozeSettingsScreen.kt`** — no theme reads. `RadioButton`/`Row`/`Text`.
- **`portability/DataPortabilityScreen.kt`** — no theme reads. `AlertDialog`/`TextButton`/`Text`.

## Test suite state and breakage risk

Instrumented tests touching these screens: `TodayComposeTest.kt`, `TodayAdaptiveComposeTest.kt`,
`HabitEditorComposeTest.kt`, `HabitListArchiveComposeTest.kt`, `HabitScheduleKindComposeTest.kt`,
`HabitEditorRotationComposeTest.kt`.

Grepped all of `app/src/androidTest` for `color|Color|swatch|Swatch|Palette` — **zero colour or
swatch assertions in any Compose UI test.** Every assertion is an `onNodeWithText` /
`onAllNodesWithText` string-content lookup, a `performClick()`, or (in
`TodayAdaptiveComposeTest.kt`) a `boundsInRoot` non-overlap check between two text nodes. Nothing
asserts a colour value, a component type (`Card` vs `Row`), or the palette's contents or count.

**The tonal restyle is therefore low regression risk to the existing suite**, provided
`stringResource` labels stay in place and spacing changes do not make
`TodayAdaptiveComposeTest.kt`'s two slot-row bounds overlap at `sw=600dp`.

`colorArgb` appears as a fixture value in roughly 15 test files, but in every case examined it is
an arbitrary sentinel never compared against `HabitColorPalette`; in
`HabitEditorViewModelTest.kt:117` it is set but never asserted back. **No test asserts equality
against a specific `HabitColorPalette.SWATCHES` value**, so replacing the six constants breaks no
existing test.

The flip side: **no JVM test exists that would catch a contrast regression.** A
`ColorContrastTest.kt` under `app/src/test/` must be added net-new, not adapted.

## `ui-adaptive-layout` constraint

`openspec/specs/ui-adaptive-layout/spec.md` requires that every screen — "at minimum ... the today
screen and the habit create/edit screen" — must not clip, overlap or lose content at `sw >= 600dp`
or across any permitted orientation or rotation, using a **single** responsive layout. Dedicated
tablet/landscape layouts are explicitly out of MVP scope and must stay out. A second requirement
covers soft-keyboard visibility across a configuration change on the editor, already implemented
via the `focusRestoring`/`hasInitialized` machinery.

**Constraint for this change**: the restyle must keep `TodayAdaptiveComposeTest.kt`'s no-overlap
assertion passing at `sw=600dp`, and must not introduce a second layout branch. Any new dot, halo
or pill must fit inside the existing single responsive layout rather than fork it.

## Per-screen changed-line cost estimate

Historical multiplier applied: in `habit-tracking-mvp`, **every UI work unit roughly doubled its
own line forecast** between estimate and actual diff.

| Screen/file(s) | Current | Naive estimate | With historical 2x | Notes |
|---|---|---|---|---|
| `TodayScreen.kt` | 242 | ~90–130 | ~180–260 | New colour dot + halo, tonal `ExactAlarmBanner` surface, pill rollup rows |
| `HabitListScreen.kt` | 166 | ~70–100 | ~140–200 | New colour dot + halo in `HabitRow`, row shape |
| `HabitEditorScreen.kt` + `ScheduleEditors.kt` | 589 | ~180–260 | ~360–520 | Palette swap is trivial; tonal shape/spacing touches all six sub-editors |
| `ProgressScreen.kt` | 59 | ~15–25 | ~30–50 | Smallest, plain-text screen |
| `SnoozeSettingsScreen.kt` + `DataPortabilityScreen.kt` | 176 | ~50–80 | ~100–160 | Radio rows and dialog buttons need tonal selected-state treatment |

**Budget flag.** `review_budget_lines = 700` for the whole change. The habit-editor pair alone is
forecast at 360–520 lines under the historical multiplier — 51–74% of the entire budget from one
screen pair. Summing the high estimates (260 + 200 + 520 + 50 + 160 = 1,190) is roughly 1.7x the
budget as a single PR. The habit-editor pair and probably the Today screen each warrant their own
reviewable unit. This is an observation for propose/tasks to act on, not a task breakdown.

Note that none of the above includes the theme foundation itself (`Color.kt`, `Type.kt`,
`Shape.kt`, `Theme.kt`, `themes.xml`, system bars, `ColorContrastTest.kt`) or the Room migration,
both of which are additional.

## Risks

- **Data remap for persisted `colorArgb` is undecided.** Recolouring `HabitColorPalette.SWATCHES`
  does not touch already-created habits. If they must render in the new palette, an explicit
  `Migration` or app-side remap is required, making an `AppDatabase` version bump a dependency.
- **Contrast floors would ship unverified.** No `ColorContrastTest.kt` equivalent exists.
- **Today and the habit list need new composables**, not edits — higher-uncertainty diff than a
  token swap.
- **The habit-editor pair is the largest and most coupled surface** (589 lines, nine composables);
  its six sub-editors rely entirely on implicit theme defaults, so a tonal pass risks touching all
  of them even though only three lines read `MaterialTheme` explicitly.
- **`Type.kt`/`Shape.kt` are greenfield** within `core/ui/theme/`, not edits to existing files.
- **`TodayAdaptiveComposeTest.kt`'s `sw=600dp` non-overlap assertion** is the one existing UI test
  with layout-geometry sensitivity.

## Ready for proposal

Yes. Three things the proposal must decide explicitly rather than assume:

1. Whether already-persisted `colorArgb` values are remapped to the new palette, and how.
2. That the colour dot on Today and the habit list is new scope, not restyling.
3. How the change is sliced, given the habit-editor pair alone consumes half the review budget.
