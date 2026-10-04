package com.jjrapps.constanza.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cbrt
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The graphite palette's own invariants (graphite redesign T2): twelve muted presets, mutually
 * distinguishable, each clearing WCAG 1.4.11's 3:1 non-text floor as an identity dot on every
 * surface it is drawn on, with a five-colour collapsed row that holds the default.
 *
 * **Why CIE Lab ΔE.** The defect the palette history keeps returning to is colours that are far apart
 * in hue and still hard to tell apart, because they share one band of lightness and saturation. A
 * muted palette is exactly that situation by design, so separation is measured perceptually.
 *
 * **Where the floors come from.** CIE76 ΔE ≈ 2.3 is a just-noticeable difference for adjacent large
 * patches; ΔE 10 is the conventional "clearly different at a glance" threshold, and it is what
 * [GLOBAL_SEPARATION_FLOOR] asks of every pair. The twelve measure 10.8 at their closest
 * (`SAND`/`TAN`) — a muted palette cannot be pushed much further apart without leaving "muted".
 * Grid neighbours and the collapsed row, where a person compares colours side by side, get stricter
 * floors the palette actually satisfies (17.5 and 21.1 measured).
 */
class HabitPaletteTest {

    @Test
    fun `the palette offers twelve distinct colours`() {
        assertEquals(PALETTE_SIZE, HabitPalette.ARGB.size, "the graphite palette is exactly twelve muted presets")
        assertEquals(
            HabitPalette.ARGB.size,
            HabitPalette.ARGB.toSet().size,
            "two families resolved to the same value, so the grid would show a duplicate circle",
        )
    }

    /** No two swatches anywhere in the palette may read as one colour with two names. */
    @Test
    fun `no two offered colours read as one colour`() {
        var worst = Double.MAX_VALUE
        var worstPair = ""
        HabitPalette.ORDERED.forEachIndexed { i, a ->
            HabitPalette.ORDERED.drop(i + 1).forEach { b ->
                val delta = deltaE(a.argb, b.argb)
                if (delta < worst) {
                    worst = delta
                    worstPair = "${a.name}/${b.name}"
                }
            }
        }
        assertTrue(
            worst >= GLOBAL_SEPARATION_FLOOR,
            "$worstPair measured ΔE %.1f, below the %.1f floor — those two read as one colour with two names"
                .format(worst, GLOBAL_SEPARATION_FLOOR),
        )
    }

    /**
     * Neighbouring cells are what a person compares side by side, so this walks the grid as it is drawn — six wide, checking
     * across **and** down — and requires every neighbouring pair to be far more separated than the
     * rejected pair was.
     *
     * Cell five of the first row is the custom wheel, which is a gradient and has no ΔE, so it is
     * modelled here as a hole rather than skipped silently: getting that index wrong would quietly
     * compare the wrong pairs and pass.
     *
     * This is what makes [HabitPalette.ORDERED] a deliberate arrangement rather than a list: sorting
     * it by hue, the obvious thing to do, fails this test immediately.
     */
    @Test
    fun `no two neighbouring cells in the grid look alike`() {
        val cells: List<HabitColor?> =
            HabitPalette.VISIBLE + listOf(null) + HabitPalette.COLLAPSED_REMAINDER
        var worst = Double.MAX_VALUE
        var worstPair = ""
        cells.forEachIndexed { i, cell ->
            if (cell == null) return@forEachIndexed
            val neighbours = buildList {
                if ((i + 1) % SWATCHES_PER_ROW != 0) add(i + 1)
                add(i + SWATCHES_PER_ROW)
            }
            neighbours.mapNotNull { cells.getOrNull(it) }.forEach { other ->
                val delta = deltaE(cell.argb, other.argb)
                if (delta < worst) {
                    worst = delta
                    worstPair = "${cell.name}/${other.name}"
                }
            }
        }
        assertTrue(
            worst >= NEIGHBOUR_SEPARATION_FLOOR,
            ("$worstPair are neighbours in the grid and measure ΔE %.1f, below the %.1f floor. " +
                "Neighbours have to be obviously different — that is the whole reason ORDERED is not sorted by hue.")
                .format(worst, NEIGHBOUR_SEPARATION_FLOOR),
        )
    }

    /** The collapsed row is what most users will ever see, so it carries the strictest separation
     *  requirement in this file: these five must be *obviously* different from one another. */
    @Test
    fun `the visible row's colours are obviously different from each other`() {
        assertEquals(VISIBLE_ROW_SIZE, HabitPalette.VISIBLE.size, "the collapsed row shows a fixed number of presets")
        var worst = Double.MAX_VALUE
        var worstPair = ""
        HabitPalette.VISIBLE.forEachIndexed { i, a ->
            HabitPalette.VISIBLE.drop(i + 1).forEach { b ->
                val delta = deltaE(a.argb, b.argb)
                if (delta < worst) {
                    worst = delta
                    worstPair = "${a.name}/${b.name}"
                }
            }
        }
        assertTrue(
            worst >= VISIBLE_SEPARATION_FLOOR,
            ("$worstPair are both in the collapsed row and measure ΔE %.1f, below the %.1f floor. " +
                "The visible row is chosen for distinctness; reordering ORDERED without re-checking that " +
                "is what this test exists to catch.")
                .format(worst, VISIBLE_SEPARATION_FLOOR),
        )
    }

