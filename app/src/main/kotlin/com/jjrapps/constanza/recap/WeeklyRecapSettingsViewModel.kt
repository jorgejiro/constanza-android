package com.jjrapps.constanza.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** weekly-recap S8: the Settings side of [WeeklyRecapSettingsStore]. */
@HiltViewModel
class WeeklyRecapSettingsViewModel @Inject constructor(
    private val settingsStore: WeeklyRecapSettingsStore,
) : ViewModel() {
    val enabled: StateFlow<Boolean> =
        settingsStore.enabled.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_WEEKLY_RECAP_ENABLED)

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setEnabled(enabled) }
    }
}
