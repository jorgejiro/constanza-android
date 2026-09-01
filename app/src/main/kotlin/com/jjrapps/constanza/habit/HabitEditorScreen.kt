@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jjrapps.constanza.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjrapps.constanza.R
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Tasks 6a.1 (non-schedule half)/6a.2/6a.3 — container. [habitId] is `null` for creation, an
 * existing id for editing; the effect below resets/loads the [HabitEditorViewModel] accordingly
 * whenever it changes, since this single [viewModel] instance is reused across every visit to
 * this route in this Activity-scoped, no-navigation-library setup (design.md §14).
 */
@Composable
fun HabitEditorRoute(
    habitId: Long?,
    onDone: () -> Unit,
    viewModel: HabitEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(habitId) {
        if (habitId == null) viewModel.startCreate() else viewModel.startEdit(habitId)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { onDone() }
    }
    val state by viewModel.uiState.collectAsState()
    HabitEditorScreen(
        state = state,
        actions = HabitEditorActions(
            onNameChange = viewModel::onNameChange,
            onQuestionChange = viewModel::onQuestionChange,
            onColorChange = viewModel::onColorChange,
            onNotesChange = viewModel::onNotesChange,
            onSave = viewModel::save,
        ),
        onScheduleParamChange = viewModel::onScheduleParamChange,
        onSlotAction = viewModel::onSlotAction,
    )
}

/** Bundles [HabitEditorScreen]'s non-schedule callbacks, keeping it under detekt's
 *  `LongParameterList` threshold — same reasoning as `scheduling.SchedulingDaos`/`habit.HabitDaos`. */
data class HabitEditorActions(
    val onNameChange: (String) -> Unit,
    val onQuestionChange: (String) -> Unit,
    val onColorChange: (Int) -> Unit,
    val onNotesChange: (String) -> Unit,
    val onSave: () -> Unit,
)

/** Presentational: state in, callbacks out, no dependencies of its own. No fixed orientation and
 *  no hardcoded widths — a single responsive layout that scrolls, so it does not structurally
 *  block slice ii-b's C1/C4 adaptive verification (ui-adaptive-layout). [onScheduleParamChange]/
 *  [onSlotAction] are single sealed-action callbacks (task 6a.1, slice ii-a) rather than one lambda
 *  per field — the same `LongParameterList`-avoidance reasoning as [HabitEditorActions], without
 *  needing a wrapper data class for either. */
