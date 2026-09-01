@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jjrapps.constanza.progress

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjrapps.constanza.R
import kotlin.math.roundToInt

private const val PERCENT_MULTIPLIER = 100

/** Task 6b.4 — container. [habitId] is resolved once (no navigation library, task 6a's own
 *  decision, see [ProgressViewModel]'s KDoc); this screen is single-purpose so no
 *  `hasInitialized`/`rememberSaveable` guard is needed the way the editor's is — a fresh [load]
 *  call for the same id is a cheap no-op re-collection, not a content-loss risk. */
@Composable
fun ProgressRoute(habitId: Long, onBack: () -> Unit, viewModel: ProgressViewModel = hiltViewModel()) {
    LaunchedEffect(habitId) { viewModel.load(habitId) }
    val state by viewModel.uiState.collectAsState()
    ProgressScreen(state, onBack)
}

/** Presentational: state in, one callback out — this screen has no writes (habit-progress: Streak
 *  Calculation, Compliance Calculation are both compute-on-read, no I/O). */
@Composable
fun ProgressScreen(state: ProgressUiState, onBack: () -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.habitName.ifEmpty { stringResource(R.string.progress_title) }) },
                actions = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (!state.loaded) {
                Text(stringResource(R.string.progress_no_schedule))
                return@Column
            }
            Text(stringResource(R.string.progress_current_streak, state.currentStreak))
            Text(stringResource(R.string.progress_best_streak, state.bestStreak))
            val compliancePercent = (state.complianceRatio * PERCENT_MULTIPLIER).roundToInt()
            Text(stringResource(R.string.progress_compliance, compliancePercent))
        }
    }
}
