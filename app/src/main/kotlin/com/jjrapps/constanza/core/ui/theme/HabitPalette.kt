package com.jjrapps.constanza.core.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.jjrapps.constanza.R

/**
 * The standard colours a habit's identity may be. `argb` is the Compose-free spine: it is what
 * `HabitEditorViewModel`, Room, `NotificationPoster.setColor()` and the backup format all carry.
 * [composeColor] is a derived extension so no `androidx.compose.ui.graphics` import ever reaches a
 * ViewModel (design.md decision 1).
 *
 * ## What was wrong with the six this replaces
 *
 * They were hand-mixed pastels tuned to the warm-dark ramp, and every complaint about them traces
 * to one structural cause: **all six sat in the same narrow band of high value and low-to-mid
 * saturation.** `RED` (`#FF9FA8`) and `PINK` (`#FFA8DC`) measured ΔE 23.9 apart and read as one
 * colour with two names; `RED` was a salmon rather than a red; and brown, deep blue and lilac were
 * not merely missing but *unreachable*, because a pastel brown is beige and a pastel deep blue is
 * just blue. Widening a pastel band only produces more pastels, so this palette abandons the band.
 *
 * ## Coverage over harmony, deliberately
 *
 * These are Material's own families, spread across the whole hue wheel **and** across lightness and
 * saturation. That is a conceded trade: a standard red or a violet does not harmonise with a warm
 * `#110B06` ground the way the pastels did. Coverage wins because it is what was asked for twice
 * (more colours; specifically red, brown, lilac and deep blue) while harmony was asked for once, as
 * a preference — and because the two cannot both be had, by the argument above.
 *
 * ## Which tone each family takes, and why it is not one tone for all
 *
 * A single tone is what produced the flat band. Instead each family takes **the deepest Material
 * tone that still clears the ratified 4.5:1 floor, except where deepening destroys the family's
 * identity** — a dark yellow is olive, a dark amber is brown — in which case it takes the tone that
 * still reads as its own name. So `RED` is Red 500, a true red at 4.53:1, while `YELLOW` is Yellow
 * 500 and `PEACH` is Orange 200. `ColorContrastTest` measures the floor; the worst member is Red
 * 500 at 4.53:1 against `ConstanzaColors.SurfaceSelected`.
 *
 * **The one thing that could not be delivered, stated plainly.** A true navy or a chocolate brown is
 * impossible here and no tuning reaches it. Clearing 4.5:1 against `SurfaceSelected` requires
 * relative luminance ≥ 0.233; navy `#1A237E` is 0.029 and brown `#5D4037` is 0.063 — 1.26:1 and
 * 1.79:1, invisible dots on a near-black row. Even WCAG's *non-text* 3:1 floor needs ≥ 0.139, which
 * neither reaches. What is delivered instead is the family distinction: [BLUE] is a full-strength
 * blue that reads as the dark one beside [LIGHT_BLUE], [CYAN] and [MINT]; [BROWN] is the deepest
 * legible brown; [OLIVE] carries the dark earthy slot that a dark yellow would have.
 *
 * ## Why the declaration order looks shuffled
 *
 * It is the grid order, and it is scattered on purpose. The complaint was about telling neighbours
 * apart, so no two cells adjacent in the six-wide grid — across **or** down — are near in colour:
 * the smallest such gap is ΔE 66.1, against the ΔE 23.9 pair that prompted the complaint.
 * `HabitPaletteTest` asserts that rather than trusting this paragraph. A hue-sorted order would put
 * every near-neighbour side by side, which is exactly the arrangement being avoided.
 *
 * The first five are [HabitPalette.VISIBLE], the row shown before the grid is expanded, and they are
 * first so that expanding adds rows underneath instead of rearranging what is already on screen.
 *
 * [labelRes] is not decoration. This is a radio group of twenty-four circles, and colour is never
 * this app's sole recognition channel (`HabitColorDot`'s KDoc, design.md decision 6), so every
 * swatch carries its colour's name as its accessible label rather than relying on the fill alone.
 */
enum class HabitColor(val argb: Int, @param:StringRes val labelRes: Int) {
    // The visible row (see HabitPalette.VISIBLE). These five come first because the collapsed
    // picker draws exactly them, and keeping them at the head means expanding the grid adds rows
    // below rather than rearranging the row the user is already looking at.
    RED(0xFFF44336.toInt(), R.string.habit_color_red), // Red 500
    AMBER(0xFFFFC107.toInt(), R.string.habit_color_amber), // Amber 500
    GREEN(0xFF4CAF50.toInt(), R.string.habit_color_green), // Green 500
    LIGHT_BLUE(0xFF03A9F4.toInt(), R.string.habit_color_light_blue), // Light Blue 500
    VIOLET(0xFFE040FB.toInt(), R.string.habit_color_violet), // Purple A200

    // The remaining eighteen, revealed by the expander. Cell 5 of the first row is the custom
    // wheel, so these start row two.
    LILAC(0xFF9575CD.toInt(), R.string.habit_color_lilac), // Deep Purple 300
    TEAL(0xFF009688.toInt(), R.string.habit_color_teal), // Teal 500
    MAGENTA(0xFFF06292.toInt(), R.string.habit_color_magenta), // Pink 300
    MINT(0xFF64FFDA.toInt(), R.string.habit_color_mint), // Teal A200
    OLIVE(0xFF9E9D24.toInt(), R.string.habit_color_olive), // Lime 800
    BLUE_GREY(0xFF78909C.toInt(), R.string.habit_color_blue_grey), // Blue Grey 400

