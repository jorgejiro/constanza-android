package com.jjrapps.constanza.habit

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.theme.HabitColor
import com.jjrapps.constanza.core.ui.theme.HabitPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One of the six warm-dark pastels this palette replaced. Real habits on the maintainer's device
 *  hold values like this one, which is the case the custom swatch has to carry. */
private const val RETIRED_PASTEL_TEAL = 0xFF5DD6C7.toInt()

private const val MID_SLIDER = 0.5f

/** A preset in the collapsed row that is deliberately not [HabitPalette.DEFAULT], so "selecting
 *  one" is a real change of selection rather than a click on the swatch that was already ticked. */
private val TARGET_COLOR = HabitColor.GREEN

/** A preset that is NOT in the collapsed row — the case the expander exists for. */
private val HIDDEN_COLOR = HabitColor.BROWN

/**
 * The colour picker, driven through the real [HabitEditorScreen] rather than through
 * [HabitColorGrid] in isolation — the thing being asserted is what a user can do in the editor, and
 * the grid's own callbacks are only interesting once they are wired to the form.
 *
 * No repository and no ViewModel: [HabitEditorScreen] is presentational, so the colour is held in a
 * local state here and the assertions are about what the screen reports and what it renders back.
 * That is deliberately a smaller fixture than [HabitEditorComposeTest]'s, which needs a database
 * because it asserts on persistence.
 *
 * Selection is asserted through `assertIsSelected`, which reads the `Role.RadioButton` semantics
 * each swatch publishes — not by sampling pixels. The tick's *colour* is not asserted here at all:
 * that rule is arithmetic and belongs in `ContrastingInkTest`, where it can be swept over the whole
 * colour cube in milliseconds instead of one device pixel at a time.
 */
