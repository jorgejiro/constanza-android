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
import java.time.LocalDate
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
        val archivedAt = habit.archivedAt
        val today = effectiveToday(timeProvider.today(), archivedAt)
        val entries = entities.map { it.toDomain() }
            .filter { archivedAt == null || it.date.isBefore(archivedAt) }
        return ProgressUiState(
            habitName = habit.name,
            currentStreak = StreakCalculator.current(schedule, entries, today),
            bestStreak = StreakCalculator.best(schedule, entries, today),
            complianceRatio = ComplianceCalculator.ratio(schedule, entries, today, PROGRESS_WINDOW_DAYS),
            loaded = true,
        )
    }

    /**
     * habit-management: Habit Archiving — an archived habit MUST be excluded from streak and
     * compliance calculations for any date on or after the archive date. [ComplianceCalculator]
     * and [StreakCalculator] stay pure and take no archive parameter; the boundary is expressed
     * here instead, the one place that already holds the habit's own `archivedAt`.
     *
     * Two things happen at the call site above: entries dated on or after [archivedAt] are
     * dropped before either calculator sees them (closes the boundary an import or a same-day
     * answer could otherwise leak through), and `today` itself is clamped here so the window a
     * calculator derives from it — the compliance window's start, the streak walk's upper bound —
     * never extends past the archive date either.
     *
     * Clamping means something different for each of the three calls that share this `today`:
     * - **Current streak** freezes at what it was the instant the habit was archived. A live
     *   habit's current streak is an ongoing fact; an archived one is closed history, and reading
     *   it against real "today" would silently resurrect a retired habit's streak as if still
     *   running — worse, for an `N_TIMES_PER_WEEK` schedule it would actively zero it, since every
     *   entry-less week the walk crosses after archiving reads as a missed quota, not a neutral
     *   gap (unlike a daily schedule's `UNKNOWN`, which passes through without breaking).
     * - **Best streak** is unaffected numerically by the clamp — no entry exists on or after
     *   [archivedAt] to extend it further, so the recorded maximum was already reached before the
     *   walk would reach the excluded range. Clamping it anyway keeps one `today` value threaded
     *   through every calculator call rather than special-casing one of the three.
     * - **Compliance ratio**'s window is anchored to `today`; without clamping, a habit archived
     *   long ago would compute its 30-day window against the real present, shifting the entire
     *   window past its own history and reading an empty, always-zero ratio instead of the
     *   pre-archive window the spec's scenario describes.
     */
    private fun effectiveToday(today: LocalDate, archivedAt: LocalDate?): LocalDate =
        if (archivedAt != null) minOf(today, archivedAt.minusDays(1)) else today

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
