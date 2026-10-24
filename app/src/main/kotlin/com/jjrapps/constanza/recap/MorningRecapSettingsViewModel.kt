package com.jjrapps.constanza.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjrapps.constanza.scheduling.MorningRecapAlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** day-review-and-morning-recap (S6): the settings screen's half of [MorningRecapSettingsStore],
 *  shaped like [com.jjrapps.constanza.reminding.DayReviewSettingsViewModel] — only a time change
 *  re-anchors the alarm; the switch only decides whether the fire path posts. */
@HiltViewModel
class MorningRecapSettingsViewModel @Inject constructor(
    private val settingsStore: MorningRecapSettingsStore,
    private val alarmScheduler: MorningRecapAlarmScheduler,
) : ViewModel() {

    val uiState: StateFlow<MorningRecapSettingsUiState> = combine(
        settingsStore.enabled,
        settingsStore.timeMinuteOfDay,
        ::MorningRecapSettingsUiState,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, MorningRecapSettingsUiState())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setEnabled(enabled) }
    }

    fun setTimeMinuteOfDay(minuteOfDay: Int) {
        viewModelScope.launch {
            settingsStore.setTimeMinuteOfDay(minuteOfDay)
            alarmScheduler.scheduleNext()
        }
    }
}

data class MorningRecapSettingsUiState(
    val enabled: Boolean = DEFAULT_MORNING_RECAP_ENABLED,
    val timeMinuteOfDay: Int = DEFAULT_MORNING_RECAP_MINUTE_OF_DAY,
)
