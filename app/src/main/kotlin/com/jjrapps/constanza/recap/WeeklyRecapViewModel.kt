package com.jjrapps.constanza.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** weekly-recap: [recap] is `null` until [load] resolves. The week is past, so it is read once
 *  rather than observed — an answer changed later from Today's past-day view shows on reopen. */
data class WeeklyRecapUiState(val recap: LoadedWeeklyRecap? = null)

/** Same `load(argument)` shape as [com.jjrapps.constanza.progress.ProgressViewModel]: there is no
 *  navigation library, so the week arrives from the route once it resolves. */
@HiltViewModel
class WeeklyRecapViewModel @Inject constructor(private val loader: WeeklyRecapLoader) : ViewModel() {

    private val state = MutableStateFlow(WeeklyRecapUiState())
    val uiState: StateFlow<WeeklyRecapUiState> = state.asStateFlow()

    fun load(weekStart: LocalDate) {
        if (state.value.recap?.weekStart == weekStart) return
        viewModelScope.launch { state.value = WeeklyRecapUiState(loader.load(weekStart)) }
    }
}
