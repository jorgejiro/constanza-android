@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jjrapps.constanza.habit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.TimeOfDayFormat
import com.jjrapps.constanza.core.ui.rememberTimeOfDayFormat
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.Spacing

private const val MINUTES_PER_HOUR = 60

/** The display-mode toggle inside the reminder-time dialog. Tagged because its only other handle
 *  is a `contentDescription` that comes from Material 3's own **internal** string resources
 *  (`m3c_time_picker_*`), which no test in this module can name without reaching into another
 *  library's private resource table. Same reasoning, and same shape, as
 *  [com.jjrapps.constanza.core.ui.component.HABIT_COLOR_DOT_TEST_TAG]. */
const val REMINDER_TIME_MODE_TOGGLE_TEST_TAG = "reminder_time_mode_toggle"

/**
 * **The habit editor's one time-of-day control**, shared by `ScheduleEditors`'s
 * `ReminderTimeEditor` (the single optional reminder) and by every row of the `TIMES_PER_DAY`
 * slot list (`ReminderSlotRow`). Those two used to hold a byte-identical copy of the same pair
 * of bare hour/minute `OutlinedTextField`s
 * — the duplication was half the defect, so one composable serving both call sites is part of the
 * fix, not a tidy-up alongside it.
 *
 * **Why a bordered, tappable row rather than a `readOnly` `OutlinedTextField`.** Visually the two
 * are the same thing: [Dimens.FieldBorder] and `shapes.extraSmall` are exactly what M3 draws around
 * an unfocused outlined field, so this lines up with the name/question/notes fields and with
 * `ScheduleKindPicker` directly above it. Structurally they are not. A `readOnly` text field still
 * consumes the tap to place a caret, so making one open a dialog needs either an invisible overlay
 * (two nodes, one of them unlabelled, for one control) or `enabled = false` (which leaks a
 * `Disabled` semantics property into the merged node, so TalkBack announces a live control as
 * disabled). One [Role.Button] node wrapping both texts is correct to a screen reader and is
 * directly addressable from a test — see the comment on where that click is declared. It is
 * also what the row already *is*: a value you pick, not a value you type.
 * [Icons.Filled.ArrowDropDown] is the same
 * trailing glyph `ExposedDropdownMenuDefaults.TrailingIcon` puts on the frequency picker, so the two
 * pick-a-value rows in this form read as one affordance — and it ships in `material-icons-core`,
 * the only icon artifact this project depends on, so no clock glyph is worth a new dependency.
 *
 * [label] is `null` in the slot list on purpose: `ReminderSlotEditor` already renders one
 * "Reminder times" header above the whole list, and repeating a per-row label there would push a
 * row that also carries a checkbox and a remove button past a phone's width for no information.
 */
@Composable
internal fun ReminderTimeField(
    minuteOfDay: Int,
    onMinuteOfDayChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    // One read of the device's 12/24-hour setting, shared by the row and by the dialog's
    // TimePickerState. Reading it twice would let the row and the picker it opens disagree.
    val timeFormat = rememberTimeOfDayFormat()
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = Color.Transparent,
        border = BorderStroke(Dimens.FieldBorder, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            // The click lives here, on the Row, rather than on Surface's own `onClick` overload,
            // and that is not a style preference. `Surface(onClick = ...)` sets no `role`, and its
            // `minimumInteractiveComponentSize` puts a layout node between the caller's modifier
            // and the internal `clickable` — so a `semantics { role = Role.Button }` handed to it
            // lands on a DIFFERENT node from the click action and is dropped from the exported
            // accessibility tree (checked with `uiautomator dump`, not assumed). Declared together
            // here, the role, the click and both texts merge into one node that announces as a
            // button, and the ripple is still clipped to the Surface's rounded shape.
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = { showPicker = true })
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val time = timeFormat.format(minuteOfDay)
            if (label == null) {
                Text(time, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            } else {
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(time, style = MaterialTheme.typography.titleMedium)
            }
            // Null on purpose: the whole Surface is one merged button node already carrying the
            // label and the time, so a description here would only add a third thing to read.
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
    }
    if (showPicker) {
        ReminderTimePickerDialog(
            initialMinuteOfDay = minuteOfDay,
            timeFormat = timeFormat,
            onConfirm = {
                showPicker = false
                onMinuteOfDayChange(it)
            },
            onDismiss = { showPicker = false },
        )
    }
}

