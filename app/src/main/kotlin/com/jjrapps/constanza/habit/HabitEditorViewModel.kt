package com.jjrapps.constanza.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

private const val MIN_POSITIVE = 1
private const val MIN_DAY_OF_MONTH = 1

/** habit-scheduling design.md schema comment: `dayOfMonth INTEGER (1..31, clamped to month
 *  length)` — the persisted column's own bound, not an invented business rule. */
private const val MAX_DAY_OF_MONTH = 31
private const val DEFAULT_TIMES_PER_WEEK = 3
private const val DEFAULT_EVERY_N_DAYS = 2
private const val DEFAULT_MINUTE_OF_DAY = 480 // 08:00
private const val MINUTES_PER_DAY = 24 * 60

/**
 * Task 6a.1/6a.2/6a.3 (habit-management: Habit Creation, Habit Editing; habit-scheduling: Six
 * Frequency Kinds, Reminder Slots for TIMES_PER_DAY). Slice i fixed every new habit's [Schedule]
 * to [Schedule.Daily]; slice ii-a adds the schedule-kind picker for all six kinds, each kind's
 * parameter editor, and the `TIMES_PER_DAY` reminder-slot editor. [save] still round-trips the
 * chosen [Schedule] (and, for `TIMES_PER_DAY`, its slots) through [HabitRepository], reusing
 * 6a.3's existing replan wiring — no second implementation was written for slice ii-a.
 *
 * [onScheduleParamChange]/[onSlotAction] take a single sealed action rather than one method per
 * field, keeping this class under detekt's `TooManyFunctions` threshold without losing per-field
 * type safety (each [ScheduleParamAction]/[SlotAction] variant still carries its own typed payload).
 *
 * Task 6a.8 (habit-scheduling: "Every other frequency kind MUST have exactly one configurable
 * reminder time, not per-slot times", now ratified as OPTIONAL): [HabitEditorUiState.slots] doubles
 * as that single reminder time's storage for the five non-`TIMES_PER_DAY` kinds — 0 slots means no
 * reminder, 1 means the configured time — reusing [onSlotAction]'s existing `Add`/`Remove`/`SetTime`
 * rather than adding a parallel code path. [addSlot] caps those five kinds at one slot; `TIMES_PER_DAY`
 * keeps its own multi-slot behaviour unchanged. No validation requires a slot to be present for any
 * of the five kinds — the ratified decision is that a reminder-less habit MUST still save and stay
 * trackable, never blocked.
 *
 * **[HabitEditorUiState.isDirty] is derived, never latched** (carried-forward item
 * `habit-editor-has-no-cancel-affordance`). The editor's cancel affordance has to know whether
 * anything was actually touched, and the obvious shape — a `dirty = true` line in every field
 * handler — rots the moment a seventh field is added and its handler forgets that line. Instead
 * [pristineForm] holds the exact form the editor opened with (a blank [HabitEditorUiState] for a
 * new habit, the loaded one for an edit) and every mutation funnels through [updateState], which
 * recomputes the flag by comparing the whole state through [formSignature]. The comparison is
 * total: a new field on [HabitEditorUiState] participates automatically, and the only way to
 * exclude one is to name it explicitly in [formSignature]. Editing a field back to its original
 * value therefore makes the form clean again, which a latch could never do.
 */
