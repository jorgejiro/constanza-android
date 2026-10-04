package com.jjrapps.constanza.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's single dark, neutral "graphite" colour scheme (direction E, `odd/tasks/graphite-redesign.md`).
 * It replaces the earlier warm-dark ramp: every surface and text tone is a near-achromatic grey with
 * the same faint cool cast (blue channel a few steps above red/green), so the only real chroma on
 * screen is a habit's own colour dot.
 *
 * [ColorContrastTest] asserts every text/control pairing here rather than trusting the ratios quoted
 * in these KDocs. Floors: primary text >= 12:1, secondary text >= 7:1 and muted labels >= 4.5:1 on
 * [Background], [Surface] and [SurfaceRaised]; operable strokes >= 3:1 (WCAG 2.1 SC 1.4.11).
 *
 * Each hex value is first bound to a named `..._ARGB` constant rather than inlined directly into
 * `Color(...)`, which keeps detekt's `MagicNumber` rule satisfied without a `@Suppress`.
 */
internal object ConstanzaColors {
    private const val BACKGROUND_ARGB = 0xFF141416.toInt()
    private const val SURFACE_ARGB = 0xFF1C1C20.toInt()
    private const val SURFACE_RAISED_ARGB = 0xFF232327.toInt()
    private const val SURFACE_SELECTED_ARGB = 0xFF2A2A2F.toInt()
    private const val DIVIDER_ARGB = 0xFF26262B.toInt()
    private const val CONTROL_STROKE_ARGB = 0xFF6C6C73.toInt()
    private const val CHROME_INTERACTIVE_ARGB = 0xFFECECEE.toInt()
    private const val ON_CHROME_INTERACTIVE_ARGB = 0xFF141416.toInt()
    private const val ON_BACKGROUND_ARGB = 0xFFECECEE.toInt()
    private const val ON_BACKGROUND_VARIANT_ARGB = 0xFFADADB5.toInt()
    private const val ON_BACKGROUND_MUTED_ARGB = 0xFF8C8C95.toInt()
    private const val DESTRUCTIVE_ARGB = 0xFFE3A39A.toInt()

    /** Every screen sits on this. */
    val Background = Color(BACKGROUND_ARGB)

    /** Cards, rows and controls at rest. */
    val Surface = Color(SURFACE_ARGB)

    /** Raised surfaces: sheets, dialogs, the exact-alarm banner. */
    val SurfaceRaised = Color(SURFACE_RAISED_ARGB)

    /** The selected/active control state (selected chip, selected picker half). */
    val SurfaceSelected = Color(SURFACE_SELECTED_ARGB)

    /**
     * The decorative hairline: dividers and rules between rows, and nothing else. Measures 1.22:1 on
     * [Background], which is intentional and permitted: WCAG 2.1 SC 1.4.11 exempts purely decorative
     * elements.
     *
     * **This tone must never be bound to a control stroke.** It once was (both M3 `outline` and
     * `outlineVariant` pointed at one divider tone), which made every self-stroking control in the
     * app invisible — most visibly a `Switch` thumb at 1.07:1 against its own track. Use
     * [ControlStroke] for anything a user can operate. [ColorContrastTest] fails if the two collapse.
     */
    val Divider = Color(DIVIDER_ARGB)

    /**
     * The stroke that draws an operable control: switch thumbs and unchecked track borders,
     * unfocused outlined-field borders, unselected chip and checkbox outlines.
     *
     * The approved graphite board draws control strokes at `#3A3A41`, but that tone measures only
     * 1.63:1 on [Background] and 1.39:1 on [SurfaceRaised], below SC 1.4.11's 3:1 floor for operable
     * components. This is the minimal same-cast step that clears 3:1 on every surface a stroked
     * control sits on: 3.53:1 on [Background], 3.26:1 on [Surface], 3.00:1 on [SurfaceRaised] (the
     * binding constraint — an unchecked switch's track fills with `surfaceContainerHighest`).
     */
    val ControlStroke = Color(CONTROL_STROKE_ARGB)

    /**
     * The tone that draws anything a user can operate: button labels, filled primary actions,
     * selection indicators. Graphite primary actions are light-filled with dark ink, so this is the
     * fill and [OnChromeInteractive] the ink.
     *
     * Shares its value with [OnBackground] today (`#ECECEE`) but is its own token, not an alias, for
     * the same reason [Divider] and [ControlStroke] are split: one token doing two jobs is how the
     * earlier stroke defect happened, and a re-tone of text must not silently move every control.
     */
    val ChromeInteractive = Color(CHROME_INTERACTIVE_ARGB)

    /** Ink on top of [ChromeInteractive]: 15.59:1. Equal to [Background] by design. */
    val OnChromeInteractive = Color(ON_CHROME_INTERACTIVE_ARGB)

    /** Primary text: 15.59:1 on [Background], 13.27:1 on [SurfaceRaised]. */
    val OnBackground = Color(ON_BACKGROUND_ARGB)

    /**
     * Secondary text. The board's `#A1A1A9` reached 7.17:1 on [Background] but only 6.62:1 on
     * [Surface] and 6.10:1 on [SurfaceRaised], below the 7:1 secondary floor; this is the minimal
     * same-cast step that clears it everywhere: 8.25:1, 7.62:1, 7.02:1.
     */
    val OnBackgroundVariant = Color(ON_BACKGROUND_VARIANT_ARGB)

    /** Muted labels and units: 5.52:1 on [Background], 4.70:1 on [SurfaceRaised]. */
    val OnBackgroundMuted = Color(ON_BACKGROUND_MUTED_ARGB)

    /**
     * Destructive actions and error text (M3 `error`). A desaturated rose rather than M3's baseline
     * red so it does not shout on the graphite ground: 8.74:1 on [Background], 7.44:1 on
     * [SurfaceRaised].
     */
    val Destructive = Color(DESTRUCTIVE_ARGB)
}
