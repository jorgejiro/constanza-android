package com.jjrapps.constanza.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.Hsv
import com.jjrapps.constanza.core.ui.theme.Spacing
import com.jjrapps.constanza.core.ui.theme.contrastingInk
import com.jjrapps.constanza.core.ui.theme.habitBandColor
import com.jjrapps.constanza.core.ui.theme.habitBandPositionOf
import com.jjrapps.constanza.core.ui.theme.hsvOf

/** Stops around the hue wheel. Twelve is enough that the seams are invisible at the sizes this is
 *  drawn at, and few enough to stay cheap to allocate. */
internal const val SPECTRUM_STOPS = 12

internal const val FULL_TURN_DEGREES = 360f
private const val HEX_RGB_MASK = 0x00FFFFFF

/** The dialog's own non-visual hooks, kept beside the dialog rather than with the grid's. */
const val HABIT_COLOR_CUSTOM_DIALOG_TEST_TAG = "habit_color_custom_dialog"
const val HABIT_COLOR_CUSTOM_PREVIEW_TEST_TAG = "habit_color_custom_preview"
const val HABIT_COLOR_HUE_SLIDER_TEST_TAG = "habit_color_hue_slider"
const val HABIT_COLOR_SATURATION_SLIDER_TEST_TAG = "habit_color_saturation_slider"
const val HABIT_COLOR_BRIGHTNESS_SLIDER_TEST_TAG = "habit_color_brightness_slider"
const val HABIT_COLOR_CUSTOM_CONFIRM_TEST_TAG = "habit_color_custom_confirm"

/** The full hue wheel as gradient stops, first and last both red so a sweep gradient closes without
 *  a visible seam. Shared by the custom swatch's wheel and the dialog's hue track, so the two cannot
 *  drift apart. */
internal fun hueSpectrum(): List<Color> =
    List(SPECTRUM_STOPS + 1) { Color(Hsv(it * FULL_TURN_DEGREES / SPECTRUM_STOPS, 1f, 1f).toArgb()) }

/**
 * The free colour picker: three gradient sliders over hue, saturation and brightness, and a live
 * preview that shows the resulting colour with the same tick drawn on it — so the legibility of the
 * selection marker is visible *before* the colour is committed, not discovered afterwards.
 *
 * HSV rather than three RGB sliders because hue is the axis a person actually reasons in ("a bit
 * more orange"), and because a saturation or brightness slider is meaningless without it. The
 * conversion is `core.ui.theme.Hsv`, plain Kotlin, so its round trip is a JVM unit test.
 *
 * Each slider is a stock M3 [Slider] with a transparent track drawn over a gradient bar, rather
 * than a custom-painted canvas control. That keeps the slider's own semantics, keyboard handling
 * and touch target — a canvas would have to reimplement all three, and a screen reader would get
 * nothing — while still showing what the axis does.
 *
 * The three components are held as separate floats rather than one saveable [Hsv], so a rotation
 * mid-pick restores through `rememberSaveable`'s built-in float support with no custom `Saver`.
 * Greyscale is why they must be separate at all: `hsvOf` cannot recover a hue from a grey, so a
 * picker that re-derived its sliders from the composed colour each frame would snap the hue slider
 * to zero the moment saturation reached it. The third float is no longer [Hsv.value] (see below), but
 * the same argument still holds for it: deriving it from the composed colour every frame would fight
 * the user's own drag the moment the colour's measured contrast did not land exactly back on the
 * position they set it to.
 *
 * **The third axis is the legibility band itself, not raw brightness.** This colour is about to be
 * painted on a habit's name text (see `clampToHabitBand`'s KDoc for why that needs a floor and a
 * ceiling at all), so what reaches [onConfirm] must sit inside `[7:1, 11:1]` against the app
 * background. An earlier version ran the raw HSV mix through `clampToHabitBand` for preview and
 * commit — but that clamp *rebuilds* an out-of-band colour from scratch at exactly the floor or
 * ceiling, ignoring the caller's `value` once a colour was out of band, so most of the brightness
 * slider was dead: measured over its 101 positions, saturated red produced only 12 distinct outputs
 * (50 of them identical to the maximum), saturated blue only 13 (38 identical). Only the very bottom
 * of the slider looked different, because black has no hue to preserve and clamped to a plain grey
 * instead of the same hue-preserving rebuild every other floor-bound position collapsed onto — this
 * is the "only visible at minimum or maximum" defect that was reported.
 *
 * `bandPosition` below fixes this by driving the *target contrast ratio* directly (see
 * `habitBandColor`'s KDoc): `0f` is the darkest legible colour at the current hue/saturation, `1f`
 * the lightest, and every position in between solves for the colour whose contrast equals that
 * position's point on the band. The result is in-band by construction, not by a downstream clamp —
 * `clampToHabitBand` no longer sits in this dialog's path at all; it stays in place for migrations and
 * imports, which still need to pull an arbitrary stored colour into the band.
 */
