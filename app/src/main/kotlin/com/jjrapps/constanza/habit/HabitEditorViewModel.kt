package com.jjrapps.constanza.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.domain.model.Habit
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
import javax.inject.Inject

/**
 * Task 6a.1 (non-schedule half)/6a.2/6a.3 (habit-management: Habit Creation, Habit Editing;
 * Creation requires a name). The schedule-kind picker and `TIMES_PER_DAY` slot editor are slice
 * ii's scope (tasks 6a.1's remainder); this slice fixes every new habit's [Schedule] to
 * [Schedule.Daily] and, when editing, preserves whatever [Schedule] is already persisted rather
 * than exposing it for change — [save] still round-trips it through [HabitRepository.update] so
 * 6a.3's replan wiring is real end to end, not stubbed pending slice ii.
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

    /** habit-management: Habit Creation — resets the form to a blank, unsaved new habit. */
    fun startCreate() {
        _uiState.value = HabitEditorUiState()
    }

    /** habit-management: Habit Editing — loads the persisted habit and its (currently read-only
     *  in this slice) schedule into the form. */
    fun startEdit(habitId: Long) {
        viewModelScope.launch {
            val habit = habitRepository.findById(habitId) ?: return@launch
            val schedule = habitRepository.findScheduleFor(habitId) ?: Schedule.Daily()
            _uiState.value = HabitEditorUiState(
                habitId = habit.id,
                name = habit.name,
                question = habit.question.orEmpty(),
                colorArgb = habit.colorArgb,
                notes = habit.notes.orEmpty(),
                schedule = schedule,
            )
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = false) }

    fun onQuestionChange(value: String) = _uiState.update { it.copy(question = value) }

    fun onColorChange(colorArgb: Int) = _uiState.update { it.copy(colorArgb = colorArgb) }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }

    /** habit-management: Creation requires a name — rejects the save and never reaches the
     *  repository when the name is blank (including whitespace-only, since a name made only of
     *  spaces identifies nothing any more than an empty one does). */
    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            persist(state)
            _uiState.update { it.copy(isSaving = false) }
            _events.send(HabitEditorEvent.Saved)
        }
    }

    private suspend fun persist(state: HabitEditorUiState) {
        val habit = Habit(
            id = state.habitId ?: 0L,
            name = state.name.trim(),
            question = state.question.trim().ifBlank { null },
            colorArgb = state.colorArgb,
            notes = state.notes.trim().ifBlank { null },
            archived = false,
            archivedAt = null,
            createdAt = timeProvider.now(),
            sortOrder = 0,
        )
        if (state.habitId == null) {
            habitRepository.create(habit, state.schedule)
        } else {
            habitRepository.update(habit, state.schedule)
        }
    }
}

data class HabitEditorUiState(
    val habitId: Long? = null,
    val name: String = "",
    val question: String = "",
    val colorArgb: Int = HabitColorPalette.DEFAULT,
    val notes: String = "",
    val schedule: Schedule = Schedule.Daily(),
    val nameError: Boolean = false,
    val isSaving: Boolean = false,
)

sealed interface HabitEditorEvent {
    data object Saved : HabitEditorEvent
}

/** A small fixed swatch set — a full colour picker is out of scope for this slice. */
object HabitColorPalette {
    val SWATCHES = listOf(
        0xFF00897B.toInt(),
        0xFF1E88E5.toInt(),
        0xFFE53935.toInt(),
        0xFFFB8C00.toInt(),
        0xFF8E24AA.toInt(),
        0xFF43A047.toInt(),
    )
    val DEFAULT = SWATCHES.first()
}
