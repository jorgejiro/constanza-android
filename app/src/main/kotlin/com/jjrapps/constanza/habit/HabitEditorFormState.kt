package com.jjrapps.constanza.habit

import com.jjrapps.constanza.core.ui.theme.HabitPalette
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import java.time.LocalDate

/**
 * The habit editor's form: the state itself, the rule that decides whether it may be saved, and
 * the rule that decides whether it has been touched. Split out of `HabitEditorViewModel.kt` when
 * the dirty-state derivation was added (carried-forward item
 * `habit-editor-has-no-cancel-affordance`) and that file crossed detekt's `TooManyFunctions`
 * threshold at both the file and the class level. The split is the fix rather than a raised
 * threshold: none of what follows touches the repository, the clock, or coroutines, so it is
 * pure form logic that the ViewModel calls, and it reads better beside the state it operates on
 * than beside the lifecycle plumbing.
 */
data class HabitEditorUiState(
    val habitId: Long? = null,
    val name: String = "",
    val question: String = "",
    val colorArgb: Int = HabitPalette.DEFAULT,
    val notes: String = "",
    val schedule: Schedule = Schedule.Daily(),
    val slots: List<ReminderSlot> = emptyList(),
    val anchorDateText: String = "",
    val nameError: Boolean = false,
    val slotsError: Boolean = false,
    val anchorDateError: Boolean = false,
    val isSaving: Boolean = false,
    /** Derived by [HabitEditorViewModel], never written by a field handler — see that class's
     *  KDoc. `true` once the form differs from the one the editor opened with, which is what the
     *  cancel affordance uses to decide between leaving silently and asking to discard. */
    val isDirty: Boolean = false,
)

/**
 * The comparable half of [HabitEditorUiState]: everything the *user* can edit, with every
 * non-form field flattened to a fixed value so it cannot influence the comparison.
 *
 * The exclusion list is deliberately the short one. Validation flags and [HabitEditorUiState.isSaving]
 * are the editor's own transient bookkeeping, and [HabitEditorUiState.isDirty] is the answer being
 * computed, so including it would make the comparison self-referential. Everything else — `name`,
 * `question`, `colorArgb`, `notes`, `schedule`, `slots`, `anchorDateText`, and any field added
 * after this was written — counts as content by default. That is the point: a seventh form field
 * is covered the moment it is declared, without anyone remembering to update this function.
 */
internal fun HabitEditorUiState.formSignature(): HabitEditorUiState = copy(
    nameError = false,
    slotsError = false,
    anchorDateError = false,
    isSaving = false,
    isDirty = false,
)

/** habit-management: Creation requires a name — `null` when the form may be saved, otherwise the
 *  error to project onto it. A name made only of spaces identifies nothing any more than an empty
 *  one does, so it is rejected the same way. Also enforces habit-scheduling's "`TIMES_PER_DAY` MUST
 *  define one or more explicit clock-time ReminderSlots" and a parseable `EVERY_N_DAYS` anchor. */
internal fun validationError(state: HabitEditorUiState): SaveValidationError? = when {
    state.name.isBlank() -> SaveValidationError.NameBlank
    state.schedule is Schedule.TimesPerDay && state.slots.isEmpty() -> SaveValidationError.SlotsEmpty
    state.schedule is Schedule.EveryNDays && state.anchorDateText.toLocalDateOrNull() == null ->
        SaveValidationError.InvalidAnchor

    else -> null
}

internal sealed interface SaveValidationError {
    fun apply(state: HabitEditorUiState): HabitEditorUiState

    data object NameBlank : SaveValidationError {
        override fun apply(state: HabitEditorUiState) = state.copy(nameError = true)
    }

    data object SlotsEmpty : SaveValidationError {
        override fun apply(state: HabitEditorUiState) = state.copy(slotsError = true)
    }

    data object InvalidAnchor : SaveValidationError {
        override fun apply(state: HabitEditorUiState) = state.copy(anchorDateError = true)
    }
}

internal fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