@RunWith(AndroidJUnit4::class)
class HabitColorPickerComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var reportedColor: Int = 0

    private fun text(resId: Int) = ApplicationProvider.getApplicationContext<Context>().getString(resId)

    /** Renders the editor with [initialColor] selected, keeping the reported colour in state so the
     *  grid re-renders its selection exactly as it would in the app. */
    private fun setEditorContent(initialColor: Int) {
        reportedColor = initialColor
        composeTestRule.setContent {
            var color by remember { mutableIntStateOf(initialColor) }
            HabitEditorScreen(
                state = HabitEditorUiState(name = "Stretch", colorArgb = color),
                actions = HabitEditorActions(
                    onNameChange = {},
                    onColorChange = {
                        color = it
                        reportedColor = it
                    },
                    onNotesChange = {},
                    onSave = {},
                ),
                onScheduleParamChange = {},
                onSlotAction = {},
            )
        }
    }

    /** The default state, and the one the height objection was about: one row of presets plus the
     *  custom wheel, and nothing else drawn until it is asked for. */
    @Test
    fun thePickerOpensCollapsedShowingOnlyTheVisibleRowAndTheCustomWheel() {
        setEditorContent(HabitPalette.DEFAULT)

        HabitPalette.VISIBLE.forEach { habitColor ->
            composeTestRule.onNodeWithTag(habitColorSwatchTestTag(habitColor.argb)).assertExists()
        }
        HabitPalette.COLLAPSED_REMAINDER.forEach { habitColor ->
            composeTestRule.onNodeWithTag(habitColorSwatchTestTag(habitColor.argb)).assertDoesNotExist()
        }
        composeTestRule.onNodeWithTag(HABIT_COLOR_CUSTOM_SWATCH_TEST_TAG).assertExists()
    }

    /** Editing an existing habit opens collapsed too — the expander is a choice the user makes, not
     *  a state the editor restores for them. */
    @Test
    fun editingAnExistingHabitAlsoOpensCollapsed() {
        setEditorContent(HIDDEN_COLOR.argb)

        HabitPalette.COLLAPSED_REMAINDER.forEach { habitColor ->
            composeTestRule.onNodeWithTag(habitColorSwatchTestTag(habitColor.argb)).assertDoesNotExist()
        }
    }

    @Test
    fun theExpanderRevealsTheWholePaletteAndFoldsItBackAway() {
        setEditorContent(HabitPalette.DEFAULT)

        composeTestRule.onNodeWithTag(HABIT_COLOR_EXPANDER_TEST_TAG).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        HabitPalette.ORDERED.forEach { habitColor ->
            composeTestRule.onNodeWithTag(habitColorSwatchTestTag(habitColor.argb)).assertExists()
        }

        composeTestRule.onNodeWithTag(HABIT_COLOR_EXPANDER_TEST_TAG).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        HabitPalette.COLLAPSED_REMAINDER.forEach { habitColor ->
            composeTestRule.onNodeWithTag(habitColorSwatchTestTag(habitColor.argb)).assertDoesNotExist()
        }
    }

    /**
     * The rule that stops a user ever seeing "nothing selected": a habit whose colour is a preset
     * the collapsed row does not show still has that colour on screen, carried by the last circle,
     * and expanding hands the selection back to the real swatch.
     */
    @Test
    fun aHiddenPresetIsCarriedByTheLastCircleUntilTheGridIsExpanded() {
        setEditorContent(HIDDEN_COLOR.argb)

        composeTestRule.onNodeWithTag(HABIT_COLOR_CUSTOM_SWATCH_TEST_TAG).assertIsSelected()
        HabitPalette.VISIBLE.forEach { habitColor ->
            composeTestRule.onNodeWithTag(habitColorSwatchTestTag(habitColor.argb)).assertIsNotSelected()
        }
        assertEquals("opening the editor must not rewrite the habit's colour", HIDDEN_COLOR.argb, reportedColor)

        composeTestRule.onNodeWithTag(HABIT_COLOR_EXPANDER_TEST_TAG).performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(habitColorSwatchTestTag(HIDDEN_COLOR.argb)).assertIsSelected()
        composeTestRule.onNodeWithTag(HABIT_COLOR_CUSTOM_SWATCH_TEST_TAG).assertIsNotSelected()
    }

    @Test
    fun everyStandardColourIsOfferedAndSelectingOneReportsItsArgb() {
        setEditorContent(HabitPalette.DEFAULT)

        composeTestRule.onNodeWithTag(habitColorSwatchTestTag(TARGET_COLOR.argb)).performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(TARGET_COLOR.argb, reportedColor)
        composeTestRule.onNodeWithTag(habitColorSwatchTestTag(TARGET_COLOR.argb)).assertIsSelected()
        // The previously-selected swatch, which is in the collapsed row precisely because
        // HabitPalette.DEFAULT is required to be. Asserting on a hidden preset here would assert on
        // a node that is not composed, which is how this test caught the default being wrong.
        composeTestRule.onNodeWithTag(habitColorSwatchTestTag(HabitPalette.DEFAULT)).assertIsNotSelected()
    }

    /** The grid is a radio group, so exactly one cell may be selected at a time — including the
     *  custom cell, which competes with the presets rather than sitting outside them. */
    @Test
    fun exactlyOneSwatchIsSelectedAtATime() {
        setEditorContent(HabitColor.LIGHT_BLUE.argb)
        composeTestRule.onNodeWithTag(HABIT_COLOR_EXPANDER_TEST_TAG).performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(habitColorSwatchTestTag(HabitColor.LIGHT_BLUE.argb)).assertIsSelected()
        HabitPalette.ARGB.filter { it != HabitColor.LIGHT_BLUE.argb }.forEach { argb ->
            composeTestRule.onNodeWithTag(habitColorSwatchTestTag(argb)).assertIsNotSelected()
        }
        composeTestRule.onNodeWithTag(HABIT_COLOR_CUSTOM_SWATCH_TEST_TAG).assertIsNotSelected()
    }

    /** Every swatch is reachable by its colour's name, not only by its fill — the ratified rule that
     *  colour is never this app's sole recognition channel, applied to the one control where colour
     *  is the subject. Asserted expanded, because that is when every swatch is on screen. */
    @Test
    fun everySwatchIsReachableByItsAccessibleName() {
        setEditorContent(HabitPalette.DEFAULT)
        composeTestRule.onNodeWithTag(HABIT_COLOR_EXPANDER_TEST_TAG).performScrollTo().performClick()
        composeTestRule.waitForIdle()

        HabitPalette.ORDERED.forEach { habitColor ->
            composeTestRule.onNodeWithContentDescription(text(habitColor.labelRes)).assertExists()
        }
        composeTestRule.onNodeWithContentDescription(text(R.string.habit_color_custom)).assertExists()
    }

    /**
     * The real-world case: a habit created before this change holds one of the retired pastels. It
     * must open showing that colour as its current choice, not silently reset to a preset and not
     * show nothing selected at all.
     */
    @Test
    fun aHabitHoldingAnOffPaletteColourSelectsTheCustomSwatchWithoutChangingItsColour() {
        setEditorContent(RETIRED_PASTEL_TEAL)

        composeTestRule.onNodeWithTag(HABIT_COLOR_CUSTOM_SWATCH_TEST_TAG).assertIsSelected()
        HabitPalette.VISIBLE.forEach { habitColor ->
            composeTestRule.onNodeWithTag(habitColorSwatchTestTag(habitColor.argb)).assertIsNotSelected()
        }
        assertEquals("opening the editor must not rewrite the habit's colour", RETIRED_PASTEL_TEAL, reportedColor)
    }

    @Test
    fun openingTheCustomPickerAndConfirmingReturnsTheMixedColour() {
        setEditorContent(HabitPalette.DEFAULT)

        composeTestRule.onNodeWithTag(HABIT_COLOR_CUSTOM_SWATCH_TEST_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(HABIT_COLOR_CUSTOM_DIALOG_TEST_TAG).assertExists()
        composeTestRule.onNodeWithTag(HABIT_COLOR_CUSTOM_PREVIEW_TEST_TAG).assertExists()

        setSlider(HABIT_COLOR_HUE_SLIDER_TEST_TAG)
        setSlider(HABIT_COLOR_SATURATION_SLIDER_TEST_TAG)
        setSlider(HABIT_COLOR_BRIGHTNESS_SLIDER_TEST_TAG)
        composeTestRule.onNodeWithTag(HABIT_COLOR_CUSTOM_CONFIRM_TEST_TAG).performClick()
        composeTestRule.waitForIdle()

        assertNotEquals(
            "confirming a mixed colour must report it, not the colour the picker opened on",
            HabitPalette.DEFAULT,
            reportedColor,
        )
        assertFalse(
            "a freely mixed colour is not expected to land on a preset",
            HabitPalette.contains(reportedColor),
        )
        composeTestRule.onNodeWithTag(HABIT_COLOR_CUSTOM_DIALOG_TEST_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(HABIT_COLOR_CUSTOM_SWATCH_TEST_TAG).assertIsSelected()
    }

    @Test
    fun cancellingTheCustomPickerLeavesTheColourAlone() {
        setEditorContent(TARGET_COLOR.argb)

        composeTestRule.onNodeWithTag(HABIT_COLOR_CUSTOM_SWATCH_TEST_TAG).performScrollTo().performClick()
        setSlider(HABIT_COLOR_HUE_SLIDER_TEST_TAG)
        composeTestRule.onNodeWithText(text(R.string.action_cancel)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(HABIT_COLOR_CUSTOM_DIALOG_TEST_TAG).assertDoesNotExist()
        assertEquals(TARGET_COLOR.argb, reportedColor)
        composeTestRule.onNodeWithTag(habitColorSwatchTestTag(TARGET_COLOR.argb)).assertIsSelected()
    }

    /** Drives a slider through its own `SetProgress` semantics rather than by synthesising a drag:
     *  a drag's landing value depends on the device's width and density, and these assertions are
     *  about the picker's wiring, not about touch geometry. */
    private fun setSlider(testTag: String) {
        composeTestRule.onNodeWithTag(testTag)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(MID_SLIDER) }
        composeTestRule.waitForIdle()
    }
}
