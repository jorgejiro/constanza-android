package com.jjrapps.constanza.core.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The custom picker's colour arithmetic. This is a JVM test rather than an instrumented one purely
 * because [Hsv] is plain Kotlin instead of `android.graphics.Color.HSVToColor` — the reason that
 * choice was made.
 */
class HsvTest {

    @Test
    fun `the primaries land on their expected hues`() {
        assertEquals(0xFFFF0000.toInt(), Hsv(0f, 1f, 1f).toArgb(), "0 degrees is red")
        assertEquals(0xFF00FF00.toInt(), Hsv(HUE_GREEN, 1f, 1f).toArgb(), "120 degrees is green")
        assertEquals(0xFF0000FF.toInt(), Hsv(HUE_BLUE, 1f, 1f).toArgb(), "240 degrees is blue")
    }

    @Test
    fun `zero saturation is greyscale and zero value is black`() {
        assertEquals(0xFFFFFFFF.toInt(), Hsv(HUE_GREEN, 0f, 1f).toArgb())
        assertEquals(0xFF000000.toInt(), Hsv(HUE_GREEN, 1f, 0f).toArgb())
    }

    @Test
    fun `every offered preset survives a round trip through HSV`() {
        HabitPalette.ORDERED.forEach { habitColor ->
            val roundTripped = hsvOf(habitColor.argb).toArgb()
            assertEquals(
                habitColor.argb,
                roundTripped,
                "${habitColor.name} came back as 0x%08X".format(roundTripped),
            )
        }
    }

    /** The picker's own loop: seed from a colour, move nothing, confirm. A conversion that drifted
     *  by a single channel step here would silently change a habit's colour every time its owner
     *  opened the picker and cancelled out of it. */
    @Test
    fun `an arbitrary custom colour survives a round trip`() {
        listOf(0xFF3D2B1F, 0xFF7F7F7F, 0xFF012345, 0xFFFEDCBA, 0xFF00FF7F).forEach { value ->
            val argb = value.toInt()
            assertEquals(argb, hsvOf(argb).toArgb(), "0x%08X did not survive the round trip".format(argb))
        }
    }

    @Test
    fun `alpha is always opaque, whatever went in`() {
        assertEquals(OPAQUE_ALPHA, hsvOf(0x00123456).toArgb() ushr ALPHA_SHIFT)
        assertEquals(OPAQUE_ALPHA, Hsv(HUE_BLUE, 0.5f, 0.5f).toArgb() ushr ALPHA_SHIFT)
    }

    /** Both ends of this type are sliders, whose float arithmetic overshoots routinely. Normalising
     *  rather than throwing is deliberate — a picker that crashed on 360.00003 would be absurd. */
    @Test
    fun `out-of-range components are normalised rather than rejected`() {
        assertEquals(Hsv(0f, 1f, 1f).toArgb(), Hsv(FULL_TURN, 1f, 1f).toArgb(), "360 degrees wraps to 0")
        assertEquals(Hsv(0f, 1f, 1f).toArgb(), Hsv(-FULL_TURN, 1f, 1f).toArgb(), "a negative hue wraps forward")
        assertEquals(Hsv(HUE_BLUE, 1f, 1f).toArgb(), Hsv(HUE_BLUE, OVERSHOOT, OVERSHOOT).toArgb())
    }

    @Test
    fun `a grey reports no hue rather than an arbitrary one`() {
        val grey = hsvOf(0xFF808080.toInt())
        assertEquals(0f, grey.hue)
        assertEquals(0f, grey.saturation)
        assertTrue(grey.value > 0f, "a mid grey still has a brightness")
    }

    private companion object {
        const val HUE_GREEN = 120f
        const val HUE_BLUE = 240f
        const val FULL_TURN = 360f
        const val OVERSHOOT = 1.4f
        const val OPAQUE_ALPHA = 0xFF
        const val ALPHA_SHIFT = 24
    }
}