@Composable
fun HabitEditorScreen(
    state: HabitEditorUiState,
    actions: HabitEditorActions,
    onScheduleParamChange: (ScheduleParamAction) -> Unit,
    onSlotAction: (SlotAction) -> Unit,
) {
    val titleRes = if (state.habitId == null) {
        R.string.habit_editor_title_create
    } else {
        R.string.habit_editor_title_edit
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(titleRes)) }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = actions.onNameChange,
                label = { Text(stringResource(R.string.habit_editor_name_label)) },
                isError = state.nameError,
                supportingText = if (state.nameError) {
                    { Text(stringResource(R.string.habit_editor_name_error)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.question,
                onValueChange = actions.onQuestionChange,
                label = { Text(stringResource(R.string.habit_editor_question_label)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = actions.onNotesChange,
                label = { Text(stringResource(R.string.habit_editor_notes_label)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.habit_editor_color_label),
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            ColorSwatchRow(selected = state.colorArgb, onColorChange = actions.onColorChange)
            Text(
                stringResource(R.string.habit_editor_schedule_label),
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            ScheduleSection(
                state = state,
                onScheduleParamChange = onScheduleParamChange,
                onSlotAction = onSlotAction,
            )
            Button(onClick = actions.onSave, modifier = Modifier.padding(top = 24.dp)) {
                Text(stringResource(R.string.habit_editor_save))
            }
        }
    }
}

private val SWATCH_SIZE = 40.dp
private val SWATCH_BORDER = 3.dp

@Composable
private fun ColorSwatchRow(selected: Int, onColorChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HabitColorPalette.SWATCHES.forEach { swatch ->
            val borderColor = if (swatch == selected) MaterialTheme.colorScheme.primary else Color.Transparent
            Row(
                modifier = Modifier
                    .size(SWATCH_SIZE)
                    .clip(CircleShape)
                    .background(Color(swatch))
                    .border(SWATCH_BORDER, borderColor, CircleShape)
                    .clickable { onColorChange(swatch) },
            ) {}
        }
    }
}

private const val MIN_STEPPER_VALUE = 1
private const val MAX_DAY_OF_MONTH = 31
private const val MINUTES_PER_HOUR = 60
private const val MAX_HOUR = 23
private const val MAX_MINUTE = 59

/** Task 6a.1, slice ii-a (habit-scheduling: Six Frequency Kinds). Always shows the kind picker,
 *  then the one parameter editor [state]'s current [Schedule] needs — `DAILY` needs none. */
@Composable
private fun ScheduleSection(
    state: HabitEditorUiState,
    onScheduleParamChange: (ScheduleParamAction) -> Unit,
    onSlotAction: (SlotAction) -> Unit,
) {
    Column {
        ScheduleKindPicker(
            selected = state.schedule.kind,
            onKindChange = { onScheduleParamChange(ScheduleParamAction.Kind(it)) },
        )
        when (val schedule = state.schedule) {
            is Schedule.Daily -> Unit

            is Schedule.TimesPerDay -> ReminderSlotEditor(
                slots = state.slots,
                slotsError = state.slotsError,
                onSlotAction = onSlotAction,
            )

            is Schedule.NTimesPerWeek -> NumberStepper(
                label = stringResource(R.string.habit_editor_times_per_week_label),
                value = schedule.times,
                onValueChange = { onScheduleParamChange(ScheduleParamAction.TimesPerWeek(it)) },
                modifier = Modifier.padding(top = 8.dp),
            )

            is Schedule.Weekly -> DayOfWeekPicker(
                selected = schedule.dayOfWeek,
                onDayOfWeekChange = { onScheduleParamChange(ScheduleParamAction.DayOfWeek(it)) },
                modifier = Modifier.padding(top = 8.dp),
            )

            is Schedule.Monthly -> NumberStepper(
                label = stringResource(R.string.habit_editor_day_of_month_label),
                value = schedule.dayOfMonth,
                onValueChange = { onScheduleParamChange(ScheduleParamAction.DayOfMonth(it)) },
                range = MIN_STEPPER_VALUE..MAX_DAY_OF_MONTH,
                modifier = Modifier.padding(top = 8.dp),
            )

            is Schedule.EveryNDays -> EveryNDaysEditor(
                schedule = schedule,
                anchorDateText = state.anchorDateText,
                anchorDateError = state.anchorDateError,
                onScheduleParamChange = onScheduleParamChange,
            )
        }
    }
}

private val ScheduleKind.labelRes: Int
    get() = when (this) {
        ScheduleKind.DAILY -> R.string.schedule_kind_daily
        ScheduleKind.TIMES_PER_DAY -> R.string.schedule_kind_times_per_day
        ScheduleKind.N_TIMES_PER_WEEK -> R.string.schedule_kind_n_times_per_week
        ScheduleKind.WEEKLY -> R.string.schedule_kind_weekly
        ScheduleKind.MONTHLY -> R.string.schedule_kind_monthly
        ScheduleKind.EVERY_N_DAYS -> R.string.schedule_kind_every_n_days
    }

@Composable
private fun ScheduleKindPicker(selected: ScheduleKind, onKindChange: (ScheduleKind) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(selected.labelRes),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.habit_editor_schedule_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ScheduleKind.entries.forEach { kind ->
                DropdownMenuItem(
                    text = { Text(stringResource(kind.labelRes)) },
                    onClick = {
                        onKindChange(kind)
                        expanded = false
                    },
                )
            }
        }
    }
}

private val DEFAULT_STEPPER_RANGE = MIN_STEPPER_VALUE..Int.MAX_VALUE

/** A reusable +/- stepper (habit-scheduling: N_TIMES_PER_WEEK's `times`, MONTHLY's `dayOfMonth`,
 *  EVERY_N_DAYS's `n` — the three integer parameters among the six kinds). [range] floors at 1 by
 *  default since none of the three may be zero or negative; it is only bounded above where the
 *  spec/schema states one (MONTHLY's 1..31). */
@Composable
private fun NumberStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = DEFAULT_STEPPER_RANGE,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onValueChange(value - 1) }, enabled = value > range.first) {
            Text(stringResource(R.string.habit_editor_decrement))
        }
        Text(value.toString(), modifier = Modifier.padding(horizontal = 12.dp))
        OutlinedButton(onClick = { onValueChange(value + 1) }, enabled = value < range.last) {
            Text(stringResource(R.string.habit_editor_increment))
        }
    }
}