/**
 * Material 3's clock dial, with keyboard entry one tap away.
 *
 * **`is24Hour` follows the device**, through the same [TimeOfDayFormat] the row above it reads, so
 * the picker and the value it edits can never disagree. It used to be hardcoded `true`, for two
 * stated reasons; both have been dealt with rather than dropped:
 *
 * 1. *Consistency.* The old argument was that `tracking.TodayScreen` rendered `HH:mm`
 *    unconditionally, so following the setting here alone would put `9:15 PM` in the editor and
 *    `21:15` on Today for the same slot. That is no longer true — Today reads the same
 *    [TimeOfDayFormat], and the app-wide change the old KDoc said "is worth doing" is this one.
 * 2. *Palette.* The AM/PM period selector — the only part of [TimePicker]/[TimeInput] that renders
 *    at all when `is24Hour` is false — is the only part of them that reads `tertiaryContainer` and
 *    `onTertiaryContainer`, re-verified against `TimePickerTokens`/`TimeInputTokens` in the
 *    resolved `material3` 1.4.0 artifact (`PeriodSelectorSelectedContainerColor` and the four
 *    `PeriodSelectorSelected*LabelTextColor` entries; nothing else in either token table is
 *    tertiary). Those two roles WERE the ones `core/ui/theme/Theme.kt` audited as unbound, and
 *    surfacing them would have dropped M3's stock violet into the warm ramp. So they are now bound
 *    there, to the same `SurfaceSelected`/`Accent` pair the hour/minute selector already uses, and
 *    that file's audit says so. No colour is overridden here for the period selector: the fix
 *    belongs in the theme, because the next component to render a tertiary role should inherit it
 *    rather than repeat it.
 *
 * The confirm button is unconditionally enabled because there is nothing here that can be invalid:
 * `material3` 1.4.0 (the version Compose BOM 2026.08.00 resolves) rejects an out-of-range entry
 * inside [TimeInput] before it ever reaches the state, and its `TimePickerState` exposes no
 * `isInputValid` to gate on — that property arrives in the 1.5.0 alpha line.
 *
 * Short screens fall back to [TimeInput] the way the official sample does, and the mode toggle is
 * withheld in that case rather than offered as a button that cannot do anything.
 */
@Composable
private fun ReminderTimePickerDialog(
    initialMinuteOfDay: Int,
    timeFormat: TimeOfDayFormat,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // rememberTimePickerState is itself rememberSaveable-backed, so the in-progress hour and
    // minute survive a configuration change; showPicker above is saveable for the same reason, so
    // the dialog is still open on the other side of it.
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / MINUTES_PER_HOUR,
        initialMinute = initialMinuteOfDay % MINUTES_PER_HOUR,
        is24Hour = timeFormat.is24Hour,
    )
    var dialRequested by rememberSaveable { mutableStateOf(true) }
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val dialFits = windowHeight >= TimePickerDialogDefaults.MinHeightForTimePicker
    val showDial = dialRequested && dialFits
    val displayMode = if (showDial) TimePickerDisplayMode.Picker else TimePickerDisplayMode.Input
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { TimePickerDialogDefaults.Title(displayMode = displayMode) },
        modeToggleButton = if (dialFits) {
            {
                TimePickerDialogDefaults.DisplayModeToggle(
                    onDisplayModeChange = { dialRequested = !dialRequested },
                    displayMode = displayMode,
                    modifier = Modifier.testTag(REMINDER_TIME_MODE_TOGGLE_TEST_TAG),
                )
            }
        } else {
            null
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * MINUTES_PER_HOUR + state.minute) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        // Everything except the selected time selector's text is left at the theme-derived
        // default. Verified on the emulator, not assumed: `core/ui/theme/Theme.kt` binds BOTH
        // `primaryContainer` and `surfaceContainerHighest` to ConstanzaColors.SurfaceSelected, and
        // their content roles (`onPrimaryContainer`, `onSurface`) both to OnBackground — so the
        // hour box and the minute box render pixel-identical and nothing on screen says which half
        // you are about to edit. Repointing the selected half's text at `primary` restores that
        // distinction with the app's own accent rather than a new colour value, and a selection
        // indicator is exactly what ConstanzaColors.Accent's KDoc reserves the accent FOR. The
        // container tones are deliberately left alone: two amber-filled boxes would shout where
        // one amber numeral is enough.
        val colors = TimePickerDefaults.colors(
            timeSelectorSelectedContentColor = MaterialTheme.colorScheme.primary,
        )
        if (showDial) TimePicker(state = state, colors = colors) else TimeInput(state = state, colors = colors)
    }
}
