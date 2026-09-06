package com.jjrapps.constanza.core.ui.theme

import kotlin.math.abs
import kotlin.math.roundToInt

private const val DEGREES_PER_TURN = 360f
private const val DEGREES_PER_SECTOR = 60f
private const val SECTORS = 6f
private const val CHANNEL_MAX = 255f
private const val OPAQUE_ALPHA = 0xFF
private const val BYTE_MASK = 0xFF
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val ALPHA_SHIFT = 24

// The six 60-degree sectors of the hue wheel, named where the `when` below would otherwise be a
// column of bare indices. Only the two detekt flags as magic are named; 0, 1 and 2 stay literal
// because naming half a run of indices reads worse than naming none of it.
private const val SECTOR_CYAN_TO_BLUE = 3
private const val SECTOR_BLUE_TO_MAGENTA = 4

/** The per-sector offsets in the standard RGB-to-hue formula, in sectors rather than degrees. */
private const val HUE_SECTORS_TO_BLUE = 4f

/**
 * A colour as hue/saturation/value, the axes the custom colour picker actually offers.
 *
 * Deliberately **not** `android.graphics.Color.HSVToColor`: that is framework code, so a rule about
 * it can only be checked on a device. Keeping the conversion as plain Kotlin here means
 * `HsvTest` proves the round trip in `:app:testDebugUnitTest`, in milliseconds, alongside every
 * other rule this app asserts on the JVM.
 *
 * [hue] is in `0f..360f` (wrapping), [saturation] and [value] in `0f..1f`. Values outside those
 * ranges are normalised on conversion rather than rejected, because both ends of this type are UI
 * sliders whose float arithmetic lands a hair outside its bounds routinely.
 */
data class Hsv(val hue: Float, val saturation: Float, val value: Float) {

    /** Opaque ARGB — the spine `Habit.colorArgb` carries. Alpha is always `0xFF`: a habit's
     *  identity colour is drawn as a solid fill everywhere, and a translucent one would read as a
     *  different colour on every surface it landed on. */
    fun toArgb(): Int {
        val h = ((hue % DEGREES_PER_TURN) + DEGREES_PER_TURN) % DEGREES_PER_TURN
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)

        val sector = h / DEGREES_PER_SECTOR
        val chroma = v * s
        val second = chroma * (1f - abs((sector % 2f) - 1f))
        val match = v - chroma
        val (r, g, b) = when (sector.toInt()) {
            0 -> Triple(chroma, second, 0f)
            1 -> Triple(second, chroma, 0f)
            2 -> Triple(0f, chroma, second)
            SECTOR_CYAN_TO_BLUE -> Triple(0f, second, chroma)
            SECTOR_BLUE_TO_MAGENTA -> Triple(second, 0f, chroma)
            else -> Triple(chroma, 0f, second)
        }
        return argbOf(byteOf(r + match), byteOf(g + match), byteOf(b + match))
    }

    private fun byteOf(channel: Float): Int = (channel * CHANNEL_MAX).roundToInt().coerceIn(0, BYTE_MASK)

    private fun argbOf(red: Int, green: Int, blue: Int): Int =
        (OPAQUE_ALPHA shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
}

/**
 * The inverse of [Hsv.toArgb], modulo the information HSV cannot carry: a greyscale colour has no
 * meaningful hue, so this returns `0f` for it rather than an arbitrary one. Alpha is discarded.
 *
 * Used to seed the picker from whatever colour the habit already holds, so opening it on an
 * existing custom colour starts where that colour is instead of resetting the user's choice.
 */
fun hsvOf(argb: Int): Hsv {
    val r = ((argb shr RED_SHIFT) and BYTE_MASK) / CHANNEL_MAX
    val g = ((argb shr GREEN_SHIFT) and BYTE_MASK) / CHANNEL_MAX
    val b = (argb and BYTE_MASK) / CHANNEL_MAX

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val chroma = max - min

    val hue = when {
        chroma == 0f -> 0f
        max == r -> DEGREES_PER_SECTOR * (((g - b) / chroma) % SECTORS)
        max == g -> DEGREES_PER_SECTOR * (((b - r) / chroma) + 2f)
        else -> DEGREES_PER_SECTOR * (((r - g) / chroma) + HUE_SECTORS_TO_BLUE)
    }
    val saturation = if (max == 0f) 0f else chroma / max
    return Hsv(((hue % DEGREES_PER_TURN) + DEGREES_PER_TURN) % DEGREES_PER_TURN, saturation, max)
}