@HiltViewModel
class HabitEditorViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitEditorUiState())
    val uiState: StateFlow<HabitEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<HabitEditorEvent>(Channel.BUFFERED)
    val events: Flow<HabitEditorEvent> = _events.receiveAsFlow()

    /** The baseline every dirty check compares against — the form as the editor opened it. */
    private var pristineForm: HabitEditorUiState = HabitEditorUiState().formSignature()

    /** The single write path into [_uiState]. Applies [transform], then re-derives
     *  [HabitEditorUiState.isDirty] from the result so the flag can never drift out of step with
     *  the form it describes. */
    private fun updateState(transform: (HabitEditorUiState) -> HabitEditorUiState) {
        _uiState.update { current ->
            val next = transform(current)
            next.copy(isDirty = next.formSignature() != pristineForm)
        }
    }

    /** habit-management: Habit Creation — resets the form to a blank, unsaved new habit. */
    fun startCreate() {
        val blank = HabitEditorUiState()
        pristineForm = blank.formSignature()
        _uiState.value = blank
    }

    /** habit-management: Habit Editing — loads the persisted habit, its [Schedule], and its
     *  persisted [ReminderSlot]s into the form. Loaded unconditionally regardless of [schedule]'s
     *  kind (task 6a.8): `TIMES_PER_DAY` may hold several, the other five kinds hold at most one. */
    fun startEdit(habitId: Long) {
        viewModelScope.launch {
            val habit = habitRepository.findById(habitId) ?: return@launch
            val schedule = habitRepository.findScheduleFor(habitId) ?: Schedule.Daily()
            val slots = habitRepository.findSlotsFor(habitId)
            val loaded = HabitEditorUiState(
                habitId = habit.id,
                name = habit.name,
                colorArgb = habit.colorArgb,
                notes = habit.notes.orEmpty(),
                schedule = schedule,
                slots = slots,
                anchorDateText = if (schedule is Schedule.EveryNDays) schedule.anchor.toString() else "",
            )
            pristineForm = loaded.formSignature()
            _uiState.value = loaded
        }
    }

    fun onNameChange(value: String) = updateState { it.copy(name = value, nameError = false) }

    fun onColorChange(colorArgb: Int) = updateState { it.copy(colorArgb = colorArgb) }

    fun onNotesChange(value: String) = updateState { it.copy(notes = value) }

    /** habit-scheduling: Six Frequency Kinds/N_TIMES_PER_WEEK/WEEKLY/MONTHLY/EVERY_N_DAYS —
     *  dispatches on [ScheduleParamAction]'s variant. An action whose variant does not match the
     *  current [Schedule] subtype (a stale UI event after a kind switch mid-flight) is a no-op. */
    fun onScheduleParamChange(action: ScheduleParamAction) {
        updateState { state ->
            when (action) {
                is ScheduleParamAction.Kind -> applyKindChange(state, action.kind, timeProvider.today())
                is ScheduleParamAction.TimesPerWeek -> applyTimesPerWeek(state, action.times)
                is ScheduleParamAction.DayOfWeek -> applyDayOfWeek(state, action.dayOfWeek)
                is ScheduleParamAction.DayOfMonth -> applyDayOfMonth(state, action.dayOfMonth)
                is ScheduleParamAction.EveryNDays -> applyEveryNDays(state, action.n)
                is ScheduleParamAction.AnchorDate -> applyAnchorDate(state, action.text)
            }
        }
    }

    /** habit-scheduling: Reminder Slots for TIMES_PER_DAY — add/remove/enable/reschedule one slot,
     *  addressed by [HabitEditorUiState.slots] index. */
    fun onSlotAction(action: SlotAction) {
        updateState { state ->
            when (action) {
                SlotAction.Add -> addSlot(state)
                is SlotAction.Remove -> state.copy(slots = state.slots.filterIndexed { i, _ -> i != action.index })
                is SlotAction.SetEnabled -> {
                    val slots = withSlot(state.slots, action.index) { it.copy(enabled = action.enabled) }
                    state.copy(slots = slots)
                }

                is SlotAction.SetTime -> {
                    val bounded = action.minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
                    state.copy(slots = withSlot(state.slots, action.index) { it.copy(minuteOfDay = bounded) })
                }
            }
        }
    }

    /** habit-management: Creation requires a name — rejects the save and never reaches the
     *  repository when the name is blank (including whitespace-only, since a name made only of
     *  spaces identifies nothing any more than an empty one does). Also enforces habit-scheduling's
     *  "`TIMES_PER_DAY` MUST define one or more explicit clock-time ReminderSlots" and a parseable
     *  `EVERY_N_DAYS` anchor, both blocking save the same way a missing name does. */
    fun save() {
        val state = _uiState.value
        val error = validationError(state)
        if (error != null) {
            updateState { error.apply(it) }
            return
        }
        viewModelScope.launch {
            updateState { it.copy(isSaving = true) }
            val habit = Habit(
                id = state.habitId ?: 0L,
                name = state.name.trim(),
                colorArgb = state.colorArgb,
                notes = state.notes.trim().ifBlank { null },
                archived = false,
                archivedAt = null,
                createdAt = timeProvider.now(),
                sortOrder = 0,
            )
            if (state.habitId == null) {
                habitRepository.create(habit, state.schedule, state.slots)
            } else {
                habitRepository.update(habit, state.schedule, state.slots)
            }
            updateState { it.copy(isSaving = false) }
            _events.send(HabitEditorEvent.Saved)
        }
    }
}

