package com.jjrapps.constanza.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.HabitColor
import com.jjrapps.constanza.core.ui.theme.HabitPalette
import com.jjrapps.constanza.core.ui.theme.Spacing
import com.jjrapps.constanza.core.ui.theme.contrastingInk

/** Six per row puts the twenty-three presets and the custom swatch on exactly four rows: 6 * 48dp
 *  plus five 4dp gaps is 308dp, inside the 328dp a 360dp-wide phone leaves after the form's own
 *  padding. `FlowRow` still wraps to fewer on a narrower screen or a large display scale, and the
 *  grid order's separation guarantee is written for this width — see [HabitColor]'s KDoc. */
private const val SWATCHES_PER_ROW = 6


/** Non-visual hooks the picker's compose tests assert on. Not `contentDescription`s — each swatch
 *  carries a real accessible label of its own (see [ColorSwatch]). */
const val HABIT_COLOR_GRID_TEST_TAG = "habit_color_grid"
const val HABIT_COLOR_CUSTOM_SWATCH_TEST_TAG = "habit_color_swatch_custom"
const val HABIT_COLOR_EXPANDER_TEST_TAG = "habit_color_expander"

/** The test tag of one preset swatch, derived from its own ARGB so a test names the colour it
 *  means rather than a grid index that shifts when a family is added. */
fun habitColorSwatchTestTag(argb: Int): String = "habit_color_swatch_%08X".format(argb)

/**
 * The habit colour picker: one row of five well-separated presets plus the custom wheel, and an
 * expander on the heading that opens the full twenty-three-colour grid.
 *
 * **Collapsed by default, always — including when editing an existing habit.** Twenty-four circles
 * is four rows, and four rows of colour in the middle of a form pushes the schedule section and the
 * save button down far enough to cost more than the choice is worth. It measurably did: the
 * always-open grid put `Save` outside the viewport on the tallest schedule kind and broke
 * `HabitScheduleKindComposeTest` on API 37. So the palette is available, not imposed.
 *
 * **The last circle of the visible row does double duty**, and that is what keeps a selection on
 * screen in every state. It opens the free picker, and it renders whatever colour the row cannot:
 * a custom colour, or a preset currently folded away. Opening the editor on a habit whose colour is
 * one of the eighteen hidden presets therefore shows that colour, ticked, in the last circle rather
 * than showing a row with nothing selected in it.
 *
 * The drawing order is [HabitPalette.ORDERED]'s, arranged so no two neighbouring cells look alike;
 * this composable must not re-sort it.
 *
 * **Selection is a tick, not a ring.** The previous marker was a 3dp `primary` border around the
 * chosen circle. A single fixed colour cannot mark selection legibly across a palette that now
 * includes any colour a user can mix, so the tick is tinted per-swatch by `contrastingInk`, which
 * guarantees at least 4.58:1 against *any* fill. See its KDoc for that bound.
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
    // is one of the eighteen hidden ones would show a row with nothing selected in it.
    val shownPresets = if (expanded) HabitPalette.ORDERED else HabitPalette.VISIBLE
    val lastCircleCarriesSelection = shownPresets.none { it.argb == selected }

    Column(modifier = modifier.fillMaxWidth()) {
        ColorPickerHeader(expanded = expanded, onToggle = { expanded = !expanded })
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .testTag(HABIT_COLOR_GRID_TEST_TAG),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            maxItemsInEachRow = SWATCHES_PER_ROW,
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
 * The "Colour" heading and, right-aligned on the same line, the control that opens and closes the
 * rest of the palette.
 *
 * It sits here rather than in the swatch row because of arithmetic: six cells fit on one row at
 * 360dp, and spending one of them on an expander would leave four colours visible — fewer than the
 * six the old palette showed, which would be a regression dressed up as an improvement. On the
 * heading line it costs no swatch at all.
 */
@Composable
private fun ColorPickerHeader(expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.habit_editor_color_label), modifier = Modifier.weight(1f))
        val labelRes = if (expanded) {
            R.string.habit_editor_color_show_fewer
        } else {
            R.string.habit_editor_color_show_more
        }
        TextButton(onClick = onToggle, modifier = Modifier.testTag(HABIT_COLOR_EXPANDER_TEST_TAG)) {
            Text(stringResource(labelRes))
            Icon(
                // KeyboardArrowUp/Down rather than ExpandLess/ExpandMore: the latter pair ships only
                // in material-icons-extended, and material-icons-core is the one icon artifact this
                // project depends on (app/build.gradle.kts). Same chevron, no new dependency.
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                // The adjacent label already says what this does; announcing the chevron too would
                // make a screen reader read the control twice.
                contentDescription = null,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
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

/** One preset circle. The 48dp box is the touch target; the 40dp circle is the paint. */
@Composable
private fun ColorSwatch(argb: Int, selected: Boolean, label: String, testTag: String, onClick: () -> Unit) {
    SwatchBox(selected = selected, label = label, testTag = testTag, onClick = onClick) {
        SwatchFill(fill = SolidColor(Color(argb)), tickTint = if (selected) contrastingInk(argb) else null)
    }
}

/**
 * The twentieth cell. Unselected it shows the hue wheel as a sweep gradient — the ordinary "pick
 * your own" affordance, and one that cannot be confused with any preset because no preset is a
 * gradient. Selected it shows the custom colour itself, ticked exactly like a preset, which is what
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
            tickTint = if (selected) contrastingInk(argb) else null,
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
            .size(Dimens.SwatchTouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/** [tickTint] is `null` when this swatch is not selected — the tick is the selection marker, so
 *  its presence and its colour are the same decision. */
@Composable
private fun SwatchFill(fill: Brush, tickTint: Color?) {
    Box(
        modifier = Modifier
            .size(Dimens.Swatch)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        if (tickTint != null) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = tickTint,
                modifier = Modifier.size(Dimens.SwatchTick),
            )
        }
    }
}
