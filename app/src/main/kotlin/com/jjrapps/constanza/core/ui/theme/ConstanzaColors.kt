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
    private const val DIVIDER_ARGB = 0xFF28231E.toInt()
    private const val CONTROL_STROKE_ARGB = 0xFF7A6B5D.toInt()
    private const val CHROME_INTERACTIVE_ARGB = 0xFFC4BCB6.toInt()
    private const val ON_CHROME_INTERACTIVE_ARGB = 0xFF20140A.toInt()
    private const val ON_BACKGROUND_ARGB = 0xFFEFEAE6.toInt()
    private const val ON_BACKGROUND_VARIANT_ARGB = 0xFFC4BCB6.toInt()
    private const val ON_BACKGROUND_MUTED_ARGB = 0xFF887E76.toInt()
    private const val STATUS_COMPLETED_ARGB = 0xFF5FA867.toInt()
    private const val STATUS_MISSED_ARGB = 0xFFE07B74.toInt()

    /** oklch(0.155 0.014 62) — every screen sits on this. */
    val Background = Color(BACKGROUND_ARGB)

    /** oklch(0.185 0.012 62) — cards and controls at rest. */
    val Surface = Color(SURFACE_ARGB)

    /** oklch(0.215 0.018 62) — raised surfaces: sheets, dialogs, the exact-alarm banner. */
    val SurfaceRaised = Color(SURFACE_RAISED_ARGB)

    /** oklch(0.235 0.022 62) — the selected/active control state. */
    val SurfaceSelected = Color(SURFACE_SELECTED_ARGB)

    /**
     * oklch(0.26 0.012 62) — the decorative hairline: dividers and rules between rows, and nothing
     * else. Measures 1.26:1 on [Background], which is intentional and permitted: WCAG 2.1 SC 1.4.11
     * exempts purely decorative elements, and a quiet dark app wants its rules felt, not read.
     *
     * **This tone must never be bound to a control stroke.** It was, until this token was split out
     * of the old single `Outline`: `Theme.kt` pointed both M3 `outline` and M3 `outlineVariant` here,
     * which put every self-stroking control in the app at ~1.1-1.3:1 — most visibly the "Remind me"
     * `Switch`, whose thumb sat at 1.07:1 against its own track. Use [ControlStroke] for anything a
     * user can operate. [ColorContrastTest] fails if these two are ever collapsed again.
     */
    val Divider = Color(DIVIDER_ARGB)

    /**
     * oklch(0.52 0.020 62) — the stroke that draws an operable control: switch thumbs and unchecked
     * track borders, unfocused outlined-field borders, unselected chip and checkbox outlines.
     *
     * Warm-ramp equivalent of M3's own `outline` role, which its dark baseline puts at `#938F99`
     * (L=0.2815) — roughly 16x lighter than the divider tone above, because M3 separates the two
     * jobs on purpose. This value clears WCAG 2.1 SC 1.4.11's 3:1 floor against **all four** surface
     * tones, not just the page: 3.81:1 on [Background], 3.62:1 on [Surface], 3.41:1 on
     * [SurfaceRaised] and 3.25:1 on [SurfaceSelected].
     *
     * That last figure is the binding constraint and the reason this is not a darker tone. A switch
     * thumb is drawn *inside* its own track, which fills with [SurfaceSelected]; being visible
     * against the page while invisible against the track is the exact defect being fixed here. The
     * next candidate down, `#6A5C50`, cleared [Background] at 3.03:1 but only reached 2.59:1 against
     * that track, so it was rejected. [OnBackgroundMuted] clears both, but it is a *text* role, and
     * spending a text tone on a control stroke would repeat this same mistake in the other
     * direction.
     */
    val ControlStroke = Color(CONTROL_STROKE_ARGB)

    /**
     * The tone that draws anything a user can operate: button labels, filled controls, selection
     * indicators. Deliberately achromatic, because the habit's own colour is the app's only chroma
     * now — a chrome accent would compete with it for attention.
     *
     * Shares its value with [OnBackgroundVariant] today (`#C4BCB6`) but is its own token, not an
     * alias or a reference to it, for exactly the reason the [Divider]/[ControlStroke] split
     * documented further up this file exists: one token doing two jobs is how that earlier defect
     * happened, and a future re-tone of secondary *text* must not silently move every *control*.
     *
     * Clears the 3:1 non-text floor on all four surface tones as a control fill: 10.44:1 on
     * [Background], 9.94:1 on [Surface], 9.35:1 on [SurfaceRaised] and 8.92:1 on [SurfaceSelected].
     */
    val ChromeInteractive = Color(CHROME_INTERACTIVE_ARGB)

    /** Dark enough to read on top of [ChromeInteractive]: 9.62:1. */
    val OnChromeInteractive = Color(ON_CHROME_INTERACTIVE_ARGB)

    /** oklch(0.94 0.008 62) — primary text. */
    val OnBackground = Color(ON_BACKGROUND_ARGB)

    /** oklch(0.80 0.012 62) — secondary text. */
    val OnBackgroundVariant = Color(ON_BACKGROUND_VARIANT_ARGB)

    /** oklch(0.60 0.018 62) — muted labels and units. */
    val OnBackgroundMuted = Color(ON_BACKGROUND_MUTED_ARGB)

    /**
     * The tick glyph on an answered [com.jjrapps.constanza.domain.model.EntryStatus.COMPLETED]
     * Today slot (today-status-icons), replacing the word "Hecho" that used to carry the same
     * information. Not sourced from the same oklch warm ramp as the tokens above — chosen against
     * measurement instead, the same way [HabitPalette]'s contrast band was.
     *
     * Clears WCAG 2.1 SC 1.4.11's 3:1 non-text floor on all four surface tones: 6.78:1 on
     * [Background], 6.45:1 on [Surface], 6.07:1 on [SurfaceRaised], 5.79:1 on [SurfaceSelected].
     * [ColorContrastTest] asserts all four rather than trusting this paragraph.
     *
     * Deliberately sits just below [HabitPalette]'s own `[7:1, 11:1]` contrast band (its floor is
     * 6.98:1): a habit's identity colour, painted on its own name, must read a shade louder than a
     * state glyph sitting next to it, not compete with it.
     *
     * Not `#6EC276`, the more obvious brighter green (8.97:1 on [Background]): that measures only
     * ΔE 14.0 from the habit palette's [HabitColor.GREEN] (7.03:1, ΔE from a true Green 500) —
     * below the palette's own 16.1 minimum pairwise separation, so a green habit's name and its own
     * tick would have read as one colour. `#5FA867` is ΔE 17.6 from [HabitColor.GREEN], clear of
     * that floor.
     */
    val StatusCompleted = Color(STATUS_COMPLETED_ARGB)

    /**
     * The cross glyph on an answered [com.jjrapps.constanza.domain.model.EntryStatus.MISSED] Today
     * slot (today-status-icons), replacing the word "No hecho"/"Missed".
     *
     * Clears WCAG 2.1 SC 1.4.11's 3:1 non-text floor on all four surface tones: 6.75:1 on
     * [Background], 6.43:1 on [Surface], 6.04:1 on [SurfaceRaised], 5.77:1 on [SurfaceSelected].
     * [ColorContrastTest] asserts all four rather than trusting this paragraph.
     *
     * Deliberately sits just below [HabitPalette]'s own `[7:1, 11:1]` band, for the same reason
     * [StatusCompleted] does: identity should read a shade louder than state.
     *
     * ΔE 24.2 from its nearest habit-palette neighbour, [HabitColor.PINK].
     */
    val StatusMissed = Color(STATUS_MISSED_ARGB)
}
