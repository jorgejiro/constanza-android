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
 * ## Why this is a re-tone rather than the original palette
 *
 * The habit's own colour is becoming the app's only chroma, painted directly on the habit's name
 * text — colour stopped being decoration and became load-bearing body text. Measured as name text
 * on `ConstanzaColors.Background` (`#110B06`), the previous 23 presets spanned 5.31:1 to 16.01:1, a
 * 3.02x spread: `OnBackgroundMuted` (this app's own "dimmed" tone) sits at 4.93:1, so a `RED` habit
 * read as de-emphasised while a `YELLOW` one read as emphasised — a hierarchy that tracked nothing
 * but which family a person had picked.
 *
 * The fix is a **contrast band of [7:1, 11:1]**, not a uniform tone. Uniformity was tried and
 * rejected: pinning everything to one contrast collapses the global minimum pairwise CIE-Lab ΔE from
 * 15.0 to 3.7 (amber, yellow, olive, lime, peach and orange all become the same khaki). A band wide
 * enough to keep families apart, narrow enough that none reads as louder than another, is what
 * remains.
 *
 * ## What this re-tone measures
 *
 * 22 presets, contrast band **6.98:1 – 11.05:1** on [ConstanzaColors.Background], spread **1.58x**
 * (was 3.02x). Global minimum pairwise ΔE **16.1** ([TEAL]/[MINT]) — *better* than the 15.0 the
 * previous palette had. Minimum ΔE between grid-adjacent cells (across **and** down) **68.7**
 * ([BLUE_GREY]/[MAGENTA]) — better than the 66.1 the previous palette measured. [HabitPalette.VISIBLE]
 * row minimum pairwise ΔE **56.7**. `HabitPaletteTest` asserts every one of these rather than
 * trusting this paragraph.
 *
 * Four families were hue-retoned because clamping to the band compressed the lightness that had
 * been separating them: [YELLOW] 54°→50°, [LIME] 65°→70°, [BLUE] 207°→212°, and [RED] 4°→12° (see
 * below).
 *
 * ## Why RED changed hue rather than just tone
 *
 * The previous palette's own KDoc recorded that its `RED` "was a salmon rather than a red" — that
 * complaint is *why* this paragraph exists, not something this re-tone repeats. A bright true red
 * does not exist on this ground: `#FF0000` reaches only 4.89:1, below Red 500's own 5.31:1, because
 * the red channel carries just 0.2126 of relative luminance (`ContrastingInk.kt`'s luminance
 * weights). The salmon this replaces, `#FF9FA8`, measured 10.03:1 — it failed for being *pale*, the
 * opposite defect.
 *
 * Two routes reach 7:1. Desaturating at hue 4° gives `#FF6B60`, a coral whose nearest neighbour is
 * [MAGENTA] at ΔE 32.9 — leaning toward the pink family that drew the original complaint. Shifting
 * hue to 12° at 72% saturation gives `#FF6D48`, closer to Red 500 (ΔE 15.0 vs 18.4) and better
 * separated from its nearest neighbour ([ORANGE] at ΔE 38.0). The hue shift was chosen: `RED` leans
 * vermilion, the opposite direction from salmon.
 *
 * ## Coverage over harmony, deliberately
 *
 * These are Material's own families, spread across the whole hue wheel **and** across lightness and
 * saturation. That is a conceded trade: a standard red or a violet does not harmonise with a warm
 * `#110B06` ground the way a pastel band would. Coverage wins because it is what was asked for
 * (more colours; specifically red, brown, lilac and deep blue) while harmony was asked for once, as
 * a preference.
 *
 * ## Why the declaration order looks shuffled
 *
 * It is the grid order, and it is scattered on purpose — chosen to maximise separation between
 * cells that sit next to each other. No two cells adjacent in the six-wide grid — across **or**
 * down — are near in colour: the smallest such gap is ΔE 68.7, against the ΔE 23.9 pair the original
 * pastel palette's neighbours measured. `HabitPaletteTest` asserts that rather than trusting this
 * paragraph. A hue-sorted order would put every near-neighbour side by side, which is exactly the
 * arrangement being avoided.
 *
 * The first five are [HabitPalette.VISIBLE], the row shown before the grid is expanded, and they are
 * first so that expanding adds rows underneath instead of rearranging what is already on screen.
 *
 * [labelRes] is not decoration. This is a radio group of twenty-three circles (22 presets plus the
 * custom wheel), and colour is never this app's sole recognition channel (design.md decision 6) —
 * the colour overhaul makes the point sharper rather than retiring it: the habit's *name* is now
 * itself the non-colour channel a swatch's colour is checked against, so every swatch still carries
 * its colour's name as its accessible label rather than relying on the fill alone.
 */
enum class HabitColor(val argb: Int, @param:StringRes val labelRes: Int) {
    GREEN(0xFF4CAF50.toInt(), R.string.habit_color_green), // Green 500, 7.03:1
    VIOLET(0xFFE860FF.toInt(), R.string.habit_color_violet), // Purple A200, 7.03:1
    RED(0xFFFF6D48.toInt(), R.string.habit_color_red), // Red 500, 7.01:1
    LIGHT_BLUE(0xFF03A9F4.toInt(), R.string.habit_color_light_blue), // Light Blue 500, 7.43:1
    AMBER(0xFFF5B907.toInt(), R.string.habit_color_amber), // Amber 500, 11.01:1

    LILAC(0xFFB992FF.toInt(), R.string.habit_color_lilac), // Deep Purple 300, 8.02:1
    OLIVE(0xFFA19F25.toInt(), R.string.habit_color_olive), // Lime 800, 6.98:1
    BLUE_GREY(0xFF849FAC.toInt(), R.string.habit_color_blue_grey), // Blue Grey 400, 7.01:1
    MAGENTA(0xFFFC6799.toInt(), R.string.habit_color_magenta), // Pink 300, 7.02:1
    MINT(0xFF55D7B8.toInt(), R.string.habit_color_mint), // Teal A200, 10.98:1
    BLUE(0xFF469DFF.toInt(), R.string.habit_color_blue), // Blue 500, 7.01:1

    PEACH(0xFFE9BA75.toInt(), R.string.habit_color_peach), // Orange 200, 10.93:1
    INDIGO(0xFF8896E3.toInt(), R.string.habit_color_indigo), // Indigo 300, 7.00:1
    YELLOW(0xFFDEC233.toInt(), R.string.habit_color_yellow), // Yellow 500, 11.05:1
    CYAN(0xFF00ABBD.toInt(), R.string.habit_color_cyan), // Cyan 700, 7.04:1
    ORANGE(0xFFFF9800.toInt(), R.string.habit_color_orange), // Orange 500, 9.07:1
    TEAL(0xFF00AE9D.toInt(), R.string.habit_color_teal), // Teal 500, 7.02:1

    PURPLE(0xFFD477E4.toInt(), R.string.habit_color_purple), // Purple 300, 7.04:1
    LIGHT_GREEN(0xFF7CB342.toInt(), R.string.habit_color_light_green), // Light Green 600, 7.80:1
    PINK(0xFFF48FB1.toInt(), R.string.habit_color_pink), // Pink 200, 8.76:1
    LIME(0xFFB1CA33.toInt(), R.string.habit_color_lime), // Lime 600, 10.58:1
    BROWN(0xFFB0958B.toInt(), R.string.habit_color_brown), // Brown 300, 7.00:1
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
     * apart, and separately every family shown here still must clear the [7:1, 11:1] contrast band
     * this re-tone measures. These five cover five families a person can name: a red, a warm
     * yellow-orange, a green, a blue and a violet. Their minimum pairwise ΔE is **56.7**, against
     * the **23.9** measured between the old palette's `RED` and `PINK` — the pair a real person
     * looked at and called indistinguishable. `HabitPaletteTest` asserts both the separation and the
     * family coverage, so a future reordering cannot quietly degrade this row back toward that 23.9.
     *
     * Everything not in here is still one tap away behind the expander; nothing is hidden for good.
     */
    val VISIBLE: List<HabitColor> = ORDERED.take(VISIBLE_COUNT)

    /** The remaining seventeen the expander reveals, in grid order below [VISIBLE]. */
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