    YELLOW(0xFFFFEB3B.toInt(), R.string.habit_color_yellow), // Yellow 500
    PURPLE(0xFFBA68C8.toInt(), R.string.habit_color_purple), // Purple 300
    CYAN(0xFF0097A7.toInt(), R.string.habit_color_cyan), // Cyan 700
    PEACH(0xFFFFCC80.toInt(), R.string.habit_color_peach), // Orange 200
    INDIGO(0xFF7986CB.toInt(), R.string.habit_color_indigo), // Indigo 300
    LIGHT_GREEN(0xFF7CB342.toInt(), R.string.habit_color_light_green), // Light Green 600

    BROWN(0xFFA1887F.toInt(), R.string.habit_color_brown), // Brown 300
    LIME(0xFFC0CA33.toInt(), R.string.habit_color_lime), // Lime 600
    PINK(0xFFF48FB1.toInt(), R.string.habit_color_pink), // Pink 200
    BLUE(0xFF2196F3.toInt(), R.string.habit_color_blue), // Blue 500
    ORANGE(0xFFFF9800.toInt(), R.string.habit_color_orange), // Orange 500
    SILVER(0xFFE0E0E0.toInt(), R.string.habit_color_silver), // Grey 300
}

/**
 * The offered standard palette, in the fixed grid order (see [HabitColor]'s KDoc for why that order
 * is scattered rather than sorted by hue).
 *
 * This is no longer the set of colours a habit *may* hold — the picker also offers a free custom
 * colour, so `Habit.colorArgb` is any ARGB int, as it always was at the storage layer. What this
 * list still is, exactly, is the set of colours the picker offers as named presets, which is what
 * [contains] is for: the editor asks it whether the current colour is a preset or a custom one.
 */
object HabitPalette {
    val ORDERED: List<HabitColor> = HabitColor.entries
    val ARGB: List<Int> = ORDERED.map { it.argb }

    /**
     * The five colours the picker shows before anything is expanded — the whole palette collapsed
     * down to one row, with the custom wheel as that row's sixth and last circle.
     *
     * **Five, not six, and the number was measured rather than chosen.** This repo's stated phone
     * width is 360dp (`TodaySlotRowComposeTest`), the editor form pads 16dp each side, and a swatch
     * occupies a 48dp touch target with a 4dp gap. That leaves 328dp, which fits six cells at 308dp
     * and cannot fit seven at 360dp. One of those six cells is the custom wheel, so five colours are
     * what remains. Widening the row would mean shrinking the touch target below the 48dp minimum,
     * which is not a trade worth making to gain one swatch.
     *
     * **Which five, and how they were picked.** By measured CIE Lab ΔE, not by eye — the standing
     * complaint about this palette has always been that neighbouring colours were hard to tell
     * apart. These are the five-colour subset with the largest minimum pairwise separation, subject
     * to covering five families a person can name: a red, a warm yellow-orange, a green, a blue and
     * a violet. Their minimum pairwise ΔE is **71.4**, against the **23.9** measured between the old
     * palette's `RED` and `PINK` — the pair a real person looked at and called indistinguishable.
     * `HabitPaletteTest` asserts both the separation and the family coverage, so a future reordering
     * cannot quietly degrade this row back toward that 23.9.
     *
     * Everything not in here is still one tap away behind the expander; nothing is hidden for good.
     */
    val VISIBLE: List<HabitColor> = ORDERED.take(VISIBLE_COUNT)

    /** The eighteen the expander reveals, in grid order below [VISIBLE]. */
    val COLLAPSED_REMAINDER: List<HabitColor> = ORDERED.drop(VISIBLE_COUNT)

    /**
     * A new habit's colour. Named explicitly rather than taken as `ARGB.first()`, because the grid
     * order is arranged for visual separation between neighbours and its first cell is therefore an
     * arbitrary choice, not a considered default.
     *
     * **It must be one of [VISIBLE], and that is a correctness requirement rather than a
     * preference.** The picker opens collapsed, and its last circle stands in for any colour the
     * visible row cannot show. A default outside the row would therefore put every brand-new habit
     * on that stand-in circle — the affordance that means "custom" — before the user had chosen
     * anything at all. `HabitPaletteTest` asserts the membership; it was an instrumented failure
     * that found this, when the default was still a colour the collapsed row does not draw.
     *
     * [HabitColor.LIGHT_BLUE] among the five because it is the quietest of them against this app's
     * warm ground: a colour assigned rather than chosen should not shout.
     */
    val DEFAULT: Int = HabitColor.LIGHT_BLUE.argb

    /** Whether [argb] is one of the offered presets. `false` means "custom", not "invalid". */
    fun contains(argb: Int): Boolean = argb in ARGB_SET

    private val ARGB_SET: Set<Int> = ARGB.toSet()
}

/** How many presets the collapsed picker shows. See [HabitPalette.VISIBLE] for why it is five. */
private const val VISIBLE_COUNT = 5

/** Compose-typed view of [HabitColor.argb], used only where a composable actually needs a [Color]. */
val HabitColor.composeColor: Color
    get() = Color(argb)
