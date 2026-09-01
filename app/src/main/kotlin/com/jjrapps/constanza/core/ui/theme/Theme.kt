package com.jjrapps.constanza.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The app's single fixed dark colour scheme (spec `Dark-Only Rendering`). Built from
 * [ConstanzaColors] rather than left at M3's default `darkColorScheme()`, which is cool/violet-hued
 * (Engram #47) — every role that reads visibly in this app is repointed at the warm ramp.
 */
private val DarkColors = darkColorScheme(
    background = ConstanzaColors.Background,
    onBackground = ConstanzaColors.OnBackground,
    surface = ConstanzaColors.Surface,
    onSurface = ConstanzaColors.OnBackground,
    surfaceVariant = ConstanzaColors.SurfaceRaised,
    onSurfaceVariant = ConstanzaColors.OnBackgroundVariant,
    outline = ConstanzaColors.Outline,
    primary = ConstanzaColors.Accent,
    onPrimary = ConstanzaColors.OnAccent,
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
