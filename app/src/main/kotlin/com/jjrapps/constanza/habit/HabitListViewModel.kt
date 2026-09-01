package com.jjrapps.constanza.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjrapps.constanza.domain.model.Habit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Task 6a.4 (habit-management: Habit Archiving; habit-scheduling n/a). [showArchived] is the
 * list's archived filter: `false` shows the active list, `true` shows only archived habits — the
 * two are never mixed, so archiving or un-archiving a habit always moves it out of the currently
 * visible filter, which is what the round-trip test in this slice exercises.
 */
@HiltViewModel
class HabitListViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
) : ViewModel() {

    private val showArchived = MutableStateFlow(false)

    // Eagerly, not WhileSubscribed: this is a small single-Activity app with no back stack to
    // suspend collection for, and eager sharing keeps state deterministic to test — the ViewModel
    // itself is already lifecycle-scoped, so there is no meaningful lazy-start benefit here.
    val uiState: StateFlow<HabitListUiState> =
        combine(habitRepository.observeAll(), showArchived) { habits, archivedFilter ->
            HabitListUiState(
                habits = habits.filter { it.archived == archivedFilter },
                showArchived = archivedFilter,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, HabitListUiState())

    fun toggleShowArchived() = showArchived.update { !it }

    fun setArchived(habitId: Long, archived: Boolean) {
        viewModelScope.launch { habitRepository.setArchived(habitId, archived) }
    }
}

data class HabitListUiState(
    val habits: List<Habit> = emptyList(),
    val showArchived: Boolean = false,
)
