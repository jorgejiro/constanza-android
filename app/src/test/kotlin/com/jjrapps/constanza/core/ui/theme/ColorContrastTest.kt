package com.jjrapps.constanza.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Asserts the accessibility contract as JVM unit tests instead of trusting a table in a PR
 * (Engram #47, point 10a; ported from the sibling app's `sleep-noise-android` `ColorContrastTest`).
 *
 * Spec `Habit Colour And Accent Contrast Floor` / `Contrast Floors Asserted By Automated Test`: every
 * offered habit colour and the accent MUST clear **4.5:1** against both [ConstanzaColors.Background]
 * and [ConstanzaColors.SurfaceSelected] — one floor, not a lighter graphics-only tier, because a habit
 * colour also has to carry the habit name as adjacent text. The ratified palette measures well above
 * this floor (≥10:1 vs `Background`, ≥8.4:1 vs `SurfaceSelected`), but the floor asserted here is the
 * spec's 4.5:1, not the current headroom — a future re-tone should fail this test before it ships
 * rather than after.
 */
class ColorContrastTest {

    @Test
    fun `every habit colour clears the floor against the background`() {
        HabitColor.entries.forEach { habitColor ->
            assertRatioAtLeast(
                foreground = habitColor.composeColor,
                background = ConstanzaColors.Background,
                minimum = CONTRAST_FLOOR,
                label = "${habitColor.name} on Background",
            )
        }
    }

    @Test
    fun `every habit colour clears the floor against the selected surface`() {
        HabitColor.entries.forEach { habitColor ->
            assertRatioAtLeast(
                foreground = habitColor.composeColor,
                background = ConstanzaColors.SurfaceSelected,
                minimum = CONTRAST_FLOOR,
                label = "${habitColor.name} on SurfaceSelected",
            )
        }
    }

    @Test
    fun `the accent clears the floor against the background`() {
        assertRatioAtLeast(ConstanzaColors.Accent, ConstanzaColors.Background, CONTRAST_FLOOR, "Accent on Background")
    }

    @Test
    fun `the accent clears the floor against the selected surface`() {
        assertRatioAtLeast(
            ConstanzaColors.Accent,
            ConstanzaColors.SurfaceSelected,
            CONTRAST_FLOOR,
            "Accent on SurfaceSelected",
        )
    }

    @Test
    fun `primary text clears the AA floor on the background with room to spare`() {
        assertRatioAtLeast(ConstanzaColors.OnBackground, ConstanzaColors.Background, PRIMARY_TEXT_FLOOR, "OnBackground")
    }

    @Test
    fun `secondary text clears the AA floor on the background`() {
        assertRatioAtLeast(
            ConstanzaColors.OnBackgroundVariant,
            ConstanzaColors.Background,
            SECONDARY_TEXT_FLOOR,
            "OnBackgroundVariant",
        )
    }

    @Test
    fun `muted labels clear the AA body floor`() {
        assertRatioAtLeast(
            ConstanzaColors.OnBackgroundMuted,
            ConstanzaColors.Background,
            CONTRAST_FLOOR,
            "OnBackgroundMuted",
        )
    }

    /**
     * Task 6.0 — `Theme.kt` now binds `surfaceContainer`/`surfaceContainerHigh` to
     * [ConstanzaColors.Surface]/[ConstanzaColors.SurfaceRaised], so `ListItem` (`TodayScreen`,
     * `HabitListScreen`) reads [ConstanzaColors.Surface] as its container, with [HabitColorDot]'s
     * colour and the habit name text drawn on top of it — a surface neither habit colours nor text
     * were ever measured against before this task. [ConstanzaColors.SurfaceRaised] is included too:
     * it backs `AlertDialog` (`surfaceContainerHigh`, `DataPortabilityScreen.ImportConfirmDialog`)
     * and `ExactAlarmBanner`'s explicit `Surface`, both of which carry body text.
     */
    @Test
    fun `every habit colour clears the floor against the surface container`() {
        HabitColor.entries.forEach { habitColor ->
            assertRatioAtLeast(
                foreground = habitColor.composeColor,
                background = ConstanzaColors.Surface,
                minimum = CONTRAST_FLOOR,
                label = "${habitColor.name} on Surface",
            )
        }
    }

    @Test
    fun `every habit colour clears the floor against the raised surface container`() {
        HabitColor.entries.forEach { habitColor ->
            assertRatioAtLeast(
                foreground = habitColor.composeColor,
                background = ConstanzaColors.SurfaceRaised,
                minimum = CONTRAST_FLOOR,
                label = "${habitColor.name} on SurfaceRaised",
            )
        }
    }

    @Test
    fun `primary text clears the AA floor on the surface container`() {
        assertRatioAtLeast(
            ConstanzaColors.OnBackground,
            ConstanzaColors.Surface,
            PRIMARY_TEXT_FLOOR,
            "OnBackground on Surface",
        )
    }

    @Test
    fun `primary text clears the AA floor on the raised surface container`() {
        assertRatioAtLeast(
            ConstanzaColors.OnBackground,
            ConstanzaColors.SurfaceRaised,
            PRIMARY_TEXT_FLOOR,
            "OnBackground on SurfaceRaised",
        )
    }

    @Test
    fun `secondary text clears the AA floor on the surface container`() {
        assertRatioAtLeast(
            ConstanzaColors.OnBackgroundVariant,
            ConstanzaColors.Surface,
            SECONDARY_TEXT_FLOOR,
            "OnBackgroundVariant on Surface",
        )
    }

    @Test
    fun `text on the accent is legible`() {
        assertRatioAtLeast(ConstanzaColors.OnAccent, ConstanzaColors.Accent, CONTRAST_FLOOR, "OnAccent on Accent")
    }

    // ------------------------------------------------------------------------------------------
    // WCAG 2.1 SC 1.4.11 Non-text Contrast — user-interface components and their states.
    //
    // Everything above this line asserts SC 1.4.3, which is about *text*. That is why nothing above
    // caught the defect this section exists to prevent: the disabled "Remind me" `Switch` rendered
    // its OFF state as a thumb at 1.07:1 against its own track, so you could not see where the
    // thumb sat inside the control. No text was involved, so no assertion above had an opinion.
    //
    // These tests assert against [DarkColors] — the real `ColorScheme` the app renders with — and
    // NOT against [ConstanzaColors]. That distinction is the whole point. The palette was never the
    // bug: `#28231E` is a perfectly good hairline-divider tone. The bug was that `Theme.kt` *bound*
    // both `outline` (M3's control-stroke role: switch thumbs and track borders, unfocused
    // outlined-field borders, unselected chip and checkbox outlines) and `outlineVariant` (M3's
    // decorative-divider role) to that one divider tone, collapsing a distinction M3 draws
    // deliberately — its own dark baseline separates the two by ~4.5x in relative luminance
    // (`outline` `#938F99` L=0.2815 vs `outlineVariant` `#49454F` L=0.0624). A test that walked
    // colour constants could not see a binding collision, so these walk the bindings.
    // ------------------------------------------------------------------------------------------

    /**
     * The control-stroke floor, on every surface a stroked control can be drawn on.
     *
     * This is the assertion that fails loudest on the pre-fix palette: `outline` measured 1.26:1 on
     * `Background` and 1.07:1 on `surfaceContainerHighest`, against a required 3:1.
     */
    @Test
    fun `the control stroke clears the non-text floor on every surface it can sit on`() {
        surfacesControlsSitOn().forEach { (name, surface) ->
            assertRatioAtLeast(
                foreground = DarkColors.outline,
                background = surface,
                minimum = NON_TEXT_FLOOR,
                label = "control stroke (outline) on $name",
            )
        }
    }

    /**
     * The exact relationship that failed hardest, asserted on its own rather than only as one entry
     * in the sweep above, because it is the reported defect.
     *
     * `SwitchDefaults.colors()` draws an unchecked switch as `uncheckedThumbColor = outline` inside
     * `uncheckedTrackColor = surfaceContainerHighest`. Those were `#28231E` and `#261C13` — 1.07:1,
     * two tones of the same near-black. The control was on screen and its state was unreadable.
     */
    @Test
    fun `the switch thumb is visible against its own track when the switch is off`() {
        assertRatioAtLeast(
            foreground = DarkColors.outline,
            background = DarkColors.surfaceContainerHighest,
            minimum = NON_TEXT_FLOOR,
            label = "unchecked switch thumb against its own track",
        )
    }

    /** The same relationship in the ON state: `checkedThumbColor = onPrimary` on `checkedTrackColor = primary`. */
    @Test
    fun `the switch thumb is visible against its own track when the switch is on`() {
        assertRatioAtLeast(
            foreground = DarkColors.onPrimary,
            background = DarkColors.primary,
            minimum = NON_TEXT_FLOOR,
            label = "checked switch thumb against its own track",
        )
    }

    /**
     * A checked switch's track is a filled shape with no border, so the fill itself has to carry the
     * control's boundary against whatever it sits on.
     *
     * Its unchecked counterpart is deliberately NOT asserted here, and the omission is the reasoned
     * one rather than the forgotten one: `surfaceContainerHighest` measures 1.17:1 on `Background`
     * and cannot be raised to 3:1 without turning every raised container in the app into a light
     * grey slab. An unchecked track does not need to — it is drawn *with a border*, and SC 1.4.11 is
     * satisfied by any boundary that identifies the component. That border reads `outline`, which is
     * exactly what the sweep above asserts.
     */
    @Test
    fun `the checked switch track is visible on every surface it can sit on`() {
        surfacesControlsSitOn().forEach { (name, surface) ->
            assertRatioAtLeast(
                foreground = DarkColors.primary,
                background = surface,
                minimum = NON_TEXT_FLOOR,
                label = "checked switch track (primary) on $name",
            )
        }
    }

    /**
     * The structural guard, and the one that would have caught this before it ever reached a screen.
     *
     * Every ratio asserted above can be satisfied again tomorrow by a re-tone that quietly re-points
     * `outline` back at the divider tone, because a sweep only measures what it is pointed at. This
     * asserts the *roles stay separate*: a control stroke and a hairline divider are different jobs
     * and M3 gives them different tokens for that reason. Collapsing them is what made every
     * self-stroking control in the app invisible by construction.
     */
    @Test
    fun `the control stroke role and the divider role are bound to different tones`() {
        assertNotEquals(
            illegal = DarkColors.outlineVariant,
            actual = DarkColors.outline,
            message = "outline (control strokes) and outlineVariant (decorative dividers) are bound to the " +
                "same tone. M3 separates them deliberately; collapsing them onto the divider tone is what " +
                "made the Switch thumb invisible against its own track.",
        )
    }

    /**
     * The divider role is deliberately left below the non-text floor, stated as an assertion so the
     * exemption is a decision on the record rather than a gap.
     *
     * SC 1.4.11 exempts anything purely decorative, and a hairline rule between two list rows is the
     * textbook case: no state is being communicated, nothing is operable, and dragging it up to 3:1
     * would draw a hard light line across every screen in a deliberately quiet dark app. The floor
     * that matters for a divider is only that it stays *below* the control-stroke tone, so the two
     * never drift back together — which is what this checks.
     */
    @Test
    fun `the divider role stays quieter than the control stroke role`() {
        val divider = contrastRatio(DarkColors.outlineVariant, DarkColors.background)
        val stroke = contrastRatio(DarkColors.outline, DarkColors.background)
        assertTrue(
            actual = divider < stroke,
            message = "outlineVariant measured %.2f:1 on the background and outline measured %.2f:1. A ".format(
                divider,
                stroke,
            ) + "decorative divider must stay quieter than a control stroke, not louder.",
        )
    }

    /**
     * The second collision of the same class, caught on a real screenshot rather than by a test.
     *
     * `Theme.kt` bound `primaryContainer` and `surfaceContainerHighest` to one tone AND
     * `onPrimaryContainer` and `onSurface` to one tone. M3's time-picker draws its selected half
     * with the first pair and its unselected half with the second, so with both pairs collapsed the
     * two halves rendered pixel-identical and nothing on screen said which one you were editing.
     *
     * Note what this does and does not assert. SC 1.4.11 imposes no ratio *between two states of
     * different components*, and none is achievable here anyway: making the selected fill clear 3:1
     * against `surfaceContainerHighest` (L=0.0092) would require L>=0.1276, a light grey slab in a
     * ramp whose lightest surface is L=0.0092. The distinction in this palette is therefore carried
     * by the content colour, and what is enforceable — and enforced — is that the two (container,
     * content) pairs cannot both be identical again.
     */
    @Test
    fun `the selected container is distinguishable from the plain surface container`() {
        val sameContainer = DarkColors.primaryContainer == DarkColors.surfaceContainerHighest
        val sameContent = DarkColors.onPrimaryContainer == DarkColors.onSurface
        assertTrue(
            actual = !(sameContainer && sameContent),
            message = "primaryContainer/onPrimaryContainer and surfaceContainerHighest/onSurface are bound to " +
                "identical pairs, so any component that distinguishes a selected part from an unselected one " +
                "with those two roles renders both parts pixel-identical.",
        )
    }

    /** Whichever tone carries that distinction still has to be readable on the container it sits on. */
    @Test
    fun `the selected container content is legible on its own container`() {
        assertRatioAtLeast(
            foreground = DarkColors.onPrimaryContainer,
            background = DarkColors.primaryContainer,
            minimum = CONTRAST_FLOOR,
            label = "onPrimaryContainer on primaryContainer",
        )
    }

    /**
     * The surfaces a stroked control can actually be drawn on, walked by role name so the failure
     * message names the role and so a surface added to [DarkColors] later is swept automatically
     * instead of being silently skipped.
     */
    private fun surfacesControlsSitOn(): List<Pair<String, Color>> = DarkColors.let { scheme ->
        listOf(
            "background" to scheme.background,
            "surface" to scheme.surface,
            "surfaceVariant" to scheme.surfaceVariant,
            "surfaceContainerLowest" to scheme.surfaceContainerLowest,
            "surfaceContainerLow" to scheme.surfaceContainerLow,
            "surfaceContainer" to scheme.surfaceContainer,
            "surfaceContainerHigh" to scheme.surfaceContainerHigh,
            "surfaceContainerHighest" to scheme.surfaceContainerHighest,
        )
    }.distinctSurfaces()

    /** Four tones back eight surface roles; measuring each tone once keeps failures readable. */
    private fun List<Pair<String, Color>>.distinctSurfaces(): List<Pair<String, Color>> =
        groupBy { (_, color) -> color }
            .map { (color, roles) -> roles.joinToString("/") { (name, _) -> name } to color }

    private fun assertRatioAtLeast(foreground: Color, background: Color, minimum: Double, label: String) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            actual = ratio >= minimum,
            message = "$label measured %.2f:1, below the required %.2f:1".format(ratio, minimum),
        )
    }

    /** WCAG 2.1 relative luminance and contrast ratio. */
    private fun contrastRatio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + LUMINANCE_OFFSET) / (darker + LUMINANCE_OFFSET)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= GAMMA_THRESHOLD) {
                v / GAMMA_LINEAR_DIVISOR
            } else {
                Math.pow((v + GAMMA_OFFSET) / GAMMA_DIVISOR, GAMMA_EXPONENT)
            }
        }
        return RED_WEIGHT * channel(color.red) + GREEN_WEIGHT * channel(color.green) + BLUE_WEIGHT * channel(color.blue)
    }

    private companion object {
        /** Spec `Habit Colour And Accent Contrast Floor`: one 4.5:1 floor for both surfaces. */
        const val CONTRAST_FLOOR = 4.5

        /** WCAG 2.1 SC 1.4.11 Non-text Contrast: user-interface components and their states. */
        const val NON_TEXT_FLOOR = 3.0
        const val PRIMARY_TEXT_FLOOR = 12.0
        const val SECONDARY_TEXT_FLOOR = 7.0

        // WCAG 2.1 relative-luminance constants (sRGB gamma correction + contrast formula offset).
        const val LUMINANCE_OFFSET = 0.05
        const val GAMMA_THRESHOLD = 0.04045
        const val GAMMA_LINEAR_DIVISOR = 12.92
        const val GAMMA_OFFSET = 0.055
        const val GAMMA_DIVISOR = 1.055
        const val GAMMA_EXPONENT = 2.4
        const val RED_WEIGHT = 0.2126
        const val GREEN_WEIGHT = 0.7152
        const val BLUE_WEIGHT = 0.0722
    }
}
