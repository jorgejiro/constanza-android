package com.jjrapps.constanza.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.dao.ReminderOccurrenceDao
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.habit.HabitRepository
import com.jjrapps.constanza.scheduling.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Task 6b.1 (habit-entry-tracking: Day-Level Rollup and Per-Slot Display). [expandedHabitIds]
 *  is presented state only — which multi-slot rows the user opened — never persisted, since it is
 *  meaningless once the day rolls over. [answer] is the ONLY write path this screen uses; it
 *  always goes through [entryWriter] (task 6b.2), the same one the notification action route
 *  uses. [canScheduleExactAlarms] backs task 6b.9's non-blocking banner (design §12/§13.1); it is
 *  re-read via [refreshExactAlarmPermission], since the system permission can change while this
 *  screen is paused (the user granting it from Settings) with no Room write to react to. */
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val entryDao: EntryDao,
    private val reminderOccurrenceDao: ReminderOccurrenceDao,
    private val entryWriter: EntryWriter,
    private val alarmScheduler: AlarmScheduler,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val today = timeProvider.today()
    private val expandedHabitIds = MutableStateFlow<Set<Long>>(emptySet())
    private val canScheduleExactAlarms = MutableStateFlow(alarmScheduler.canScheduleExactAlarms())

    val uiState: StateFlow<TodayUiState> = combine(
        habitRepository.observeAll(),
        entryDao.observeByDate(today.toString()),
        reminderOccurrenceDao.observeUnresolved(),
        expandedHabitIds,
        canScheduleExactAlarms,
    ) { habits, entriesToday, unresolved, expanded, exactAlarms ->
        val snapshot = TodaySnapshot(entriesToday, unresolved, today)
        val rows = habits.filterNot { it.archived }.mapNotNull { habit ->
            val schedule = habitRepository.findScheduleFor(habit.id) ?: return@mapNotNull null
            val slots = habitRepository.findSlotsFor(habit.id)
            buildTodayHabitRow(habit, schedule, slots, snapshot)
        }
        TodayUiState(
            rows = rows,
            expandedHabitIds = expanded,
            zone = timeProvider.zone(),
            canScheduleExactAlarms = exactAlarms,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TodayUiState(zone = timeProvider.zone()))

    fun toggleExpanded(habitId: Long) = expandedHabitIds.update {
        if (habitId in it) it - habitId else it + habitId
    }

    fun answer(habitId: Long, slot: TodaySlot, status: InAppEntryStatus) {
        viewModelScope.launch {
            entryWriter.answerInApp(habitId, today, slot.slotId, status, slot.occurrenceId)
        }
    }

    /** Called from [TodayRoute] on `ON_RESUME` — the permission may have changed while this
     *  screen was paused (task 6b.9). */
    fun refreshExactAlarmPermission() {
        canScheduleExactAlarms.value = alarmScheduler.canScheduleExactAlarms()
    }
}

data class TodayUiState(
    val rows: List<TodayHabitRow> = emptyList(),
    val expandedHabitIds: Set<Long> = emptySet(),
    val zone: ZoneId = ZoneId.of("UTC"),
    val canScheduleExactAlarms: Boolean = true,
)
