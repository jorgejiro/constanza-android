package com.jjrapps.constanza.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.HabitColor
import com.jjrapps.constanza.core.ui.theme.HabitPalette
import com.jjrapps.constanza.core.ui.theme.Spacing

/** Seven cells per row: collapsed, the five visible presets, the custom swatch and the "More"
 *  expander share one row (6 * 44dp plus the text button fits the 328dp a 360dp-wide phone leaves
 *  after the form's padding); expanded, the seven remaining presets fill the row below. `FlowRow`
 *  still wraps to fewer on a narrower screen or a large display scale, and the grid order's
 *  separation guarantee is written for this width — see [HabitColor]'s KDoc. */
private const val CELLS_PER_ROW = 7


/** Non-visual hooks the picker's compose tests assert on. Not `contentDescription`s — each swatch
 *  carries a real accessible label of its own (see [ColorSwatch]). */
const val HABIT_COLOR_GRID_TEST_TAG = "habit_color_grid"
const val HABIT_COLOR_CUSTOM_SWATCH_TEST_TAG = "habit_color_swatch_custom"
const val HABIT_COLOR_EXPANDER_TEST_TAG = "habit_color_expander"

/** The test tag of one preset swatch, derived from its own ARGB so a test names the colour it
 *  means rather than a grid index that shifts when a family is added. */
fun habitColorSwatchTestTag(argb: Int): String = "habit_color_swatch_%08X".format(argb)

/**
 * The habit colour picker: one row of five well-separated presets plus the custom wheel, and a
 * "More" text button at the end of that row that opens the full twelve-colour grid.
 *
 * **Collapsed by default, always — including when editing an existing habit.** Twenty-two circles
 * is four rows, and four rows of colour in the middle of a form pushes the schedule section and the
 * save button down far enough to cost more than the choice is worth. It measurably did: the
 * always-open grid put `Save` outside the viewport on the tallest schedule kind and broke
 * `HabitScheduleKindComposeTest` on API 37. So the palette is available, not imposed.
 *
 * **The last circle of the visible row does double duty**, and that is what keeps a selection on
 * screen in every state. It opens the free picker, and it renders whatever colour the row cannot:
 * a custom colour, or a preset currently folded away. Opening the editor on a habit whose colour is
 * one of the seven hidden presets therefore shows that colour, ticked, in the last circle rather
 * than showing a row with nothing selected in it.
 *
 * The drawing order is [HabitPalette.ORDERED]'s, arranged so no two neighbouring cells look alike;
 * this composable must not re-sort it.
 *
 * **Selection is a ring around the dot** (graphite redesign): the selected 24dp dot shrinks to 20dp
 * inside a 30dp ring in the text colour. The ring sits on the screen background, not on the fill,
 * so one achromatic tone (15.59:1 on the background) marks every colour a user can mix — which is
 * why it replaced the earlier per-fill tick.
 *
 * **A habit holding a colour that is not a preset is not orphaned.** It selects the custom swatch,
 * which renders that exact colour — so a habit created with one of the six retired pastels opens
 * in the editor showing its own colour, already selected, and editing anything else about it
 * leaves the colour alone. That is the whole reason [HabitPalette.contains] exists.
 *
 * `selectableGroup` + `Role.RadioButton` per swatch is the accessible shape for "one of these",
 * and it is what lets a compose test ask which swatch is selected instead of sampling pixels. Each
 * swatch's `contentDescription` is its colour's name, so the control is usable without seeing the
 * fill at all — colour is never the sole recognition channel in this app (design.md decision 6).
 */
