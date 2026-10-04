package com.jjrapps.constanza.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The app's single fixed dark colour scheme (spec `Dark-Only Rendering`), built from the graphite
 * [ConstanzaColors] rather than left at M3's default `darkColorScheme()`, which is violet-hued. Every
 * role that any M3 component in this app reads is repointed; the ones deliberately left at default
 * are audited at the end of this comment.
 *
 * **Surfaces.** The five `surfaceContainer*` roles map onto the ramp one tier apart, with the two
 * lowest collapsing onto [ConstanzaColors.Background] (nothing in this app is more recessed than the
 * screen) and the two highest both on [ConstanzaColors.SurfaceRaised]. `surfaceContainerHighest` is
 * deliberately NOT [ConstanzaColors.SurfaceSelected]: it is the fill an unchecked `Switch` track
 * draws its thumb inside, so every extra step of lightness there is a step the control stroke has to
 * climb to stay at 3:1. [ConstanzaColors.SurfaceSelected] is kept for the selected-state containers.
 *
 * **`outline` and `outlineVariant` are two roles, not one.** `outline` is the control stroke
 * ([ConstanzaColors.ControlStroke], >= 3:1 on every surface per WCAG 2.1 SC 1.4.11) and
 * `outlineVariant` the decorative hairline ([ConstanzaColors.Divider], exempt as decorative).
 * Collapsing them once made every self-stroking control invisible; `ColorContrastTest` guards it.
 * Note that in material3 1.4.0 `outlineVariant` also backs `OutlinedButton` and unselected
 * `FilterChip` borders, so the app's operable consumers of it are pointed back at `outline` by hand
 * (`ControlDefaults`).
 *
 * **Primary actions are light-filled with dark ink.** `primary`/`onPrimary` are
 * [ConstanzaColors.ChromeInteractive]/[ConstanzaColors.OnChromeInteractive]; `secondary` and
 * `tertiary` collapse onto the same achromatic pair, because the habit's own colour is the app's
 * only chroma and no member of these families may put M3's violet on screen.
 *
 * **Selected containers stay dark.** `primaryContainer`, `secondaryContainer` and
 * `tertiaryContainer` are [ConstanzaColors.SurfaceSelected], one step above
 * `surfaceContainerHighest`, so a selected half of a two-part selector (time picker hour/minute,
 * AM/PM) is never pixel-identical to the unselected one. `onTertiaryContainer` is
 * [ConstanzaColors.OnBackground] and differs from the unselected AM/PM label (`onSurfaceVariant`).
 *
 * **`error`/`onError`** are [ConstanzaColors.Destructive] on [ConstanzaColors.OnChromeInteractive].
 *
 * **Audited and deliberately left at M3's default:**
 * - `errorContainer`/`onErrorContainer` — no component renders a filled error container.
 * - `inverseSurface`/`inverseOnSurface`/`inversePrimary` — no `Snackbar` or inverse-styled component.
 * - `scrim` — a fixed black-with-alpha, already right for a dark app.
 * - `surfaceTint` — every surface sets an explicit container or sits at zero tonal elevation.
 * - `surfaceDim`/`surfaceBright` — zero call sites.
 *
 * `internal` rather than `private` so `ColorContrastTest` can assert the WCAG floors against these
 * *bindings*, not merely the palette constants: both historical defects here were binding
 * collisions between two roles that each held a perfectly good colour.
 */
internal val DarkColors = darkColorScheme(
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
    surfaceContainerHighest = ConstanzaColors.SurfaceRaised,
    outline = ConstanzaColors.ControlStroke,
    outlineVariant = ConstanzaColors.Divider,
    primary = ConstanzaColors.ChromeInteractive,
    onPrimary = ConstanzaColors.OnChromeInteractive,
    primaryContainer = ConstanzaColors.SurfaceSelected,
    onPrimaryContainer = ConstanzaColors.ChromeInteractive,
    secondary = ConstanzaColors.ChromeInteractive,
    onSecondary = ConstanzaColors.OnChromeInteractive,
    secondaryContainer = ConstanzaColors.SurfaceSelected,
    onSecondaryContainer = ConstanzaColors.OnBackground,
    tertiary = ConstanzaColors.ChromeInteractive,
    onTertiary = ConstanzaColors.OnChromeInteractive,
    tertiaryContainer = ConstanzaColors.SurfaceSelected,
    onTertiaryContainer = ConstanzaColors.OnBackground,
    error = ConstanzaColors.Destructive,
    onError = ConstanzaColors.OnChromeInteractive,
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
