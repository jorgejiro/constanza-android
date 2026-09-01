package com.jjrapps.constanza.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The six colours a habit's identity may be. `argb` is the Compose-free spine: it is what
 * `HabitEditorViewModel`, Room, `NotificationPoster.setColor()` and the backup format all carry.
 * [composeColor] is a derived extension so no `androidx.compose.ui.graphics` import ever reaches a
 * ViewModel (design.md decision 1).
 *
 * Purple returns here (as [VIOLET]) now that orange has moved to [ConstanzaColors.Accent] — a hue
 * cannot be both the app's identity and a habit's identity (Engram #47).
 */
enum class HabitColor(val argb: Int) {
    RED(0xFFFF9FA8.toInt()),
    PINK(0xFFFFA8DC.toInt()),
    VIOLET(0xFFCBB2FF.toInt()),
    BLUE(0xFF8FC5FF.toInt()),
    TEAL(0xFF5DD6C7.toInt()),
    GREEN(0xFF8BDB95.toInt()),
}

/** The offered habit palette, in the fixed picker order. */
object HabitPalette {
    val ORDERED: List<HabitColor> = HabitColor.entries
    val ARGB: List<Int> = ORDERED.map { it.argb }
    val DEFAULT: Int = ARGB.first()
}

/** Compose-typed view of [HabitColor.argb], used only where a composable actually needs a [Color]. */
val HabitColor.composeColor: Color
    get() = Color(argb)
