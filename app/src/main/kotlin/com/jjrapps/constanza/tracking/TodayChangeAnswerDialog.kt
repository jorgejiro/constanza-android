package com.jjrapps.constanza.tracking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jjrapps.constanza.R
import com.jjrapps.constanza.domain.model.EntryStatus

/**
 * today-one-line-row, point 2: an answered slot's row is a tap target now that "Cambiar" is gone,
 * and this is where that tap lands.
 *
 * Shaped like [com.jjrapps.constanza.habit.HabitListScreen]'s `DeleteHabitDialog` and
 * [com.jjrapps.constanza.portability.DataPortabilityScreen]'s `ImportConfirmDialog` — a plain
 * [AlertDialog] titled with the subject at stake, dismissible by its own Cancel button — and its
 * option list follows [com.jjrapps.constanza.reminding.SnoozeSettingsScreen]'s
 * `SnoozeDurationRow` precedent: one `Role.RadioButton`-selectable row per choice, the current
 * answer pre-selected. Unlike that settings screen, choosing a row here both answers AND closes
 * the dialog immediately — there is nothing left to confirm once an option is picked, so
 * [confirmButton] is empty rather than a second, redundant action.
 *
 * Exactly three rows, in the order the brief specifies: Sí, No, Omitido — reusing
 * [R.string.today_answer_yes]/[R.string.today_answer_no] and, for the third, [R.string
 * .today_slot_skipped] ("Omitido"/"Skipped") rather than the now-deleted [R.string
 * .today_answer_skip] ("Omitir"): this dialog is naming a status the day already has, in the same
 * past-tense register the day already has, and skipping a still-open moment ("Omitir") is a
 * different thing from a moment already gone by ("Skipped").
 *
 * today-clear-answer adds the fourth option this KDoc used to defer: "Sin responder"/"Not
 * answered" (returning a slot to [EntryStatus.UNKNOWN] by deleting its `Entry` row, [EntryWriter
 * .clearAnswer]) — reachable through [onClear], a separate callback from [onSelect] since
 * clearing writes nothing an [InAppEntryStatus] can name. It is gated by [showNotAnsweredOption],
 * which the caller always passes as `!isPastDay` ([com.jjrapps.constanza.tracking
 * .TodayUiState.isPastDay]), a deliberate asymmetry: on a past day the midnight law has already
 * decided this slot's fate, so "Not answered" and "No" are the same outcome there — offering both
 * would be offering one outcome under two names, and picking "Sin responder" would visibly turn
 * into a red cross moments later, which is not what the tap asked for. A past day's three options
 * are already the complete set; there is no third distinct outcome to reach. This dialog stays a
 * plain list of rows rather than a fixed-arity layout, so a fifth option later is one more row,
 * not a restructure.
 */
// [onClear] pushes this to exactly 6 parameters, `LongParameterList`'s unconfigured threshold —
// the same class of Compose false positive `TodayScreen.kt`'s own `fun TodayScreen` is suppressed
// for: one state value plus a hoisted lambda per event IS the parameter list here, and collapsing
// them into a holder object to satisfy a count would make the call site harder to read, not easier.
@Suppress("LongParameterList")
@Composable
internal fun ChangeAnswerDialog(
    habitName: String,
    current: EntryStatus,
    showNotAnsweredOption: Boolean,
    onSelect: (InAppEntryStatus) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(habitName) },
        text = {
            Column {
                ChangeAnswerOption(
                    label = stringResource(R.string.today_answer_yes),
                    selected = current == EntryStatus.COMPLETED,
                    onClick = { onSelect(InAppEntryStatus.COMPLETED) },
                )
                ChangeAnswerOption(
                    label = stringResource(R.string.today_answer_no),
                    selected = current == EntryStatus.MISSED,
                    onClick = { onSelect(InAppEntryStatus.MISSED) },
                )
                ChangeAnswerOption(
                    label = stringResource(R.string.today_slot_skipped),
                    selected = current == EntryStatus.SKIPPED,
                    onClick = { onSelect(InAppEntryStatus.SKIPPED) },
                )
                if (showNotAnsweredOption) {
                    // Never `selected`: this dialog only ever opens for an already-answered slot
                    // (today-one-line-row point 2), so `current` can never be UNKNOWN here — there
                    // is no state in which this row is the current one.
                    ChangeAnswerOption(
                        label = stringResource(R.string.today_answer_not_answered),
                        selected = false,
                        onClick = onClear,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ChangeAnswerOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 16.dp))
    }
}
