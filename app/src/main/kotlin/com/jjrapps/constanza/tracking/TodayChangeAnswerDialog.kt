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
 * A fourth option, "Sin responder" (returning a slot to [EntryStatus.UNKNOWN]), is a separate
 * future change — this dialog is a plain list of rows rather than a fixed-arity layout, so adding
 * it later is one more row, not a restructure.
 */
@Composable
internal fun ChangeAnswerDialog(
    habitName: String,
    current: EntryStatus,
    onSelect: (InAppEntryStatus) -> Unit,
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