    /** The visible row must also be *nameable*, not just far apart: five families a person would
     *  actually ask for. Asserted by membership so a reordering cannot drop one for a neighbour that
     *  happens to score better. */
    @Test
    fun `the visible row covers the families a person would name`() {
        val visible = HabitPalette.VISIBLE.toSet()
        mapOf(
            "a red" to setOf(HabitColor.CLAY, HabitColor.ROSE),
            "a warm yellow or sand" to setOf(HabitColor.SAND, HabitColor.OLIVE, HabitColor.TAN),
            "a green" to setOf(HabitColor.SAGE, HabitColor.LIME),
            "a blue" to setOf(HabitColor.BLUE, HabitColor.INDIGO, HabitColor.TEAL),
            "a violet" to setOf(HabitColor.LAVENDER),
        ).forEach { (description, family) ->
            assertTrue(
                visible.any { it in family },
                "the collapsed row must contain $description; it is the row most users will ever see",
            )
        }
    }

    /** [HabitPalette.VISIBLE] and [HabitPalette.COLLAPSED_REMAINDER] are what the picker draws
     *  before and after expanding, so between them they must be the whole palette exactly once —
     *  a colour in neither would be unreachable, and one in both would render twice. */
    @Test
    fun `the visible row and the remainder partition the palette`() {
        assertEquals(HabitPalette.ORDERED, HabitPalette.VISIBLE + HabitPalette.COLLAPSED_REMAINDER)
        assertTrue(
            HabitPalette.VISIBLE.none { it in HabitPalette.COLLAPSED_REMAINDER },
            "a colour in both halves would be drawn twice once the grid is expanded",
        )
    }

    @Test
    fun `every preset is fully opaque`() {
        HabitPalette.ORDERED.forEach { habitColor ->
            assertEquals(
                OPAQUE_ALPHA,
                habitColor.argb ushr ALPHA_SHIFT,
                "${habitColor.name} is not opaque; a translucent identity colour reads differently on every surface",
            )
        }
    }

    /**
     * The non-text floor the identity dot needs (WCAG 2.1 SC 1.4.11, 3:1), on every surface it is
     * drawn on: Background (Today, the list), Surface and SurfaceRaised (sheets, raised rows). This
     * is the floor `openspec/specs/visual-design-system/spec.md` states for a habit's colour.
     */
    @Test
    fun `every preset clears the non-text floor on every surface`() {
        val surfaces = mapOf(
            "Background" to ConstanzaColors.Background,
            "Surface" to ConstanzaColors.Surface,
            "SurfaceRaised" to ConstanzaColors.SurfaceRaised,
        )
        HabitPalette.ORDERED.forEach { habitColor ->
            surfaces.forEach { (name, surface) ->
                val ratio = contrastRatio(Color(habitColor.argb), surface)
                assertTrue(
                    ratio >= NON_TEXT_FLOOR,
                    "${habitColor.name} measured %.2f:1 on $name, below the 3:1 non-text floor".format(ratio),
                )
            }
        }
    }

    /** Muted means no preset shouts: none may be lighter than the custom band's ceiling, so a preset
     *  is never louder than anything the custom dialog could produce either. */
    @Test
    fun `every preset stays under the muted ceiling`() {
        HabitPalette.ORDERED.forEach { habitColor ->
            val ratio = contrastRatio(Color(habitColor.argb), ConstanzaColors.Background)
            assertTrue(
                ratio <= HABIT_BAND_CEILING,
                "${habitColor.name} measured %.2f:1, above the %.1f:1 muted ceiling".format(ratio, HABIT_BAND_CEILING),
            )
        }
    }

    /** [HabitPalette.contains] is what tells the editor whether to select a preset or the custom
     *  swatch, so both of its answers are asserted rather than only the interesting one. */
    @Test
    fun `contains distinguishes a preset from a custom colour`() {
        assertTrue(HabitPalette.contains(HabitPalette.DEFAULT))
        assertFalse(
            HabitPalette.contains(RETIRED_PASTEL_RED),
            "a colour from the retired six must read as custom, not as a preset",
        )
        assertFalse(
            HabitPalette.contains(RETIRED_LEGIBLE_BAND_GREEN),
            "a colour from the retired 21 must read as custom, not as a preset",
        )
    }

    /**
     * The default must be drawn by the *collapsed* picker, not merely be a palette member. A default
     * the visible row cannot show lands on the last circle, which is the "custom" affordance, so
     * every new habit would open looking as though someone had already mixed a colour by hand.
     */
    @Test
    fun `the default is a colour the collapsed row actually shows`() {
        assertTrue(HabitPalette.contains(HabitPalette.DEFAULT), "the default must be a real preset")
        assertTrue(
            HabitPalette.VISIBLE.any { it.argb == HabitPalette.DEFAULT },
            "the default must be one of the presets the collapsed row draws, not one behind the expander",
        )
    }

