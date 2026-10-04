package com.jjrapps.constanza.habit

import androidx.compose.ui.graphics.Color
import com.jjrapps.constanza.core.ui.theme.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The time picker's selected hour/minute half must be distinguishable from the unselected half,
 * asserted on the tones [ReminderTimeField]'s dialog actually passes to `TimePickerDefaults.colors`.
 *
 * This is the T1 review's warning turned into a guard: the selected half was once marked only by
 * `SurfaceSelected` against `SurfaceRaised`, about 1.1:1. The two states are now required to sit at
 * least 3:1 apart (WCAG 2.1 SC 1.4.11, a state of a user-interface component), and each numeral to
 * read at 4.5:1 on its own container (SC 1.4.3).
 */
class ReminderTimeSelectorContrastTest {

    @Test
    fun `the selected half stands at least 3 to 1 apart from the unselected half`() {
        assertAtLeast(
            NON_TEXT_FLOOR,
            ReminderTimeSelectorTones.SelectedContainer,
            ReminderTimeSelectorTones.UnselectedContainer,
            "selected vs unselected container",
        )
    }

    @Test
    fun `the selected half stands out from the dialog surface it sits on`() {
        assertAtLeast(
            NON_TEXT_FLOOR,
            ReminderTimeSelectorTones.SelectedContainer,
            ReminderTimeSelectorTones.DialogSurface,
            "selected container vs dialog surface",
        )
    }

    @Test
    fun `each half's numerals are legible on their own container`() {
        assertAtLeast(
            TEXT_FLOOR,
            ReminderTimeSelectorTones.SelectedContent,
            ReminderTimeSelectorTones.SelectedContainer,
            "selected numerals",
        )
        assertAtLeast(
            TEXT_FLOOR,
            ReminderTimeSelectorTones.UnselectedContent,
            ReminderTimeSelectorTones.UnselectedContainer,
            "unselected numerals",
        )
    }

    private fun assertAtLeast(floor: Double, a: Color, b: Color, what: String) {
        val ratio = contrastRatio(a, b)
        assertTrue(ratio >= floor, "$what measured %.2f:1, below the %.1f:1 floor".format(ratio, floor))
    }

    private companion object {
        const val NON_TEXT_FLOOR = 3.0
        const val TEXT_FLOOR = 4.5
    }
}
