@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jjrapps.constanza.habit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.theme.ConstanzaControlDefaults
import com.jjrapps.constanza.core.ui.theme.Spacing
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import java.time.DayOfWeek
import java.time.format.TextStyle

private const val MIN_STEPPER_VALUE = 1
private const val MAX_DAY_OF_MONTH = 31


/** Task 6a.1, slice ii-a (habit-scheduling: Six Frequency Kinds). Always shows the kind picker,
 *  then the one parameter editor [state]'s current [Schedule] needs — `DAILY` needs none. Every
 *  kind except `TIMES_PER_DAY` additionally gets [ReminderTimeEditor] (task 6a.8) below its own
 *  parameter editor, `DAILY` included, since that editor IS the only schedule-specific UI it has. */
@Composable
fun ScheduleSection(
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
        if (state.schedule !is Schedule.TimesPerDay) {
            ReminderTimeEditor(slot = state.slots.firstOrNull(), onSlotAction = onSlotAction)
        }
    }
}

/**
 * Task 6a.8 (habit-scheduling: the single configurable reminder time, ratified OPTIONAL for the
 * five non-`TIMES_PER_DAY` kinds). The switch itself IS the "no time at all" affordance the
 * requirement calls for: off means [slot] is `null` and the helper text below states plainly that
 * nothing will fire, rather than exposing a disabled/blank time field a user could mistake for "not
 * set yet". Toggling on adds the single slot via [SlotAction.Add] (capped at one, since the
 * ViewModel's `addSlot` refuses a second slot outside `TIMES_PER_DAY`); toggling off removes it via
 * [SlotAction.Remove], not a separate "enabled" flag, so presence in [HabitEditorUiState.slots]
 * alone decides whether a reminder is armed (matching `OccurrencePlanner`'s own check).
 */
@Composable
private fun ReminderTimeEditor(slot: ReminderSlot?, onSlotAction: (SlotAction) -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.habit_editor_reminder_time_switch), modifier = Modifier.weight(1f))
            Switch(
                checked = slot != null,
                onCheckedChange = { checked ->
                    if (checked) onSlotAction(SlotAction.Add) else onSlotAction(SlotAction.Remove(0))
                },
            )
        }
        if (slot != null) {
            ReminderTimeField(
                minuteOfDay = slot.minuteOfDay,
                onMinuteOfDayChange = { onSlotAction(SlotAction.SetTime(0, it)) },
                label = stringResource(R.string.habit_editor_reminder_time_label),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
        } else {
            Text(
                stringResource(R.string.habit_editor_reminder_time_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
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
        OutlinedButton(
            onClick = { onValueChange(value - 1) },
            enabled = value > range.first,
            border = ConstanzaControlDefaults.outlinedButtonBorder(enabled = value > range.first),
        ) {
            Text(stringResource(R.string.habit_editor_decrement))
        }
        Text(value.toString(), modifier = Modifier.padding(horizontal = 12.dp))
        OutlinedButton(
            onClick = { onValueChange(value + 1) },
            enabled = value < range.last,
            border = ConstanzaControlDefaults.outlinedButtonBorder(enabled = value < range.last),
        ) {
            Text(stringResource(R.string.habit_editor_increment))
        }
    }
}

/**
 * Reads the locale from [LocalConfiguration] rather than `Locale.getDefault()`: the latter is not
 * observable Compose state, so the day labels would keep rendering in the old language after an
 * in-app or system locale change until something else happened to invalidate this composable.
 *
 * It deliberately does NOT read `LocalLocale`, which this code used until app-localization-es-en
 * and which looks like the more direct choice. `LocalLocale` computes from `LocalLocaleList`, whose
 * backing local is private to compose-ui and is provided at the composition root from
 * `AndroidComposeView.localeList` — derived from the *Activity's* configuration, not from
 * composition. A per-app language override installed below the root therefore cannot reach it, and
 * because the backing local is private it cannot be provided either. Under a Spanish override these
 * chips kept rendering English day names while every `stringResource` around them was already
 * Spanish. See design.md's Finding B, read from the compose-ui sources.
 *
 * [LocalConfiguration] is the only Compose-root-overridable locale source here, and it is the same
 * one `TimeOfDayFormat` reads, so the two formatters now agree.
 */
@Composable
private fun DayOfWeekPicker(
    selected: DayOfWeek,
    onDayOfWeekChange: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { day ->
            val isSelected = day == selected
            FilterChip(
                selected = isSelected,
                onClick = { onDayOfWeekChange(day) },
                label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                border = ConstanzaControlDefaults.filterChipBorder(selected = isSelected),
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
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ReminderTimeField(
            minuteOfDay = slot.minuteOfDay,
            onMinuteOfDayChange = { onSlotAction(SlotAction.SetTime(index, it)) },
            modifier = Modifier.weight(1f),
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