    @Test
    fun `every preset carries its own accessible label`() {
        HabitPalette.ORDERED.forEach { habitColor ->
            assertTrue(habitColor.labelRes != 0, "${habitColor.name} has no accessible label")
        }
        assertEquals(
            HabitPalette.ORDERED.size,
            HabitPalette.ORDERED.map { it.labelRes }.toSet().size,
            "two colours share one label, so a screen reader would announce them identically",
        )
    }

    /** CIE76 ΔE between two opaque ARGB ints — perceptual distance, via CIE Lab over D65. Good
     *  enough for "can a person tell these apart", which is all it is asked here. */
    private fun deltaE(argbA: Int, argbB: Int): Double {
        val (l1, a1, b1) = lab(argbA)
        val (l2, a2, b2) = lab(argbB)
        return sqrt((l1 - l2) * (l1 - l2) + (a1 - a2) * (a1 - a2) + (b1 - b2) * (b1 - b2))
    }

    private fun lab(argb: Int): Triple<Double, Double, Double> {
        val color = Color(argb)
        val r = linear(color.red)
        val g = linear(color.green)
        val b = linear(color.blue)
        val x = (X_R * r + X_G * g + X_B * b) / WHITE_X
        val y = Y_R * r + Y_G * g + Y_B * b
        val z = (Z_R * r + Z_G * g + Z_B * b) / WHITE_Z
        val fx = pivot(x)
        val fy = pivot(y)
        val fz = pivot(z)
        return Triple(LAB_L_SCALE * fy - LAB_L_OFFSET, LAB_A_SCALE * (fx - fy), LAB_B_SCALE * (fy - fz))
    }

    private fun linear(channel: Float): Double {
        val v = channel.toDouble()
        return if (v <= GAMMA_THRESHOLD) v / GAMMA_LINEAR_DIVISOR else ((v + GAMMA_OFFSET) / GAMMA_DIVISOR).pow()
    }

    private fun Double.pow(): Double = Math.pow(this, GAMMA_EXPONENT)

    private fun pivot(t: Double): Double =
        if (t > LAB_EPSILON) cbrt(t) else LAB_KAPPA * t + LAB_PIVOT_OFFSET

    private companion object {
        /** The graphite redesign's muted palette. */
        const val PALETTE_SIZE = 12

        /** Must match `HabitColorPicker`'s `SWATCHES_PER_ROW`. Duplicated rather than exposed: that
         *  constant is a layout detail of a private composable, and making it public so a test could
         *  read it would be the test dictating the production API. */
        const val SWATCHES_PER_ROW = 6

        /** "Clearly different at a glance" (CIE76). The twelve measure 10.8 at their closest. */
        const val GLOBAL_SEPARATION_FLOOR = 10.0

        /** Cells that touch in the grid are compared side by side. Measured minimum 17.5. */
        const val NEIGHBOUR_SEPARATION_FLOOR = 15.0

        /** The collapsed row is stricter still. Measured minimum 21.1. */
        const val VISIBLE_SEPARATION_FLOOR = 20.0

        /** WCAG 2.1 SC 1.4.11, non-text contrast. */
        const val NON_TEXT_FLOOR = 3.0

        /** Five, because six cells fit on one row at 360dp and one of them is the custom wheel.
         *  See [HabitPalette.VISIBLE] for the measurement. */
        const val VISIBLE_ROW_SIZE = 5

        /** One of the six warm-dark pastels this palette replaced. */
        const val RETIRED_PASTEL_RED = 0xFFFF9FA8.toInt()

        /** The 21-preset legible-band palette's `GREEN`, retired by Room v8. */
        const val RETIRED_LEGIBLE_BAND_GREEN = 0xFF4CAF50.toInt()

        const val OPAQUE_ALPHA = 0xFF
        const val ALPHA_SHIFT = 24

        // sRGB -> linear, then linear RGB -> CIE XYZ (D65), then XYZ -> Lab.
        const val GAMMA_THRESHOLD = 0.04045
        const val GAMMA_LINEAR_DIVISOR = 12.92
        const val GAMMA_OFFSET = 0.055
        const val GAMMA_DIVISOR = 1.055
        const val GAMMA_EXPONENT = 2.4
        const val X_R = 0.4124
        const val X_G = 0.3576
        const val X_B = 0.1805
        const val Y_R = 0.2126
        const val Y_G = 0.7152
        const val Y_B = 0.0722
        const val Z_R = 0.0193
        const val Z_G = 0.1192
        const val Z_B = 0.9505
        const val WHITE_X = 0.95047
        const val WHITE_Z = 1.08883
        const val LAB_EPSILON = 0.008856
        const val LAB_KAPPA = 7.787
        const val LAB_PIVOT_OFFSET = 16.0 / 116.0
        const val LAB_L_SCALE = 116.0
        const val LAB_L_OFFSET = 16.0
        const val LAB_A_SCALE = 500.0
        const val LAB_B_SCALE = 200.0
    }
}
