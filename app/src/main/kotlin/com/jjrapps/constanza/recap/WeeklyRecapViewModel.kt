package com.jjrapps.constanza.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.domain.startOfWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** weekly-recap: [recap] is `null` until [WeeklyRecapViewModel.load] resolves. The week is past, so
 *  it is read once rather than observed — an answer changed later from Today's past-day view shows
 *  on reopen. */
data class WeeklyRecapUiState(val recap: LoadedWeeklyRecap? = null)

/** Same `load(argument)` shape as [com.jjrapps.constanza.progress.ProgressViewModel]: there is no
 *  navigation library, so the week arrives from the route once it resolves. */
@HiltViewModel
class WeeklyRecapViewModel @Inject constructor(
    private val loader: WeeklyRecapLoader,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val state = MutableStateFlow(WeeklyRecapUiState())
    val uiState: StateFlow<WeeklyRecapUiState> = state.asStateFlow()

    /** [weekStart] is the week a notification named; `null` (S7, opened from Today) means the last
     *  whole Monday-to-Sunday week before today. */
    fun load(weekStart: LocalDate?) {
        val week = weekStart ?: startOfWeek(timeProvider.today(), WEEKLY_RECAP_DAY).minusWeeks(1)
        if (state.value.recap?.weekStart == week) return
        viewModelScope.launch { state.value = WeeklyRecapUiState(loader.load(week)) }
    }
}