@Composable
private fun DayOfWeekPicker(
    selected: DayOfWeek,
    onDayOfWeekChange: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day == selected,
                onClick = { onDayOfWeekChange(day) },
                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
            )
        }
    }
}

@Composable
private fun EveryNDaysEditor(
    schedule: Schedule.EveryNDays,
    anchorDateText: String,
    anchorDateError: Boolean,
    onScheduleParamChange: (ScheduleParamAction) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        NumberStepper(
            label = stringResource(R.string.habit_editor_every_n_days_label),
            value = schedule.n,
            onValueChange = { onScheduleParamChange(ScheduleParamAction.EveryNDays(it)) },
        )
        OutlinedTextField(
            value = anchorDateText,
            onValueChange = { onScheduleParamChange(ScheduleParamAction.AnchorDate(it)) },
            label = { Text(stringResource(R.string.habit_editor_anchor_date_label)) },
            isError = anchorDateError,
            supportingText = if (anchorDateError) {
                { Text(stringResource(R.string.habit_editor_anchor_date_error)) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

/** habit-scheduling: Reminder Slots for TIMES_PER_DAY. Each row edits one [ReminderSlot]'s hour,
 *  minute, and enabled flag; [slotsError] surfaces "MUST define one or more" the same way the
 *  name field surfaces its own required-field error. */
@Composable
private fun ReminderSlotEditor(slots: List<ReminderSlot>, slotsError: Boolean, onSlotAction: (SlotAction) -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.habit_editor_slots_label))
        slots.forEachIndexed { index, slot ->
            ReminderSlotRow(index = index, slot = slot, onSlotAction = onSlotAction)
        }
        if (slotsError) {
            Text(
                stringResource(R.string.habit_editor_slots_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        TextButton(onClick = { onSlotAction(SlotAction.Add) }, modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.habit_editor_slot_add))
        }
    }
}

@Composable
private fun ReminderSlotRow(index: Int, slot: ReminderSlot, onSlotAction: (SlotAction) -> Unit) {
    val hour = slot.minuteOfDay / MINUTES_PER_HOUR
    val minute = slot.minuteOfDay % MINUTES_PER_HOUR
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = hour.toString(),
            onValueChange = { text ->
                val newHour = text.toIntOrNull()?.coerceIn(0, MAX_HOUR) ?: hour
                onSlotAction(SlotAction.SetTime(index, newHour * MINUTES_PER_HOUR + minute))
            },
            label = { Text(stringResource(R.string.habit_editor_slot_hour_label)) },
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = minute.toString(),
            onValueChange = { text ->
                val newMinute = text.toIntOrNull()?.coerceIn(0, MAX_MINUTE) ?: minute
                onSlotAction(SlotAction.SetTime(index, hour * MINUTES_PER_HOUR + newMinute))
            },
            label = { Text(stringResource(R.string.habit_editor_slot_minute_label)) },
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        Checkbox(
            checked = slot.enabled,
            onCheckedChange = { onSlotAction(SlotAction.SetEnabled(index, it)) },
            modifier = Modifier.padding(start = 8.dp),
        )
        Text(stringResource(R.string.habit_editor_slot_enabled_label))
        TextButton(onClick = { onSlotAction(SlotAction.Remove(index)) }, modifier = Modifier.padding(start = 8.dp)) {
            Text(stringResource(R.string.habit_editor_slot_remove))
        }
    }
}
