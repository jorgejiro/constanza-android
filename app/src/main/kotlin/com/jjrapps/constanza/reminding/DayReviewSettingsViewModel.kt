package com.jjrapps.constanza.reminding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjrapps.constanza.scheduling.DayReviewAlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * day-review, slice C (day-review-notification): the settings screen's half of
 * [DayReviewSettingsStore] — the store slice A already built, and the schedule
 * [DayReviewAlarmScheduler.scheduleNext] (day-review-exact-alarm) now anchors.
 *
 * Mirrors [SnoozeSettingsViewModel]'s own shape: no locally-cached copy of either setting, every
 * read comes straight from [settingsStore]'s own flows, combined into one [uiState] so the
 * presentational composable reads one flow rather than two.
 *
 * Only [setReviewTimeMinuteOfDay] re-anchors the alarm afterwards — an already-armed alarm is
 * replaced by [DayReviewAlarmScheduler.scheduleNext]'s own `PendingIntent.FLAG_UPDATE_CURRENT` re-arm,
 * so no separate policy argument is needed the way the deleted `WorkManager` path required one.
 * [setReviewFiresEveryNight] changes what [com.jjrapps.constanza.scheduling.DayReviewFireWorker] does
 * once it runs, not when it runs, so it never needs to touch [DayReviewAlarmScheduler] at all.
 */
@HiltViewModel
class DayReviewSettingsViewModel @Inject constructor(
    private val settingsStore: DayReviewSettingsStore,
    private val dayReviewAlarmScheduler: DayReviewAlarmScheduler,
) : ViewModel() {

    val uiState: StateFlow<DayReviewSettingsUiState> = combine(
        settingsStore.reviewTimeMinuteOfDay,
        settingsStore.reviewFiresEveryNight,
        ::DayReviewSettingsUiState,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, DayReviewSettingsUiState())

    fun setReviewTimeMinuteOfDay(minuteOfDay: Int) {
        viewModelScope.launch {
            settingsStore.setReviewTimeMinuteOfDay(minuteOfDay)
            dayReviewAlarmScheduler.scheduleNext()
        }
    }

    fun setReviewFiresEveryNight(fireEveryNight: Boolean) {
        viewModelScope.launch { settingsStore.setReviewFiresEveryNight(fireEveryNight) }
    }
}

/** [DayReviewSettingsViewModel.uiState]'s shape, defaulting to exactly
 *  [DayReviewSettingsStore]'s own two defaults so the very first frame — before either flow has
 *  emitted — already matches what the store itself would report. */
data class DayReviewSettingsUiState(
    val reviewTimeMinuteOfDay: Int = DEFAULT_REVIEW_TIME_MINUTE_OF_DAY,
    val reviewFiresEveryNight: Boolean = DEFAULT_REVIEW_FIRES_EVERY_NIGHT,
)
