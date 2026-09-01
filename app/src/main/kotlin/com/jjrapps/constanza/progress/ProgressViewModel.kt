@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.jjrapps.constanza.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.mapper.toDomain
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.domain.ComplianceCalculator
import com.jjrapps.constanza.domain.StreakCalculator
import com.jjrapps.constanza.habit.HabitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Task 6b.4 (habit-progress: Streak Calculation, Compliance Calculation). `:domain`'s
 *  `StreakCalculator`/`ComplianceCalculator` are called with `windowDays = `[PROGRESS_WINDOW_DAYS]
 *  (OA-4, ratified 2026-09-01, design.md §1: a fixed 30-day window in the MVP UI — the calculator
 *  itself stays parameterised, so a user-selectable window remains a cheap later addition). Neither
 *  calculator is reimplemented here; this ViewModel only supplies the `Entry` history and the
 *  `Schedule` they read.
 *
 *  [load] mirrors [com.jjrapps.constanza.habit.HabitEditorViewModel]'s `startEdit(habitId)` shape:
 *  the habit id is not available at construction (no navigation library, task 6a's own decision),
 *  so the caller supplies it once navigation resolves it. */
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val entryDao: EntryDao,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val habitId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<ProgressUiState> = habitId.filterNotNull()
        .flatMapLatest { id ->
            entryDao.observeByHabitId(id).map { entities -> buildState(id, entities) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProgressUiState())

    fun load(id: Long) {
        habitId.value = id
    }

    private suspend fun buildState(id: Long, entities: List<EntryEntity>): ProgressUiState {
        val habit = habitRepository.findById(id) ?: return ProgressUiState()
        val schedule = habitRepository.findScheduleFor(id) ?: return ProgressUiState(habitName = habit.name)
        val entries = entities.map { it.toDomain() }
        val today = timeProvider.today()
        return ProgressUiState(
            habitName = habit.name,
            currentStreak = StreakCalculator.current(schedule, entries, today),
            bestStreak = StreakCalculator.best(schedule, entries, today),
            complianceRatio = ComplianceCalculator.ratio(schedule, entries, today, PROGRESS_WINDOW_DAYS),
            loaded = true,
        )
    }

    private companion object {
        const val PROGRESS_WINDOW_DAYS = 30
    }
}

data class ProgressUiState(
    val habitName: String = "",
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val complianceRatio: Double = 0.0,
    val loaded: Boolean = false,
)
