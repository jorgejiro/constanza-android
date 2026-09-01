package com.jjrapps.constanza.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The app's single fixed dark colour scheme (spec `Dark-Only Rendering`). Built from
 * [ConstanzaColors] rather than left at M3's default `darkColorScheme()`, which is cool/violet-hued
 * (Engram #47) — every role that reads visibly in this app is repointed at the warm ramp.
 *
 * **Task 6.0 (found during unit 5, fixed here).** Unit 1 bound only 11 roles. That left `TopAppBar`,
 * `ListItem`, `ExposedDropdownMenu` (`ScheduleEditors.ScheduleKindPicker`), `AlertDialog`
 * (`DataPortabilityScreen.ImportConfirmDialog`), `FilterChip` (`ScheduleEditors.DayOfWeekPicker`) and
 * `Switch` (`ScheduleEditors.ReminderTimeEditor`) reading M3's built-in cool/violet defaults for the
 * roles below on every screen that uses them — exactly the failure this whole change exists to
 * prevent, just one layer further down than `background`/`surface`/`primary`. Reference: the sibling
 * app `sleep-noise-android`'s `Theme.kt`, which binds the same `surfaceContainer*`/`outlineVariant`
 * family from its own single-ramp palette; the mapping below mirrors it.
 *
 * **The five-tone ramp maps onto the five `surfaceContainer*` roles one tier apart, with the two
 * lowest tiers collapsing onto [ConstanzaColors.Background].** This app has no container that reads
 * as more "recessed" than the screen it sits on — nothing in the component inventory (`ListItem`,
 * `TopAppBar`, `ExposedDropdownMenu`, `AlertDialog`) needs that distinction, confirmed by `rg`
 * across `app/src/main/kotlin` before writing this. `surfaceContainer`/`High`/`Highest` reuse
 * [ConstanzaColors.Surface]/[ConstanzaColors.SurfaceRaised]/[ConstanzaColors.SurfaceSelected] exactly
 * as the sibling app does, each one ramp step lighter than the last. [ConstanzaColors.Outline] is
 * deliberately NOT reused as a container fill (unlike a naive sixth ramp step would suggest): a
 * `FilterChip`'s unselected border already reads `colorScheme.outline`, so giving its fill the same
 * value would render an invisible border — fill and border blending into one flat colour.
 *
 * **`primaryContainer`/`secondary` reuse existing tones rather than introduce new hex values**
 * (design.md decision 1's "no new colour values" spirit, applied to a role addition instead of a
 * token addition). `primaryContainer`/`onPrimaryContainer` mirror `secondaryContainer`/
 * `onSecondaryContainer` exactly — both are "the selected/active state" regardless of which M3 role a
 * given default happens to read. `secondary`/`onSecondary` collapse onto `primary`/`onPrimary`,
 * mirroring the sibling app's identical choice: this app has exactly one saturated accent (spec
 * `Accent Reserved For Chrome`), so a distinct "secondary" hue would contradict that requirement.
 *
 * **Audited and deliberately left at M3's default — stated here so no future omission is silent:**
 * - `tertiary`/`onTertiary`/`tertiaryContainer`/`onTertiaryContainer` — zero call sites anywhere in
 *   `app/src/main/kotlin` (`rg -i tertiary` confirms). Nothing renders a tertiary role.
 * - `error`/`onError`/`errorContainer`/`onErrorContainer` — only `colorScheme.error` is read
 *   (`ScheduleEditors`'s slot-count error text), and M3's baseline error red is not violet/cool-hued
 *   regardless of the seed primary, so it needs no repointing. No component renders a filled
 *   `errorContainer`; revisit if one is ever added.
 * - `inverseSurface`/`inverseOnSurface`/`inversePrimary` — no `Snackbar` or inverse-styled component
 *   exists in this codebase (`rg -i snackbar` finds nothing).
 * - `scrim` — dims `AlertDialog`'s backdrop; M3's default is a fixed black-with-alpha, not hue-derived,
 *   so it is already appropriate for a dark app regardless of ramp.
 * - `surfaceTint` — M3's default is a fixed violet constant, NOT auto-derived from the `primary`
 *   passed above, but every `Surface`/`Card`/`TopAppBar`/`Scaffold` in this app either sets an
 *   explicit `containerColor` or reads a now-warm container role at zero `tonalElevation`, so nothing
 *   currently blends it into a visible pixel. Left at default; revisit if a future `Card` introduces
 *   genuine tonal-elevation blending.
 * - `surfaceDim`/`surfaceBright` — zero call sites (`rg -i "surfaceDim|surfaceBright"` confirms).
 */
private val DarkColors = darkColorScheme(
    background = ConstanzaColors.Background,
    onBackground = ConstanzaColors.OnBackground,
    surface = ConstanzaColors.Surface,
    onSurface = ConstanzaColors.OnBackground,
    surfaceVariant = ConstanzaColors.SurfaceRaised,
    onSurfaceVariant = ConstanzaColors.OnBackgroundVariant,
    surfaceContainerLowest = ConstanzaColors.Background,
    surfaceContainerLow = ConstanzaColors.Background,
    surfaceContainer = ConstanzaColors.Surface,
    surfaceContainerHigh = ConstanzaColors.SurfaceRaised,
    surfaceContainerHighest = ConstanzaColors.SurfaceSelected,
    outline = ConstanzaColors.Outline,
    outlineVariant = ConstanzaColors.Outline,
    primary = ConstanzaColors.Accent,
    onPrimary = ConstanzaColors.OnAccent,
    primaryContainer = ConstanzaColors.SurfaceSelected,
    onPrimaryContainer = ConstanzaColors.OnBackground,
    secondary = ConstanzaColors.Accent,
    onSecondary = ConstanzaColors.OnAccent,
    secondaryContainer = ConstanzaColors.SurfaceSelected,
    onSecondaryContainer = ConstanzaColors.OnBackground,
)

/**
 * The app's Material 3 theme wrapper. **Dark-only, deliberately**: no `darkTheme` parameter, no
 * `isSystemInDarkTheme()` read, no `lightColorScheme()` anywhere in this file. The app MUST NOT vary
 * its colour scheme with the device's system-wide appearance setting or with wallpaper-derived
 * dynamic colour (spec `Dark-Only Rendering`) — a `darkTheme` seam here would be dead code by
 * construction (design.md "Migration / Rollout").
 */
@Composable
fun ConstanzaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = ConstanzaTypography,
        shapes = ConstanzaShapes,
        content = content,
    )
}
