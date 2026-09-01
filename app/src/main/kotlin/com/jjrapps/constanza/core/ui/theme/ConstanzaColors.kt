package com.jjrapps.constanza.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's single dark, warm-hued colour scheme (design.md decision 1; Engram #47 "Constanza goes
 * warm-dark"). Every token shares one oklch hue band (27–31°) instead of Material 3's default
 * violet-tinted dark scheme (hue ~257–260°), because dropping a warm accent onto a cool ground reads
 * worse than the cool scheme did on its own.
 *
 * Each token's oklch source is kept in its KDoc so a future re-tone starts from intent, not from the
 * computed hex below it. [ColorContrastTest] asserts every text/accent pairing here clears WCAG AA.
 *
 * Each hex value is first bound to a named `..._ARGB` constant (design.md decision 1's chosen
 * `MagicNumber` fallback) rather than inlined directly into `Color(...)`, which is a `@Suppress`.
 */
internal object ConstanzaColors {
    private const val BACKGROUND_ARGB = 0xFF110B06.toInt()
    private const val SURFACE_ARGB = 0xFF17120D.toInt()
    private const val SURFACE_RAISED_ARGB = 0xFF201811.toInt()
    private const val SURFACE_SELECTED_ARGB = 0xFF261C13.toInt()
    private const val OUTLINE_ARGB = 0xFF28231E.toInt()
    private const val ACCENT_ARGB = 0xFFE8A860.toInt()
    private const val ON_ACCENT_ARGB = 0xFF20140A.toInt()
    private const val ON_BACKGROUND_ARGB = 0xFFEFEAE6.toInt()
    private const val ON_BACKGROUND_VARIANT_ARGB = 0xFFC4BCB6.toInt()
    private const val ON_BACKGROUND_MUTED_ARGB = 0xFF887E76.toInt()

    /** oklch(0.155 0.014 62) — every screen sits on this. */
    val Background = Color(BACKGROUND_ARGB)

    /** oklch(0.185 0.012 62) — cards and controls at rest. */
    val Surface = Color(SURFACE_ARGB)

    /** oklch(0.215 0.018 62) — raised surfaces: sheets, dialogs, the exact-alarm banner. */
    val SurfaceRaised = Color(SURFACE_RAISED_ARGB)

    /** oklch(0.235 0.022 62) — the selected/active control state. */
    val SurfaceSelected = Color(SURFACE_SELECTED_ARGB)

    /** oklch(0.26 0.012 62) — borders and dividers. */
    val Outline = Color(OUTLINE_ARGB)

    /**
     * The single saturated colour in the app's chrome: app bars, selection indicators, primary
     * controls. Reserved for chrome only — never offered as a habit colour (spec `Accent Reserved
     * For Chrome`), because the shipped orange habit swatch sat 1.7° of hue from this exact accent.
     */
    val Accent = Color(ACCENT_ARGB)

    /** Dark enough to read on top of [Accent]. */
    val OnAccent = Color(ON_ACCENT_ARGB)

    /** oklch(0.94 0.008 62) — primary text. */
    val OnBackground = Color(ON_BACKGROUND_ARGB)

    /** oklch(0.80 0.012 62) — secondary text. */
    val OnBackgroundVariant = Color(ON_BACKGROUND_VARIANT_ARGB)

    /** oklch(0.60 0.018 62) — muted labels and units. */
    val OnBackgroundMuted = Color(ON_BACKGROUND_MUTED_ARGB)
}