/** A new, unsaved slot (`id = 0` sentinel, same convention as [Habit.id]) at a default morning
 *  time. `TIMES_PER_DAY` (habit-scheduling: Reminder Slots for TIMES_PER_DAY) allows any number;
 *  every other kind allows at most one, since it is a single configurable reminder time, not a
 *  slot list (task 6a.8) — a second `Add` while one already exists is a no-op, which is how the UI
 *  (a switch, not a repeatable "Add time" button) already presents that limit. */
private fun addSlot(state: HabitEditorUiState): HabitEditorUiState {
    if (state.schedule !is Schedule.TimesPerDay && state.slots.isNotEmpty()) return state
    val newSlot = ReminderSlot(
        id = 0L,
        habitId = state.habitId ?: 0L,
        minuteOfDay = DEFAULT_MINUTE_OF_DAY,
        enabled = true,
    )
    return state.copy(slots = state.slots + newSlot, slotsError = false)
}

private fun withSlot(slots: List<ReminderSlot>, index: Int, transform: (ReminderSlot) -> ReminderSlot) =
    slots.mapIndexed { i, slot -> if (i == index) transform(slot) else slot }

/** habit-scheduling: Six Frequency Kinds — a sensible starting value for each kind's own
 *  parameter(s) when the user switches into it. */
private fun defaultScheduleFor(kind: ScheduleKind, weekStart: DayOfWeek, today: LocalDate): Schedule = when (kind) {
    ScheduleKind.DAILY -> Schedule.Daily(weekStart)
    ScheduleKind.TIMES_PER_DAY -> Schedule.TimesPerDay(weekStart)
    ScheduleKind.N_TIMES_PER_WEEK -> Schedule.NTimesPerWeek(DEFAULT_TIMES_PER_WEEK, weekStart)
    ScheduleKind.WEEKLY -> Schedule.Weekly(DayOfWeek.MONDAY, weekStart)
    ScheduleKind.MONTHLY -> Schedule.Monthly(MIN_DAY_OF_MONTH, weekStart)
    ScheduleKind.EVERY_N_DAYS -> Schedule.EveryNDays(DEFAULT_EVERY_N_DAYS, today, weekStart)
}

/** Extracted so [HabitEditorViewModel.onScheduleParamChange]'s `when` stays a plain dispatch —
 *  each of these six carries its own branch's cyclomatic cost instead of piling it all into one
 *  function (top-level, not a class member, so it does not count toward `TooManyFunctions` either).
 *  Task 6a.8: the single reminder time carries over across a switch between two non-`TIMES_PER_DAY`
 *  kinds (e.g. DAILY to WEEKLY) — it is the same concept in both. It is cleared entering or leaving
 *  `TIMES_PER_DAY`, whose multi-slot editor has different semantics the single time cannot represent. */
private fun applyKindChange(state: HabitEditorUiState, kind: ScheduleKind, today: LocalDate): HabitEditorUiState {
    val newSchedule = defaultScheduleFor(kind, state.schedule.weekStart, today)
    val keepsSingleReminderTime = kind != ScheduleKind.TIMES_PER_DAY && state.schedule !is Schedule.TimesPerDay
    return state.copy(
        schedule = newSchedule,
        slots = if (keepsSingleReminderTime) state.slots else emptyList(),
        anchorDateText = if (kind == ScheduleKind.EVERY_N_DAYS) today.toString() else "",
        slotsError = false,
        anchorDateError = false,
    )
}