@Composable
internal fun CustomColorDialog(initialArgb: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val initial = remember(initialArgb) { hsvOf(initialArgb) }
    var hue by rememberSaveable { mutableFloatStateOf(initial.hue) }
    var saturation by rememberSaveable { mutableFloatStateOf(initial.saturation) }
    var bandPosition by rememberSaveable { mutableFloatStateOf(habitBandPositionOf(initialArgb)) }
    val committed = habitBandColor(hue, saturation, bandPosition)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(HABIT_COLOR_CUSTOM_DIALOG_TEST_TAG),
        title = { Text(stringResource(R.string.habit_editor_color_custom_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                CustomColorPreview(argb = committed)
                GradientSlider(
                    label = stringResource(R.string.habit_editor_color_hue),
                    fraction = hue / FULL_TURN_DEGREES,
                    trackColors = remember { hueSpectrum() },
                    testTag = HABIT_COLOR_HUE_SLIDER_TEST_TAG,
                    onFractionChange = { hue = it * FULL_TURN_DEGREES },
                )
                GradientSlider(
                    label = stringResource(R.string.habit_editor_color_saturation),
                    fraction = saturation,
                    trackColors = remember(hue, bandPosition) {
                        listOf(
                            Color(habitBandColor(hue, 0f, bandPosition)),
                            Color(habitBandColor(hue, 1f, bandPosition)),
                        )
                    },
                    testTag = HABIT_COLOR_SATURATION_SLIDER_TEST_TAG,
                    onFractionChange = { saturation = it },
                )
                GradientSlider(
                    label = stringResource(R.string.habit_editor_color_brightness),
                    fraction = bandPosition,
                    trackColors = remember(hue, saturation) {
                        listOf(
                            Color(habitBandColor(hue, saturation, 0f)),
                            Color(habitBandColor(hue, saturation, 1f)),
                        )
                    },
                    testTag = HABIT_COLOR_BRIGHTNESS_SLIDER_TEST_TAG,
                    onFractionChange = { bandPosition = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(committed) },
                modifier = Modifier.testTag(HABIT_COLOR_CUSTOM_CONFIRM_TEST_TAG),
            ) {
                Text(stringResource(R.string.habit_editor_color_custom_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** The colour being mixed, with the selection tick on it and its hex beside it. The tick is here
 *  on purpose: it is the one place a user can see whether the marker will be readable on the
 *  colour they are choosing. */
@Composable
private fun CustomColorPreview(argb: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HABIT_COLOR_CUSTOM_PREVIEW_TEST_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.PickerPreview)
                .clip(CircleShape)
                .background(Color(argb)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = contrastingInk(argb),
                modifier = Modifier.size(Dimens.SwatchTick),
            )
        }
        // A hex code, not a translatable phrase: the same six digits in every locale.
        Text(text = "#%06X".format(argb and HEX_RGB_MASK), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * One axis of the picker. [fraction] is always `0f..1f` — hue is scaled by its caller — so this
 * needs no range parameter and stays inside detekt's `LongParameterList` threshold.
 *
 * The gradient is a plain [Box] behind a track-less [Slider], not a custom `track` slot, so it does
 * not depend on which overloads material3 exposes as stable in a given release.
 */
@Composable
private fun GradientSlider(
    label: String,
    fraction: Float,
    trackColors: List<Color>,
    testTag: String,
    onFractionChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(top = Spacing.md)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.PickerTrack)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(trackColors)),
            )
            Slider(
                value = fraction,
                onValueChange = onFractionChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = label }
                    .testTag(testTag),
                colors = SliderDefaults.colors(
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
            )
        }
    }
}
