package com.jjrapps.constanza.reminding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Task 6b.5 (reminder-response: Snooze Configuration and Re-arm) — bound to
 *  [ReminderSettingsStore], the same DataStore entry task 5.6 already built. No second source of
 *  truth: this ViewModel reads/writes [ReminderSettingsStore.snoozeDuration] directly, never a
 *  locally-cached copy of the default. */
@HiltViewModel
class SnoozeSettingsViewModel @Inject constructor(
    private val settingsStore: ReminderSettingsStore,
) : ViewModel() {

    val currentDuration: StateFlow<SnoozeDuration> = settingsStore.snoozeDuration
        .stateIn(viewModelScope, SharingStarted.Eagerly, SnoozeDuration.DEFAULT)

    fun select(duration: SnoozeDuration) {
        viewModelScope.launch { settingsStore.setSnoozeDuration(duration) }
    }
}