private fun applyTimesPerWeek(state: HabitEditorUiState, times: Int): HabitEditorUiState {
    val schedule = state.schedule as? Schedule.NTimesPerWeek ?: return state
    return state.copy(schedule = schedule.copy(times = times.coerceAtLeast(MIN_POSITIVE)))
}

private fun applyDayOfWeek(state: HabitEditorUiState, dayOfWeek: DayOfWeek): HabitEditorUiState {
    val schedule = state.schedule as? Schedule.Weekly ?: return state
    return state.copy(schedule = schedule.copy(dayOfWeek = dayOfWeek))
}

private fun applyDayOfMonth(state: HabitEditorUiState, dayOfMonth: Int): HabitEditorUiState {
    val schedule = state.schedule as? Schedule.Monthly ?: return state
    val bounded = dayOfMonth.coerceIn(MIN_DAY_OF_MONTH, MAX_DAY_OF_MONTH)
    return state.copy(schedule = schedule.copy(dayOfMonth = bounded))
}

private fun applyEveryNDays(state: HabitEditorUiState, n: Int): HabitEditorUiState {
    val schedule = state.schedule as? Schedule.EveryNDays ?: return state
    return state.copy(schedule = schedule.copy(n = n.coerceAtLeast(MIN_POSITIVE)))
}

private fun applyAnchorDate(state: HabitEditorUiState, text: String): HabitEditorUiState {
    val schedule = state.schedule as? Schedule.EveryNDays ?: return state
    val parsed = text.toLocalDateOrNull()
    return state.copy(
        anchorDateText = text,
        schedule = if (parsed != null) schedule.copy(anchor = parsed) else schedule,
        anchorDateError = false,
    )
}

/** The six frequency kinds a habit's [Schedule] can take (habit-scheduling: Six Frequency Kinds) —
 *  a UI-facing, stable identifier for the schedule-kind picker; [Schedule] itself has no such
 *  discriminator beyond its own sealed subtype. */
enum class ScheduleKind {
    DAILY,
    TIMES_PER_DAY,
    N_TIMES_PER_WEEK,
    WEEKLY,
    MONTHLY,
    EVERY_N_DAYS,
}

val Schedule.kind: ScheduleKind
    get() = when (this) {
        is Schedule.Daily -> ScheduleKind.DAILY
        is Schedule.TimesPerDay -> ScheduleKind.TIMES_PER_DAY
        is Schedule.NTimesPerWeek -> ScheduleKind.N_TIMES_PER_WEEK
        is Schedule.Weekly -> ScheduleKind.WEEKLY
        is Schedule.Monthly -> ScheduleKind.MONTHLY
        is Schedule.EveryNDays -> ScheduleKind.EVERY_N_DAYS
    }

/** One action per editable schedule parameter across the six kinds — [HabitEditorScreen] sends
 *  these through a single callback instead of one lambda per field. */
sealed interface ScheduleParamAction {
    data class Kind(val kind: ScheduleKind) : ScheduleParamAction
    data class TimesPerWeek(val times: Int) : ScheduleParamAction
    data class DayOfWeek(val dayOfWeek: java.time.DayOfWeek) : ScheduleParamAction
    data class DayOfMonth(val dayOfMonth: Int) : ScheduleParamAction
    data class EveryNDays(val n: Int) : ScheduleParamAction
    data class AnchorDate(val text: String) : ScheduleParamAction
}

/** One action per `TIMES_PER_DAY` reminder-slot edit, addressed by [HabitEditorUiState.slots]
 *  index — same single-callback reasoning as [ScheduleParamAction]. */
sealed interface SlotAction {
    data object Add : SlotAction
    data class Remove(val index: Int) : SlotAction
    data class SetEnabled(val index: Int, val enabled: Boolean) : SlotAction
    data class SetTime(val index: Int, val minuteOfDay: Int) : SlotAction
}

sealed interface HabitEditorEvent {
    data object Saved : HabitEditorEvent
}
