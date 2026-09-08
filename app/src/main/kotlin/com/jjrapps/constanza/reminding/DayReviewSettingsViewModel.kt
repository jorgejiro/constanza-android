package com.jjrapps.constanza.reminding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import com.jjrapps.constanza.scheduling.WorkScheduler
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
 * [WorkScheduler.scheduleDayReview] slice B already anchors.
 *
 * Mirrors [SnoozeSettingsViewModel]'s own shape: no locally-cached copy of either setting, every
 * read comes straight from [settingsStore]'s own flows, combined into one [uiState] so the
 * presentational composable reads one flow rather than two.
 *
 * Only [setReviewTimeMinuteOfDay] re-anchors the job afterwards, with [ExistingWorkPolicy.REPLACE]
 * — [WorkScheduler.scheduleDayReview]'s own KDoc calls out exactly this call site as the one a
 * settings screen must use once it exists, and `REPLACE` for the same reason
 * [WorkScheduler.scheduleNextDayReview] uses it: an already-pending request must be replaced, not
 * merely kept. [setReviewFiresEveryNight] changes what [DayReviewWorker] does once it runs, not
 * when it runs, so it never needs to touch [WorkScheduler] at all.
 */
@HiltViewModel
class DayReviewSettingsViewModel @Inject constructor(
    private val settingsStore: DayReviewSettingsStore,
    private val workScheduler: WorkScheduler,
) : ViewModel() {

    val uiState: StateFlow<DayReviewSettingsUiState> = combine(
        settingsStore.reviewTimeMinuteOfDay,
        settingsStore.reviewFiresEveryNight,
        ::DayReviewSettingsUiState,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, DayReviewSettingsUiState())

    fun setReviewTimeMinuteOfDay(minuteOfDay: Int) {
        viewModelScope.launch {
            settingsStore.setReviewTimeMinuteOfDay(minuteOfDay)
            workScheduler.scheduleDayReview(ExistingWorkPolicy.REPLACE)
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