@Composable
fun HabitColorPicker(selected: Int, onColorChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showCustomPicker by rememberSaveable { mutableStateOf(false) }

    // The presets actually on screen right now. The last circle stands in for anything not among
    // them, which is what keeps a selection visible in every state: a custom colour, and equally a
    // preset that is currently folded away. Without this, opening the editor on a habit whose colour
    // is one of the seventeen hidden ones would show a row with nothing selected in it.
    val shownPresets = if (expanded) HabitPalette.ORDERED else HabitPalette.VISIBLE
    val lastCircleCarriesSelection = shownPresets.none { it.argb == selected }

    Column(modifier = modifier.fillMaxWidth().padding(top = Spacing.xl)) {
        FieldLabel(stringResource(R.string.habit_editor_color_label))
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .testTag(HABIT_COLOR_GRID_TEST_TAG),
            itemVerticalAlignment = Alignment.CenterVertically,
            maxItemsInEachRow = CELLS_PER_ROW,
        ) {
            HabitPalette.VISIBLE.forEach { PresetSwatch(it, selected, onColorChange) }
            // Deliberately the sixth cell of the first row rather than the last cell overall, so
            // expanding the grid grows it downward and this circle never moves under the finger
            // that just tapped the expander.
            CustomColorSwatch(
                argb = selected,
                selected = lastCircleCarriesSelection,
                onClick = { showCustomPicker = true },
            )
            ColorPickerExpander(expanded = expanded, onToggle = { expanded = !expanded })
            if (expanded) {
                HabitPalette.COLLAPSED_REMAINDER.forEach { PresetSwatch(it, selected, onColorChange) }
            }
        }
    }

    if (showCustomPicker) {
        CustomColorDialog(
            initialArgb = selected,
            onConfirm = {
                showCustomPicker = false
                onColorChange(it)
            },
            onDismiss = { showCustomPicker = false },
        )
    }
}

/**
 * "More" / "Fewer": a plain text button closing the first row, after the custom swatch.
 *
 * It sits in that row rather than taking a swatch cell's place: spending one of the six cells on it
 * would leave four colours visible. As the row's last item it never moves when the grid expands —
 * the remaining presets grow downward beneath it.
 */
@Composable
private fun ColorPickerExpander(expanded: Boolean, onToggle: () -> Unit) {
    val labelRes = if (expanded) {
        R.string.habit_editor_color_show_fewer
    } else {
        R.string.habit_editor_color_show_more
    }
    TextButton(
        onClick = onToggle,
        modifier = Modifier.heightIn(min = Dimens.ColorDotTouchTarget).testTag(HABIT_COLOR_EXPANDER_TEST_TAG),
    ) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun PresetSwatch(habitColor: HabitColor, selected: Int, onColorChange: (Int) -> Unit) {
    ColorSwatch(
        argb = habitColor.argb,
        selected = habitColor.argb == selected,
        label = stringResource(habitColor.labelRes),
        testTag = habitColorSwatchTestTag(habitColor.argb),
        onClick = { onColorChange(habitColor.argb) },
    )
}

/** One preset circle. The 44dp box is the touch target; the 24dp dot is the paint. */
@Composable
private fun ColorSwatch(argb: Int, selected: Boolean, label: String, testTag: String, onClick: () -> Unit) {
    SwatchBox(selected = selected, label = label, testTag = testTag, onClick = onClick) {
        SwatchFill(fill = SolidColor(Color(argb)), selected = selected)
    }
}

/**
 * The twentieth cell. Unselected it shows the hue wheel as a sweep gradient — the ordinary "pick
 * your own" affordance, and one that cannot be confused with any preset because no preset is a
 * gradient. Selected it shows the custom colour itself, ringed exactly like a preset, which is what
 * makes an off-palette habit colour visible rather than lost.
 */
@Composable
private fun CustomColorSwatch(argb: Int, selected: Boolean, onClick: () -> Unit) {
    val spectrum = remember { hueSpectrum() }
    SwatchBox(
        selected = selected,
        label = stringResource(R.string.habit_color_custom),
        testTag = HABIT_COLOR_CUSTOM_SWATCH_TEST_TAG,
        onClick = onClick,
    ) {
        SwatchFill(
            fill = if (selected) SolidColor(Color(argb)) else Brush.sweepGradient(spectrum),
            selected = selected,
        )
    }
}

@Composable
private fun SwatchBox(
    selected: Boolean,
    label: String,
    testTag: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(Dimens.ColorDotTouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/** The dot itself: 24dp at rest; selected, a 20dp dot inside a 30dp ring in the text colour. */
@Composable
private fun SwatchFill(fill: Brush, selected: Boolean) {
    if (selected) {
        Box(
            modifier = Modifier
                .size(Dimens.ColorDotRing)
                .border(Dimens.ColorDotRingStroke, ConstanzaColors.OnBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(Dimens.ColorDotSelected).clip(CircleShape).background(fill))
        }
    } else {
        Box(Modifier.size(Dimens.ColorDot).clip(CircleShape).background(fill))
    }
}
