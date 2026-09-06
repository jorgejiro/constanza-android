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
 * The palette's own invariants. Contrast is `ColorContrastTest`'s job and is deliberately not
 * repeated here; what this file guards is everything else — that the set is wide, that no two of its
 * members read as one colour with two names, and that neighbouring cells in the grid are obviously
 * different.
 *
 * **Why CIE Lab ΔE and not a hue comparison.** The defect this replaces was six colours that were
 * *far apart in hue and still hard to tell apart*, because they shared one narrow band of lightness
 * and saturation — `RED` `#FF9FA8` and `PINK` `#FFA8DC` sat 32° apart and still read as one colour.
 * A hue-only rule would have passed that palette. ΔE measures the thing actually complained about.
 * [OLD_PALETTE_WORST_PAIR] is that pair's measured separation, used here as the calibration point:
 * it is the number a real person looked at and called too similar, so every threshold below is
 * stated relative to it rather than pulled from the air.
 */
class HabitPaletteTest {

    @Test
    fun `the palette offers a wide set of distinct colours`() {
        assertTrue(
            HabitPalette.ARGB.size >= MINIMUM_OFFERED,
            "the picker offers ${HabitPalette.ARGB.size} presets; the point of this change was a wide standard set",
        )
        assertEquals(
            HabitPalette.ARGB.size,
            HabitPalette.ARGB.toSet().size,
            "two families resolved to the same value, so the grid would show a duplicate circle",
        )
    }

    /**
     * No two swatches anywhere in the palette may be as close as the pair that prompted this change.
     * A twenty-three colour palette is necessarily denser than a six colour one, so the bar here is
     * "closer than the pair a person rejected", not "as far apart as six colours can be".
     */
    @Test
    fun `no two offered colours are as alike as the pair that was complained about`() {
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
            worst < OLD_PALETTE_WORST_PAIR,
            "expected a dense palette to have closer pairs than the old six; $worstPair measured %.1f".format(worst),
        )
        assertTrue(
            worst >= GLOBAL_SEPARATION_FLOOR,
            "$worstPair measured ΔE %.1f, below the %.1f floor — those two read as one colour with two names"
                .format(worst, GLOBAL_SEPARATION_FLOOR),
        )
    }

    /**
     * The assertion that carries the actual complaint. "The first two are too alike" was about two
     * cells sitting next to each other, so this walks the grid as it is drawn — six wide, checking
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

    /**
     * The collapsed row is what most users will ever see, so it carries the strictest separation
     * requirement in this file: these five must be *obviously* different from one another, not
     * merely different.
     *
     * The floor is stated as a multiple of the rejected pair rather than as a bare number, because
     * that is where it comes from: [OLD_PALETTE_WORST_PAIR] is what a person called too similar, and
     * a row of five chosen for distinctness should not be arguing about the margin.
     */
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
            ("$worstPair are both in the collapsed row and measure ΔE %.1f, below the %.1f floor " +
                "(the pair a person rejected measured %.1f). The visible row is chosen for distinctness; " +
                "reordering ORDERED without re-checking that is what this test exists to catch.")
                .format(worst, VISIBLE_SEPARATION_FLOOR, OLD_PALETTE_WORST_PAIR),
        )
    }

    /** The visible row must also be *nameable*, not just far apart: five families a person would
     *  actually ask for. Asserted by membership so a reordering cannot drop one for a neighbour that
     *  happens to score better. */
    @Test
    fun `the visible row covers the families a person would name`() {
        val visible = HabitPalette.VISIBLE.toSet()
        mapOf(
            "a red" to setOf(HabitColor.RED),
            "a warm yellow or orange" to setOf(HabitColor.AMBER, HabitColor.ORANGE, HabitColor.YELLOW),
            "a green" to setOf(HabitColor.GREEN, HabitColor.LIGHT_GREEN),
            "a blue" to setOf(HabitColor.BLUE, HabitColor.LIGHT_BLUE),
            "a violet or pink" to setOf(HabitColor.VIOLET, HabitColor.PURPLE, HabitColor.MAGENTA, HabitColor.PINK),
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

    /** The families the maintainer named as missing. Asserted by value, so a re-tone that quietly
     *  drops one back out of the palette fails here rather than on his device. */
    @Test
    fun `the palette carries the families that were missing from the old six`() {
        listOf(
            HabitColor.RED to "a true red, not a salmon",
            HabitColor.BROWN to "a brown",
            HabitColor.LILAC to "a lilac",
            HabitColor.BLUE to "a full-strength blue",
            HabitColor.LIGHT_BLUE to "a light blue distinct from it",
            HabitColor.MAGENTA to "a pink distinct from the red",
        ).forEach { (color, description) ->
            assertTrue(HabitPalette.contains(color.argb), "the palette must offer $description")
        }
        assertTrue(
            deltaE(HabitColor.RED.argb, HabitColor.MAGENTA.argb) >= GLOBAL_SEPARATION_FLOOR,
            "the red and the pink must not repeat the RED/PINK collision of the old palette",
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

    /** Spec `Accent Reserved For Chrome`: the accent must not be selectable as a habit colour. */
    @Test
    fun `the chrome accent is not offered as a habit colour`() {
        assertFalse(
            HabitPalette.contains(ConstanzaColors.Accent.toArgb()),
            "the accent must not be selectable as a habit identity colour",
        )
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
        /** The maintainer asked for "many more" than six, of the order of twenty. */
        const val MINIMUM_OFFERED = 20

        /** Must match `HabitColorPicker`'s `SWATCHES_PER_ROW`. Duplicated rather than exposed: that
         *  constant is a layout detail of a private composable, and making it public so a test could
         *  read it would be the test dictating the production API. */
        const val SWATCHES_PER_ROW = 6

        /** ΔE between the old palette's `RED` (`#FF9FA8`) and `PINK` (`#FFA8DC`) — the pair a real
         *  person looked at and called indistinguishable. Every threshold here is relative to it. */
        const val OLD_PALETTE_WORST_PAIR = 23.9

        /** No two colours anywhere may be closer than this. Below the calibration point, because a
         *  twenty-three colour palette is legitimately denser than a six colour one. */
        const val GLOBAL_SEPARATION_FLOOR = 14.0

        /** Cells that touch in the grid, though, must be far clearer than the rejected pair. */
        const val NEIGHBOUR_SEPARATION_FLOOR = 40.0

        /** The collapsed row is stricter still: roughly twice the rejected pair's separation. */
        const val VISIBLE_SEPARATION_FLOOR = 48.0

        /** Five, because six cells fit on one row at 360dp and one of them is the custom wheel.
         *  See [HabitPalette.VISIBLE] for the measurement. */
        const val VISIBLE_ROW_SIZE = 5

        /** One of the six warm-dark pastels this palette replaced. */
        const val RETIRED_PASTEL_RED = 0xFFFF9FA8.toInt()

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
