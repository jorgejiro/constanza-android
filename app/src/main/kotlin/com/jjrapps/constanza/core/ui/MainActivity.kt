package com.jjrapps.constanza.core.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jjrapps.constanza.core.ui.theme.ConstanzaTheme
import com.jjrapps.constanza.habit.HabitEditorRoute
import com.jjrapps.constanza.habit.HabitListRoute
import com.jjrapps.constanza.scheduling.ReplanOnResumeObserver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The single launcher Activity. Hosts the habit list and editor (work unit 6a, design.md §14);
 * the Today screen is implemented in work unit 6b.
 *
 * The one non-UI thing it does is register design.md §5.5/§13.1's `onResume()` re-check
 * (task G.5). The decision logic lives in [ReplanOnResumeObserver], not here.
 *
 * Still deferred to work unit 6b, and deliberately NOT built here: §13.1's non-blocking banner
 * explaining that reminders may arrive late, with one tap to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var replanOnResumeObserver: ReplanOnResumeObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(replanOnResumeObserver)
        setContent {
            ConstanzaTheme {
                ConstanzaApp()
            }
        }
    }
}

/**
 * Task 6a's navigation decision: no navigation library. A list plus one editor is small enough
 * that hoisted [ConstanzaRoute] state in this single Activity is defensible and cheaper than
 * adding a navigation dependency — work unit 6b adds more screens and can revisit (design.md §14).
 */
private sealed interface ConstanzaRoute {
    data object HabitList : ConstanzaRoute
    data class HabitEditor(val habitId: Long?) : ConstanzaRoute
}

@Composable
private fun ConstanzaApp() {
    var route by remember { mutableStateOf<ConstanzaRoute>(ConstanzaRoute.HabitList) }
    when (val current = route) {
        is ConstanzaRoute.HabitList -> HabitListRoute(
            onCreateHabit = { route = ConstanzaRoute.HabitEditor(habitId = null) },
            onEditHabit = { habitId -> route = ConstanzaRoute.HabitEditor(habitId) },
        )

        is ConstanzaRoute.HabitEditor -> HabitEditorRoute(
            habitId = current.habitId,
            onDone = { route = ConstanzaRoute.HabitList },
        )
    }
}
