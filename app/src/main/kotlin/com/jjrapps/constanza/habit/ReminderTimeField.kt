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
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.Spacing

private const val MINUTES_PER_HOUR = 60

/** Zero-padded, 24-hour, both halves always two digits — see [formatTimeOfDay]. */
private const val TIME_OF_DAY_FORMAT = "%02d:%02d"

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
            val time = formatTimeOfDay(minuteOfDay)
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
 * **`is24Hour = true`, hardcoded, and NOT [rememberTimePickerState]'s system-setting default.**
 * Two independent reasons, both of which would have to change together before this should:
 *
 * 1. *Consistency.* Everywhere else this app renders a time it renders `HH:mm` —
 *    `tracking.TodayScreen`'s `TIME_FORMATTER` and its `slotStatusText`. Following the system
 *    setting here alone would put `9:15 PM` in the editor and `21:15` on Today for the same slot.
 *    Following it *everywhere* is the genuinely right answer and is worth doing, but it is an
 *    app-wide change to how times are read, not a fix to this control, and it does not belong in
 *    the same diff. Until then, one convention beats two.
 * 2. *Palette.* The AM/PM period selector — the only part of [TimePicker]/[TimeInput] that renders
 *    at all when `is24Hour` is false — is the only part of them that reads `tertiaryContainer` and
 *    `onTertiaryContainer`, verified against `TimePickerTokens`/`TimeInputTokens` in the resolved
 *    `material3` artifact. Those two roles are the ones `core/ui/theme/Theme.kt` explicitly audits
 *    as *unbound*, on the grounds that nothing renders them; a 12-hour dial would drop M3's stock
 *    violet into the middle of the warm ramp and silently falsify that audit. Every other role
 *    these two composables touch — `surfaceContainerHigh`/`Highest`, `primary`/`onPrimary`,
 *    `primaryContainer`/`onPrimaryContainer`, `secondary`, `onSurface`, `onSurfaceVariant`,
 *    `outline` — is already bound there.
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
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // rememberTimePickerState is itself rememberSaveable-backed, so the in-progress hour and
    // minute survive a configuration change; showPicker above is saveable for the same reason, so
    // the dialog is still open on the other side of it.
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / MINUTES_PER_HOUR,
        initialMinute = initialMinuteOfDay % MINUTES_PER_HOUR,
        is24Hour = true,
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

/** `21:05`, never `21:5` and never `9:0`. Matches `tracking.TodayScreen`'s `slotStatusText`
 *  character for character, which is the point — see [ReminderTimePickerDialog]'s KDoc. */
private fun formatTimeOfDay(minuteOfDay: Int): String =
    TIME_OF_DAY_FORMAT.format(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)
