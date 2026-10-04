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
 * ## Why twelve muted colours (graphite redesign, T2)
 *
 * The graphite redesign stopped painting a habit's colour on its *name*: names render in the text
 * colour, and the habit's colour survives only as an 8dp identity dot. A dot is a **non-text**
 * graphic, so the floor that matters is WCAG 1.4.11's 3:1, not a text floor, and the previous
 * palette's `[7:1, 11:1]` band — which existed only because the colour *was* the body text — no
 * longer has a job. What the owner asked for instead is calm: twelve desaturated tones that sit on
 * the neutral graphite ground without shouting, and stay mutually distinguishable.
 *
 * ## What this palette measures
 *
 * Against [ConstanzaColors.Background] (`#141416`) every preset measures **6.20:1** ([LAVENDER]) to
 * **8.37:1** ([LIME]); against `SurfaceRaised` (`#232327`, the lightest surface a dot is drawn on)
 * the lowest is still **5.27:1** — every one far above the 3:1 non-text floor. Global minimum
 * pairwise CIE76 ΔE **10.8** ([SAND]/[TAN]); minimum ΔE between grid-adjacent cells **17.5**
 * ([TEAL]/[GREY]); [HabitPalette.VISIBLE] row minimum pairwise ΔE **21.1**. `HabitPaletteTest`
 * asserts every one of these rather than trusting this paragraph.
 *
 * ## The previous palettes are history, not deleted
 *
 * The 21 retired presets still exist as literal ints in the migrations that rewrote them:
 * `HabitColorMutedRemap` (Room v7 -> v8, backup schema 5) maps each onto one of the twelve below.
 * Nothing outside those frozen migration files may refer to a retired colour.
 *
 * ## Declaration order
 *
 * It is the grid order. The first five are [HabitPalette.VISIBLE], the row shown before the grid is
 * expanded — five hue families a person can name (green, blue, sand/yellow, clay/red, lavender) —
 * and they come first so that expanding adds rows underneath instead of rearranging what is
 * already on screen.
 *
 * [labelRes] is not decoration. This is a radio group of thirteen circles (12 presets plus the
 * custom wheel), and colour is never this app's sole recognition channel (design.md decision 6), so
 * every swatch carries its colour's name as its accessible label rather than relying on the fill.
 */
enum class HabitColor(val argb: Int, @param:StringRes val labelRes: Int) {
    SAGE(0xFF7FA889.toInt(), R.string.habit_color_sage), // 6.89:1 on Background
    BLUE(0xFF7D9CC4.toInt(), R.string.habit_color_blue), // 6.50:1
    SAND(0xFFC4A77A.toInt(), R.string.habit_color_sand), // 8.02:1
    CLAY(0xFFC48A7D.toInt(), R.string.habit_color_clay), // 6.38:1
    LAVENDER(0xFFA98BB8.toInt(), R.string.habit_color_lavender), // 6.20:1

    TEAL(0xFF6FA6A6.toInt(), R.string.habit_color_teal), // 6.73:1
    OLIVE(0xFFB5B072.toInt(), R.string.habit_color_olive), // 8.25:1
    ROSE(0xFFC2879F.toInt(), R.string.habit_color_rose), // 6.36:1
    INDIGO(0xFF8F94C9.toInt(), R.string.habit_color_indigo), // 6.35:1
    LIME(0xFF9CB87A.toInt(), R.string.habit_color_lime), // 8.37:1
    TAN(0xFFB89A80.toInt(), R.string.habit_color_tan), // 6.98:1
    GREY(0xFF9AA0A8.toInt(), R.string.habit_color_grey), // 6.98:1
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
     * **Which five, and how they were picked.** By measured CIE Lab ΔE, not by eye: five hue
     * families a person can name — a green ([HabitColor.SAGE]), a blue, a warm sand, a clay red and
     * a lavender. Muting the palette compresses separation (everything shares one band of lightness
     * and saturation), so these five are the most mutually distinct of the twelve: minimum pairwise
     * ΔE **21.1**, against **10.8** for the closest pair in the whole palette. `HabitPaletteTest`
     * asserts both the separation and the family coverage.
     *
     * Everything not in here is still one tap away behind the expander; nothing is hidden for good.
     */
    val VISIBLE: List<HabitColor> = ORDERED.take(VISIBLE_COUNT)

    /** The remaining seven the expander reveals, in grid order below [VISIBLE]. */
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
     * [HabitColor.BLUE] among the five because it is the most neutral of them on the graphite
     * ground: a colour assigned rather than chosen should not carry a mood.
     */
    val DEFAULT: Int = HabitColor.BLUE.argb

    /** Whether [argb] is one of the offered presets. `false` means "custom", not "invalid". */
    fun contains(argb: Int): Boolean = argb in ARGB_SET

    private val ARGB_SET: Set<Int> = ARGB.toSet()
}

/** How many presets the collapsed picker shows. See [HabitPalette.VISIBLE] for why it is five. */
private const val VISIBLE_COUNT = 5

/** Compose-typed view of [HabitColor.argb], used only where a composable actually needs a [Color]. */
val HabitColor.composeColor: Color
    get() = Color(argb)
