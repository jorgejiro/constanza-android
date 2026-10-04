@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jjrapps.constanza.habit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.TimeOfDayFormat
import com.jjrapps.constanza.core.ui.rememberTimeOfDayFormat
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.Dimens

private const val MINUTES_PER_HOUR = 60

/** The display-mode toggle inside the reminder-time dialog. Tagged because its only other handle
 *  is a `contentDescription` that comes from Material 3's own **internal** string resources
 *  (`m3c_time_picker_*`), which no test in this module can name without reaching into another
 *  library's private resource table. Same reasoning, and same shape, as
 *  [com.jjrapps.constanza.tracking.TODAY_ADD_HABIT_FAB_TEST_TAG]: a tag that adds no accessibility
 *  announcement, for a node no test can otherwise name. */
const val REMINDER_TIME_MODE_TOGGLE_TEST_TAG = "reminder_time_mode_toggle"

/**
 * **The habit editor's one time-of-day control**, shared by `ScheduleEditors`'s
 * `ReminderTimeEditor` (the single optional reminder), by every row of the `TIMES_PER_DAY` slot
 * list (`ReminderSlotRow`) and by Settings' day-review time. One composable serving every call site
 * is deliberate: the editor once held two byte-identical copies of a time control, and that
 * duplication was half of a defect.
 *
 * **Graphite redesign: a list row, not a bordered field.** The label sits on the left in the text
 * colour and the time on the right with tabular figures, at the graphite row height; the caller
 * draws the hairlines between rows. It is still a value you pick, not one you type, so it is still
 * not a `readOnly` text field (which would consume the tap to place a caret).
 *
 * **One button node.** The click, its [Role.Button] and both texts are declared on the same `Row`,
 * so they merge into one node that announces as a button carrying the label and the time, and is
 * directly addressable from a test. The click must not move onto a wrapper with its own `onClick`
 * overload: `Surface(onClick = ...)` sets no role, and a role handed to it lands on a different node
 * and is dropped from the exported accessibility tree (checked with `uiautomator dump`).
 *
 * [label] is `null` in the slot list on purpose: `ReminderSlotEditor` already renders one
 * "Reminder times" header above the whole list, so each slot row shows only its time.
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.SettingsRow)
            .clickable(role = Role.Button, onClick = { showPicker = true }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val time = timeFormat.format(minuteOfDay)
        val rowText = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal)
        if (label != null) {
            Text(label, style = rowText, color = ConstanzaColors.OnBackground, modifier = Modifier.weight(1f))
        }
        Text(
            time,
            style = rowText.copy(fontFeatureSettings = TABULAR_FIGURES),
            color = ConstanzaColors.OnBackground,
            modifier = if (label == null) Modifier.weight(1f) else Modifier,
        )
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
 * The time picker's hour/minute (and AM/PM) selector tones, named so a unit test can measure them
 * (`ReminderTimeSelectorContrastTest`).
 *
 * The T1 review found the selected half of the selector distinguishable only by its fill,
 * `SurfaceSelected` against `SurfaceRaised` — about 1.1:1, so nothing on screen said which half
 * you were about to edit. The selected half is now light-filled with dark ink (the same pair as
 * every graphite primary action) and the unselected half a dark fill with light ink, which puts
 * the two states more than 3:1 apart (WCAG 2.1 SC 1.4.11) and each numeral well above 4.5:1 on its
 * own container.
 */
internal object ReminderTimeSelectorTones {
    val SelectedContainer = ConstanzaColors.ChromeInteractive
    val SelectedContent = ConstanzaColors.OnChromeInteractive
    val UnselectedContainer = ConstanzaColors.SurfaceSelected
    val UnselectedContent = ConstanzaColors.OnBackground

    /** The surface the selector sits on: the dialog's `surfaceContainerHigh`. */
    val DialogSurface = ConstanzaColors.SurfaceRaised
}

private const val TABULAR_FIGURES = "tnum"

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
 *    there, to the same `SurfaceSelected`/`ChromeInteractive` pair the hour/minute selector already
 *    uses, and that file's audit says so. No colour is overridden here for the period selector: the fix
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
        // See ReminderTimeSelectorTones: the selected half is light-filled with dark ink so it is
        // unmistakable against the dark unselected half, for both the hour/minute selector and the
        // 12-hour period selector.
        val colors = TimePickerDefaults.colors(
            clockDialColor = ReminderTimeSelectorTones.UnselectedContainer,
            timeSelectorSelectedContainerColor = ReminderTimeSelectorTones.SelectedContainer,
            timeSelectorSelectedContentColor = ReminderTimeSelectorTones.SelectedContent,
            timeSelectorUnselectedContainerColor = ReminderTimeSelectorTones.UnselectedContainer,
            timeSelectorUnselectedContentColor = ReminderTimeSelectorTones.UnselectedContent,
            periodSelectorSelectedContainerColor = ReminderTimeSelectorTones.SelectedContainer,
            periodSelectorSelectedContentColor = ReminderTimeSelectorTones.SelectedContent,
            periodSelectorUnselectedContainerColor = ReminderTimeSelectorTones.UnselectedContainer,
            periodSelectorUnselectedContentColor = ReminderTimeSelectorTones.UnselectedContent,
        )
        if (showDial) TimePicker(state = state, colors = colors) else TimeInput(state = state, colors = colors)
    }
}
